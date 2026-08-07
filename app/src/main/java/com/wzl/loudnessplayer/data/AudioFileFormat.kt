package com.wzl.loudnessplayer.data

import java.util.Locale

enum class AudioFileFormat(
    val extension: String,
    val displayName: String,
    val supportsLoudnessAnalysis: Boolean,
    val decoderPath: DecoderPath,
) {
    MP3("mp3", "MP3", true, DecoderPath.PLATFORM),
    FLAC("flac", "FLAC", true, DecoderPath.PLATFORM),
    WAV("wav", "WAV", true, DecoderPath.PLATFORM),
    APE("ape", "APE", true, DecoderPath.FFMPEG_PCM),
    M4A("m4a", "M4A", true, DecoderPath.PLATFORM),
    AAC("aac", "AAC", true, DecoderPath.PLATFORM),
    OGG("ogg", "OGG", true, DecoderPath.PLATFORM),
    OPUS("opus", "Opus", true, DecoderPath.PLATFORM),
    WMA("wma", "WMA", true, DecoderPath.FFMPEG_PCM),
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
                "audio/mp4", "audio/x-m4a", "audio/m4a" -> M4A
                "audio/aac", "audio/aacp", "audio/x-aac" -> AAC
                "audio/ogg", "application/ogg" -> OGG
                "audio/opus", "audio/ogg; codecs=opus" -> OPUS
                "audio/x-ms-wma", "audio/wma", "audio/x-wma" -> WMA
                else -> fallback
            }
        }
    }
}

enum class DecoderPath {
    PLATFORM,
    FFMPEG_PCM,
}
