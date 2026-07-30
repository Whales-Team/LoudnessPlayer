package com.wzl.loudnessplayer.data

import java.util.Locale

enum class AudioFileFormat(
    val extension: String,
    val displayName: String,
    val supportsLoudnessAnalysis: Boolean,
) {
    MP3("mp3", "MP3", true),
    FLAC("flac", "FLAC", true),
    WAV("wav", "WAV", true),
    APE("ape", "APE", false),
    ;

    companion object {
        fun from(
            displayName: String?,
            mimeType: String?,
            fallback: AudioFileFormat? = null,
        ): AudioFileFormat? {
            val extension = displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.US)
            entries.firstOrNull { it.extension == extension }?.let { return it }

            return when (mimeType?.lowercase(Locale.US)) {
                "audio/mpeg", "audio/mp3" -> MP3
                "audio/flac", "audio/x-flac" -> FLAC
                "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> WAV
                "audio/ape", "audio/x-ape", "audio/monkeys-audio" -> APE
                else -> fallback
            }
        }
    }
}
