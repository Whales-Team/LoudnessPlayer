import AVFoundation
import CryptoKit
import Foundation

enum ImportError: LocalizedError {
    case emptyFile
    case missingAudioTrack

    var errorDescription: String? {
        switch self {
        case .emptyFile: "文件为空"
        case .missingAudioTrack: "无法读取音频轨道"
        }
    }
}

struct AudioMetadataReader: AudioMetadataReading {
    func read(url: URL, format: AudioFormat) async throws -> AudioMetadata {
        let values = try url.resourceValues(forKeys: [.fileSizeKey])
        let fileSize = Int64(values.fileSize ?? 0)
        guard fileSize > 0 else { throw ImportError.emptyFile }

        let asset = AVURLAsset(url: url)
        let duration = (try? await asset.load(.duration)).map(CMTimeGetSeconds) ?? 0
        let commonMetadata = (try? await asset.load(.commonMetadata)) ?? []
        let title = await metadataString(commonMetadata, identifier: .commonIdentifierTitle)
            ?? url.deletingPathExtension().lastPathComponent
        let artist = await metadataString(commonMetadata, identifier: .commonIdentifierArtist) ?? "未知歌手"
        return AudioMetadata(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            artist: artist.trimmingCharacters(in: .whitespacesAndNewlines),
            duration: duration.isFinite && duration > 0 ? duration : 0,
            fileSize: fileSize
        )
    }

    private func metadataString(_ metadata: [AVMetadataItem], identifier: AVMetadataIdentifier) async -> String? {
        guard let item = AVMetadataItem.metadataItems(from: metadata, filteredByIdentifier: identifier).first else {
            return nil
        }
        return try? await item.load(.stringValue)
    }
}

struct SecurityScopedBookmarkCreator: BookmarkCreating {
    func create(for url: URL) throws -> Data {
        try url.bookmarkData(options: [.minimalBookmark], includingResourceValuesForKeys: nil, relativeTo: nil)
    }
}

struct AudioFingerprinter: AudioFingerprinting {
    private let chunkSize = 128 * 1_024

    func fingerprint(url: URL, metadata: AudioMetadata) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let head = try handle.read(upToCount: chunkSize) ?? Data()
        let tailOffset = max(0, metadata.fileSize - Int64(chunkSize))
        try handle.seek(toOffset: UInt64(tailOffset))
        let tail = try handle.read(upToCount: chunkSize) ?? Data()
        var payload = Data("\(metadata.fileSize)|\(metadata.title)|\(metadata.artist)".utf8)
        payload.append(head)
        payload.append(tail)
        return SHA256.hash(data: payload).map { String(format: "%02x", $0) }.joined()
    }
}
