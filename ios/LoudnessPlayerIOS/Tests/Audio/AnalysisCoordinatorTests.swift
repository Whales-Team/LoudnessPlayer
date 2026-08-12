import AVFoundation
import XCTest
@testable import LoudnessPlayer

final class AnalysisCoordinatorTests: XCTestCase {
    func testSkipsAlreadySucceededTracks() async {
        let probe = DecoderProbe()
        let coordinator = AnalysisCoordinator(
            decode: { track in try await probe.decode(track) },
            commit: { _, _ in }
        )
        let succeeded = analysisTrack(status: .succeeded)

        await coordinator.start(tracks: [succeeded])
        await coordinator.waitUntilIdle()

        let started = await probe.startedIDs()
        XCTAssertEqual(started, [])
    }

    func testUsesAtMostTwoConcurrentAnalysisJobs() async {
        let probe = DecoderProbe(delay: 40_000_000)
        let coordinator = AnalysisCoordinator(
            decode: { track in try await probe.decode(track) },
            commit: { _, _ in }
        )

        await coordinator.start(tracks: (0..<6).map { _ in analysisTrack() })
        await coordinator.waitUntilIdle()

        let maximum = await probe.maximumConcurrency()
        XCTAssertEqual(maximum, 2)
    }

    func testPlaybackStartCancelsActiveAnalysisAndLeavesPending() async {
        let probe = DecoderProbe(delay: 2_000_000_000)
        let updates = UpdateRecorder()
        let coordinator = AnalysisCoordinator(
            decode: { track in try await probe.decode(track) },
            commit: { id, update in await updates.record(id: id, update: update) }
        )
        let tracks = [analysisTrack(), analysisTrack()]

        await coordinator.start(tracks: tracks)
        while await probe.startedIDs().count < 2 { await Task.yield() }
        await coordinator.playbackDidStart()
        await coordinator.waitUntilIdle()

        let wasCancelled = await probe.wasCancelled()
        let statuses = await updates.statuses()
        XCTAssertTrue(wasCancelled)
        XCTAssertEqual(statuses, [.pending, .pending])
    }
}

private actor DecoderProbe {
    private let delay: UInt64
    private var active = 0
    private var maximum = 0
    private var ids: [UUID] = []
    private var cancelled = false

    init(delay: UInt64 = 0) { self.delay = delay }

    func decode(_ track: AudioTrack) async throws -> LoudnessResult {
        ids.append(track.id)
        active += 1
        maximum = max(maximum, active)
        defer { active -= 1 }
        do { try await Task.sleep(nanoseconds: delay) }
        catch { cancelled = true; throw error }
        return LoudnessResult(integratedLUFS: -14, samplePeak: 0.5)
    }

    func startedIDs() -> [UUID] { ids }
    func maximumConcurrency() -> Int { maximum }
    func wasCancelled() -> Bool { cancelled }
}

private actor UpdateRecorder {
    private var values: [AnalysisStatus] = []
    func record(id: UUID, update: AnalysisUpdate) { values.append(update.status) }
    func statuses() -> [AnalysisStatus] { values }
}

private func analysisTrack(status: AnalysisStatus = .pending) -> AudioTrack {
    AudioTrack(
        bookmark: Data(), fileName: "fixture.wav", format: .wav,
        title: "Fixture", artist: "Artist", duration: 1, fileSize: 1,
        analysisStatus: status
    )
}
