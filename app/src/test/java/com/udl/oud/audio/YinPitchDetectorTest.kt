package com.udl.oud.audio

import com.udl.oud.tuner.Tunings
import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class YinPitchDetectorTest {
    private val sampleRate = 44100
    private val windowSize = 4096
    private val detector = YinPitchDetector(sampleRate, windowSize)
    private val tuning = Tunings.all.first()

    private fun signal(frequency: Double, vararg amplitudes: Double): FloatArray = FloatArray(windowSize) { i ->
        val t = i.toDouble() / sampleRate
        amplitudes.indices.sumOf { h -> amplitudes[h] * sin(2 * PI * (h + 1) * frequency * t) }.toFloat()
    }

    /** Harmonic-rich signal, roughly what a plucked course looks like. */
    private fun pluck(frequency: Double) = signal(frequency, 0.6, 0.5, 0.3, 0.2)

    /** Runs the detector and the tuner's octave correction, as the app does. */
    private fun resolved(buffer: FloatArray, lockedCourse: Int? = null): Double {
        val estimate = detector.detect(buffer)
        assertNotNull("no pitch detected", estimate)
        return tuning.resolveOctave(estimate!!.frequency.toDouble(), estimate.canBeOctaveLower, lockedCourse, 440.0)
    }

    private fun assertDetects(frequency: Double) {
        val error = centsBetween(resolved(pluck(frequency)), frequency)
        assertTrue("$frequency Hz off by $error cents", abs(error) < 1.0)
    }

    @Test fun detectsBassC2() = assertDetects(65.41)
    @Test fun detectsF2() = assertDetects(87.31)
    @Test fun detectsA2() = assertDetects(110.0)
    @Test fun detectsD3() = assertDetects(146.83)
    @Test fun detectsG3() = assertDetects(196.0)
    @Test fun detectsTrebleC4() = assertDetects(261.63)
    /** Fretted notes are outside the tuner's open-string octave correction: check the raw detector. */
    @Test fun detectsQuarterToneAboveE3() {
        val frequency = 164.81 * 1.0293
        val detected = detector.detect(pluck(frequency))!!.frequency.toDouble()
        val error = centsBetween(detected, frequency)
        assertTrue("$frequency Hz off by $error cents", abs(error) < 1.0)
    }

    /** What a tablet mic hears from the bass C: the 65 Hz fundamental is almost gone. */
    private val weakBassC2 = signal(65.41, 0.02, 1.0, 0.15, 0.5)

    @Test fun bassC2WithWeakFundamentalIsNotOctaveUp() {
        val error = centsBetween(resolved(weakBassC2), 65.41)
        assertTrue("weak C2 off by $error cents", abs(error) < 2.0)
    }

    @Test fun bassC2WithWeakFundamentalWhenLocked() {
        val error = centsBetween(resolved(weakBassC2, lockedCourse = 0), 65.41)
        assertTrue("weak C2 off by $error cents", abs(error) < 2.0)
    }

    @Test fun slightlyFlatBassC2StaysC2() {
        val flat = 65.41 * 0.985 // about −26 cents
        val error = centsBetween(resolved(signal(flat, 0.02, 1.0, 0.15, 0.5)), flat)
        assertTrue("flat C2 off by $error cents", abs(error) < 2.0)
    }

    @Test fun noiseHasNoPitch() {
        val random = Random(42)
        val noise = FloatArray(windowSize) { random.nextFloat() * 2 - 1 }
        assertNull(detector.detect(noise))
    }
}
