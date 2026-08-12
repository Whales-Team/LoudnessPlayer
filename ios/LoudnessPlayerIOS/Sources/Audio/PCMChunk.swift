import AVFoundation
import Foundation

struct PCMChunk: Equatable, Sendable {
    let samples: [Float]
    let frameCount: AVAudioFrameCount
    let channels: Int
    let sampleRate: Double
}

struct AudioStreamInfo: Equatable, Sendable {
    let sampleRate: Double
    let channels: Int
    let totalFrames: Int64
    let containerDuration: TimeInterval

    var duration: TimeInterval {
        if containerDuration.isFinite, containerDuration >= 0 { return containerDuration }
        guard sampleRate > 0, totalFrames >= 0 else { return 0 }
        return Double(totalFrames) / sampleRate
    }
}
