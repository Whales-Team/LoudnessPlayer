import Foundation

struct UserPreferences: Codable, Equatable, Sendable {
    var targetLoudness: Double
    var theme: AppTheme
    var playbackMode: PlaybackMode
    var mergeSameArtist: Bool

    static let `default` = UserPreferences(
        targetLoudness: -14,
        theme: .green,
        playbackMode: .sequential,
        mergeSameArtist: false
    )

    init(
        targetLoudness: Double,
        theme: AppTheme,
        playbackMode: PlaybackMode = .sequential,
        mergeSameArtist: Bool = false
    ) {
        self.targetLoudness = targetLoudness
        self.theme = theme
        self.playbackMode = playbackMode
        self.mergeSameArtist = mergeSameArtist
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        targetLoudness = try container.decodeIfPresent(Double.self, forKey: .targetLoudness) ?? -14
        theme = try container.decodeIfPresent(AppTheme.self, forKey: .theme) ?? .green
        playbackMode = try container.decodeIfPresent(PlaybackMode.self, forKey: .playbackMode) ?? .sequential
        mergeSameArtist = try container.decodeIfPresent(Bool.self, forKey: .mergeSameArtist) ?? false
    }
}

struct LibraryDocument: Codable, Equatable, Sendable {
    static let currentSchema = 1
    static let empty = LibraryDocument()

    var schema: Int
    var tracks: [AudioTrack]
    var folders: [MusicFolder]
    var preferences: UserPreferences

    init(
        schema: Int = currentSchema,
        tracks: [AudioTrack] = [],
        folders: [MusicFolder] = [],
        preferences: UserPreferences = .default
    ) {
        self.schema = schema
        self.tracks = tracks
        self.folders = folders
        self.preferences = preferences
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        _ = try container.decodeIfPresent(Int.self, forKey: .schema) ?? 0
        schema = Self.currentSchema
        tracks = try container.decodeIfPresent([AudioTrack].self, forKey: .tracks) ?? []
        folders = try container.decodeIfPresent([MusicFolder].self, forKey: .folders) ?? []
        preferences = try container.decodeIfPresent(UserPreferences.self, forKey: .preferences) ?? .default
    }
}
