package com.udl.oud.fretboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillTest {
    @Test fun steadyNoteIsTakenWithItsMedianOffset() {
        val steady = SteadyNote()
        val readings = listOf(-8.0, -6.0, -7.0, -5.0, -9.0)
        readings.forEach { assertNull(steady.add(it)) }
        assertEquals(-7.0, steady.add(-7.0)!!, 0.001)
    }

    @Test fun wobblingNoteIsNotTakenYet() {
        val steady = SteadyNote()
        listOf(-20.0, 5.0, -18.0, 4.0, -22.0, 6.0).forEach { assertNull(steady.add(it)) }
    }

    @Test fun anotherNoteStartsOver() {
        val steady = SteadyNote()
        repeat(5) { steady.add(3.0) }
        assertNull(steady.add(120.0)) // a semitone and more away: not this note
        repeat(5) { assertNull(steady.add(3.0)) } // the count starts again
        assertEquals(3.0, steady.add(3.0)!!, 0.001)
    }

    @Test fun outOfTuneButSameNoteIsStillTaken() {
        val steady = SteadyNote()
        repeat(5) { steady.add(30.0) }
        assertEquals(30.0, steady.add(30.0)!!, 0.001)
    }

    @Test fun fretboardDrillNamesTenSpotsWithoutRepeatingAPitchBackToBack() {
        val tuning = com.udl.oud.tuner.Tunings.all.first()
        val choices = Fretboard.positions(tuning).filter { it.cents > 0 && it.pitch.isNatural }
        val drill = Drill.onFretboard(choices, random = kotlin.random.Random(7))
        assertEquals(Drill.FRETBOARD_NOTES, drill.steps.size)
        assertEquals(drill.steps, drill.places!!.map { it.pitch.step })
        assertTrue(drill.places.all { it in choices })
        assertTrue(drill.steps.zipWithNext().none { (a, b) -> a == b })
        assertEquals(drill.places.first(), drill.targetPlace)
    }

    @Test fun fretboardDrillWithOneChoiceStillWorks() {
        val only = Fretboard.positions(com.udl.oud.tuner.Tunings.all.first()).first { it.cents == 200 }
        val drill = Drill.onFretboard(listOf(only), count = 3)
        assertEquals(listOf(only, only, only), drill.places)
    }

    @Test fun exerciseWalksThroughTheStepsAndScores() {
        var exercise = Drill(listOf(96, 100, 103))
        assertEquals(96, exercise.target)
        exercise = exercise.record(4.0).record(-25.0)
        assertEquals(103, exercise.target)
        assertFalse(exercise.done)
        exercise = exercise.record(-6.0)
        assertTrue(exercise.done)
        assertNull(exercise.target)
        assertEquals(2, exercise.inTuneCount)
        assertEquals(35.0 / 3, exercise.meanError, 0.001)
        assertEquals(1, exercise.worst)
    }
}
