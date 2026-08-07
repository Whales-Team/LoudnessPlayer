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
    val analysisStatus: AnalysisStatus = if (loudnessLufs != null) {
        AnalysisStatus.SUCCESS
    } else {
        AnalysisStatus.PENDING
    },
    val analysisFailureMessage: String? = null,
)

enum class AnalysisStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

fun AudioTrack.withEditedMetadata(title: String, artist: String): AudioTrack = copy(
    title = title.trim().takeIf(String::isNotEmpty) ?: this.title,
    artist = artist.trim().takeIf(String::isNotEmpty) ?: this.artist,
)
