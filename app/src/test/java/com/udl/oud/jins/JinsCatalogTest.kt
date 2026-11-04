package com.udl.oud.jins

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Spelling
import com.udl.oud.taqsim.JinsGrammars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JinsCatalogTest {
    private fun names(steps: List<Int>) = steps.map { Spelling.of(it).label(NoteLabels.Solfege) }

    @Test fun everyJinsHasAHostAGrammarAndPlaces() {
        assertEquals(10, JinsCatalog.ajnas.size)
        JinsCatalog.ajnas.forEach { j ->
            assertEquals(j, JinsCatalog.host(j).lower)
            JinsGrammars.of(j)
            assertTrue(j.name, JinsCatalog.appearances(j).isNotEmpty())
            assertTrue(j.name, JinsCatalog.roots(j).all { it in 92 until 116 })
        }
    }

    @Test fun bayatiOnDIsSpelledWithItsHalfFlat() {
        val s = JinsCatalog.station(Ajnas.Bayati, 100) // D3
        assertEquals("Ре Ми½♭ Фа Соль", (0..3).joinToString(" ") { s.spelling(it).label(NoteLabels.Solfege) })
        // The note under it, from Bayati's own scale.
        assertEquals("До", s.spelling(-1).label(NoteLabels.Solfege))
    }

    @Test fun bayatiIsFoundLowAndHighAndAsAModulation() {
        val a = JinsCatalog.appearances(Ajnas.Bayati)
        assertTrue(a.any { it.maqam.name == "Баяти" && it.where == Appearance.Where.Lower })
        assertTrue(a.any { it.maqam.name == "Хусейни" && it.where == Appearance.Where.Upper }) // on A
        assertTrue(a.any { it.maqam.name == "Найруз" && it.where == Appearance.Where.Upper }) // on G
        assertTrue(a.any { it.maqam.name == "Раст" && it.where == Appearance.Where.Move }) // Rast → Bayati on D
        assertTrue(names(JinsCatalog.roots(Ajnas.Bayati)).containsAll(listOf("Ре", "Соль", "Ля")))
    }

    @Test fun nahawandIsRastsWayDown() {
        val a = JinsCatalog.appearances(Ajnas.Nahawand)
        assertTrue(a.any { it.maqam.name == "Раст" && it.where == Appearance.Where.Descent })
    }

    @Test fun hijazOnGIsTheTopOfSuznak() {
        val a = JinsCatalog.appearances(Ajnas.Hijaz).first { it.maqam.name == "Сузнак" }
        assertEquals(Appearance.Where.Upper, a.where)
        assertEquals("Соль", Spelling.of(a.root).label(NoteLabels.Solfege))
    }
}
