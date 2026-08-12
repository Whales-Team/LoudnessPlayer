import AVFoundation
import Foundation

final class NativeAudioDecoder: AudioDecoder, @unchecked Sendable {
    private let lock = NSLock()
    private var file: AVAudioFile?
    private var converter: AVAudioConverter?
    private var outputFormat: AVAudioFormat?
    private var cancelled = false

    func open(url: URL) throws -> AudioStreamInfo {
        let file = try AVAudioFile(forReading: url)
        guard let outputFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: file.fileFormat.sampleRate,
            channels: file.fileFormat.channelCount,
            interleaved: true
        ), let converter = AVAudioConverter(from: file.processingFormat, to: outputFormat) else {
            throw DecoderError.invalidPCMFormat
        }
        lock.withLock {
            self.file = file; self.converter = converter; self.outputFormat = outputFormat; cancelled = false
        }
        return AudioStreamInfo(
            sampleRate: outputFormat.sampleRate, channels: Int(outputFormat.channelCount),
            totalFrames: file.length,
            containerDuration: file.fileFormat.sampleRate > 0 ? Double(file.length) / file.fileFormat.sampleRate : .nan
        )
    }

    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk? {
        try lock.withLock {
            if cancelled { throw DecoderError.cancelled }
            guard let file, let converter, let outputFormat else { throw DecoderError.notOpen }
            guard let output = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: maxFrames) else {
                throw DecoderError.invalidPCMFormat
            }
            var inputError: Error?
            let status = converter.convert(to: output, error: nil) { requested, flag in
                guard let input = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: requested) else {
                    flag.pointee = .noDataNow; return nil
                }
                do { try file.read(into: input, frameCount: requested) }
                catch { inputError = error; flag.pointee = .noDataNow; return nil }
                flag.pointee = input.frameLength == 0 ? .endOfStream : .haveData
                return input
            }
            if let inputError { throw inputError }
            if status == .error { throw DecoderError.conversionFailed }
            guard output.frameLength > 0, let data = output.floatChannelData else { return nil }
            let channels = Int(output.format.channelCount)
            let count = Int(output.frameLength) * channels
            return PCMChunk(
                samples: Array(UnsafeBufferPointer(start: data[0], count: count)),
                frameCount: output.frameLength, channels: channels, sampleRate: output.format.sampleRate
            )
        }
    }

    func seek(to seconds: TimeInterval) throws {
        try lock.withLock {
            guard let file else { throw DecoderError.notOpen }
            let frame = max(0, min(file.length, AVAudioFramePosition(seconds * file.fileFormat.sampleRate)))
            file.framePosition = frame
            converter?.reset()
        }
    }

    func cancel() { lock.withLock { cancelled = true } }
}

private extension NSLock {
    func withLock<T>(_ operation: () throws -> T) rethrows -> T {
        lock(); defer { unlock() }; return try operation()
    }
}
