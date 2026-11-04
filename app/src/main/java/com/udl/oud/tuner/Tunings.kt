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
    /** The same tuning with every course moved by [semitones]; keeps the name. */
    fun transposed(semitones: Int): Tuning =
        if (semitones == 0) this else copy(courses = courses.map { Note(it.midi + semitones) })

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

/** The two oud traditions the app knows: they differ in tunings and, later, in maqam theory. */
enum class OudType(val title: String) {
    Arabic("Арабский"),
    Turkish("Турецкий"),
}

object Tunings {
    val arabic = listOf(
        Tuning("До-До", listOf(Note(36), Note(41), Note(45), Note(50), Note(55), Note(60))), // C2 F2 A2 D3 G3 C4
        Tuning("Ре-До", listOf(Note(38), Note(43), Note(45), Note(50), Note(55), Note(60))), // D2 G2 A2 D3 G3 C4
        Tuning("Фа-Фа", listOf(Note(41), Note(45), Note(50), Note(55), Note(60), Note(65))), // F2 A2 D3 G3 C4 F4
    )

    /** Sounding pitches of the usual Turkish tunings, not the transposed Turkish notation. */
    val turkish = listOf(
        Tuning("До♯-Ре", listOf(Note(37), Note(42), Note(47), Note(52), Note(57), Note(62))), // C♯2 F♯2 B2 E3 A3 D4
        Tuning("Ре-Ре", listOf(Note(38), Note(42), Note(47), Note(52), Note(57), Note(62))), // D2 F♯2 B2 E3 A3 D4
    )

    val all = arabic + turkish

    /** Tunings of [oud] as they sound; a Turkish oud is tuned down to its [ahenk]. */
    fun forOud(oud: OudType, ahenk: Ahenk = Ahenk.Bolahenk): List<Tuning> = when (oud) {
        OudType.Arabic -> arabic
        OudType.Turkish -> turkish.map { it.transposed(ahenk.semitones) }
    }
}

/**
 * Ahenk: the pitch level Turkish notation is played at, named after the ney sizes. Notation stays
 * the same; the whole instrument sounds lower than in Bolahenk. Shifts follow the âhenk ladder
 * (which perde of each ahenk sounds Mansur's dügâh), taken downwards from Bolahenk, as far as an
 * oud can be tuned down. [komas] and [letters] spell the shift, [semitones] retunes the oud.
 */
enum class Ahenk(val title: String, val semitones: Int, val komas: Int, val letters: Int, val shift: String) {
    Bolahenk("Bolâhenk", 0, 0, 0, "основной"),
    Supurde("Süpürde", -2, -9, -1, "на тон ниже"),
    Mustahsen("Müstahsen", -3, -13, -2, "на малую терцию ниже"),
    Kiz("Kız", -5, -22, -3, "на кварту ниже"),
    Mansur("Mansur", -7, -31, -4, "на квинту ниже"),
}
