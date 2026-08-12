import Foundation

struct GainDecision: Equatable, Sendable {
    let targetLUFS: Double
    let gainDB: Double
    let linearGain: Double
    let isPeakLimited: Bool
}

enum NormalizationPolicy {
    static func gain(target: Double, measured: Double, peak: Double) -> GainDecision {
        let target = min(-8, max(-24, target))
        let requestedDB = target - measured
        let requestedLinear = pow(10, requestedDB / 20)
        let peakCeiling = pow(10, -1.0 / 20)
        let maximumLinear = peak > 0 ? peakCeiling / peak : .infinity
        let linear = min(requestedLinear, maximumLinear)
        return GainDecision(
            targetLUFS: target,
            gainDB: 20 * log10(linear),
            linearGain: linear,
            isPeakLimited: linear < requestedLinear
        )
    }
}
