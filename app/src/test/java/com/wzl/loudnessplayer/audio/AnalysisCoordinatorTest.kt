package com.wzl.loudnessplayer.audio

import com.wzl.loudnessplayer.data.AnalysisStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisCoordinatorTest {
    @Test
    fun startSkipsSuccessfulTracksAndPlaybackPausesPendingWork() {
        val coordinator = AnalysisCoordinator()

        coordinator.start(
            listOf(
                "pending" to AnalysisStatus.PENDING,
                "failed" to AnalysisStatus.FAILED,
                "success" to AnalysisStatus.SUCCESS,
            ),
        )
        coordinator.markActive("pending")
        coordinator.onPlaybackChanged(true)

        assertEquals(setOf("pending", "failed"), coordinator.pendingIds)
        assertTrue(coordinator.activeIds.isEmpty())
        assertTrue(coordinator.isPausedForPlayback)
    }

    @Test
    fun manualStopPreventsAutomaticResume() {
        val coordinator = AnalysisCoordinator()

        coordinator.start(listOf("track" to AnalysisStatus.PENDING))
        coordinator.stop()
        coordinator.onPlaybackChanged(false)

        assertTrue(coordinator.pendingIds.contains("track"))
        assertFalse(coordinator.shouldRun)
    }
}
