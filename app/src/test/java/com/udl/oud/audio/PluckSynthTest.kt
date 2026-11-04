package com.udl.oud.audio

import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PluckSynthTest {
    private val rate = 44100
    private val synth = PluckSynth(rate)

    private fun pitchOf(frequency: Double): Double {
        val samples = synth.render(listOf(PluckSynth.Pluck(frequency, 0, rate)), rate)
        val detector = YinPitchDetector(rate, 4096)
        // Past the attack, in the ringing part.
        val est = detector.detect(samples.copyOfRange(8000, 8000 + 4096))
        assertNotNull(est)
        return est!!.frequency.toDouble()
    }

    @Test fun inTuneAcrossTheOudRange() {
        // C2, E½♭3, A3, G4: from the bass course to the top of the neck.
        listOf(65.41, 151.1, 220.0, 392.0).forEach { f ->
            val cents = centsBetween(pitchOf(f), f)
            assertTrue("$f Hz is $cents cents off", abs(cents) < 4)
        }
    }

    @Test fun quarterTonesAreTellableApart() {
        val e = 164.81
        val eHalfFlat = e * Math.pow(2.0, -0.5 / 12)
        assertEquals(-50.0, centsBetween(pitchOf(eHalfFlat), pitchOf(e)), 5.0)
    }

    @Test fun aDampedNoteFallsSilent() {
        val samples = synth.render(listOf(PluckSynth.Pluck(220.0, 0, rate / 4)), rate)
        val tail = samples.copyOfRange(rate / 2, rate).maxOf { abs(it) }
        assertTrue("tail $tail", tail < 0.01f)
        assertTrue(samples.take(rate / 4).maxOf { abs(it) } > 0.5f)
    }
}
