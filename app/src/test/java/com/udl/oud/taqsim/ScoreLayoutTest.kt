package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreLayoutTest {
    private val width = 160f // about a tablet's width in half staff spaces

    @Test fun everyNoteIsWrittenOnceInOrderAndFitsItsLine() {
        Maqamat.all.forEach { m ->
            val t = TaqsimGenerator(3).generate(m, TaqsimLength.Long)
            val lines = ScoreLayout.layout(t, Ornamenter.decorate(t, OrnamentLevel.Rich), width)
            val notes = lines.flatMap { it.items }.map { it.note }
            assertEquals(m.name, t.notes.indices.toList(), notes)
            lines.forEach { line ->
                assertTrue(line.items.all { it.x in ScoreLayout.CLEF..width })
                assertTrue(line.items.zipWithNext().all { (a, b) -> b.x > a.x })
                assertTrue(line.low <= 0 && line.high >= 8)
            }
        }
    }

    @Test fun shortPhrasesAreNotSplitAcrossLines() {
        val t = TaqsimGenerator(5).generate(Maqamat.all.first(), TaqsimLength.Medium)
        val lines = ScoreLayout.layout(t, emptyMap(), width)
        t.phrases.indices.forEach { pi ->
            val on = lines.count { l -> l.items.any { it.phrase == pi } }
            assertEquals("phrase $pi", 1, on)
        }
    }

    @Test fun valuesSketchTheRhythm() {
        val t = TaqsimGenerator(5).generate(Maqamat.all.first(), TaqsimLength.Medium)
        val items = ScoreLayout.layout(t, emptyMap(), width).flatMap { it.items }
        assertEquals(NoteValue.Whole, items.last().value)
        assertEquals(1, items.count { it.value == NoteValue.Whole })
        assertTrue(items.any { it.value == NoteValue.Half } && items.any { it.value == NoteValue.Eighth })
        // Every phrase is numbered at its first note, theme notes and breaths carried over.
        assertEquals(t.phrases.size, items.count { it.phraseStart })
        assertEquals(t.phrases.sumOf { it.themeNotes.size }, items.count { it.theme })
        assertEquals(t.notes.count { it.pauseAfter > 0 }, items.count { it.breath })
    }
}
