package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class R128MeterTest {
    @Test
    fun silenceHasNoMeasurableLoudness() {
        val meter = R128Meter(SAMPLE_RATE, 2)
        repeat(SAMPLE_RATE) {
            meter.addFrame(doubleArrayOf(0.0, 0.0))
        }

        assertNull(meter.result())
    }

    @Test
    fun halvingAmplitudeLowersLoudnessBySixDecibels() {
        val louder = sineLoudness(amplitude = 0.1)
        val quieter = sineLoudness(amplitude = 0.05)

        assertNotNull(louder)
        assertNotNull(quieter)
        assertEquals(6.0206, louder!! - quieter!!, 0.15)
    }

    private fun sineLoudness(amplitude: Double): Double? {
        val meter = R128Meter(SAMPLE_RATE, 2)
        repeat(SAMPLE_RATE * 4) { frame ->
            val sample =
                amplitude * sin(2.0 * PI * 1_000.0 * frame.toDouble() / SAMPLE_RATE)
            meter.addFrame(doubleArrayOf(sample, sample))
        }
        return meter.result()?.integratedLufs
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
