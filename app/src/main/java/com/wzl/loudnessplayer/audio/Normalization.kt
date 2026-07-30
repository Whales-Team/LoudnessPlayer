package com.wzl.loudnessplayer.audio

import kotlin.math.pow

object Normalization {
    const val DEFAULT_TARGET_LUFS = -14.0
    const val MIN_TARGET_LUFS = -24.0
    const val MAX_TARGET_LUFS = -8.0
    const val MIN_GAIN_DB = -12.0
    const val MAX_GAIN_DB = 9.0
    const val SAFE_SAMPLE_PEAK_DBFS = -1.0

    fun gainDb(
        loudnessLufs: Double?,
        enabled: Boolean,
        targetLufs: Double = DEFAULT_TARGET_LUFS,
        samplePeakDbfs: Double? = null,
    ): Double {
        if (!enabled || loudnessLufs == null || !loudnessLufs.isFinite()) return 0.0
        val safeTarget = targetLufs.coerceIn(MIN_TARGET_LUFS, MAX_TARGET_LUFS)
        val requestedGain = safeTarget - loudnessLufs
        val peakLimitedGain = samplePeakDbfs
            ?.takeIf(Double::isFinite)
            ?.let { peak -> minOf(requestedGain, SAFE_SAMPLE_PEAK_DBFS - peak) }
            ?: requestedGain
        return peakLimitedGain.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    fun attenuationFactor(gainDb: Double): Float =
        if (gainDb >= 0.0) {
            1f
        } else {
            10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
        }

    fun enhancerGainMillibels(gainDb: Double): Int =
        (gainDb.coerceAtLeast(0.0) * 100.0).toInt()
}
