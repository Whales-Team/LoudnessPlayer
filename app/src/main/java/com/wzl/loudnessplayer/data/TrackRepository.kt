package com.wzl.loudnessplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class TrackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadTracks(): List<AudioTrack> {
        val serialized = preferences.getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(serialized)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        AudioTrack(
                            id = item.getString("id"),
                            uri = item.getString("uri"),
                            title = item.optString("title", "未知曲目"),
                            artist = item.optString("artist", "未知艺术家"),
                            durationMs = item.optLong("durationMs", 0L),
                            format = runCatching {
                                AudioFileFormat.valueOf(item.optString("format", "MP3"))
                            }.getOrDefault(AudioFileFormat.MP3),
                            loudnessLufs = item.optNullableDouble("loudnessLufs"),
                            samplePeakDbfs = item.optNullableDouble("samplePeakDbfs"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTracks(tracks: List<AudioTrack>) {
        val json = JSONArray()
        tracks.forEach { track ->
            json.put(
                JSONObject().apply {
                    put("id", track.id)
                    put("uri", track.uri)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("durationMs", track.durationMs)
                    put("format", track.format.name)
                    put("loudnessLufs", track.loudnessLufs ?: JSONObject.NULL)
                    put("samplePeakDbfs", track.samplePeakDbfs ?: JSONObject.NULL)
                },
            )
        }
        preferences.edit().putString(KEY_TRACKS, json.toString()).apply()
    }

    fun isNormalizationEnabled(): Boolean =
        preferences.getBoolean(KEY_NORMALIZATION_ENABLED, true)

    fun setNormalizationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NORMALIZATION_ENABLED, enabled).apply()
    }

    fun isGroupedByArtist(): Boolean =
        preferences.getBoolean(KEY_GROUP_BY_ARTIST, false)

    fun setGroupedByArtist(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_GROUP_BY_ARTIST, enabled).apply()
    }

    suspend fun createTrack(
        uri: Uri,
        hint: AudioMetadataHint? = null,
    ): AudioTrack = withContext(Dispatchers.IO) {
        val displayName = hint?.displayName ?: queryDisplayName(uri)
        val mimeType = hint?.mimeType ?: appContext.contentResolver.getType(uri)
        val format = AudioFileFormat.from(displayName, mimeType)
            ?: error("不支持的音频格式")

        val hasCompleteHint = hint?.title.cleanMetadata() != null &&
            hint?.artist.cleanArtist() != null &&
            (hint?.durationMs ?: 0L) > 0L
        val extracted = if (hasCompleteHint) {
            null
        } else {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(appContext, uri)
                    ExtractedMetadata(
                        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                        durationMs = retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull(),
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }

        val fallbackTitle = displayName
            ?.substringBeforeLast('.', missingDelimiterValue = displayName)
            ?.takeIf(String::isNotBlank)
            ?: "未知曲目"
        AudioTrack(
            id = uri.toString().sha256(),
            uri = uri.toString(),
            title = extracted?.title.cleanMetadata()
                ?: hint?.title.cleanMetadata()
                ?: fallbackTitle,
            artist = extracted?.artist.cleanArtist()
                ?: hint?.artist.cleanArtist()
                ?: "未知艺术家",
            durationMs = extracted?.durationMs
                ?.takeIf { it > 0L }
                ?: hint?.durationMs?.coerceAtLeast(0L)
                ?: 0L,
            format = format,
        )
    }

    fun isSupportedAudio(uri: Uri): Boolean {
        val displayName = queryDisplayName(uri)
        val mimeType = appContext.contentResolver.getType(uri)
        return AudioFileFormat.from(displayName, mimeType) != null
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }

    private fun String?.cleanMetadata(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private fun String?.cleanArtist(): String? {
        val cleaned = cleanMetadata() ?: return null
        return cleaned.takeUnless {
            it.equals("<unknown>", ignoreCase = true) ||
                it.equals("unknown", ignoreCase = true)
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key).takeUnless(Double::isNaN)
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFERENCES_NAME = "loudness_player"
        const val KEY_TRACKS = "tracks"
        const val KEY_NORMALIZATION_ENABLED = "normalization_enabled"
        const val KEY_GROUP_BY_ARTIST = "group_by_artist"
    }
}

data class AudioMetadataHint(
    val displayName: String?,
    val mimeType: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
)

private data class ExtractedMetadata(
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
)
