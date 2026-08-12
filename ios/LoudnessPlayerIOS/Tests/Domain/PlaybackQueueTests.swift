import XCTest
@testable import LoudnessPlayer

final class PlaybackQueueTests: XCTestCase {
    func testPersonalFolderTracksAreTheCompleteSequentialScope() {
        let first = queueTrack("A")
        let second = queueTrack("B")
        var queue = PlaybackQueue(scope: [first, second], mode: .sequential, seed: 7)

        XCTAssertNil(queue.preview.previous)
        XCTAssertEqual(queue.preview.current, first)
        XCTAssertEqual(queue.preview.next, second)

        queue.advance()
        XCTAssertEqual(queue.preview.previous, first)
        XCTAssertEqual(queue.preview.current, second)
        XCTAssertNil(queue.preview.next)
    }

    func testRepeatOneKeepsTheCurrentTrack() {
        let first = queueTrack("A")
        let second = queueTrack("B")
        var queue = PlaybackQueue(scope: [first, second], mode: .repeatOne, seed: 7)

        queue.advance()

        XCTAssertEqual(queue.preview.current, first)
        XCTAssertEqual(queue.preview.next, first)
    }

    func testShuffleIsDeterministicForASeed() {
        let tracks = (1...6).map { queueTrack("\($0)") }
        var left = PlaybackQueue(scope: tracks, mode: .shuffle, seed: 7)
        var right = PlaybackQueue(scope: tracks, mode: .shuffle, seed: 7)
        var leftOrder: [UUID] = []
        var rightOrder: [UUID] = []

        for _ in tracks.indices {
            leftOrder.append(left.preview.current!.id)
            rightOrder.append(right.preview.current!.id)
            left.advance()
            right.advance()
        }

        XCTAssertEqual(leftOrder, rightOrder)
        XCTAssertEqual(Set(leftOrder), Set(tracks.map(\.id)))
    }

    func testSequentialRetreatAtStartDoesNotWrap() {
        let first = queueTrack("A")
        var queue = PlaybackQueue(scope: [first, queueTrack("B")], mode: .sequential, seed: 7)

        queue.retreat()

        XCTAssertEqual(queue.preview.current, first)
    }
}

private func queueTrack(_ title: String) -> AudioTrack {
    AudioTrack(
        id: UUID(), bookmark: Data(), fileName: "\(title).mp3", format: .mp3,
        title: title, artist: "歌手", duration: 60, fileSize: 100
    )
}
