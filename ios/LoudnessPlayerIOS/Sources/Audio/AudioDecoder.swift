import AVFoundation
import Foundation

enum DecoderError: Error, Equatable {
    case cancelled
    case notOpen
    case unsupportedFormat
    case invalidPCMFormat
    case conversionFailed
}

protocol AudioDecoder: AnyObject, Sendable {
    func open(url: URL) throws -> AudioStreamInfo
    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk?
    func seek(to seconds: TimeInterval) throws
    func cancel()
}

struct OpenedAudioDecoder: Sendable {
    let decoder: any AudioDecoder
    let info: AudioStreamInfo
}
