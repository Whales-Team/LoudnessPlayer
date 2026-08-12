import MediaPlayer

@MainActor
final class RemoteCommandService {
    enum Command { case play, pause, next, previous }

    struct Actions {
        let play: () -> Void
        let pause: () -> Void
        let next: () -> Void
        let previous: () -> Void
        let seek: (TimeInterval) -> Void
    }

    private let commandCenter: MPRemoteCommandCenter?
    private var actions: Actions?
    private var tokens: [(MPRemoteCommand, Any)] = []

    init(commandCenter: MPRemoteCommandCenter? = .shared()) {
        self.commandCenter = commandCenter
    }

    func bind(actions: Actions) {
        shutdown()
        self.actions = actions
        guard let commandCenter else { return }
        register(commandCenter.playCommand) { [weak self] _ in self?.perform(.play) }
        register(commandCenter.pauseCommand) { [weak self] _ in self?.perform(.pause) }
        register(commandCenter.nextTrackCommand) { [weak self] _ in self?.perform(.next) }
        register(commandCenter.previousTrackCommand) { [weak self] _ in self?.perform(.previous) }
        register(commandCenter.changePlaybackPositionCommand) { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.actions?.seek(event.positionTime)
            return .success
        }
    }

    func publish(_ snapshot: NowPlayingSnapshot?) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = snapshot?.dictionary()
        MPNowPlayingInfoCenter.default().playbackState = {
            switch snapshot?.state {
            case .playing: .playing
            case .paused: .paused
            default: .stopped
            }
        }()
    }

    func shutdown() {
        for (command, token) in tokens { command.removeTarget(token) }
        tokens.removeAll()
        actions = nil
    }

    func performForTesting(_ command: Command) { _ = perform(command) }
    func seekForTesting(to seconds: TimeInterval) { actions?.seek(seconds) }

    private func perform(_ command: Command) -> MPRemoteCommandHandlerStatus {
        guard let actions else { return .noActionableNowPlayingItem }
        switch command {
        case .play: actions.play()
        case .pause: actions.pause()
        case .next: actions.next()
        case .previous: actions.previous()
        }
        return .success
    }

    private func register(
        _ command: MPRemoteCommand,
        handler: @escaping (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus
    ) {
        tokens.append((command, command.addTarget(handler: handler)))
    }
}
