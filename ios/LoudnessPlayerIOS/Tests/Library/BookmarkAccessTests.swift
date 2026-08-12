import XCTest
@testable import LoudnessPlayer

final class BookmarkAccessTests: XCTestCase {
    func testStopsSecurityAccessAfterSuccessfulOperation() throws {
        let access = RecordingSecurityAccess(startResult: true)
        let resolved = ResolvedBookmark(url: URL(fileURLWithPath: "/music/song.ape"), isStale: false, access: access)

        let name = try resolved.withAccess { $0.lastPathComponent }

        XCTAssertEqual(name, "song.ape")
        XCTAssertEqual(access.events, [.start, .stop])
    }

    func testStopsSecurityAccessAfterOperationThrows() {
        let access = RecordingSecurityAccess(startResult: true)
        let resolved = ResolvedBookmark(url: URL(fileURLWithPath: "/music/broken.wma"), isStale: false, access: access)

        XCTAssertThrowsError(try resolved.withAccess { _ in throw TestError.decodeFailed })

        XCTAssertEqual(access.events, [.start, .stop])
    }

    func testDoesNotStopWhenSecurityAccessDidNotStart() {
        let access = RecordingSecurityAccess(startResult: false)
        let resolved = ResolvedBookmark(url: URL(fileURLWithPath: "/music/missing.mp3"), isStale: false, access: access)

        XCTAssertThrowsError(try resolved.withAccess { _ in "unreachable" }) { error in
            XCTAssertEqual(error as? BookmarkError, .accessDenied)
        }
        XCTAssertEqual(access.events, [.start])
    }

    func testStaleBookmarkIsReturnedForReauthorization() throws {
        let resolver = StubBookmarkResolver(result: ResolvedBookmark(
            url: URL(fileURLWithPath: "/music/moved.flac"), isStale: true,
            access: RecordingSecurityAccess(startResult: true)
        ))

        XCTAssertTrue(try resolver.resolve(Data([4, 5])).isStale)
    }
}

private enum TestError: Error { case decodeFailed }

private final class RecordingSecurityAccess: SecurityResourceAccess, @unchecked Sendable {
    enum Event: Equatable { case start, stop }
    let startResult: Bool
    private(set) var events: [Event] = []

    init(startResult: Bool) { self.startResult = startResult }
    func start(url: URL) -> Bool { events.append(.start); return startResult }
    func stop(url: URL) { events.append(.stop) }
}

private struct StubBookmarkResolver: BookmarkResolving {
    let result: ResolvedBookmark
    func resolve(_ bookmark: Data) throws -> ResolvedBookmark { result }
}
