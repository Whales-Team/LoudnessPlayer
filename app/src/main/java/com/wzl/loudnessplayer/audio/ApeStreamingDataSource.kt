package com.wzl.loudnessplayer.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Streams APE through FFmpeg into ExoPlayer as PCM WAV without creating a converted audio file.
 *
 * FFmpegKit's named pipe is only a kernel FIFO. It does not contain a persistent copy of the
 * decoded audio and is removed when this data source closes.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ApeStreamingDataSource private constructor(
    context: Context,
) : BaseDataSource(false) {
    private val appContext = context.applicationContext
    private val resourceLock = Any()

    private var currentUri: Uri? = null
    private var pipePath: String? = null
    private var pipeAnchor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var ffmpegSession: FFmpegSession? = null
    private var opened = false
    @Volatile
    private var receivedAudio = false

    @Volatile
    private var decodingFailure: IOException? = null

    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "APE streaming data source is already open" }
        transferInitializing(dataSpec)

        val sourceUri = sourceUri(dataSpec.uri)
            ?: throw IOException("APE 实时解码地址无效")
        val registeredPipe = FFmpegKitConfig.registerNewFFmpegPipe(appContext)
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("无法创建 APE 实时解码管道")

        currentUri = dataSpec.uri
        pipePath = registeredPipe

        try {
            // O_RDWR keeps FIFO opening non-blocking until FFmpeg writes the first decoded bytes.
            pipeAnchor = ParcelFileDescriptor.open(
                File(registeredPipe),
                ParcelFileDescriptor.MODE_READ_WRITE,
            )
            inputStream = FileInputStream(registeredPipe)
            ffmpegSession = FFmpegKit.executeWithArgumentsAsync(
                ApeFfmpegCommands.stream(
                    input = ffmpegInput(sourceUri),
                    outputPipe = registeredPipe,
                    requestedBytePosition = dataSpec.position,
                ),
                ::onFfmpegCompleted,
            )
        } catch (error: Exception) {
            closeResources(cancelSession = true)
            throw IOException("无法启动 APE 实时解码", error)
        }

        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        decodingFailure?.let { throw it }
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val bytesRead = try {
            stream.read(buffer, offset, length)
        } catch (error: IOException) {
            decodingFailure?.let { throw it }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            decodingFailure?.let { throw it }
            return C.RESULT_END_OF_INPUT
        }
        if (!receivedAudio) {
            receivedAudio = true
            synchronized(resourceLock) {
                pipeAnchor?.close()
                pipeAnchor = null
            }
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        val wasOpened = opened
        opened = false
        closeResources(cancelSession = true)
        currentUri = null
        receivedAudio = false
        decodingFailure = null
        if (wasOpened) transferEnded()
    }

    private fun onFfmpegCompleted(session: FFmpegSession) {
        if (!ReturnCode.isSuccess(session.returnCode) && currentUri != null) {
            val detail = session.allLogsAsString.failureDetail()
            decodingFailure = IOException(
                buildString {
                    append("APE 实时解码失败")
                    if (detail != null) append("：$detail")
                },
            )
            if (!receivedAudio) {
                synchronized(resourceLock) {
                    runCatching { inputStream?.close() }
                    inputStream = null
                    runCatching { pipeAnchor?.close() }
                    pipeAnchor = null
                }
            }
        }
    }

    private fun closeResources(cancelSession: Boolean) {
        synchronized(resourceLock) {
            if (cancelSession) {
                ffmpegSession?.sessionId?.let { sessionId ->
                    FFmpegKit.cancel(sessionId)
                }
            }
            ffmpegSession = null
            runCatching { inputStream?.close() }
            inputStream = null
            runCatching { pipeAnchor?.close() }
            pipeAnchor = null
            pipePath?.let { path ->
                runCatching { FFmpegKitConfig.closeFFmpegPipe(path) }
            }
            pipePath = null
        }
    }

    private fun ffmpegInput(sourceUri: Uri): String =
        if (sourceUri.scheme.equals("content", ignoreCase = true)) {
            FFmpegKitConfig.getSafParameterForRead(appContext, sourceUri)
        } else {
            sourceUri.path ?: sourceUri.toString()
        }

    class Factory(
        context: Context,
    ) : DataSource.Factory {
        private val appContext = context.applicationContext

        override fun createDataSource(): DataSource = ApeStreamingDataSource(appContext)
    }

    companion object {
        private const val STREAM_SCHEME = "loudness-ape"
        private const val SOURCE_PARAMETER = "source"

        fun streamingUri(sourceUri: String): Uri = Uri.Builder()
            .scheme(STREAM_SCHEME)
            .authority("decode")
            .appendQueryParameter(SOURCE_PARAMETER, sourceUri)
            .build()

        private fun sourceUri(streamingUri: Uri): Uri? {
            if (!streamingUri.scheme.equals(STREAM_SCHEME, ignoreCase = true)) return null
            return streamingUri.getQueryParameter(SOURCE_PARAMETER)
                ?.takeIf(String::isNotBlank)
                ?.let(Uri::parse)
        }
    }
}

private fun String.failureDetail(): String? =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .lastOrNull()
        ?.take(160)
