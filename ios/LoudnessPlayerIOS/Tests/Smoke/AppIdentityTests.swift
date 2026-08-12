import XCTest
@testable import LoudnessPlayer

final class AppIdentityTests: XCTestCase {
    func testReleaseIdentityMatchesThePublishedIOSRelease() {
        XCTAssertEqual(AppIdentity.version, "1.0.0")
        XCTAssertEqual(AppIdentity.displayName, "音悦")
        XCTAssertEqual(AppIdentity.minimumIOS, "16.0")
    }
}
