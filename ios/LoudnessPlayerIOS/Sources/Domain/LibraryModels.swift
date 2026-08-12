import Foundation

enum PlaybackMode: String, Codable, Sendable { case sequential, repeatOne, shuffle }
enum LibraryViewMode: String, Codable, Sendable { case all, failed, titleGroups, artistGroups }
enum AppTheme: String, Codable, CaseIterable, Sendable { case light, dark, green, blue, brown }

struct MusicFolder: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    var name: String
    var trackIDs: Set<UUID>
}

struct TrackGroup: Identifiable, Equatable, Sendable {
    let id: String
    let title: String
    let tracks: [AudioTrack]
}

struct QueuePreview: Equatable, Sendable {
    let previous: AudioTrack?
    let current: AudioTrack?
    let next: AudioTrack?
}
