package com.wzl.loudnessplayer.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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

    suspend fun createTrack(uri: Uri): AudioTrack = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val fallbackTitle = queryDisplayName(uri) ?: "未知曲目"
            AudioTrack(
                id = uri.toString().sha256(),
                uri = uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf(String::isNotBlank)
                    ?: fallbackTitle.substringBeforeLast('.'),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf(String::isNotBlank)
                    ?: "未知艺术家",
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L,
            )
        } finally {
            retriever.release()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
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
    }
}

