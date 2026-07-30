package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizationTest {
    @Test
    fun calculatesGainTowardTargetLoudness() {
        assertEquals(6.0, Normalization.gainDb(-20.0, enabled = true), 0.001)
        assertEquals(-4.0, Normalization.gainDb(-10.0, enabled = true), 0.001)
    }

    @Test
    fun clampsUnsafeGainAndHandlesMissingAnalysis() {
        assertEquals(
            Normalization.MAX_GAIN_DB,
            Normalization.gainDb(-40.0, enabled = true),
            0.001,
        )
        assertEquals(
            Normalization.MIN_GAIN_DB,
            Normalization.gainDb(2.0, enabled = true),
            0.001,
        )
        assertEquals(0.0, Normalization.gainDb(null, enabled = true), 0.001)
        assertEquals(0.0, Normalization.gainDb(-20.0, enabled = false), 0.001)
    }

    @Test
    fun supportsUserTargetAndProtectsSamplePeak() {
        assertEquals(
            2.0,
            Normalization.gainDb(
                loudnessLufs = -20.0,
                targetLufs = -18.0,
                enabled = true,
            ),
            0.001,
        )
        assertEquals(
            1.0,
            Normalization.gainDb(
                loudnessLufs = -20.0,
                samplePeakDbfs = -2.0,
                targetLufs = -14.0,
                enabled = true,
            ),
            0.001,
        )
    }
}
