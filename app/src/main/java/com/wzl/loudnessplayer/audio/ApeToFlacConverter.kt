package com.wzl.loudnessplayer.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.wzl.loudnessplayer.data.AudioMetadataHint
import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ApeToFlacConverter(
    context: Context,
    private val repository: TrackRepository,
) {
    private val appContext = context.applicationContext

    suspend fun convert(source: AudioTrack): AudioTrack = withContext(Dispatchers.IO) {
        val workDirectory = File(appContext.cacheDir, "ape_conversion").apply { mkdirs() }
        val inputFile = File(workDirectory, "${source.id}.ape")
        val outputFile = File(workDirectory, "${source.id}.flac")
        try {
            appContext.contentResolver.openInputStream(Uri.parse(source.uri)).use { input ->
                requireNotNull(input) { "无法读取 APE 文件" }
                inputFile.outputStream().use(input::copyTo)
            }

            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y",
                    "-i",
                    inputFile.absolutePath,
                    "-map_metadata",
                    "0",
                    "-vn",
                    "-c:a",
                    "flac",
                    "-compression_level",
                    FLAC_COMPRESSION_LEVEL.toString(),
                    outputFile.absolutePath,
                ),
            )
            if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.isFile) {
                error("APE 转 FLAC 失败：${session.failStackTrace.orEmpty().take(180)}")
            }

            val displayName = "${safeFileName(source.title)}.flac"
            val outputUri = saveToMusicLibrary(outputFile, displayName)
            repository.createTrack(
                uri = outputUri,
                hint = AudioMetadataHint(
                    displayName = displayName,
                    mimeType = "audio/flac",
                    title = source.title,
                    artist = source.artist,
                    durationMs = source.durationMs,
                ),
            ).copy(
                loudnessLufs = source.loudnessLufs,
                samplePeakDbfs = source.samplePeakDbfs,
                lyrics = source.lyrics,
            )
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun saveToMusicLibrary(
        convertedFile: File,
        displayName: String,
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/flac")
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/LoudnessPlayer",
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val outputUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法在系统音乐库中创建 FLAC 文件")
            try {
                resolver.openOutputStream(outputUri, "w").use { output ->
                    requireNotNull(output) { "无法写入 FLAC 文件" }
                    convertedFile.inputStream().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(outputUri, values, null, null)
                return outputUri
            } catch (error: Throwable) {
                resolver.delete(outputUri, null, null)
                throw error
            }
        }

        val musicRoot = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: appContext.filesDir
        val outputDirectory = File(musicRoot, "LoudnessPlayer").apply { mkdirs() }
        val outputFile = uniqueFile(outputDirectory, displayName)
        convertedFile.copyTo(outputFile)
        return Uri.fromFile(outputFile)
    }

    private fun uniqueFile(
        directory: File,
        displayName: String,
    ): File {
        val baseName = displayName.substringBeforeLast('.')
        val extension = displayName.substringAfterLast('.', "flac")
        var candidate = File(directory, displayName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(directory, "$baseName ($suffix).$extension")
            suffix += 1
        }
        return candidate
    }

    private fun safeFileName(title: String): String =
        title
            .replace(INVALID_FILE_CHARACTERS, "_")
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "converted_audio" }

    private companion object {
        const val FLAC_COMPRESSION_LEVEL = 5
        const val MAX_FILE_NAME_LENGTH = 80
        val INVALID_FILE_CHARACTERS = Regex("""[\\/:*?"<>|]""")
    }
}
