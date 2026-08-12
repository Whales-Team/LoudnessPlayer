import AVFoundation
import XCTest
@testable import LoudnessPlayer

final class DecoderFactoryTests: XCTestCase {
    func testNativeFirstFormatsUseNativeDecoder() throws {
        let native = StubDecoder(kind: "native")
        let ffmpeg = StubDecoder(kind: "ffmpeg")
        let factory = DecoderFactory(native: { native }, ffmpeg: { ffmpeg })

        for format in [AudioFormat.mp3, .flac, .wav, .m4a, .aac] {
            XCTAssertTrue(factory.make(for: format) === native)
        }
    }

    func testFallbackFormatsUseFFmpegDecoder() throws {
        let native = StubDecoder(kind: "native")
        let ffmpeg = StubDecoder(kind: "ffmpeg")
        let factory = DecoderFactory(native: { native }, ffmpeg: { ffmpeg })

        for format in [AudioFormat.ape, .wma, .ogg, .opus] {
            XCTAssertTrue(factory.make(for: format) === ffmpeg)
        }
    }

    func testOpenFallsBackToFFmpegWhenNativeCannotOpen() throws {
        let native = StubDecoder(kind: "native", openError: DecoderTestError.unsupported)
        let ffmpeg = StubDecoder(kind: "ffmpeg")
        let factory = DecoderFactory(native: { native }, ffmpeg: { ffmpeg })

        let opened = try factory.open(url: URL(fileURLWithPath: "/music/song.m4a"), format: .m4a)

        XCTAssertTrue(opened.decoder === ffmpeg)
        XCTAssertEqual(native.openCount, 1)
        XCTAssertEqual(ffmpeg.openCount, 1)
    }

    func testStreamInfoCorrectsInvalidContainerDurationFromFrames() {
        let info = AudioStreamInfo(sampleRate: 48_000, channels: 2, totalFrames: 288_000, containerDuration: .infinity)
        XCTAssertEqual(info.duration, 6, accuracy: 0.000_1)
    }

    func testPCMChunkCarriesInterleavedFloatSamples() {
        let chunk = PCMChunk(samples: [0.25, -0.25, 0.5, -0.5], frameCount: 2, channels: 2, sampleRate: 48_000)
        XCTAssertEqual(chunk.samples, [0.25, -0.25, 0.5, -0.5])
        XCTAssertEqual(chunk.frameCount, 2)
    }
}

private enum DecoderTestError: Error { case unsupported }

private final class StubDecoder: AudioDecoder, @unchecked Sendable {
    let kind: String
    let openError: Error?
    private(set) var openCount = 0

    init(kind: String, openError: Error? = nil) { self.kind = kind; self.openError = openError }
    func open(url: URL) throws -> AudioStreamInfo {
        openCount += 1
        if let openError { throw openError }
        return AudioStreamInfo(sampleRate: 48_000, channels: 2, totalFrames: 48_000, containerDuration: 1)
    }
    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk? { nil }
    func seek(to seconds: TimeInterval) throws {}
    func cancel() {}
}
