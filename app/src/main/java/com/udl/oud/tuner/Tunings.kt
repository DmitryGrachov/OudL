package com.udl.oud.tuner

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private val SOLFEGE = listOf("До", "До♯", "Ре", "Ре♯", "Ми", "Фа", "Фа♯", "Соль", "Соль♯", "Ля", "Ля♯", "Си")
private val LATIN = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/** A chromatic note identified by its MIDI number (C4 = 60, A4 = 69). */
data class Note(val midi: Int) {
    val solfege: String get() = SOLFEGE[midi.mod(12)]
    val latin: String get() = LATIN[midi.mod(12)]
    val octave: Int get() = midi / 12 - 1

    fun frequency(a4: Double): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    companion object {
        fun nearest(frequency: Double, a4: Double): Note =
            Note((69 + 12 * log2(frequency / a4)).roundToInt())
    }
}

/** Signed distance in cents from [target] to [frequency]; positive means sharp. */
fun centsBetween(frequency: Double, target: Double): Double = 1200 * log2(frequency / target)

/** An oud tuning; [courses] go from the bass course to the treble course. */
data class Tuning(val name: String, val courses: List<Note>) {
    fun nearestCourse(frequency: Double, a4: Double): Int =
        courses.indices.minBy { abs(centsBetween(frequency, courses[it].frequency(a4))) }

    /**
     * Fixes octave errors: when the detected pitch may really be an octave lower, keeps whichever
     * reading lands closer to a course (the [lockedCourse] if set). Small microphones barely pick
     * up the 65 Hz fundamental of the bass C, so the detector often hears its 2nd harmonic instead.
     */
    fun resolveOctave(frequency: Double, canBeOctaveLower: Boolean, lockedCourse: Int?, a4: Double): Double {
        if (!canBeOctaveLower) return frequency
        fun distance(f: Double): Double {
            val course = lockedCourse ?: nearestCourse(f, a4)
            return abs(centsBetween(f, courses[course].frequency(a4)))
        }
        val lower = frequency / 2
        return if (distance(lower) < distance(frequency)) lower else frequency
    }
}

object Tunings {
    val all = listOf(
        Tuning("До-До", listOf(Note(36), Note(41), Note(45), Note(50), Note(55), Note(60))), // C2 F2 A2 D3 G3 C4
        Tuning("Ре-До", listOf(Note(38), Note(43), Note(45), Note(50), Note(55), Note(60))), // D2 G2 A2 D3 G3 C4
    )
}
