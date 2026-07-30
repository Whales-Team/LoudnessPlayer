package com.wzl.loudnessplayer.data

data class AudioTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val format: AudioFileFormat = AudioFileFormat.MP3,
    val loudnessLufs: Double? = null,
    val samplePeakDbfs: Double? = null,
    val lyrics: String? = null,
)
