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
                ApeFfmpegCommands.analyze(input),
            ) { completedSession ->
                if (!continuation.isActive) return@executeWithArgumentsAsync
                val logs = completedSession.allLogsAsString
                val result = logs
                    .takeIf { ReturnCode.isSuccess(completedSession.returnCode) }
                    ?.let(::parseFfmpegLoudnessSummary)
                when {
                    !ReturnCode.isSuccess(completedSession.returnCode) -> {
                        continuation.resumeWithException(
                            IllegalStateException(
                                "APE 响度解码失败：${logs.analysisFailureDetail()}",
                            ),
                        )
                    }

                    result == null -> {
                        continuation.resumeWithException(
                            IllegalStateException("APE 响度结果无法解析"),
                        )
                    }

                    else -> continuation.resume(result)
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

    val integrated = INTEGRATED_LOUDNESS_PATTERN.findAll(summary)
        .lastOrNull()
        ?.groupValues
        ?.get(1)
        ?.toFfmpegDouble()
        ?.takeIf(Double::isFinite)
        ?: return null
    val peak = SAMPLE_PEAK_PATTERN.findAll(summary)
        .lastOrNull()
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
    Regex("""\bI:\s*([-+]?(?:\d+(?:\.\d+)?|inf))\s+LUFS\b""", RegexOption.IGNORE_CASE)

private val SAMPLE_PEAK_PATTERN =
    Regex("""\bPeak:\s*([-+]?(?:\d+(?:\.\d+)?|inf))\s+dBFS\b""", RegexOption.IGNORE_CASE)

private fun String.analysisFailureDetail(): String =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .lastOrNull()
        ?.take(120)
        ?: "FFmpeg 未返回错误信息"
