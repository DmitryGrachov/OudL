package com.udl.oud.fretboard

import com.udl.oud.taqsim.ArabicScale
import com.udl.oud.tuner.centsBetween
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class IntonationTest {
    private val c4 = Pitch(120)
    private val eHalfFlat4 = Pitch(127)
    private val a4 = 440.0

    @After fun reset() {
        Intonation.school = IntonationSchool.Theory
        Intonation.custom = 0
        Intonation.functional = false
    }

    @Test fun theoryIsTheGrid() {
        assertEquals(350.0, centsBetween(eHalfFlat4.frequency(a4), c4.frequency(a4)), 1e-6)
    }

    @Test fun schoolsMoveOnlyTheQuarterTones() {
        Intonation.school = IntonationSchool.Egypt
        assertEquals(342.0, centsBetween(eHalfFlat4.frequency(a4), c4.frequency(a4)), 1e-6)
        assertEquals(440.0, Pitch(69 * 2).frequency(a4), 1e-9) // A4 stays
        Intonation.school = IntonationSchool.Levant
        assertEquals(356.0, centsBetween(eHalfFlat4.frequency(a4), c4.frequency(a4)), 1e-6)
        Intonation.school = IntonationSchool.Custom
        Intonation.custom = -14
        assertEquals(336.0, centsBetween(eHalfFlat4.frequency(a4), c4.frequency(a4)), 1e-6)
    }

    @Test fun aHeardNeutralNoteIsNamedAfterItsSchool() {
        // 337 cents over C: in theory nearer E♭ (300)? No — nearer E½♭ (350); in a school that
        // puts E½♭ at 342 it is in tune within 5 cents.
        val f = c4.frequency(a4) * Math.pow(2.0, 337 / 1200.0)
        Intonation.school = IntonationSchool.Egypt
        val p = Pitch.nearest(f, a4, quarterTones = true)
        assertEquals(127, p.step)
        assertEquals(-5.0, centsBetween(f, p.frequency(a4)), 1e-6)
        // A semitone note keeps its place.
        assertEquals(124, Pitch.nearest(c4.frequency(a4) * Math.pow(2.0, 203 / 1200.0), a4, quarterTones = true).step)
    }

    @Test fun bayatiSecondIsLowerOnlyWhenFunctional() {
        val bayati = Maqamat.all.first { it.lower == Ajnas.Bayati }
        val scale = ArabicScale(bayati, bayati.tonic, false)
        val rast = Maqamat.all.first { it.name == "Раст" }
        val rastScale = ArabicScale(rast, rast.tonic, false)
        val plain = scale.hz(1)
        Intonation.functional = true
        assertEquals(-10.0, centsBetween(scale.hz(1), plain), 1e-6)
        // Rast's own half-flat third is left as the school puts it.
        assertEquals(0.0, centsBetween(rastScale.hz(2), Pitch(rastScale.step(2)).frequency(440.0)), 1e-6)
    }
}
