package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RhythmAndMotifTest {
    private val rast = Maqamat.all.first { it.name == "Раст" }
    private val station = TaqsimSayrs.Stations(rast, rast.tonic).lower
    private val line = listOf(0, 1, 2, 3, 2, 1, 2, 1, 0) // a phrase of nine notes

    private fun beats(figure: RhythmFigure, role: PhraseRole = PhraseRole.Develop) =
        Timing.beats(line, role, SectionKind.Upper, station, Random(3), figure = figure)

    @Test fun everyJinsHasARichVocabulary() {
        JinsGrammars.all.forEach { g ->
            assertTrue("${g.jins.name}: ${g.motifs.size}", g.motifs.size >= 8)
            MotifUse.entries.forEach { use -> assertTrue("${g.jins.name} $use", g.motifsFor(use).size >= 5) }
        }
    }

    @Test fun dottedSwingsLongShort() {
        val b = beats(RhythmFigure.Dotted).dropLast(1)
        b.zipWithNext().forEachIndexed { k, (x, y) -> if (k % 2 == 0) assertTrue("$b", x > y * 1.8) }
    }

    @Test fun gallopIsALongAndTwoShort() {
        val b = beats(RhythmFigure.Gallop).dropLast(1)
        assertTrue("$b", b[0] > b[1] * 1.8 && b[0] > b[2] * 1.8 && b[3] > b[4] * 1.8)
    }

    @Test fun runGathersSpeed() {
        val b = beats(RhythmFigure.Run).dropLast(1)
        assertTrue("$b", b.first() > b.last() * 1.6)
    }

    @Test fun hesitantHoldsTheNoteBeforeTheRest() {
        val b = beats(RhythmFigure.Hesitant)
        assertTrue("$b", b[b.lastIndex - 1] >= 2.0 && b[b.lastIndex - 1] > b[b.lastIndex - 2] * 1.6)
    }

    @Test fun lingeringHoldsTheColourNote() {
        val b = beats(RhythmFigure.Lingering)
        val colour = line.indices.filter { line[it] == 2 && it != line.lastIndex } // E½♭ in Rast
        colour.forEach { assertTrue("$b", b[it] > 1.6) }
    }

    @Test fun nothingIsTooQuickToPlay() {
        Maqamat.all.forEach { m ->
            (0L until 30L).forEach { s ->
                val t = TaqsimGenerator(s).generate(m, TaqsimLength.Medium)
                assertTrue(t.notes.all { it.beats >= Timing.SHORTEST * 0.9 })
            }
        }
    }

    @Test fun aTaqsimMixesFigures() {
        val figures = (0L until 20L).map { s ->
            TaqsimGenerator(s).generate(rast, TaqsimLength.Medium).phrases.map { it.figure }.toSet().size
        }
        assertTrue("$figures", figures.average() >= 3.5)
        // And the figure is named in the phrase's description.
        val p = TaqsimGenerator(1).generate(rast, TaqsimLength.Medium).phrases.first { it.figure != RhythmFigure.Flowing }
        assertTrue(p.how, p.how.contains(p.figure.title))
    }

    @Test fun climbsTendToRunAndCadencesToHesitate() {
        val r = Random(9)
        val climbs = (0 until 300).map { Timing.figureFor(PhraseRole.Climb, r) }
        val cadences = (0 until 300).map { Timing.figureFor(PhraseRole.Cadence, r) }
        assertTrue(climbs.count { it == RhythmFigure.Run } > climbs.size / 3)
        assertTrue(cadences.count { it == RhythmFigure.Hesitant } > cadences.size / 3)
        assertEquals(0, cadences.count { it == RhythmFigure.Run })
    }
}
