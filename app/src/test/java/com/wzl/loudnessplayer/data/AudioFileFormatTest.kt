package com.wzl.loudnessplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileFormatTest {
    @Test
    fun recognizesSupportedExtensionsCaseInsensitively() {
        assertEquals(AudioFileFormat.MP3, AudioFileFormat.from("song.MP3", null))
        assertEquals(AudioFileFormat.FLAC, AudioFileFormat.from("song.flac", null))
        assertEquals(AudioFileFormat.WAV, AudioFileFormat.from("song.WaV", null))
        assertEquals(AudioFileFormat.APE, AudioFileFormat.from("song.ape", null))
        assertEquals(AudioFileFormat.M4A, AudioFileFormat.from("song.M4A", null))
        assertEquals(AudioFileFormat.AAC, AudioFileFormat.from("song.aac", null))
        assertEquals(AudioFileFormat.OGG, AudioFileFormat.from("song.ogg", null))
        assertEquals(AudioFileFormat.OPUS, AudioFileFormat.from("song.opus", null))
        assertEquals(AudioFileFormat.WMA, AudioFileFormat.from("song.wma", null))
    }

    @Test
    fun usesMimeTypeWhenDisplayNameHasNoExtension() {
        assertEquals(AudioFileFormat.FLAC, AudioFileFormat.from("audio", "audio/x-flac"))
        assertEquals(AudioFileFormat.APE, AudioFileFormat.from(null, "audio/x-ape"))
        assertEquals(AudioFileFormat.M4A, AudioFileFormat.from(null, "audio/mp4"))
        assertEquals(AudioFileFormat.AAC, AudioFileFormat.from(null, "audio/aac"))
        assertEquals(AudioFileFormat.OGG, AudioFileFormat.from(null, "audio/ogg"))
        assertEquals(AudioFileFormat.OPUS, AudioFileFormat.from(null, "audio/opus"))
        assertEquals(AudioFileFormat.WMA, AudioFileFormat.from(null, "audio/x-ms-wma"))
    }

    @Test
    fun rejectsFormatsOutsideRequestedScope() {
        assertNull(AudioFileFormat.from("song.aiff", "audio/aiff"))
    }

    @Test
    fun apeSupportsLoudnessAnalysisThroughBundledFfmpeg() {
        assertTrue(AudioFileFormat.APE.supportsLoudnessAnalysis)
        assertTrue(AudioFileFormat.WMA.supportsLoudnessAnalysis)
    }
}
