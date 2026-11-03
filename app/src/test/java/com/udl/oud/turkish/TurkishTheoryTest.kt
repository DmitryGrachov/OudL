package com.udl.oud.turkish

import com.udl.oud.tuner.Note
import com.udl.oud.tuner.Tunings
import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TurkishTheoryTest {
    private val a4 = 440.0
    private fun makam(name: String) = TurkishMakams.all.first { it.turkishName == name }
    private fun written(name: String) = makam(name).degrees().joinToString(" ") { it.spelling.label() }
    private fun perdes(name: String) = makam(name).degrees().joinToString(" ") { Perdes.of(it.written) ?: "?" }

    @Test fun everyMakamIsAnOctaveOf53Komas() {
        // The constructor checks the octave and both ajnas; building the list must not throw.
        assertEquals(12, TurkishMakams.all.size)
        TurkishMakams.all.forEach { assertEquals(it.name, 53, it.intervals.sum()) }
    }

    @Test fun rastPerdes() = assertEquals("Rast Dügâh Segâh Çârgâh Nevâ Hüseynî Eviç", perdes("Rast"))

    @Test fun rastWrittenWithKomaAccidentals() = assertEquals("Соль Ля Си♭¹ До Ре Ми Фа♯⁴", written("Rast"))

    @Test fun hicazHasDikKurdiAndNimHicaz() {
        assertEquals("Dügâh Dik kürdî Nim hicaz Nevâ Hüseynî Eviç Gerdâniye", perdes("Hicaz"))
        assertEquals("Ля Си♭⁴ До♯⁴ Ре Ми Фа♯⁴ Соль", written("Hicaz"))
    }

    @Test fun kurdiTakesTheBakiyeFlat() = assertEquals("Ля Си♭⁵ До Ре Ми Фа Соль", written("Kürdî"))

    @Test fun ussakAndHuseyniDifferAtTheSixth() {
        assertEquals("Фа", makam("Uşşak").degrees()[5].spelling.label())
        assertEquals("Фа♯⁴", makam("Hüseynî").degrees()[5].spelling.label()) // Eviç
    }

    @Test fun segahSitsOneKomaBelowB() {
        val segah = makam("Segâh").degrees().first()
        assertEquals("Segâh", Perdes.of(segah.written))
        assertEquals(-1, segah.spelling.alter)
    }

    @Test fun bolahenkSoundsAnOctaveAndAFourthLower() {
        val rast = makam("Rast").degrees().first()
        assertEquals(Note(50).frequency(a4), Koma.frequency(rast.written, rast.spelling, a4), 1e-6) // D3
        assertEquals("Ре", rast.spelling.sounding(rast.written).label())
        val dugah = makam("Uşşak").degrees().first()
        assertEquals(Note(52).frequency(a4), Koma.frequency(dugah.written, dugah.spelling, a4), 1e-6) // E3
    }

    @Test fun segahSoundsAKomaUnderFSharp() {
        val segah = makam("Segâh").degrees().first()
        val f = Koma.frequency(segah.written, segah.spelling, a4)
        assertEquals(-Koma.CENTS, centsBetween(f, Note(54).frequency(a4)), 1e-6) // F♯3 minus one koma
        assertEquals("Фа♯⁴", segah.spelling.sounding(segah.written).label())
    }

    @Test fun ussakDurakIsTheOpenEStringOfTheTurkishOud() {
        val tuning = Tunings.turkish.first() // C♯2 F♯2 B2 E3 A3 D4
        val marks = TurkishBoard.marks(makam("Uşşak"), tuning, a4, 700)
        assertTrue(marks.any { it.course == 3 && it.cents == 0.0 && it.degree == 0 && it.octave == 0 })
    }

    @Test fun segahMarkIsAKomaBelowTheSemitoneGrid() {
        val tuning = Tunings.turkish.first()
        // Segâh (written B♭¹4) sounds F♯3 − 1 koma: on the E3 course that is 200 − 22.6 cents up.
        val mark = TurkishBoard.marks(makam("Rast"), tuning, a4, 700)
            .first { it.course == 3 && it.degree == 2 && it.octave == 0 }
        assertEquals(200 - Koma.CENTS, mark.cents, 0.01)
    }

    @Test fun heardPitchFindsItsDegree() {
        val rast = makam("Rast")
        val segah = TurkishBoard.degreeFrequencies(rast, a4)[2]
        val heard = TurkishBoard.nearest(rast, segah * 1.004, a4) // ~7 cents sharp
        assertEquals(2, heard.degree)
        assertEquals(0, heard.octave)
        assertTrue(abs(heard.cents - 6.9) < 0.2)
        assertEquals(1, TurkishBoard.nearest(rast, segah * 2, a4).octave)
    }

    @Test fun komaStepsHaveTheirTurkishNames() =
        assertEquals("T K S T T K S", makam("Rast").intervals.joinToString(" ") { komaStepLabel(it) })
}
