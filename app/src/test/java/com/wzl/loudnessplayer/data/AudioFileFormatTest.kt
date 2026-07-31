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
    }

    @Test
    fun usesMimeTypeWhenDisplayNameHasNoExtension() {
        assertEquals(AudioFileFormat.FLAC, AudioFileFormat.from("audio", "audio/x-flac"))
        assertEquals(AudioFileFormat.APE, AudioFileFormat.from(null, "audio/x-ape"))
    }

    @Test
    fun rejectsFormatsOutsideRequestedScope() {
        assertNull(AudioFileFormat.from("song.m4a", "audio/mp4"))
    }

    @Test
    fun apeSupportsLoudnessAnalysisThroughBundledFfmpeg() {
        assertTrue(AudioFileFormat.APE.supportsLoudnessAnalysis)
    }
}
