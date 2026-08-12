import Foundation

struct ImportFailure: Equatable, Sendable {
    let fileName: String
    let message: String
}

struct ImportResult: Equatable, Sendable {
    var imported: [AudioTrack]
    var skipped: Int
    var failures: [ImportFailure]

    static let empty = ImportResult(imported: [], skipped: 0, failures: [])
}

struct AudioMetadata: Equatable, Sendable {
    let title: String
    let artist: String
    let duration: TimeInterval
    let fileSize: Int64
}

protocol AudioMetadataReading: Sendable {
    func read(url: URL, format: AudioFormat) async throws -> AudioMetadata
}

protocol BookmarkCreating: Sendable {
    func create(for url: URL) throws -> Data
}

protocol AudioFingerprinting: Sendable {
    func fingerprint(url: URL, metadata: AudioMetadata) throws -> String
}
