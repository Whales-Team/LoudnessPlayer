import XCTest
@testable import LoudnessPlayer

final class FileImporterTests: XCTestCase {
    func testImportsAllNineSupportedFormatsRecursivelyWithoutCopyingAudio() async throws {
        let root = try temporaryDirectory()
        let nested = root.appendingPathComponent("专辑")
        try FileManager.default.createDirectory(at: nested, withIntermediateDirectories: true)
        let names = ["a.mp3", "b.flac", "c.wav", "d.ape", "e.m4a", "f.aac", "g.ogg", "h.opus", "i.wma"]
        for (index, name) in names.enumerated() {
            let destination = (index.isMultiple(of: 2) ? root : nested).appendingPathComponent(name)
            try Data(repeating: UInt8(index + 1), count: 256).write(to: destination)
        }
        try Data([1]).write(to: root.appendingPathComponent("cover.jpg"))
        try Data([1]).write(to: root.appendingPathComponent(".hidden.mp3"))
        let bookmarks = RecordingBookmarkCreator()
        let importer = FileImporter(
            metadataReader: FileNameMetadataReader(), bookmarkCreator: bookmarks,
            fingerprinting: StableFingerprinting()
        )

        let result = await importer.importDirectory(root, existing: [])

        XCTAssertEqual(Set(result.imported.map(\.format)), Set(AudioFormat.allCases))
        XCTAssertEqual(result.imported.count, 9)
        XCTAssertEqual(bookmarks.urls.count, 9)
        XCTAssertTrue(result.imported.allSatisfy { !$0.bookmark.isEmpty })
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.appendingPathComponent("Application Support").path))
    }

    func testSkipsBadFilesIndividuallyAndSummarizesFailures() async throws {
        let root = try temporaryDirectory()
        let good = root.appendingPathComponent("good.mp3")
        let empty = root.appendingPathComponent("empty.flac")
        let corrupt = root.appendingPathComponent("corrupt.ape")
        try Data(repeating: 7, count: 128).write(to: good)
        try Data().write(to: empty)
        try Data(repeating: 9, count: 128).write(to: corrupt)
        let reader = SelectiveMetadataReader(failingNames: ["corrupt.ape"])
        let importer = FileImporter(
            metadataReader: reader, bookmarkCreator: RecordingBookmarkCreator(),
            fingerprinting: StableFingerprinting()
        )

        let result = await importer.importFiles([good, empty, corrupt], existing: [])

        XCTAssertEqual(result.imported.map(\.fileName), ["good.mp3"])
        XCTAssertEqual(result.failures.count, 2)
        XCTAssertTrue(result.failures.contains { $0.fileName == "empty.flac" })
        XCTAssertTrue(result.failures.contains { $0.fileName == "corrupt.ape" })
    }

    func testDeduplicatesBeforeCreatingBookmarks() async throws {
        let root = try temporaryDirectory()
        let mp3 = root.appendingPathComponent("晴天.mp3")
        let flac = root.appendingPathComponent("晴天.flac")
        try Data(repeating: 3, count: 200).write(to: mp3)
        try Data(repeating: 4, count: 400).write(to: flac)
        let bookmarks = RecordingBookmarkCreator()
        let importer = FileImporter(
            metadataReader: SameSongMetadataReader(), bookmarkCreator: bookmarks,
            fingerprinting: StableFingerprinting()
        )

        let result = await importer.importFiles([mp3, flac], existing: [])

        XCTAssertEqual(result.imported.map(\.format), [.flac])
        XCTAssertEqual(bookmarks.urls.map(\.lastPathComponent), ["晴天.flac"])
        XCTAssertEqual(result.skipped, 1)
    }

    func testExistingLibraryDuplicateWinsAndCreatesNoBookmark() async throws {
        let root = try temporaryDirectory()
        let incoming = root.appendingPathComponent("夜曲.flac")
        try Data(repeating: 2, count: 256).write(to: incoming)
        let existing = AudioTrack(
            bookmark: Data([8]), fileName: "夜曲.mp3", format: .mp3,
            title: "夜曲", artist: "周杰伦", duration: 220, fileSize: 123
        )
        let bookmarks = RecordingBookmarkCreator()
        let importer = FileImporter(
            metadataReader: SameSongMetadataReader(title: "夜曲", duration: 221),
            bookmarkCreator: bookmarks, fingerprinting: StableFingerprinting()
        )

        let result = await importer.importFiles([incoming], existing: [existing])

        XCTAssertEqual(result.imported, [])
        XCTAssertEqual(result.skipped, 1)
        XCTAssertEqual(bookmarks.urls, [])
    }

    private func temporaryDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }
}

private final class RecordingBookmarkCreator: BookmarkCreating, @unchecked Sendable {
    private(set) var urls: [URL] = []
    func create(for url: URL) throws -> Data { urls.append(url); return Data(url.path.utf8) }
}

private struct FileNameMetadataReader: AudioMetadataReading {
    func read(url: URL, format: AudioFormat) async throws -> AudioMetadata {
        AudioMetadata(title: url.deletingPathExtension().lastPathComponent, artist: "歌手", duration: 180, fileSize: 256)
    }
}

private struct SelectiveMetadataReader: AudioMetadataReading {
    let failingNames: Set<String>
    init(failingNames: Set<String>) { self.failingNames = failingNames }
    func read(url: URL, format: AudioFormat) async throws -> AudioMetadata {
        if failingNames.contains(url.lastPathComponent) { throw ImportTestError.corrupt }
        let size = (try url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        if size == 0 { throw ImportTestError.empty }
        return AudioMetadata(title: url.deletingPathExtension().lastPathComponent, artist: "歌手", duration: 180, fileSize: Int64(size))
    }
}

private struct SameSongMetadataReader: AudioMetadataReading {
    var title = "晴天"
    var duration: TimeInterval = 270
    func read(url: URL, format: AudioFormat) async throws -> AudioMetadata {
        AudioMetadata(title: title, artist: "周杰伦", duration: duration, fileSize: Int64((try url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0))
    }
}

private struct StableFingerprinting: AudioFingerprinting {
    func fingerprint(url: URL, metadata: AudioMetadata) throws -> String { "\(metadata.title)|\(metadata.artist)|\(Int(metadata.duration))" }
}

private enum ImportTestError: Error { case corrupt, empty }
