package com.udl.oud.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningsTest {
    private val faFa = Tunings.all.first { it.name == "Фа-Фа" }
    private val a4 = 440.0

    @Test fun faFaRunsFromF2ToF4() =
        assertEquals("F2 A2 D3 G3 C4 F4", faFa.courses.joinToString(" ") { "${it.latin}${it.octave}" })

    @Test fun highF4IsNotTakenForItsLowerOctave() {
        val f4 = Note(65).frequency(a4)
        assertEquals(f4, faFa.resolveOctave(f4, canBeOctaveLower = true, lockedCourse = null, a4 = a4), 1e-9)
    }

    @Test fun bassF2HeardAsItsSecondHarmonicIsBroughtDown() {
        val f3 = Note(53).frequency(a4) // what a small mic may report for the F2 course
        assertEquals(f3 / 2, faFa.resolveOctave(f3, canBeOctaveLower = true, lockedCourse = null, a4 = a4), 1e-9)
    }

    @Test fun nearestCourseFindsTheTopF() = assertEquals(5, faFa.nearestCourse(Note(65).frequency(a4) * 1.01, a4))
}
