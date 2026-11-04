package com.udl.oud.turkish

import com.udl.oud.jins.JinsEtude
import com.udl.oud.taqsim.PhraseRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishJinsTest {
    @Test
    fun everyJinsIsFoundInAMakam() {
        TurkishJinsCatalog.ajnas.forEach { j ->
            assertTrue("${j.name} has no makam", TurkishJinsCatalog.appearances(j).isNotEmpty())
        }
    }

    @Test
    fun stationStepsAreTheJinsKomas() {
        TurkishJinsCatalog.ajnas.forEach { j ->
            TurkishJinsCatalog.roots(j).forEach { r ->
                val s = TurkishJinsCatalog.station(j, r)
                assertEquals(r, s.step(0))
                val steps = (1..j.intervals.size).map { s.step(it) - s.step(it - 1) }
                assertEquals("${j.name} on $r", j.intervals, steps)
            }
        }
    }

    @Test
    fun rastUpperIsOnNeva() {
        val roots = TurkishJinsCatalog.roots(TurkishAjnas.RastDortlu).map { Perdes.of(it) }
        assertTrue(roots.toString(), "Nevâ" in roots)
        // Below Rast beşli on Rast lies Irak, the makam's own seventh.
        val s = TurkishJinsCatalog.station(TurkishAjnas.RastBesli, TurkishJinsCatalog.defaultRoot(TurkishAjnas.RastBesli))
        assertEquals("Rast", s.label(0))
        assertEquals("Irak", s.label(-1))
    }

    @Test
    fun etudeAsksAndComesHome() {
        TurkishJinsCatalog.ajnas.forEach { j ->
            val e = JinsEtude.generateTurkish(j, TurkishJinsCatalog.defaultRoot(j), 7)
            assertTrue(j.name, e.phrases.any { it.role == PhraseRole.Question })
            assertEquals(j.name, 0, e.phrases.last().notes.last().degree)
            assertEquals(j.name, e.title)
        }
    }

    @Test
    fun heardNoteKeepsItsOctave() {
        val s = TurkishJinsCatalog.station(TurkishAjnas.RastBesli, TurkishJinsCatalog.defaultRoot(TurkishAjnas.RastBesli))
        assertEquals(7 to 0.0, s.degreeOfHz(s.hz(7)).let { it.first to Math.round(it.second).toDouble() })
        assertEquals(-1, s.degreeOfHz(s.hz(-1)).first)
    }
}
