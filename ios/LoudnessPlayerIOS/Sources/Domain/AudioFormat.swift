import Foundation

enum AudioFormat: String, Codable, CaseIterable, Sendable {
    case mp3, flac, wav, ape, m4a, aac, ogg, opus, wma

    static func from(fileName: String, mimeType: String? = nil) -> AudioFormat? {
        let ext = URL(fileURLWithPath: fileName).pathExtension.lowercased()
        if let format = AudioFormat(rawValue: ext) { return format }
        guard let mimeType = mimeType?.lowercased() else { return nil }
        return mimeTypes[mimeType]
    }

    var qualityRank: Int {
        switch self {
        case .flac: 90
        case .ape: 80
        case .wav: 70
        case .m4a: 60
        case .opus: 50
        case .ogg: 40
        case .aac: 30
        case .wma: 20
        case .mp3: 10
        }
    }

    private static let mimeTypes: [String: AudioFormat] = [
        "audio/mpeg": .mp3, "audio/flac": .flac, "audio/x-flac": .flac,
        "audio/wav": .wav, "audio/x-wav": .wav, "audio/ape": .ape,
        "audio/x-ape": .ape, "audio/mp4": .m4a, "audio/x-m4a": .m4a,
        "audio/aac": .aac, "audio/ogg": .ogg, "application/ogg": .ogg,
        "audio/opus": .opus, "audio/x-ms-wma": .wma, "audio/wma": .wma,
    ]
}
