package com.udl.oud.audio

import com.udl.oud.tuner.centsBetween
import kotlin.math.abs
import kotlin.math.pow

/**
 * Turns raw detector readings into a steady pitch: skips the sharp attack of each pluck,
 * rejects outliers with a short median and smooths what is left.
 */
class PitchTracker(
    private val minFrequency: Double = 55.0,
    private val maxFrequency: Double = 1200.0,
) {
    /**
     * Result of one reading. [frequency] is the smoothed pitch and [steady] the unsmoothed median,
     * both null when there is nothing new to show; [lost] means the sound has been gone long enough
     * to clear the display.
     */
    class Update(
        val pluck: Boolean,
        val frequency: Double? = null,
        val steady: Double? = null,
        val lost: Boolean = false,
    )

    private val recent = ArrayDeque<Double>()
    private var smoothed: Double? = null
    private var previousRms = 0f
    private var onsetAt = 0L
    private var lastHeardAt = 0L
    private var holding = false

    /** [resolve] maps a detector estimate to the frequency to track (e.g. after octave correction). */
    fun update(reading: PitchReading, now: Long, resolve: (PitchEstimate) -> Double): Update {
        val loud = reading.rms >= SILENCE_RMS
        // A sudden jump in loudness is a new pluck. A string rings sharp for its first instants,
        // so readings from the attack are skipped instead of swinging the needle.
        val pluck = loud && reading.rms > previousRms * ONSET_RATIO
        if (pluck) {
            onsetAt = now
            reset()
        }
        previousRms = reading.rms

        val f = reading.pitch?.let(resolve)
        if (!loud || f == null || f < minFrequency || f > maxFrequency || now - onsetAt < ATTACK_SKIP_MS) {
            val lost = holding && now - lastHeardAt > HOLD_MS
            if (lost) {
                reset()
                holding = false
            }
            return Update(pluck, lost = lost)
        }
        lastHeardAt = now
        holding = true

        // A jump of more than ~a semitone is a new note: drop the old history.
        recent.lastOrNull()?.let { if (abs(centsBetween(f, it)) > 80) reset() }
        recent.addLast(f)
        if (recent.size > MEDIAN_SIZE) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]

        // Exponential smoothing in cents, so the needle settles instead of jittering.
        val next = smoothed?.let { it * 2.0.pow(SMOOTHING * centsBetween(median, it) / 1200) } ?: median
        smoothed = next
        return Update(pluck, frequency = next, steady = median)
    }

    fun reset() {
        recent.clear()
        smoothed = null
    }

    private companion object {
        const val SILENCE_RMS = 0.008f
        const val HOLD_MS = 1500L
        const val MEDIAN_SIZE = 5
        const val ONSET_RATIO = 2f
        const val ATTACK_SKIP_MS = 150L
        const val SMOOTHING = 0.35
    }
}
