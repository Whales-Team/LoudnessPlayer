import Foundation

struct AnalysisUpdate: Equatable, Sendable {
    let status: AnalysisStatus
    let result: LoudnessResult?
    let message: String?

    static let pending = AnalysisUpdate(status: .pending, result: nil, message: nil)

    static func succeeded(_ result: LoudnessResult) -> AnalysisUpdate {
        AnalysisUpdate(status: .succeeded, result: result, message: nil)
    }

    static func failed(_ error: Error) -> AnalysisUpdate {
        AnalysisUpdate(status: .failed, result: nil, message: String(describing: error))
    }
}

actor AnalysisCoordinator {
    typealias Decode = @Sendable (AudioTrack) async throws -> LoudnessResult
    typealias Commit = @Sendable (UUID, AnalysisUpdate) async -> Void

    private let decode: Decode
    private let commit: Commit
    private var work: Task<Void, Never>?

    init(decode: @escaping Decode, commit: @escaping Commit) {
        self.decode = decode
        self.commit = commit
    }

    func start(tracks: [AudioTrack]) {
        stop()
        let candidates = tracks.filter { $0.analysisStatus != .succeeded }
        guard !candidates.isEmpty else { return }
        let decode = self.decode
        let commit = self.commit
        work = Task {
            await withTaskGroup(of: Void.self) { group in
                var iterator = candidates.makeIterator()
                for _ in 0..<2 {
                    guard let track = iterator.next() else { break }
                    group.addTask { await Self.analyze(track, decode: decode, commit: commit) }
                }
                while await group.next() != nil {
                    guard !Task.isCancelled, let track = iterator.next() else { continue }
                    group.addTask { await Self.analyze(track, decode: decode, commit: commit) }
                }
                if Task.isCancelled { group.cancelAll() }
            }
        }
    }

    func stop() {
        work?.cancel()
    }

    func playbackDidStart() {
        stop()
    }

    func waitUntilIdle() async {
        let current = work
        await current?.value
    }

    private static func analyze(
        _ track: AudioTrack, decode: Decode, commit: Commit
    ) async {
        do {
            try Task.checkCancellation()
            let result = try await decode(track)
            try Task.checkCancellation()
            await commit(track.id, .succeeded(result))
        } catch is CancellationError {
            await commit(track.id, .pending)
        } catch {
            await commit(track.id, .failed(error))
        }
    }
}
