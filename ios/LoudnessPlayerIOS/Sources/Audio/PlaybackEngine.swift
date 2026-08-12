import Foundation

enum PlaybackState: String, Equatable, Sendable { case stopped, playing, paused }

struct PlaybackSnapshot: Equatable, Sendable {
    var preview: QueuePreview
    var state: PlaybackState
    var elapsed: TimeInterval

    static let empty = PlaybackSnapshot(
        preview: QueuePreview(previous: nil, current: nil, next: nil),
        state: .stopped, elapsed: 0
    )
}

protocol PlaybackSink: Sendable {
    func prepare(track: AudioTrack, gain: Double) async throws
    func play() async throws
    func pause() async
    func stop() async
    func seek(to seconds: TimeInterval) async throws
}

actor PlaybackEngine {
    private var queue: PlaybackQueue?
    private let sink: any PlaybackSink
    private let targetLoudness: Double
    private(set) var snapshot: PlaybackSnapshot = .empty

    init(targetLoudness: Double = -14, sink: any PlaybackSink) {
        self.targetLoudness = targetLoudness
        self.sink = sink
    }

    func play(queue: PlaybackQueue) async throws {
        self.queue = queue
        try await prepareCurrent()
    }

    func pause() async {
        await sink.pause()
        snapshot.state = .paused
    }

    func resume() async throws {
        guard snapshot.preview.current != nil else { return }
        try await sink.play()
        snapshot.state = .playing
    }

    func next() async throws {
        guard var queue else { return }
        queue.advance()
        self.queue = queue
        try await prepareCurrent()
    }

    func previous() async throws {
        guard var queue else { return }
        queue.retreat()
        self.queue = queue
        try await prepareCurrent()
    }

    func seek(to seconds: TimeInterval) async throws {
        guard let current = snapshot.preview.current else { return }
        let duration = current.duration.isFinite ? max(0, current.duration) : 0
        let clamped = min(duration, max(0, seconds))
        try await sink.seek(to: clamped)
        snapshot.elapsed = clamped
    }

    private func prepareCurrent() async throws {
        guard let queue, let current = queue.preview.current else {
            await sink.stop()
            snapshot = .empty
            return
        }
        let gain: Double
        if let measured = current.integratedLoudness, let peak = current.samplePeak {
            gain = NormalizationPolicy.gain(
                target: targetLoudness, measured: measured, peak: peak
            ).linearGain
        } else { gain = 1 }
        await sink.stop()
        try await sink.prepare(track: current, gain: gain)
        try await sink.play()
        snapshot = PlaybackSnapshot(preview: queue.preview, state: .playing, elapsed: 0)
    }
}
