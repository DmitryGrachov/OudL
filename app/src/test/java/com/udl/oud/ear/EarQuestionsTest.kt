package com.udl.oud.ear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EarQuestionsTest {
    private val random = Random(7)

    @Test fun everyExerciseAndLevelMakesSoundQuestions() {
        EarExercise.entries.forEach { e ->
            EarLevel.entries.forEach { l ->
                repeat(25) {
                    val q = EarQuestions.make(e, l, random)
                    assertTrue("$e $l", q.options.size >= 2)
                    assertTrue(q.sound.all { it.lengthMs > 0 && it.startMs >= 0 && it.step in 60..150 })
                    assertTrue(q.explanation.isNotBlank())
                }
            }
        }
    }

    @Test fun harderLevelsOfferMoreOptions() {
        listOf(EarExercise.Jins, EarExercise.Interval, EarExercise.QuarterTone, EarExercise.Rest).forEach { e ->
            val easy = EarQuestions.make(e, EarLevel.Easy, random).options.size
            val hard = EarQuestions.make(e, EarLevel.Hard, random).options.size
            assertTrue("$e: $easy vs $hard", hard > easy)
        }
    }

    @Test fun theIntervalPlayedIsTheOneMarkedRight() {
        EarLevel.entries.forEach { l ->
            repeat(30) {
                val q = EarQuestions.make(EarExercise.Interval, l, random)
                val kinds = EarQuestions.intervalKinds(l)
                val (a, b) = q.sound.take(2).map { it.step }
                assertEquals(kinds[q.correct].quarters, kotlin.math.abs(b - a))
            }
        }
    }

    @Test fun theQuarterToneQuestionPlaysTheNamedNote() {
        repeat(30) {
            val q = EarQuestions.make(EarExercise.QuarterTone, EarLevel.Hard, random)
            val root = q.sound.first().step
            val middle = q.sound[2].step
            // The options are the minor, neutral and major third, spelled on one letter.
            assertEquals(listOf(6, 7, 8)[q.correct], middle - root)
            assertEquals(3, q.options.size)
        }
        assertEquals("Ми½♭", EarQuestions.spellOn(2, 103))
        assertEquals("Си♭", EarQuestions.spellOn(6, 116))
    }

    @Test fun compareGivesOneSoundPerOption() {
        listOf(EarExercise.Jins, EarExercise.Maqam, EarExercise.Interval, EarExercise.QuarterTone).forEach { e ->
            val q = EarQuestions.make(e, EarLevel.Medium, random)
            assertEquals(q.options.size, q.compare.size)
        }
    }

    @Test fun progressCountsAndKnowsWhenToMoveOn() {
        var s = EarStats()
        repeat(9) { s = s.record(true) }
        assertTrue(!s.readyForMore)
        s = s.record(false)
        assertTrue(s.readyForMore) // 9 of the last 10
        repeat(25) { s = s.record(false) }
        assertEquals(EarStats.KEEP, s.recent.size)
        assertEquals(35, s.answered)
        assertTrue(!s.readyForMore)
    }
}
