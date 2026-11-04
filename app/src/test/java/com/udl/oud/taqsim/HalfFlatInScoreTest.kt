package com.udl.oud.taqsim

import com.udl.oud.fretboard.Accidental
import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HalfFlatInScoreTest {
    /** Every note the taqsim spells with a half-flat or half-sharp carries that sign in the score. */
    @Test fun quarterToneNotesKeepTheirSignInTheScore() {
        Maqamat.all.forEach { m ->
            (0L until 10L).forEach { s ->
                val t = TaqsimGenerator(s).generate(m, TaqsimLength.Medium)
                val items = ScoreLayout.layout(t, emptyMap(), 160f).flatMap { it.items }
                t.notes.forEachIndexed { i, n ->
                    val expected = when (n.spelling.alter) {
                        -2 -> Accidental.Flat
                        -1 -> Accidental.HalfFlat
                        1 -> Accidental.HalfSharp
                        2 -> Accidental.Sharp
                        else -> Accidental.None
                    }
                    assertEquals("${m.name} $s note $i", expected, items[i].staff.accidental)
                }
            }
        }
    }

    @Test fun rastHasHalfFlatsInItsScore() {
        val rast = Maqamat.all.first { it.name == "Раст" }
        val items = ScoreLayout.layout(TaqsimGenerator(1).generate(rast, TaqsimLength.Medium), emptyMap(), 160f).flatMap { it.items }
        assertTrue(items.count { it.staff.accidental == Accidental.HalfFlat } >= 5)
    }
}
