import MediaPlayer
import XCTest
@testable import LoudnessPlayer

@MainActor
final class RemoteCommandServiceTests: XCTestCase {
    func testSnapshotMapsTrackAndPlaybackStateToNowPlayingInfo() {
        let track = AudioTrack(
            bookmark: Data(), fileName: "夜曲.flac", format: .flac,
            title: "夜曲", artist: "周杰伦", duration: 226, fileSize: 1
        )
        let snapshot = NowPlayingSnapshot(
            track: track, elapsed: 42, state: .playing
        )

        let info = snapshot.dictionary()

        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "夜曲")
        XCTAssertEqual(info[MPMediaItemPropertyArtist] as? String, "周杰伦")
        XCTAssertEqual(info[MPMediaItemPropertyPlaybackDuration] as? Double, 226)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 42)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 1)
    }

    func testRemoteActionsCallBoundPlayerExactlyOnce() {
        let probe = RemoteCommandProbe()
        let service = RemoteCommandService(commandCenter: nil)
        service.bind(actions: .init(
            play: { probe.play += 1 }, pause: { probe.pause += 1 },
            next: { probe.next += 1 }, previous: { probe.previous += 1 },
            seek: { probe.seek = $0 }
        ))

        service.performForTesting(.play)
        service.performForTesting(.pause)
        service.performForTesting(.next)
        service.performForTesting(.previous)
        service.seekForTesting(to: 18)

        XCTAssertEqual(probe.play, 1)
        XCTAssertEqual(probe.pause, 1)
        XCTAssertEqual(probe.next, 1)
        XCTAssertEqual(probe.previous, 1)
        XCTAssertEqual(probe.seek, 18)
    }
}

@MainActor
private final class RemoteCommandProbe {
    var play = 0
    var pause = 0
    var next = 0
    var previous = 0
    var seek: TimeInterval?
}
