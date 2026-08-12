import Foundation

struct PlaybackQueue: Sendable {
    private var tracks: [AudioTrack]
    private var index = 0
    private let mode: PlaybackMode

    init(scope: [AudioTrack], mode: PlaybackMode, seed: UInt64) {
        self.mode = mode
        if mode == .shuffle {
            var generator = SplitMix64(seed: seed)
            tracks = scope.shuffled(using: &generator)
        } else { tracks = scope }
    }

    var preview: QueuePreview {
        guard tracks.indices.contains(index) else { return QueuePreview(previous: nil, current: nil, next: nil) }
        if mode == .repeatOne {
            return QueuePreview(previous: tracks[index], current: tracks[index], next: tracks[index])
        }
        return QueuePreview(
            previous: index > 0 ? tracks[index - 1] : nil,
            current: tracks[index], next: index + 1 < tracks.count ? tracks[index + 1] : nil
        )
    }

    mutating func advance() { if mode != .repeatOne, index + 1 < tracks.count { index += 1 } }
    mutating func retreat() { if mode != .repeatOne, index > 0 { index -= 1 } }
    mutating func select(id: UUID) { if let found = tracks.firstIndex(where: { $0.id == id }) { index = found } }
}

private struct SplitMix64: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var value = state
        value = (value ^ (value >> 30)) &* 0xBF58476D1CE4E5B9
        value = (value ^ (value >> 27)) &* 0x94D049BB133111EB
        return value ^ (value >> 31)
    }
}
