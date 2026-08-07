package com.wzl.loudnessplayer.audio

import com.wzl.loudnessplayer.data.AnalysisStatus

class AnalysisCoordinator {
    private val pending = linkedSetOf<String>()
    private val active = linkedSetOf<String>()
    private var started = false
    var isPausedForPlayback: Boolean = false
        private set

    val pendingIds: Set<String> get() = pending
    val activeIds: Set<String> get() = active
    val shouldRun: Boolean get() = started && !isPausedForPlayback

    fun start(tracks: Collection<Pair<String, AnalysisStatus>>) {
        started = true
        pending += tracks.filter { (_, status) -> status != AnalysisStatus.SUCCESS }.map { it.first }
    }

    fun stop() {
        pending += active
        active.clear()
        started = false
        isPausedForPlayback = false
    }

    fun onPlaybackChanged(isPlaying: Boolean) {
        if (isPlaying) {
            pending += active
            active.clear()
            isPausedForPlayback = true
        } else if (started) {
            isPausedForPlayback = false
        }
    }

    fun nextPendingId(): String? =
        pending.firstOrNull()?.also { id ->
            pending -= id
            active += id
        }

    fun markActive(id: String) {
        pending -= id
        active += id
    }

    fun complete(id: String) {
        active -= id
    }
}
