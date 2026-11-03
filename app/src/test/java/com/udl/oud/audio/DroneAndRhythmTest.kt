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
        val maqsum = Iqaat.all.first { it.name == "Максум" }
        val pcm = Drum.render(maqsum, 120, sampleRate)
        val step = maqsum.stepSamples(120, sampleRate)
        fun loudness(from: Int) = (from until from + step / 4).maxOf { abs(pcm[it].toInt()) }
        // "D T . T D . T .": strokes start loud on their steps; a rest only carries a fading tail.
        assertTrue(loudness(step) > 2 * loudness(2 * step + step / 2))
        assertTrue(loudness(3 * step) > 2 * loudness(2 * step + step / 2))
    }

    @Test fun metronomeBarIsBeatsTimesSubdivisionClicks() {
        // 3 beats of triplets at 60 bpm: three seconds, a click every third of a second.
        assertEquals(3 * sampleRate, Metronome.render(60, 3, 3, accent = true, sampleRate = sampleRate).size)
        assertEquals(sampleRate / 3, Metronome.clickSamples(60, 3, sampleRate))
    }

    @Test fun metronomeAccentIsLouderThanOtherBeats() {
        val pcm = Metronome.render(120, 4, 1, accent = true, sampleRate = sampleRate)
        val beat = Metronome.clickSamples(120, 1, sampleRate)
        fun peak(from: Int) = (from until from + beat / 4).maxOf { abs(pcm[it].toInt()) }
        assertTrue(peak(0) > peak(beat))
        val flat = Metronome.render(120, 4, 1, accent = false, sampleRate = sampleRate)
        assertEquals((0 until beat / 4).maxOf { abs(flat[it].toInt()) }, peak(beat))
    }
}
