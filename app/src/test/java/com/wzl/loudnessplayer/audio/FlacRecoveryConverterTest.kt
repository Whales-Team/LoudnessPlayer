package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacRecoveryConverterTest {
    @Test
    fun createsAnAudioOnlyFlacCommand() {
        val arguments = FlacRecoveryConverter.arguments("input", "output")

        assertEquals(listOf("-y", "-i", "input", "-map", "0:a:0", "-c:a", "flac", "output"), arguments)
        assertTrue(arguments.contains("flac"))
    }
}
