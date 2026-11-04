package com.udl.oud.fretboard

import com.udl.oud.tuner.Note
import com.udl.oud.tuner.Tuning
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

enum class NoteLabels { Solfege, Latin, Arabic }

/**
 * A pitch on the 24-tone (quarter-tone) grid: [step] is twice the MIDI number,
 * so even steps are the usual semitones and odd steps the quarter tones between them.
 */
data class Pitch(val step: Int) {
    val isQuarterTone: Boolean get() = step % 2 != 0

    /** C D E F G A B without sharps or flats. */
    val isNatural: Boolean get() = !isQuarterTone && Note(step / 2).latin.length == 1

    /** As it sounds: on the grid, quarter tones moved by the [Intonation] in use. */
    fun frequency(a4: Double): Double = a4 * 2.0.pow((step / 2.0 - 69) / 12 + Intonation.cents(step) / 1200)

    /** Arabic name of this exact pitch, if it has a common one. */
    val arabicName: String? get() = ARABIC_NAMES[step]

    fun label(style: NoteLabels): String {
        if (style == NoteLabels.Arabic) arabicName?.let { return it }
        val latin = style == NoteLabels.Latin
        if (!isQuarterTone) return Note(step / 2).let { if (latin) it.latin else it.solfege }
        // A quarter tone is written as the natural note above it lowered by half a flat,
        // or, when there is no natural note above, as the one below raised by half a sharp.
        val upper = Note((step + 1) / 2)
        val lower = Note((step - 1) / 2)
        return if (upper.latin.length == 1) {
            (if (latin) upper.latin else upper.solfege) + "½♭"
        } else {
            (if (latin) lower.latin else lower.solfege) + "½♯"
        }
    }

    companion object {
        /** Nearest grid pitch; with [quarterTones] off, only semitones are considered. */
        fun nearest(frequency: Double, a4: Double, quarterTones: Boolean): Pitch {
            val semitones = 69 + 12 * log2(frequency / a4)
            if (!quarterTones) return Pitch(semitones.roundToInt() * 2)
            // The quarter tones may sit off the grid: of the two steps around, the one sounding nearer.
            val below = kotlin.math.floor(semitones * 2).toInt()
            return listOf(below, below + 1).map(::Pitch).minBy { kotlin.math.abs(log2(frequency / it.frequency(a4))) }
        }

        fun of(note: Note) = Pitch(note.midi * 2)

        /**
         * Arabic note names in the Rast-on-C system as an oud in C–C tuning plays it
         * (Rast = C3, Yakah = G2); notes below Yakah carry the "qarar" (lower) prefix.
         */
        private val ARABIC_NAMES = mapOf(
            72 to "к. Раст", // C2
            76 to "к. Дукях", // D2
            79 to "к. Сикях", // E½♭2
            82 to "к. Джахаркях", // F2
            84 to "к. Хиджаз", // F♯2
            86 to "Якях", // G2
            90 to "Ушайран", // A2
            93 to "Ирак", // B½♭2
            96 to "Раст", // C3
            100 to "Дукях", // D3
            102 to "Курди", // E♭3
            103 to "Сикях", // E½♭3
            106 to "Джахаркях", // F3
            108 to "Хиджаз", // F♯3
            110 to "Нава", // G3
            112 to "Хисар", // G♯3
            114 to "Хусейни", // A3
            116 to "Аджам", // B♭3
            117 to "Ауж", // B½♭3
            120 to "Кирдан", // C4
            124 to "Мухайяр", // D4
            127 to "Бузрук", // E½♭4
            130 to "Махуран", // F4
            134 to "Рамаль Тути", // G4
        )
    }
}

/** One playable spot: a [course] (0 = bass) and how many cents above the open string. */
data class Position(val course: Int, val cents: Int, val pitch: Pitch) {
    /** Usual first-position finger: 0 for the open string, null beyond the little finger's reach. */
    val finger: Int?
        get() = when {
            cents == 0 -> 0
            cents <= 200 -> 1
            cents <= 350 -> 2
            cents <= 450 -> 3
            cents <= 550 -> 4
            else -> null
        }
}

object Fretboard {
    /** The neck ends where the body starts, around a fifth above the open string. */
    const val NECK_CENTS = 700

    /** Playing on over the body, up to the octave (half the string). */
    const val EXTENDED_CENTS = 1200

    /** Every quarter-tone position on every course, from the open string up to [maxCents]. */
    fun positions(tuning: Tuning, maxCents: Int = NECK_CENTS): List<Position> =
        tuning.courses.flatMapIndexed { course, open ->
            (0..maxCents step 50).map { cents -> Position(course, cents, Pitch(open.midi * 2 + cents / 50)) }
        }

    /** Distance from the nut as a fraction of the string length, where a note [cents] above open stops. */
    fun distanceFraction(cents: Double): Double = 1 - 2.0.pow(-cents / 1200)

    /**
     * Positions where the heard [frequency] can be played, with its offset in cents from the
     * nearest grid pitch. Restricted to [onlyCourse] when the player says which course they use.
     */
    fun matches(
        tuning: Tuning,
        frequency: Double,
        a4: Double,
        quarterTones: Boolean,
        onlyCourse: Int? = null,
        maxCents: Int = NECK_CENTS,
    ): Pair<List<Position>, Double> {
        val pitch = Pitch.nearest(frequency, a4, quarterTones)
        val cents = 1200 * log2(frequency / pitch.frequency(a4))
        val positions = positions(tuning, maxCents).filter {
            it.pitch == pitch && (onlyCourse == null || it.course == onlyCourse)
        }
        return positions to cents
    }

    /**
     * Picks the octave of a detected pitch that fits the neck. Without knowing the course, a
     * reading that may be an octave lower keeps the higher reading unless only the lower one
     * can be played; with a course, it keeps whichever lies on that course.
     */
    fun resolveOctave(
        tuning: Tuning,
        frequency: Double,
        canBeOctaveLower: Boolean,
        a4: Double,
        onlyCourse: Int?,
        maxCents: Int = NECK_CENTS,
    ): Double {
        if (!canBeOctaveLower) return frequency
        val lower = frequency / 2
        fun playable(f: Double): Boolean {
            val courses = onlyCourse?.let { listOf(tuning.courses[it]) } ?: tuning.courses
            return courses.any {
                val above = 1200 * log2(f / it.frequency(a4))
                above > -25 && above < maxCents + 25
            }
        }
        return if (!playable(frequency) && playable(lower)) lower else frequency
    }
}
