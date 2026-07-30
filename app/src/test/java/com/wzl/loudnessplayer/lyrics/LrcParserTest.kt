package com.wzl.loudnessplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMultipleTimestampsAndFindsCurrentLine() {
        val lines = LrcParser.parse(
            """
            [00:01.50]第一句
            [00:03.20][00:05.00]第二句
            """.trimIndent(),
        )

        assertEquals(3, lines.size)
        assertNull(LrcParser.lineAt(lines, 1_000L))
        assertEquals("第一句", LrcParser.lineAt(lines, 2_000L))
        assertEquals("第二句", LrcParser.lineAt(lines, 5_500L))
    }

    @Test
    fun fallsBackToPlainLyrics() {
        assertEquals("没有时间轴的歌词", LrcParser.fallbackLine("没有时间轴的歌词"))
    }
}
