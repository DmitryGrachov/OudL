package com.udl.oud.fretboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SayrTest {
    private fun maqam(name: String) = Maqamat.all.first { it.name == name }

    @Test fun coreMaqamatHaveASayr() {
        listOf("Раст", "Баяти", "Хиджаз", "Саба", "Нахаванд", "Курд", "Аджам", "Сикях").forEach {
            assertNotNull(it, Sayrs.of(maqam(it)))
        }
    }

    @Test fun everyModulationLeadsToAKnownMaqam() {
        Sayrs.all.forEach { (name, sayr) ->
            sayr.modulations.forEach { m ->
                assertTrue("$name → ${m.maqam}", Maqamat.all.any { it.name == m.maqam })
            }
        }
    }

    /** A modulation pivots on a note the maqam already has: the new tonic is one of its degrees. */
    @Test fun everyModulationStartsOnANoteOfTheMaqam() {
        Sayrs.all.forEach { (name, sayr) ->
            val m = maqam(name)
            val classes = m.notes().map { (m.tonic + it.offset).mod(24) }.toSet()
            sayr.modulations.forEach { mod ->
                assertTrue("$name → ${mod.maqam} on ${m.tonic + mod.offset}", (m.tonic + mod.offset).mod(24) in classes)
            }
        }
    }

    @Test fun startDegreeIsInTheScale() {
        Sayrs.all.forEach { (name, sayr) -> assertTrue(name, sayr.start in maqam(name).notes().indices) }
    }

    @Test fun rastModulatesToBayatiOnD() {
        val rast = maqam("Раст")
        val toBayati = Sayrs.of(rast)!!.modulations.first { it.maqam == "Баяти" }
        assertEquals("Ре", Spelling.of(rast.tonic + toBayati.offset).label(NoteLabels.Solfege))
    }
}
