package com.wzl.loudnessplayer.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Streaming approximation of ITU-R BS.1770 / EBU R128 integrated loudness.
 *
 * It applies the standard K-weighting filters, measures overlapping 400 ms
 * blocks every 100 ms, then applies the -70 LUFS absolute gate and the
 * relative -10 LU gate. MP3 is normally mono or stereo; additional channels
 * use equal weighting as a safe fallback.
 */
class R128Meter(
    sampleRate: Int,
    private val channelCount: Int,
) {
    init {
        require(sampleRate > 0)
        require(channelCount > 0)
    }

    private val filters = Array(channelCount) { KWeightingFilter(sampleRate.toDouble()) }
    private val windowFrames = max(1, (sampleRate * BLOCK_SECONDS).roundToInt())
    private val hopFrames = max(1, (sampleRate * HOP_SECONDS).roundToInt())
    private val energyWindow = DoubleArray(windowFrames)
    private val blockEnergies = mutableListOf<Double>()

    private var windowPosition = 0
    private var framesInWindow = 0
    private var framesSinceBlock = 0
    private var energySum = 0.0
    private var peak = 0.0

    fun addFrame(samples: DoubleArray) {
        require(samples.size >= channelCount)

        var frameEnergy = 0.0
        for (channel in 0 until channelCount) {
            val sample = samples[channel].coerceIn(-1.0, 1.0)
            peak = max(peak, kotlin.math.abs(sample))
            val weighted = filters[channel].process(sample)
            frameEnergy += weighted * weighted
        }

        if (framesInWindow == windowFrames) {
            energySum -= energyWindow[windowPosition]
        } else {
            framesInWindow += 1
        }

        energyWindow[windowPosition] = frameEnergy
        energySum += frameEnergy
        windowPosition = (windowPosition + 1) % windowFrames

        if (framesInWindow == windowFrames) {
            if (blockEnergies.isEmpty()) {
                emitBlock()
            } else {
                framesSinceBlock += 1
                if (framesSinceBlock >= hopFrames) {
                    emitBlock()
                }
            }
        }
    }

    fun result(): LoudnessResult? {
        val absoluteGated = blockEnergies.filter { energy ->
            energy > 0.0 && loudnessForEnergy(energy) >= ABSOLUTE_GATE_LUFS
        }
        if (absoluteGated.isEmpty()) return null

        val relativeGate =
            loudnessForEnergy(absoluteGated.average()) + RELATIVE_GATE_LU
        val finalGate = max(ABSOLUTE_GATE_LUFS, relativeGate)
        val relativeGated = absoluteGated.filter { loudnessForEnergy(it) >= finalGate }
        if (relativeGated.isEmpty()) return null

        val loudness = loudnessForEnergy(relativeGated.average())
        val peakDbfs = if (peak > 0.0) 20.0 * log10(peak) else Double.NEGATIVE_INFINITY
        return LoudnessResult(
            integratedLufs = loudness,
            samplePeakDbfs = peakDbfs,
        )
    }

    private fun emitBlock() {
        blockEnergies += energySum / windowFrames
        framesSinceBlock = 0
    }

    private fun loudnessForEnergy(energy: Double): Double =
        LUFS_OFFSET + 10.0 * log10(energy)

    data class LoudnessResult(
        val integratedLufs: Double,
        val samplePeakDbfs: Double,
    )

    private class KWeightingFilter(sampleRate: Double) {
        private val shelf = highShelf(sampleRate)
        private val highPass = highPass(sampleRate)

        fun process(sample: Double): Double = highPass.process(shelf.process(sample))

        private companion object {
            fun highShelf(sampleRate: Double): Biquad {
                val frequency = 1681.974450955533
                val gainDb = 3.999843853973347
                val quality = 0.7071752369554196
                val k = tan(PI * frequency / sampleRate)
                val vh = 10.0.pow(gainDb / 20.0)
                val vb = vh.pow(0.4996667741545416)
                val a0 = 1.0 + k / quality + k * k
                return Biquad(
                    b0 = (vh + vb * k / quality + k * k) / a0,
                    b1 = 2.0 * (k * k - vh) / a0,
                    b2 = (vh - vb * k / quality + k * k) / a0,
                    a1 = 2.0 * (k * k - 1.0) / a0,
                    a2 = (1.0 - k / quality + k * k) / a0,
                )
            }

            fun highPass(sampleRate: Double): Biquad {
                val frequency = 38.13547087602444
                val quality = 0.5003270373238773
                val k = tan(PI * frequency / sampleRate)
                val a0 = 1.0 + k / quality + k * k
                return Biquad(
                    b0 = 1.0 / a0,
                    b1 = -2.0 / a0,
                    b2 = 1.0 / a0,
                    a1 = 2.0 * (k * k - 1.0) / a0,
                    a2 = (1.0 - k / quality + k * k) / a0,
                )
            }
        }
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }
    }

    private companion object {
        const val BLOCK_SECONDS = 0.4
        const val HOP_SECONDS = 0.1
        const val ABSOLUTE_GATE_LUFS = -70.0
        const val RELATIVE_GATE_LU = -10.0
        const val LUFS_OFFSET = -0.691
    }
}

