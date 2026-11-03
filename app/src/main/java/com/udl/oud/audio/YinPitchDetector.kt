package com.udl.oud.audio

/**
 * A detected pitch. [canBeOctaveLower] is set when the signal is just as periodic at twice
 * the detected period: then the true note may be an octave lower and the detector caught its
 * 2nd harmonic, which happens with bass strings on small microphones.
 */
data class PitchEstimate(val frequency: Float, val canBeOctaveLower: Boolean)

/**
 * YIN fundamental-frequency estimator (de Cheveigné & Kawahara, 2002).
 *
 * The buffer passed to [detect] must hold [windowSize] samples. The first half
 * is the integration window; lags are searched up to the lag of [minFrequency].
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val windowSize: Int,
    minFrequency: Float = 55f,
    maxFrequency: Float = 1200f,
    private val threshold: Float = 0.15f,
) {
    private val integration = windowSize / 2
    private val minTau = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
    private val maxTau = (sampleRate / minFrequency).toInt()
    private val yin = FloatArray(maxTau + 2)

    init {
        require(maxTau + 1 < windowSize - integration) { "Window too short for minFrequency" }
    }

    /** Returns the fundamental frequency, or null when there is no clear pitch. */
    fun detect(buffer: FloatArray): PitchEstimate? {
        // Difference function.
        for (tau in 1..maxTau + 1) {
            var sum = 0f
            for (i in 0 until integration) {
                val d = buffer[i] - buffer[i + tau]
                sum += d * d
            }
            yin[tau] = sum
        }

        // Cumulative mean normalized difference.
        yin[0] = 1f
        var running = 0f
        for (tau in 1..maxTau + 1) {
            running += yin[tau]
            yin[tau] = if (running == 0f) 1f else yin[tau] * tau / running
        }

        // First dip under the threshold, followed down to its local minimum.
        var tauEstimate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (yin[tau] < threshold) {
                while (tau + 1 <= maxTau && yin[tau + 1] < yin[tau]) tau++
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate == -1) return null

        // Parabolic interpolation around the minimum for sub-sample accuracy.
        val s0 = yin[tauEstimate - 1]
        val s1 = yin[tauEstimate]
        val s2 = yin[tauEstimate + 1]
        val denominator = 2f * (2f * s1 - s2 - s0)
        val refined = if (denominator != 0f) tauEstimate + (s2 - s0) / denominator else tauEstimate.toFloat()

        val doubleTau = tauEstimate * 2
        val canBeOctaveLower = doubleTau + 1 <= maxTau + 1 &&
            minOf(yin[doubleTau - 1], yin[doubleTau], yin[doubleTau + 1]) < SUB_OCTAVE_THRESHOLD
        return PitchEstimate(sampleRate / refined, canBeOctaveLower)
    }

    private companion object {
        const val SUB_OCTAVE_THRESHOLD = 0.3f
    }
}
