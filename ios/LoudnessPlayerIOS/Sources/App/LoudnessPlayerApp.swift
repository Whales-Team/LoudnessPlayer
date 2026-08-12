import SwiftUI

enum AppIdentity {
    static let version = "1.0.0"
    static let displayName = "音悦"
    static let minimumIOS = "16.0"
}

@main
struct LoudnessPlayerApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
