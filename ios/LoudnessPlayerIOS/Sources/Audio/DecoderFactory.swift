import Foundation

struct DecoderFactory: Sendable {
    typealias Builder = @Sendable () -> any AudioDecoder
    private let native: Builder
    private let ffmpeg: Builder

    init(
        native: @escaping Builder = { NativeAudioDecoder() },
        ffmpeg: @escaping Builder = { FFmpegAudioDecoder() }
    ) {
        self.native = native
        self.ffmpeg = ffmpeg
    }

    func make(for format: AudioFormat) -> any AudioDecoder {
        switch format {
        case .ape, .wma, .ogg, .opus: ffmpeg()
        case .mp3, .flac, .wav, .m4a, .aac: native()
        }
    }

    func open(url: URL, format: AudioFormat) throws -> OpenedAudioDecoder {
        let first = make(for: format)
        do { return OpenedAudioDecoder(decoder: first, info: try first.open(url: url)) }
        catch {
            guard [.mp3, .flac, .wav, .m4a, .aac].contains(format) else { throw error }
            let fallback = ffmpeg()
            return OpenedAudioDecoder(decoder: fallback, info: try fallback.open(url: url))
        }
    }
}
