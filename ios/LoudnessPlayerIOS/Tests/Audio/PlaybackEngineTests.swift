import XCTest
@testable import LoudnessPlayer

final class PlaybackEngineTests: XCTestCase {
    func testPlayPublishesTruthfulFolderScopedPreview() async throws {
        let tracks = (0..<3).map { playbackTrack(title: "Song \($0)") }
        var queue = PlaybackQueue(tracks: tracks, mode: .sequential)
        queue.select(id: tracks[1].id)
        let sink = PlaybackSinkProbe()
        let engine = PlaybackEngine(sink: sink)

        try await engine.play(queue: queue)
        let snapshot = await engine.snapshot

        XCTAssertEqual(snapshot.preview.previous?.id, tracks[0].id)
        XCTAssertEqual(snapshot.preview.current?.id, tracks[1].id)
        XCTAssertEqual(snapshot.preview.next?.id, tracks[2].id)
        XCTAssertEqual(snapshot.state, .playing)
    }

    func testNextAndPreviousFollowScopedQueue() async throws {
        let tracks = (0..<3).map { playbackTrack(title: "Song \($0)") }
        let engine = PlaybackEngine(sink: PlaybackSinkProbe())
        try await engine.play(queue: PlaybackQueue(tracks: tracks, mode: .sequential))

        try await engine.next()
        var snapshot = await engine.snapshot
        XCTAssertEqual(snapshot.preview.current?.id, tracks[1].id)
        try await engine.previous()
        snapshot = await engine.snapshot
        XCTAssertEqual(snapshot.preview.current?.id, tracks[0].id)
    }

    func testSeekClampsToCorrectedDuration() async throws {
        let track = playbackTrack(title: "Song", duration: 120)
        let sink = PlaybackSinkProbe()
        let engine = PlaybackEngine(sink: sink)
        try await engine.play(queue: PlaybackQueue(tracks: [track], mode: .sequential))

        try await engine.seek(to: 500)

        let seek = await sink.lastSeek()
        let snapshot = await engine.snapshot
        XCTAssertEqual(seek, 120)
        XCTAssertEqual(snapshot.elapsed, 120)
    }

    func testNormalizedGainIsAppliedBeforePlaybackStarts() async throws {
        var track = playbackTrack(title: "Song")
        track.integratedLoudness = -20
        track.samplePeak = 0.1
        let sink = PlaybackSinkProbe()
        let engine = PlaybackEngine(targetLoudness: -14, sink: sink)

        try await engine.play(queue: PlaybackQueue(tracks: [track], mode: .sequential))

        let gain = await sink.lastGain()
        XCTAssertEqual(gain, pow(10, 6.0 / 20), accuracy: 0.000_1)
    }

    func testRepeatOneNextKeepsCurrentTrack() async throws {
        let tracks = [playbackTrack(title: "A"), playbackTrack(title: "B")]
        let engine = PlaybackEngine(sink: PlaybackSinkProbe())
        try await engine.play(queue: PlaybackQueue(tracks: tracks, mode: .repeatOne))

        try await engine.next()

        let snapshot = await engine.snapshot
        XCTAssertEqual(snapshot.preview.current?.id, tracks[0].id)
    }
}

private actor PlaybackSinkProbe: PlaybackSink {
    private var seekValue: TimeInterval?
    private var gainValue: Double = 1

    func prepare(track: AudioTrack, gain: Double) async throws { gainValue = gain }
    func play() async throws {}
    func pause() async {}
    func stop() async {}
    func seek(to seconds: TimeInterval) async throws { seekValue = seconds }
    func lastSeek() -> TimeInterval? { seekValue }
    func lastGain() -> Double { gainValue }
}

private func playbackTrack(title: String, duration: TimeInterval = 180) -> AudioTrack {
    AudioTrack(
        bookmark: Data(), fileName: "\(title).flac", format: .flac,
        title: title, artist: "Artist", duration: duration, fileSize: 1
    )
}
