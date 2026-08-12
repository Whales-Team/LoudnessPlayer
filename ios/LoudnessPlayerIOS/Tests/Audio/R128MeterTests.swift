import XCTest
@testable import LoudnessPlayer

final class R128MeterTests: XCTestCase {
    func testMinus20DBFSSineHasExpectedIntegratedLoudnessAndPeak() throws {
        var meter = R128Meter(sampleRate: 48_000, channels: 1)
        meter.consume(sineChunk(dbFS: -20, seconds: 3))

        let result = try meter.result()

        XCTAssertEqual(result.integratedLUFS, -23.05, accuracy: 0.35)
        XCTAssertEqual(result.samplePeak, 0.1, accuracy: 0.000_1)
    }

    func testSilenceIsBelowAbsoluteGate() {
        var meter = R128Meter(sampleRate: 48_000, channels: 1)
        meter.consume(PCMChunk(
            samples: [Float](repeating: 0, count: 96_000), frameCount: 96_000,
            channels: 1, sampleRate: 48_000
        ))

        XCTAssertThrowsError(try meter.result()) { error in
            XCTAssertEqual(error as? R128MeterError, .belowAbsoluteGate)
        }
    }

    func testRelativeGateRejectsQuietBlocks() throws {
        var mixed = R128Meter(sampleRate: 48_000, channels: 1)
        mixed.consume(sineChunk(dbFS: -20, seconds: 2))
        mixed.consume(sineChunk(dbFS: -55, seconds: 2))

        var loudOnly = R128Meter(sampleRate: 48_000, channels: 1)
        loudOnly.consume(sineChunk(dbFS: -20, seconds: 2))

        XCTAssertEqual(
            try mixed.result().integratedLUFS,
            try loudOnly.result().integratedLUFS,
            accuracy: 0.5
        )
    }

    func testStereoChannelsContributeToIntegratedLoudness() throws {
        let mono = sineChunk(dbFS: -20, seconds: 3)
        let stereoSamples = mono.samples.flatMap { [$0, $0] }
        var meter = R128Meter(sampleRate: 48_000, channels: 2)
        meter.consume(PCMChunk(
            samples: stereoSamples, frameCount: mono.frameCount,
            channels: 2, sampleRate: 48_000
        ))

        XCTAssertEqual(try meter.result().integratedLUFS, -20.04, accuracy: 0.35)
    }

    private func sineChunk(dbFS: Double, seconds: Double) -> PCMChunk {
        let sampleRate = 48_000.0
        let count = Int(sampleRate * seconds)
        let amplitude = pow(10, dbFS / 20)
        let samples = (0..<count).map { frame in
            Float(amplitude * sin(2 * Double.pi * 1_000 * Double(frame) / sampleRate))
        }
        return PCMChunk(
            samples: samples, frameCount: UInt32(count), channels: 1,
            sampleRate: sampleRate
        )
    }
}
