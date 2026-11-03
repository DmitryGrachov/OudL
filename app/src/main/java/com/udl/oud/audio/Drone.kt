package com.udl.oud.audio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A sustained drone on one note, optionally with its fifth, rendered as a loop that joins
 * seamlessly: the loop holds a whole, even number of periods, so the fifth (1.5 × frequency)
 * also completes whole periods and nothing clicks at the seam.
 */
object Drone {
    /** About this long; the exact length is set by the whole number of periods. */
    private const val LOOP_SECONDS = 2.0

    // Harmonic weights of the tonic, a soft reedy timbre that sits under the oud.
    private val HARMONICS = floatArrayOf(1f, 0.55f, 0.35f, 0.22f, 0.12f, 0.07f)
    private const val FIFTH_LEVEL = 0.45f

    /** Whole periods of [frequency] in the loop (even) and the loop length in samples. */
    fun loopSize(frequency: Double, sampleRate: Int): Pair<Int, Int> {
        val periods = (frequency * LOOP_SECONDS / 2).roundToInt().coerceAtLeast(1) * 2
        val samples = (periods * sampleRate / frequency).roundToInt()
        return periods to samples
    }

    fun render(frequency: Double, withFifth: Boolean, sampleRate: Int = LoopTrack.SAMPLE_RATE): ShortArray {
        val (periods, samples) = loopSize(frequency, sampleRate)
        // Use the frequency the loop actually fits; it is off by far less than a cent.
        val exact = periods.toDouble() * sampleRate / samples
        val mix = FloatArray(samples)
        val norm = HARMONICS.sum() * (if (withFifth) 1 + FIFTH_LEVEL else 1f)
        for (i in 0 until samples) {
            val phase = 2 * PI * exact * i / sampleRate
            var s = 0.0
            for (h in HARMONICS.indices) s += HARMONICS[h] * sin(phase * (h + 1))
            if (withFifth) for (h in HARMONICS.indices) s += FIFTH_LEVEL * HARMONICS[h] * sin(phase * 1.5 * (h + 1))
            // One slow swell per loop, so the drone breathes instead of buzzing flat.
            val swell = 0.9 + 0.1 * sin(2 * PI * i / samples)
            mix[i] = (0.8 * s * swell / norm).toFloat()
        }
        return mix.toPcm()
    }

    /** Frequency the rendered loop really plays for [frequency]. */
    fun playedFrequency(frequency: Double, sampleRate: Int = LoopTrack.SAMPLE_RATE): Double {
        val (periods, samples) = loopSize(frequency, sampleRate)
        return periods.toDouble() * sampleRate / samples
    }
}
