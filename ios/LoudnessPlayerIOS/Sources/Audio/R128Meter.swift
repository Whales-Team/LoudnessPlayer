import Foundation

struct LoudnessResult: Equatable, Sendable {
    let integratedLUFS: Double
    let samplePeak: Double
}

enum R128MeterError: Error, Equatable {
    case invalidFormat
    case insufficientAudio
    case belowAbsoluteGate
}

struct R128Meter: Sendable {
    private let sampleRate: Double
    private let channels: Int
    private let windowFrames: Int
    private let stepFrames: Int
    private var filters: [KWeightingFilter]
    private var energyWindow: [Double]
    private var windowIndex = 0
    private var populatedFrames = 0
    private var framesSinceBlock = 0
    private var rollingEnergy = 0.0
    private var blockEnergies: [Double] = []
    private var peak = 0.0
    private var hasInvalidFormat = false

    init(sampleRate: Double, channels: Int) {
        self.sampleRate = sampleRate
        self.channels = channels
        windowFrames = max(1, Int((sampleRate * 0.4).rounded()))
        stepFrames = max(1, Int((sampleRate * 0.1).rounded()))
        filters = (0..<max(0, channels)).map { _ in KWeightingFilter(sampleRate: sampleRate) }
        energyWindow = [Double](repeating: 0, count: max(1, windowFrames))
        hasInvalidFormat = sampleRate <= 0 || channels <= 0
    }

    mutating func consume(_ chunk: PCMChunk) {
        guard !hasInvalidFormat,
              chunk.channels == channels,
              abs(chunk.sampleRate - sampleRate) < 0.5,
              chunk.samples.count >= Int(chunk.frameCount) * channels else {
            hasInvalidFormat = true
            return
        }

        for frame in 0..<Int(chunk.frameCount) {
            var frameEnergy = 0.0
            for channel in 0..<channels {
                let raw = Double(chunk.samples[frame * channels + channel])
                peak = max(peak, abs(raw))
                let weighted = filters[channel].process(raw)
                frameEnergy += weighted * weighted
            }

            if populatedFrames == windowFrames {
                rollingEnergy -= energyWindow[windowIndex]
            } else {
                populatedFrames += 1
            }
            energyWindow[windowIndex] = frameEnergy
            rollingEnergy += frameEnergy
            windowIndex = (windowIndex + 1) % windowFrames
            framesSinceBlock += 1

            if populatedFrames == windowFrames,
               (blockEnergies.isEmpty || framesSinceBlock >= stepFrames) {
                blockEnergies.append(max(0, rollingEnergy / Double(windowFrames)))
                framesSinceBlock = 0
            }
        }
    }

    func result() throws -> LoudnessResult {
        if hasInvalidFormat { throw R128MeterError.invalidFormat }
        if blockEnergies.isEmpty { throw R128MeterError.insufficientAudio }
        let absolutelyGated = blockEnergies.filter { loudness(of: $0) >= -70 }
        guard !absolutelyGated.isEmpty else { throw R128MeterError.belowAbsoluteGate }
        let ungatedEnergy = absolutelyGated.reduce(0, +) / Double(absolutelyGated.count)
        let relativeGate = loudness(of: ungatedEnergy) - 10
        let relativelyGated = absolutelyGated.filter { loudness(of: $0) >= relativeGate }
        guard !relativelyGated.isEmpty else { throw R128MeterError.belowAbsoluteGate }
        let integratedEnergy = relativelyGated.reduce(0, +) / Double(relativelyGated.count)
        return LoudnessResult(integratedLUFS: loudness(of: integratedEnergy), samplePeak: peak)
    }

    private func loudness(of energy: Double) -> Double {
        guard energy > 0 else { return -.infinity }
        return -0.691 + 10 * log10(energy)
    }
}

private struct KWeightingFilter: Sendable {
    private var shelf: Biquad
    private var highPass: Biquad

    init(sampleRate: Double) {
        shelf = .highShelf(
            sampleRate: sampleRate, frequency: 1_681.974_450_955_533,
            gainDB: 3.999_843_853_973_347, q: 0.707_175_236_955_419_6
        )
        highPass = .highPass(
            sampleRate: sampleRate, frequency: 38.135_470_876_024_44,
            q: 0.500_327_037_323_877_3
        )
    }

    mutating func process(_ sample: Double) -> Double {
        highPass.process(shelf.process(sample))
    }
}

private struct Biquad: Sendable {
    let b0: Double
    let b1: Double
    let b2: Double
    let a1: Double
    let a2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    static func highShelf(
        sampleRate: Double, frequency: Double, gainDB: Double, q: Double
    ) -> Biquad {
        let k = tan(Double.pi * frequency / sampleRate)
        let vh = pow(10, gainDB / 20)
        let vb = pow(vh, 0.499_666_774_154_541_6)
        let denominator = 1 + k / q + k * k
        return Biquad(
            b0: (vh + vb * k / q + k * k) / denominator,
            b1: 2 * (k * k - vh) / denominator,
            b2: (vh - vb * k / q + k * k) / denominator,
            a1: 2 * (k * k - 1) / denominator,
            a2: (1 - k / q + k * k) / denominator
        )
    }

    static func highPass(sampleRate: Double, frequency: Double, q: Double) -> Biquad {
        let k = tan(Double.pi * frequency / sampleRate)
        let denominator = 1 + k / q + k * k
        return Biquad(
            b0: 1 / denominator, b1: -2 / denominator, b2: 1 / denominator,
            a1: 2 * (k * k - 1) / denominator,
            a2: (1 - k / q + k * k) / denominator
        )
    }

    mutating func process(_ input: Double) -> Double {
        let output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input; y2 = y1; y1 = output
        return output
    }
}
