package com.udl.oud.rhythm

import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.Stroke
import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IqaEtudeTest {
    @Test fun phrasesSpanAboutTwoBars() {
        assertEquals(2, IqaEtudes.cyclesPerPhrase(Iqaat.all.first { it.latin == "Maqsum" }))
        assertEquals(4, IqaEtudes.cyclesPerPhrase(Iqaat.all.first { it.latin == "Malfuf" }))
        assertEquals(1, IqaEtudes.cyclesPerPhrase(Iqaat.all.first { it.latin == "Masmoudi Kabir" }))
        assertEquals(1, IqaEtudes.cyclesPerPhrase(Iqaat.all.first { it.latin == "Muhajjar" }))
    }

    @Test fun everyIqaAndMaqamMakesAWellFormedEtude() {
        val maqamat = Maqamat.families.map { it.scales.first() as com.udl.oud.fretboard.Maqam }
        Iqaat.all.forEach { iqa ->
            maqamat.forEach { m ->
                val e = IqaEtudes.generate(iqa, m, 11)
                val what = "${m.name} on ${iqa.name}"
                val n = iqa.strokes.size
                assertTrue(what, e.phrases.size in 2..IqaEtudes.PHRASES)
                // Notes go forward, never overlap, and stay inside the etude.
                e.notes.zipWithNext().forEach { (a, b) -> assertTrue(what, b.step >= a.step + a.length || b.phrase != a.phrase) }
                assertTrue(what, e.notes.all { it.length > 0 && it.step + it.length <= e.cycles * n })
                // Each phrase comes to rest on a dum (or its last stroke), and the etude on the tonic.
                e.notes.groupBy { it.phrase }.values.forEach { p ->
                    val last = p.last()
                    assertTrue(what, iqa.strokes[last.step % n] != Stroke.Rest)
                }
                assertEquals(what, 0, Math.floorMod(e.notes.last().note.degree, 7))
            }
        }
    }

    @Test fun strongNotesSitOnStrokes() {
        val maqsum = Iqaat.all.first { it.latin == "Maqsum" }
        val e = IqaEtudes.generate(maqsum, Maqamat.all.first(), 5)
        val onStrokes = e.notes.count { maqsum.strokes[it.step % maqsum.strokes.size] != Stroke.Rest }
        // Most notes fall on the dums and teks; rests only take the overflow.
        assertTrue("$onStrokes of ${e.notes.size}", onStrokes * 2 >= e.notes.size)
    }
}
