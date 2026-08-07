package com.wzl.loudnessplayer.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ConversionResult(val isSuccess: Boolean, val message: String? = null)

/** Converts only after the user has explicitly selected a writable destination folder. */
class FlacRecoveryConverter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun convert(source: Uri, destination: Uri): ConversionResult =
        suspendCancellableCoroutine { continuation ->
            val input = source.toFfmpegInput()
            val output = FFmpegKitConfig.getSafParameterForWrite(appContext, destination)
            val session = FFmpegKit.executeWithArgumentsAsync(arguments(input, output).toTypedArray()) { completed ->
                if (continuation.isActive) {
                    continuation.resume(
                        ConversionResult(
                            isSuccess = ReturnCode.isSuccess(completed.returnCode),
                            message = completed.allLogsAsString.takeLast(240).takeIf { !ReturnCode.isSuccess(completed.returnCode) },
                        ),
                    )
                }
            }
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }

    private fun Uri.toFfmpegInput(): String =
        if (scheme.equals("content", ignoreCase = true)) {
            FFmpegKitConfig.getSafParameterForRead(appContext, this)
        } else {
            path ?: toString()
        }

    companion object {
        internal fun arguments(input: String, output: String): List<String> = listOf(
            "-y", "-i", input, "-map", "0:a:0", "-c:a", "flac", output,
        )
    }
}
