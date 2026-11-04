package com.udl.oud.talwin

import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.taqsim.SectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalwinTest {
    private fun move(from: String, to: String) = Talwin.of(Maqamat.all.first { it.name == from }).first { it.target.name == to }

    @Test fun everyRecordedModulationMakesATalwin() {
        val recorded = Maqamat.all.sumOf { m -> Sayrs.of(m)?.modulations.orEmpty().count { mod -> Maqamat.all.any { it.name == mod.maqam } } }
        val built = Maqamat.all.sumOf { Talwin.of(it).size }
        assertEquals(recorded, built)
        assertTrue(built > 40)
    }

    @Test fun rastToBayatiOnDChangesNothing() {
        val t = move("Раст", "Баяти")
        assertEquals(1, t.pivot)
        assertTrue(t.changes.isEmpty())
        assertTrue(t.explanation[1].startsWith("Ни одна нота не меняется"))
    }

    @Test fun rastToNahawandOnGLowersBHalfFlat() {
        val t = move("Раст", "Нахаванд")
        assertEquals(4, t.pivot) // the ghammaz
        assertEquals(1, t.changes.size)
        assertEquals("Си♭", t.changes.single().to.label(NoteLabels.Solfege))
        assertEquals(-1, t.changes.single().quarterTones)
        assertTrue(t.where, t.where.startsWith("на гаммазе"))
    }

    @Test fun rastToHijazOnGChangesTwoNotes() {
        val t = move("Раст", "Хиджаз")
        assertEquals(setOf(-2, 1), t.changes.map { it.quarterTones }.toSet())
    }

    @Test fun everyTalwinTaqsimGoesThereAndBack() {
        Maqamat.all.flatMap { Talwin.of(it) }.forEach { t ->
            val taqsim = t.generate(3)
            val kinds = taqsim.phrases.map { it.section }
            assertTrue(t.title, SectionKind.Modulation in kinds)
            assertEquals(t.title, SectionKind.Qafla, kinds.last())
            // The move's answer comes to rest on the new jins' root; the taqsim on the maqam's tonic.
            val move = taqsim.phrases.last { it.section == SectionKind.Modulation }
            assertEquals(t.title, 0, move.notes.last().degree)
            assertEquals(t.title, 0, taqsim.phrases.last().notes.last().degree)
        }
    }
}
