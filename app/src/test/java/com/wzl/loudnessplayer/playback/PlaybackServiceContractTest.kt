package com.wzl.loudnessplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServiceContractTest {
    @Test
    fun declaresTheManifestServiceClassName() {
        assertEquals(
            "com.wzl.loudnessplayer.playback.LoudnessPlaybackService",
            LoudnessPlaybackService.SERVICE_CLASS_NAME,
        )
    }
}
