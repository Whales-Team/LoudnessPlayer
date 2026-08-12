import AVFoundation
import FFmpegAudioBridge
import Foundation

final class FFmpegAudioDecoder: AudioDecoder, @unchecked Sendable {
    private let lock = NSLock()
    private var handle: OpaquePointer?
    private var streamInfo: AudioStreamInfo?
    private var cancelled = false
    private var decodedFrames: Int64 = 0
    private var didReachEnd = false

    var resolvedInfo: AudioStreamInfo? {
        lock.withCriticalSection {
            guard didReachEnd, let streamInfo else { return nil }
            return AudioStreamInfo(
                sampleRate: streamInfo.sampleRate,
                channels: streamInfo.channels,
                totalFrames: decodedFrames,
                containerDuration: .nan
            )
        }
    }

    deinit { if let handle { lp_ffmpeg_close(handle) } }

    func open(url: URL) throws -> AudioStreamInfo {
        try lock.withCriticalSection {
            if let handle { lp_ffmpeg_close(handle) }
            var raw = LPFFmpegStreamInfo()
            var error = [CChar](repeating: 0, count: 512)
            let opened = url.path.withCString { path in
                lp_ffmpeg_open(path, &raw, &error, Int32(error.count))
            }
            guard let opened else { throw FFmpegDecoderError.message(String(cString: error)) }
            handle = opened
            cancelled = false
            decodedFrames = 0
            didReachEnd = false
            let distrustContainerDuration = ["ape", "wma"].contains(url.pathExtension.lowercased())
            let info = AudioStreamInfo(
                sampleRate: Double(raw.sample_rate), channels: Int(raw.channels),
                totalFrames: distrustContainerDuration ? -1 : raw.total_frames,
                containerDuration: distrustContainerDuration ? .nan : raw.duration
            )
            streamInfo = info
            return info
        }
    }

    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk? {
        try lock.withCriticalSection {
            if cancelled { throw DecoderError.cancelled }
            guard let handle, let streamInfo else { throw DecoderError.notOpen }
            var samples = [Float](
                repeating: 0,
                count: Int(maxFrames) * streamInfo.channels
            )
            var error = [CChar](repeating: 0, count: 512)
            let frames = samples.withUnsafeMutableBufferPointer { output in
                lp_ffmpeg_read(handle, output.baseAddress, Int32(maxFrames), &error, Int32(error.count))
            }
            if frames == 0 { didReachEnd = true; return nil }
            if frames < 0 {
                if cancelled { throw DecoderError.cancelled }
                throw FFmpegDecoderError.message(String(cString: error))
            }
            decodedFrames += Int64(frames)
            samples.removeLast(samples.count - Int(frames) * streamInfo.channels)
            return PCMChunk(
                samples: samples, frameCount: AVAudioFrameCount(frames),
                channels: streamInfo.channels, sampleRate: streamInfo.sampleRate
            )
        }
    }

    func seek(to seconds: TimeInterval) throws {
        try lock.withCriticalSection {
            guard let handle else { throw DecoderError.notOpen }
            var error = [CChar](repeating: 0, count: 512)
            let result = lp_ffmpeg_seek(handle, seconds, &error, Int32(error.count))
            if result < 0 { throw FFmpegDecoderError.message(String(cString: error)) }
        }
    }

    func cancel() {
        lock.withCriticalSection {
            cancelled = true
            if let handle { lp_ffmpeg_cancel(handle) }
        }
    }
}

enum FFmpegDecoderError: Error, Equatable {
    case message(String)
}

private extension NSLock {
    func withCriticalSection<T>(_ operation: () throws -> T) rethrows -> T {
        lock(); defer { unlock() }; return try operation()
    }
}
