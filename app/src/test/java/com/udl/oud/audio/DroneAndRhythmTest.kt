package com.udl.oud.audio

import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DroneAndRhythmTest {
    private val sampleRate = 44100

    @Test fun droneLoopHoldsAnEvenNumberOfPeriodsSoTheFifthJoinsToo() {
        listOf(65.41, 130.81, 164.81 * 1.0293, 261.63).forEach { f ->
            val (periods, samples) = Drone.loopSize(f, sampleRate)
            assertEquals("periods for $f", 0, periods % 2)
            assertTrue("about two seconds for $f", samples in (1.5 * sampleRate).toInt()..(2.5 * sampleRate).toInt())
        }
    }

    @Test fun dronePlaysWithinACentOfTheAskedPitch() {
        listOf(65.41, 98.0, 151.0, 261.63).forEach { f ->
            assertTrue(abs(centsBetween(Drone.playedFrequency(f, sampleRate), f)) < 0.1)
        }
    }

    @Test fun droneLoopIsSeamless() {
        val pcm = Drone.render(130.81, withFifth = true, sampleRate = sampleRate)
        // The step across the seam is no bigger than steps inside the loop.
        val seam = abs(pcm.first() - pcm.last())
        val largest = (1 until pcm.size).maxOf { abs(pcm[it] - pcm[it - 1]) }
        assertTrue("seam $seam vs largest $largest", seam <= largest)
    }

    @Test fun iqaatParse() {
        val maqsum = Iqaat.all.first { it.name == "Максум" }
        assertEquals(
            listOf(Stroke.Dum, Stroke.Tak, Stroke.Rest, Stroke.Tak, Stroke.Dum, Stroke.Rest, Stroke.Tak, Stroke.Rest),
            maqsum.strokes,
        )
        assertEquals(10, Iqaat.all.first { it.meter == "10/8" }.strokes.size)
    }

    @Test fun rhythmLoopIsOneCycleAtTheTempo() {
        val maqsum = Iqaat.all.first { it.name == "Максум" }
        // 4 beats at 120 bpm is 2 seconds.
        assertEquals(2 * sampleRate, Drum.render(maqsum, 120, sampleRate).size)
        assertEquals(sampleRate / 4, maqsum.stepSamples(120, sampleRate))
    }

    @Test fun strokesLandOnTheirSteps() {
        val metronome = Iqaat.all.first { it.name == "Метроном" }
        val pcm = Drum.render(metronome, 120, sampleRate)
        val step = metronome.stepSamples(120, sampleRate)
        fun loudness(from: Int) = (from until from + step / 4).maxOf { abs(pcm[it].toInt()) }
        // "D . T . T . T .": sound right after steps 0, 2, 4, 6; the rests (1, 3, …) start quieter.
        assertTrue(loudness(0) > 3 * loudness(step * 3 + step / 2))
        assertTrue(loudness(2 * step) > loudness(step * 3 + step / 2))
    }
}
