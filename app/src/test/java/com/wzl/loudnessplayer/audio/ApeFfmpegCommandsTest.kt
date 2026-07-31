package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApeFfmpegCommandsTest {
    @Test
    fun streamOverwritesThePrecreatedNamedPipe() {
        val arguments = ApeFfmpegCommands.stream(
            input = "saf:1",
            outputPipe = "/cache/ffmpeg_pipe_1",
            requestedBytePosition = 0L,
        )

        assertTrue("-y" in arguments)
        assertEquals("wav", arguments.valueAfter("-f"))
        assertEquals("/cache/ffmpeg_pipe_1", arguments.last())
    }

    @Test
    fun resumedStreamUsesHeaderlessPcmAndAnApproximateSeek() {
        val arguments = ApeFfmpegCommands.stream(
            input = "saf:1",
            outputPipe = "/cache/ffmpeg_pipe_1",
            requestedBytePosition = 192_000L,
        )

        assertEquals("1.000", arguments.valueAfter("-ss"))
        assertEquals("s16le", arguments.valueAfter("-f"))
    }

    @Test
    fun analyzerUsesPortableSamplePeakAndDiscardOutput() {
        val arguments = ApeFfmpegCommands.analyze("saf:1")

        assertEquals("ebur128=peak=sample", arguments.valueAfter("-af"))
        assertEquals("null", arguments.valueAfter("-f"))
        assertEquals("-", arguments.last())
    }

    private fun Array<String>.valueAfter(option: String): String {
        val index = indexOf(option)
        require(index >= 0 && index < lastIndex)
        return this[index + 1]
    }
}
