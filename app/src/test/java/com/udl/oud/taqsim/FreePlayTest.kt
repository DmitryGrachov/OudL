package com.udl.oud.taqsim

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreePlayTest {
    // Grid steps: C3 96, D 100, E½♭ 103, E♭ 102, F 106, F♯ 108, G 110, A♭ 112, A 114, B½♭ 117, B♭ 116, C4 120.
    /** Plays phrases of [steps], 300 ms a note, 1.5 s between phrases. */
    private fun play(vararg phrases: List<Int>): List<HeardNote> {
        val out = mutableListOf<HeardNote>()
        var t = 0L
        phrases.forEach { p ->
            p.forEach { s ->
                out += HeardNote(s, 0.0, t, 280)
                t += 300
            }
            t += 1500
        }
        return out
    }

    private fun maqam(name: String) = Maqamat.all.first { it.name == name }

    private val rastPhrases = arrayOf(
        listOf(96, 100, 103, 100, 96), // around C
        listOf(96, 100, 103, 106, 110), // up to the ghammaz G
        listOf(110, 114, 117, 120, 117, 114, 110), // Rast on G
        listOf(110, 106, 103, 100, 96), // home
    )

    @Test fun pausesSplitPhrases() {
        val phrases = FreePlay.phrases(play(*rastPhrases))
        assertEquals(4, phrases.size)
        assertEquals(listOf(5, 5, 7, 5), phrases.map { it.notes.size })
    }

    @Test fun eachPhraseFindsItsJins() {
        val phrases = FreePlay.phrases(play(*rastPhrases))
        val first = FreePlay.jinsOf(phrases[0])!!
        assertEquals(Ajnas.Rast, first.jins)
        assertEquals(96 % 24, first.root % 24) // on C
        val bayati = FreePlay.jinsOf(FreePlay.phrases(play(listOf(100, 103, 106, 103, 100, 96, 100))).single())!!
        assertEquals(Ajnas.Bayati, bayati.jins)
        assertEquals(100 % 24, bayati.root % 24)
        val hijaz = FreePlay.jinsOf(FreePlay.phrases(play(listOf(100, 102, 108, 110, 108, 102, 100))).single())!!
        assertEquals(Ajnas.Hijaz, hijaz.jins)
    }

    @Test fun theMaqamIsFoundAndTheSayrFollowed() {
        val review = FreePlay.review(play(*rastPhrases), expected = null)
        assertEquals("Раст", review.maqam?.name)
        assertTrue(review.phrases[0].place.startsWith("нижний джинс Раст"))
        assertTrue(review.phrases[2].place, review.phrases[2].place.startsWith("верхний джинс"))
        assertTrue(review.points.all { it.passed })
    }

    @Test fun aWrongNoteAndAnEndAwayFromHomeAreSaid() {
        // E♭ instead of E½♭, and stopping on G.
        val review = FreePlay.review(play(listOf(96, 100, 102, 100, 96), listOf(96, 100, 102, 106, 110)), expected = maqam("Раст"))
        val (inMaqam, colour, _, end) = review.points
        assertTrue(!inMaqam.passed && inMaqam.text.contains("Ми♭"))
        assertTrue(!colour.passed)
        assertTrue(!end.passed && end.text.contains("Соль"))
    }

    @Test fun aModulationIsRecognised() {
        // Rast, then Bayati on D (one of Rast's usual moves), then home.
        val review = FreePlay.review(
            play(listOf(96, 100, 103, 100, 96), listOf(100, 103, 106, 110, 106, 103, 100), listOf(103, 100, 96)),
            expected = maqam("Раст"),
        )
        assertTrue(review.phrases.map { it.place }.toString(), review.phrases[1].place.startsWith("переход: Баяти"))
    }

    @Test fun nothingPlayedIsSaidPlainly() {
        val r = FreePlay.review(emptyList(), expected = null)
        assertEquals(null, r.maqam)
        assertTrue(r.points.single().text.contains("ничего"))
    }
}
