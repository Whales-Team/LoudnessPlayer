package com.wzl.loudnessplayer.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Measures an APE source with FFmpeg's EBU R128 filter without writing decoded audio to storage.
 */
class ApeLoudnessAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun analyze(uri: Uri): R128Meter.LoudnessResult =
        suspendCancellableCoroutine { continuation ->
            val input = if (uri.scheme.equals("content", ignoreCase = true)) {
                FFmpegKitConfig.getSafParameterForRead(appContext, uri)
            } else {
                uri.path ?: uri.toString()
            }
            val session = FFmpegKit.executeWithArgumentsAsync(
                arrayOf(
                    "-nostdin",
                    "-hide_banner",
                    "-nostats",
                    "-loglevel",
                    "info",
                    "-i",
                    input,
                    "-map",
                    "0:a:0",
                    "-vn",
                    "-sn",
                    "-dn",
                    "-af",
                    "ebur128=peak=true",
                    "-f",
                    "null",
                    "-",
                ),
            ) { completedSession ->
                if (!continuation.isActive) return@executeWithArgumentsAsync
                val result = completedSession.allLogsAsString
                    .takeIf { ReturnCode.isSuccess(completedSession.returnCode) }
                    ?.let(::parseFfmpegLoudnessSummary)
                if (result == null) {
                    continuation.resumeWithException(
                        IllegalStateException("APE 响度分析失败"),
                    )
                } else {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                FFmpegKit.cancel(session.sessionId)
            }
        }
}

internal fun parseFfmpegLoudnessSummary(logOutput: String): R128Meter.LoudnessResult? {
    val summary = logOutput.substringAfterLast("Summary:", missingDelimiterValue = "")
    if (summary.isEmpty()) return null

    val integrated = INTEGRATED_LOUDNESS_PATTERN.find(summary)
        ?.groupValues
        ?.get(1)
        ?.toFfmpegDouble()
        ?.takeIf(Double::isFinite)
        ?: return null
    val peak = TRUE_PEAK_PATTERN.find(summary)
        ?.groupValues
        ?.get(1)
        ?.toFfmpegDouble()
        ?: return null
    return R128Meter.LoudnessResult(
        integratedLufs = integrated,
        samplePeakDbfs = peak,
    )
}

private fun String.toFfmpegDouble(): Double? = when (lowercase()) {
    "inf", "+inf" -> Double.POSITIVE_INFINITY
    "-inf" -> Double.NEGATIVE_INFINITY
    else -> toDoubleOrNull()
}

private val INTEGRATED_LOUDNESS_PATTERN =
    Regex("""(?m)^\s*I:\s*([-+]?(?:\d+(?:\.\d+)?|inf))\s+LUFS\s*$""", RegexOption.IGNORE_CASE)

private val TRUE_PEAK_PATTERN =
    Regex("""(?m)^\s*Peak:\s*([-+]?(?:\d+(?:\.\d+)?|inf))\s+dBFS\s*$""", RegexOption.IGNORE_CASE)
