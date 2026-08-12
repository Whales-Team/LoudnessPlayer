import XCTest
@testable import LoudnessPlayer

final class AudioFormatTests: XCTestCase {
    func testRecognizesEverySupportedExtensionIgnoringCase() {
        let cases: [(String, AudioFormat)] = [
            ("song.MP3", .mp3), ("song.flac", .flac), ("song.WaV", .wav),
            ("song.ape", .ape), ("song.M4A", .m4a), ("song.aac", .aac),
            ("song.OGG", .ogg), ("song.opus", .opus), ("song.WMA", .wma),
        ]

        for (name, expected) in cases {
            XCTAssertEqual(AudioFormat.from(fileName: name), expected, name)
        }
        XCTAssertNil(AudioFormat.from(fileName: "cover.jpg"))
    }

    func testUsesKnownMIMETypeWhenFileNameHasNoExtension() {
        XCTAssertEqual(AudioFormat.from(fileName: "track", mimeType: "audio/x-ms-wma"), .wma)
        XCTAssertEqual(AudioFormat.from(fileName: "track", mimeType: "audio/ape"), .ape)
    }
}
