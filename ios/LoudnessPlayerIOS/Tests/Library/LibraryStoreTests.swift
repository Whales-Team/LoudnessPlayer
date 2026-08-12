import XCTest
@testable import LoudnessPlayer

final class LibraryStoreTests: XCTestCase {
    func testSavesAndLoadsCompleteDocument() async throws {
        let directory = try temporaryDirectory()
        let store = LibraryStore(directory: directory)
        let track = storedTrack(title: "夜曲")
        let document = LibraryDocument(
            tracks: [track],
            folders: [MusicFolder(id: UUID(), name: "睡前", trackIDs: [track.id])],
            preferences: UserPreferences(targetLoudness: -16, theme: .brown)
        )

        try await store.save(document)

        XCTAssertEqual(try await store.load(), document)
    }

    func testMigratesSchemaZeroDefaultsWithoutLosingTracks() async throws {
        let directory = try temporaryDirectory()
        let track = storedTrack(title: "晴天")
        let legacy: [String: Any] = [
            "schema": 0,
            "tracks": [[
                "id": track.id.uuidString,
                "bookmark": track.bookmark.base64EncodedString(),
                "fileName": track.fileName,
                "format": track.format.rawValue,
                "title": track.title,
                "artist": track.artist,
                "duration": track.duration,
                "fileSize": track.fileSize,
                "analysisStatus": track.analysisStatus.rawValue,
            ]],
        ]
        let data = try JSONSerialization.data(withJSONObject: legacy)
        try data.write(to: directory.appendingPathComponent("library.json"))

        let loaded = try await LibraryStore(directory: directory).load()

        XCTAssertEqual(loaded.schema, LibraryDocument.currentSchema)
        XCTAssertEqual(loaded.tracks.map(\.id), [track.id])
        XCTAssertEqual(loaded.folders, [])
        XCTAssertEqual(loaded.preferences, .default)
    }

    func testFallsBackToPreviousValidDocumentWhenCurrentIsCorrupt() async throws {
        let directory = try temporaryDirectory()
        let expected = LibraryDocument(tracks: [storedTrack(title: "稻香")])
        let encoder = JSONEncoder()
        try encoder.encode(expected).write(to: directory.appendingPathComponent("library.previous.json"))
        try Data("not json".utf8).write(to: directory.appendingPathComponent("library.json"))

        let loaded = try await LibraryStore(directory: directory).load()

        XCTAssertEqual(loaded, expected)
    }

    func testReturnsEmptyDocumentWhenNoLibraryExists() async throws {
        XCTAssertEqual(try await LibraryStore(directory: temporaryDirectory()).load(), .empty)
    }

    private func temporaryDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }
}

private func storedTrack(title: String) -> AudioTrack {
    AudioTrack(
        id: UUID(), bookmark: Data([1, 2, 3]), fileName: "\(title).flac", format: .flac,
        title: title, artist: "周杰伦", duration: 240, fileSize: 2_048
    )
}
