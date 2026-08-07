package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApeLoudnessAnalyzerTest {
    @Test
    fun parsesOnlyTheFinalEbur128Summary() {
        val output = """
            [Parsed_ebur128_0] t: 1.0 I: -45.0 LUFS
            [Parsed_ebur128_0] Summary:

            [Parsed_ebur128_0] Integrated loudness:
            [Parsed_ebur128_0]   I:         -14.2 LUFS
            [Parsed_ebur128_0]   Threshold: -24.5 LUFS

            [Parsed_ebur128_0] Sample peak:
            [Parsed_ebur128_0]   Peak:       -0.7 dBFS
        """.trimIndent()

        val result = parseFfmpegLoudnessSummary(output)

        assertEquals(-14.2, result?.integratedLufs ?: 0.0, 0.001)
        assertEquals(-0.7, result?.samplePeakDbfs ?: 0.0, 0.001)
    }

    @Test
    fun rejectsOutputWithoutACompleteSummary() {
        assertNull(parseFfmpegLoudnessSummary("I: -14.2 LUFS"))
        assertNull(
            parseFfmpegLoudnessSummary(
                """
                    Summary:
                      Integrated loudness:
                        I: -inf LUFS
                      True peak:
                        Peak: -inf dBFS
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun parsesIntegratedLoudnessWhenThePeakModeIsUnavailable() {
        val output = """
            [Parsed_ebur128_0] Summary:

            [Parsed_ebur128_0] Integrated loudness:
            [Parsed_ebur128_0]   I:         -16.4 LUFS
            [Parsed_ebur128_0]   Threshold: -26.4 LUFS
        """.trimIndent()

        val result = parseFfmpegIntegratedLoudnessSummary(output)

        assertEquals(-16.4, result?.integratedLufs ?: 0.0, 0.001)
        assertEquals(0.0, result?.samplePeakDbfs ?: -1.0, 0.001)
    }
}
