package com.udl.oud.jins

import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.PhraseRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JinsEtudeTest {
    @Test fun anEtudeStaysInItsJinsAndComesHome() {
        JinsCatalog.ajnas.forEach { j ->
            JinsCatalog.roots(j).forEach { root ->
                (0L until 15L).forEach { seed ->
                    val e = JinsEtude.generate(j, root, seed)
                    val top = j.intervals.size
                    e.phrases.forEach { p -> assertTrue("${j.name} $seed", p.notes.all { it.degree in -2..top + 1 }) }
                    assertEquals("${j.name} $seed", 0, e.phrases.last().notes.last().degree)
                    assertEquals(root, e.phrases.last().notes.last().step)
                    assertTrue(e.phrases.size in 5..7)
                }
            }
        }
    }

    @Test fun theQuestionRestsOnAPillarAndIsAnswered() {
        JinsCatalog.ajnas.forEach { j ->
            val e = JinsEtude.generate(j, JinsCatalog.defaultRoot(j), 4)
            val q = e.phrases.indexOfFirst { it.role == PhraseRole.Question }
            assertEquals(PhraseRole.Answer, e.phrases[q + 1].role)
            assertEquals(JinsEtude.questionRest(j), e.phrases[q].notes.last().degree)
            assertTrue(JinsEtude.questionRest(j) in JinsGrammars.of(j).rests || JinsEtude.questionRest(j) == j.intervals.size)
            assertEquals(0, e.phrases[q + 1].notes.last().degree)
        }
    }

    @Test fun theColourIsHeard() {
        JinsCatalog.ajnas.forEach { j ->
            val colour = JinsGrammars.of(j).characteristic
            val heard = (0L until 20L).count { s -> JinsEtude.generate(j, JinsCatalog.defaultRoot(j), s).notes.any { it.degree in colour } }
            assertTrue("${j.name}: $heard", heard >= 18)
        }
    }
}
