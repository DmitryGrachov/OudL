package com.udl.oud.fretboard

import com.udl.oud.tuner.Note
import com.udl.oud.tuner.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class FretboardTest {
    private val tuning = Tunings.all.first() // C2 F2 A2 D3 G3 C4
    private val a4 = 440.0

    @Test fun namesSemitonesAndQuarterTones() {
        val e = Pitch.of(Note(52)) // E3
        assertEquals("Ми", e.label(NoteLabels.Solfege))
        assertEquals("E", e.label(NoteLabels.Latin))
        val sikah = Pitch(103) // E½♭3
        assertEquals("Ми½♭", sikah.label(NoteLabels.Solfege))
        assertEquals("E½♭", sikah.label(NoteLabels.Latin))
        assertEquals("Сикях", sikah.label(NoteLabels.Arabic))
        assertEquals("До½♯", Pitch(97).label(NoteLabels.Solfege)) // between C3 and C♯3
    }

    @Test fun arabicNamesFallBackToSolfege() {
        assertEquals("Раст", Pitch.of(Note(48)).label(NoteLabels.Arabic))
        assertEquals("Си", Pitch.of(Note(59)).label(NoteLabels.Arabic)) // B3 has no name in the table
    }

    @Test fun eachCourseHasQuarterTonePositionsUpToTheFifth() {
        val positions = Fretboard.positions(tuning)
        assertEquals(6 * 15, positions.size)
        val onD = positions.filter { it.course == 3 }
        assertEquals(Pitch(100), onD.first().pitch) // open D3
        assertEquals(Pitch(103), onD.first { it.cents == 150 }.pitch) // Sikah, 1½ tones above D
        assertEquals(Pitch(110), onD.first { it.cents == 500 }.pitch) // G3, a fourth above
        assertEquals(Pitch(114), onD.last().pitch) // A3, a fifth above
    }

    @Test fun extendedRangeReachesTheOctave() {
        val positions = Fretboard.positions(tuning, Fretboard.EXTENDED_CENTS)
        assertEquals(6 * 25, positions.size)
        assertEquals(Pitch(96), positions.filter { it.course == 0 }.last().pitch) // bass C2 course up to C3
    }

    @Test fun extendedRangeChangesOctaveCorrectionOnTheBassCourse() {
        val c3 = Pitch(96).frequency(a4)
        // Over the body, C3 is playable on the bass C course itself, so the reading is kept.
        assertEquals(c3, Fretboard.resolveOctave(tuning, c3, true, a4, 0, Fretboard.EXTENDED_CENTS), 1e-9)
    }

    @Test fun positionsGetCloserTowardsTheBody() {
        val first = Fretboard.distanceFraction(100.0)
        val sixth = Fretboard.distanceFraction(700.0) - Fretboard.distanceFraction(600.0)
        assertTrue(sixth < first)
        assertEquals(0.5, Fretboard.distanceFraction(1200.0), 1e-9) // octave at half the string
    }

    @Test fun heardNoteLightsEveryPlaceItCanBePlayed() {
        val aSharp2 = Pitch(92).frequency(a4) // A♯2: F2 + 5 semitones, A2 + 1 semitone
        val (positions, cents) = Fretboard.matches(tuning, aSharp2, a4, quarterTones = false)
        assertEquals(setOf(1 to 500, 2 to 100), positions.map { it.course to it.cents }.toSet())
        assertTrue(abs(cents) < 0.01)

        val (onlyA, _) = Fretboard.matches(tuning, aSharp2, a4, quarterTones = false, onlyCourse = 2)
        assertEquals(listOf(2 to 100), onlyA.map { it.course to it.cents })
    }

    @Test fun offsetIsMeasuredFromTheNearestQuarterTone() {
        val sikahSharp = Pitch(103).frequency(a4) * 2.0.pow(12.0 / 1200) // Sikah + 12 cents
        val (positions, cents) = Fretboard.matches(tuning, sikahSharp, a4, quarterTones = true)
        assertTrue(positions.all { it.pitch == Pitch(103) })
        assertEquals(12.0, cents, 0.01)
    }

    @Test fun octaveCorrectionOnlyWhenTheHigherReadingIsOffTheNeck() {
        val c3 = Pitch(96).frequency(a4)
        // C3 is on the F2 course (+700) and the A2 course (+300): keep it.
        assertEquals(c3, Fretboard.resolveOctave(tuning, c3, true, a4, onlyCourse = null), 1e-9)
        // Locked to the bass C course, C3 is off its neck, so the reading must be C2's 2nd harmonic.
        assertEquals(c3 / 2, Fretboard.resolveOctave(tuning, c3, true, a4, onlyCourse = 0), 1e-9)
    }

    @Test fun fingersFollowFirstPosition() {
        val onD = Fretboard.positions(tuning).filter { it.course == 3 }.associateBy { it.cents }
        assertEquals(0, onD.getValue(0).finger)
        assertEquals(1, onD.getValue(200).finger)
        assertEquals(2, onD.getValue(300).finger)
        assertEquals(3, onD.getValue(400).finger)
        assertEquals(4, onD.getValue(500).finger)
        assertEquals(null, onD.getValue(700).finger)
    }
}
