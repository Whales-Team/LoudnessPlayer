package com.wzl.loudnessplayer.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Measures an APE source with FFmpeg's EBU R128 filter without writing decoded audio to storage.
 */
class ApeLoudnessAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun analyze(
        uri: Uri,
        formatLabel: String = "闊抽",
    ): R128Meter.LoudnessResult {
        val input = if (uri.scheme.equals("content", ignoreCase = true)) {
            FFmpegKitConfig.getSafParameterForRead(appContext, uri)
        } else {
            uri.path ?: uri.toString()
        }
        val primaryRun = execute(input, includeSamplePeak = true)
        if (primaryRun.isSuccess) {
            parseFfmpegLoudnessSummary(primaryRun.logs)?.let { return it }
            execute(input, includeSamplePeak = false)
                .takeIf { it.isSuccess }
                ?.logs
                ?.let(::parseFfmpegIntegratedLoudnessSummary)
                ?.let { return it }
            throw IllegalStateException("APE 响度结果无法解析")
        }
        if (primaryRun.canRetryWithoutSamplePeak()) {
            val fallbackRun = execute(input, includeSamplePeak = false)
            if (fallbackRun.isSuccess) {
                parseFfmpegIntegratedLoudnessSummary(fallbackRun.logs)?.let { return it }
            }
            throw IllegalStateException("APE 响度解码失败：${fallbackRun.logs.analysisFailureDetail()}")
        }
        throw IllegalStateException("APE 响度解码失败：${primaryRun.logs.analysisFailureDetail()}")
    }

    private suspend fun execute(
        input: String,
        includeSamplePeak: Boolean,
    ): FfmpegRun =
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ApeFfmpegCommands.analyze(input, includeSamplePeak),
            ) { completedSession ->
                if (!continuation.isActive) return@executeWithArgumentsAsync
                continuation.resume(
                    FfmpegRun(
                        isSuccess = ReturnCode.isSuccess(completedSession.returnCode),
                        logs = completedSession.allLogsAsString,
                    ),
                )
            }
            continuation.invokeOnCancellation {
                FFmpegKit.cancel(session.sessionId)
            }
        }
}

private data class FfmpegRun(
    val isSuccess: Boolean,
    val logs: String,
)

private fun FfmpegRun.canRetryWithoutSamplePeak(): Boolean =
    logs.contains("ebur128", ignoreCase = true) ||
        logs.contains("peak=sample", ignoreCase = true) ||
        logs.contains("option", ignoreCase = true)

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

internal fun parseFfmpegIntegratedLoudnessSummary(logOutput: String): R128Meter.LoudnessResult? {
    val summary = logOutput.substringAfterLast("Summary:", missingDelimiterValue = "")
    if (summary.isEmpty()) return null
    val integrated = INTEGRATED_LOUDNESS_PATTERN.findAll(summary)
        .lastOrNull()
        ?.groupValues
        ?.get(1)
        ?.toFfmpegDouble()
        ?.takeIf(Double::isFinite)
        ?: return null
    return R128Meter.LoudnessResult(
        integratedLufs = integrated,
        samplePeakDbfs = 0.0,
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
        .filter { line ->
            line.contains("error", ignoreCase = true) ||
                line.contains("invalid", ignoreCase = true) ||
                line.contains("unsupported", ignoreCase = true) ||
                line.contains("not implemented", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true)
        }
        .lastOrNull()
        ?.take(120)
        ?: "FFmpeg 未返回错误信息"
