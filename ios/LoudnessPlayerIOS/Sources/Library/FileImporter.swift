import Foundation

actor FileImporter {
    private let metadataReader: any AudioMetadataReading
    private let bookmarkCreator: any BookmarkCreating
    private let fingerprinting: any AudioFingerprinting
    private let fileManager: FileManager

    init(
        metadataReader: any AudioMetadataReading = AudioMetadataReader(),
        bookmarkCreator: any BookmarkCreating = SecurityScopedBookmarkCreator(),
        fingerprinting: any AudioFingerprinting = AudioFingerprinter(),
        fileManager: FileManager = .default
    ) {
        self.metadataReader = metadataReader
        self.bookmarkCreator = bookmarkCreator
        self.fingerprinting = fingerprinting
        self.fileManager = fileManager
    }

    func importDirectory(_ root: URL, existing: [AudioTrack]) async -> ImportResult {
        do {
            let urls = try enumerateFiles(at: root)
            return await importFiles(urls, existing: existing)
        } catch is CancellationError {
            return .empty
        } catch {
            return ImportResult(imported: [], skipped: 0, failures: [
                ImportFailure(fileName: root.lastPathComponent, message: error.localizedDescription),
            ])
        }
    }

    private func enumerateFiles(at root: URL) throws -> [URL] {
        let keys: [URLResourceKey] = [.isRegularFileKey, .isHiddenKey]
        guard let enumerator = fileManager.enumerator(
            at: root, includingPropertiesForKeys: keys,
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else { throw CocoaError(.fileReadUnknown) }
        var urls: [URL] = []
        while let url = enumerator.nextObject() as? URL {
            if Task.isCancelled { throw CancellationError() }
            let values = try url.resourceValues(forKeys: Set(keys))
            if values.isRegularFile == true, values.isHidden != true { urls.append(url) }
        }
        return urls
    }

    func importFiles(_ urls: [URL], existing: [AudioTrack]) async -> ImportResult {
        var candidates: [Candidate] = []
        var skipped = 0
        var failures: [ImportFailure] = []

        for url in urls where !url.lastPathComponent.hasPrefix(".") {
            if Task.isCancelled { break }
            guard let format = AudioFormat.from(fileName: url.lastPathComponent) else {
                skipped += 1
                continue
            }
            do {
                let metadata = try await metadataReader.read(url: url, format: format)
                guard metadata.fileSize > 0 else { throw ImportError.emptyFile }
                let fingerprint = try fingerprinting.fingerprint(url: url, metadata: metadata)
                candidates.append(Candidate(url: url, format: format, metadata: metadata, fingerprint: fingerprint))
            } catch {
                failures.append(ImportFailure(fileName: url.lastPathComponent, message: error.localizedDescription))
            }
        }

        var selected: [Candidate] = []
        for candidate in candidates.sorted(by: { $0.format.qualityRank > $1.format.qualityRank }) {
            let provisional = candidate.track(bookmark: Data())
            let retained = LibraryOrganizer.preferredDuplicates(
                existing: existing + selected.map { $0.track(bookmark: Data()) }, incoming: [provisional]
            )
            if retained.contains(where: { $0.id == provisional.id }) { selected.append(candidate) }
            else { skipped += 1 }
        }

        var imported: [AudioTrack] = []
        for candidate in selected {
            do {
                imported.append(candidate.track(bookmark: try bookmarkCreator.create(for: candidate.url)))
            } catch {
                failures.append(ImportFailure(fileName: candidate.url.lastPathComponent, message: error.localizedDescription))
            }
        }
        return ImportResult(imported: LibraryOrganizer.sorted(imported), skipped: skipped, failures: failures)
    }
}

private struct Candidate: Sendable {
    let id = UUID()
    let url: URL
    let format: AudioFormat
    let metadata: AudioMetadata
    let fingerprint: String

    func track(bookmark: Data) -> AudioTrack {
        AudioTrack(
            id: id, bookmark: bookmark, fileName: url.lastPathComponent, format: format,
            title: metadata.title, artist: metadata.artist, duration: metadata.duration,
            fileSize: metadata.fileSize, contentFingerprint: fingerprint
        )
    }
}
