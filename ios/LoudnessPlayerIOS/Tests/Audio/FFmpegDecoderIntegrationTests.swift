import AVFoundation
import XCTest
@testable import LoudnessPlayer

final class FFmpegDecoderIntegrationTests: XCTestCase {
    func testBundledAPEFixtureDecodesFiniteAudiblePCM() throws {
        try assertDecodes("NoLegacy-cut", extension: "ape")
    }

    func testBundledWMAFixtureDecodesFiniteAudiblePCM() throws {
        try assertDecodes("Mega_Weird_Audio_Test_24bit", extension: "wma")
    }

    func testBundledOggVorbisFixtureDecodesFiniteAudiblePCM() throws {
        try assertDecodes("chained-meta", extension: "ogg")
    }

    func testBundledOpusFixtureDecodesFiniteAudiblePCM() throws {
        try assertDecodes("tones_opus_48000_stereo", extension: "opus")
    }

    private func assertDecodes(_ name: String, extension fileExtension: String) throws {
        let bundle = Bundle(for: Self.self)
        let url = try XCTUnwrap(bundle.url(forResource: name, withExtension: fileExtension))
        let decoder = FFmpegAudioDecoder()
        let info = try decoder.open(url: url)

        XCTAssertGreaterThan(info.sampleRate, 0)
        XCTAssertGreaterThan(info.channels, 0)
        XCTAssertTrue(info.duration.isFinite)
        XCTAssertGreaterThan(info.duration, 0)

        var decodedFrames: Int64 = 0
        var samplePeak: Float = 0
        while let chunk = try decoder.read(maxFrames: 4_096) {
            decodedFrames += Int64(chunk.frameCount)
            samplePeak = max(samplePeak, chunk.samples.lazy.map(abs).max() ?? 0)
        }

        XCTAssertGreaterThan(decodedFrames, 0)
        XCTAssertGreaterThan(samplePeak, 0.000_1)
        let decodedDuration = Double(decodedFrames) / info.sampleRate
        XCTAssertEqual(info.duration, decodedDuration, accuracy: max(0.25, decodedDuration * 0.02))
    }
}
