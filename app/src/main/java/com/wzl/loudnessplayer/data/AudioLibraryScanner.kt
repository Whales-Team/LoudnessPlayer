package com.wzl.loudnessplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AudioLibraryScanner(
    context: Context,
    private val repository: TrackRepository,
) {
    private val appContext = context.applicationContext

    suspend fun scanDevice(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )

        buildList {
            appContext.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    val displayName = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeIndex)
                    if (AudioFileFormat.from(displayName, mimeType) == null) continue

                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                    val hint = AudioMetadataHint(
                        displayName = displayName,
                        mimeType = mimeType,
                        title = cursor.getString(titleIndex),
                        artist = cursor.getString(artistIndex),
                        durationMs = cursor.getLong(durationIndex),
                    )
                    runCatching { repository.createTrack(uri, hint) }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }
    }

    suspend fun scanFolder(treeUri: Uri): List<AudioTrack> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("无法读取所选文件夹")
        val pending = ArrayDeque<DocumentFile>()
        pending.add(root)

        buildList {
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val current = pending.removeFirst()
                current.listFiles().forEach { child ->
                    when {
                        child.isDirectory -> pending.add(child)
                        child.isFile &&
                            AudioFileFormat.from(child.name, child.type) != null -> {
                            val hint = AudioMetadataHint(
                                displayName = child.name,
                                mimeType = child.type,
                                title = null,
                                artist = null,
                                durationMs = null,
                            )
                            runCatching { repository.createTrack(child.uri, hint) }
                                .getOrNull()
                                ?.let(::add)
                        }
                    }
                }
            }
        }
    }
}
