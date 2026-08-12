import MediaPlayer

struct NowPlayingSnapshot: Sendable {
    let track: AudioTrack
    let elapsed: TimeInterval
    let state: PlaybackState

    func dictionary() -> [String: Any] {
        [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPMediaItemPropertyPlaybackDuration: max(0, track.duration),
            MPNowPlayingInfoPropertyElapsedPlaybackTime: max(0, elapsed),
            MPNowPlayingInfoPropertyPlaybackRate: state == .playing ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
        ]
    }
}
