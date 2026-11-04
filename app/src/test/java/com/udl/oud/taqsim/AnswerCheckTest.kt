package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AnswerCheckTest {
    private val a4 = 440.0
    private val bayati = Maqamat.all.first { it.name == "Баяти" }
    private val lower = TaqsimSayrs.Stations(bayati, bayati.tonic).lower // Bayati on D: D E½♭ F G

    private fun heard(vararg steps: Int) = steps.map { HeardNote(it, 0.0) }

    // Grid steps around D3 = 100: C 96, D 100, E½♭ 103, E♭ 102, F 106, G 110, A 114.
    @Test fun aGoodAnswerPassesAllFour() {
        val v = AnswerCheck.evaluate(heard(106, 103, 100, 96, 100), lower, 0)
        assertEquals(4, v.passed)
        assertTrue(v.outside.isEmpty())
    }

    @Test fun eFlatInsteadOfEHalfFlatGetsAHint() {
        val v = AnswerCheck.evaluate(heard(106, 102, 100), lower, 0)
        assertEquals(setOf(1), v.outside) // the E♭
        val inMaqam = v.points.first()
        assertFalse(inMaqam.passed)
        assertTrue(inMaqam.text, inMaqam.text.contains("чуть выше"))
        // And without E½♭ the colour of Bayati is missing.
        assertFalse(v.points[1].passed)
    }

    @Test fun endingOnAnotherPillarIsNamed() {
        val v = AnswerCheck.evaluate(heard(100, 103, 106, 110), lower, 0)
        val end = v.points.last()
        assertFalse(end.passed)
        assertTrue(end.text, end.text.contains("опоре"))
    }

    @Test fun anyOctaveOfTheTargetCounts() {
        val v = AnswerCheck.evaluate(heard(110, 106, 103, 124), lower, 0) // ends on D4
        assertTrue(v.points.last().passed)
    }

    @Test fun tooShortIsSaid() {
        val v = AnswerCheck.evaluate(heard(100, 103), lower, 0)
        assertEquals(1, v.points.size)
        assertFalse(v.points.single().passed)
    }

    @Test fun jumpyAnswerIsFlagged() {
        val v = AnswerCheck.evaluate(heard(100, 110, 96, 114, 100), lower, 0)
        assertFalse(v.points[2].passed)
    }

    private fun f(step: Int, cents: Double = 0.0) = Pitch(step).frequency(a4) * 2.0.pow(cents / 1200)

    @Test fun collectorSplitsByPluckSlideAndSilence() {
        val c = NoteCollector(a4)
        var t = 0L
        fun ring(step: Int, readings: Int = 5, cents: Double = 0.0) = repeat(readings) { c.steady(f(step, cents), t); t += 23 }
        ring(100)
        c.pluck()
        ring(103, cents = 8.0)
        ring(106) // a slide up without a new pluck
        c.silence()
        ring(110, readings = 2) // too short to count
        val notes = c.finish()
        assertEquals(listOf(100, 103, 106), notes.map { it.step })
        assertEquals(8.0, notes[1].cents, 0.5)
    }
}
