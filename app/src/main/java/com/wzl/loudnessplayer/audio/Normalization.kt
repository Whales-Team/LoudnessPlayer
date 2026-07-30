package com.wzl.loudnessplayer.audio

import kotlin.math.pow

object Normalization {
    const val TARGET_LUFS = -14.0
    const val MIN_GAIN_DB = -12.0
    const val MAX_GAIN_DB = 9.0

    fun gainDb(loudnessLufs: Double?, enabled: Boolean): Double {
        if (!enabled || loudnessLufs == null || !loudnessLufs.isFinite()) return 0.0
        return (TARGET_LUFS - loudnessLufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
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

