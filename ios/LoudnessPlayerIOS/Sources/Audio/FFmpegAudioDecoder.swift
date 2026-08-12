import AVFoundation
import Foundation

final class FFmpegAudioDecoder: AudioDecoder, @unchecked Sendable {
    func open(url: URL) throws -> AudioStreamInfo { throw DecoderError.unsupportedFormat }
    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk? { throw DecoderError.notOpen }
    func seek(to seconds: TimeInterval) throws { throw DecoderError.notOpen }
    func cancel() {}
}
