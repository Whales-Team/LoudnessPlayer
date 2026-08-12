import XCTest
@testable import LoudnessPlayer

final class NormalizationPolicyTests: XCTestCase {
    func testClampsUserTargetToSupportedRange() {
        XCTAssertEqual(
            NormalizationPolicy.gain(target: -40, measured: -20, peak: 0.1).targetLUFS,
            -24
        )
        XCTAssertEqual(
            NormalizationPolicy.gain(target: 0, measured: -20, peak: 0.1).targetLUFS,
            -8
        )
    }

    func testReturnsRequestedGainWhenPeakHasHeadroom() {
        let decision = NormalizationPolicy.gain(target: -14, measured: -20, peak: 0.1)

        XCTAssertEqual(decision.gainDB, 6, accuracy: 0.000_1)
        XCTAssertEqual(decision.linearGain, pow(10, 6.0 / 20), accuracy: 0.000_1)
        XCTAssertFalse(decision.isPeakLimited)
    }

    func testLimitsGainToMinusOneDBFS() {
        let decision = NormalizationPolicy.gain(target: -8, measured: -20, peak: 0.9)
        let maximum = pow(10, -1.0 / 20) / 0.9

        XCTAssertEqual(decision.linearGain, maximum, accuracy: 0.000_1)
        XCTAssertEqual(decision.gainDB, 20 * log10(maximum), accuracy: 0.000_1)
        XCTAssertTrue(decision.isPeakLimited)
    }
}
