package com.udl.oud.fretboard

/**
 * A jins: the building block of a maqam, a short run of intervals in quarter tones
 * (4 = whole tone, 3 = three-quarter tone, 2 = semitone, 6 = augmented second).
 */
data class Jins(val name: String, val intervals: List<Int>)

object Ajnas {
    val Rast = Jins("Раст", listOf(4, 3, 3))
    val Bayati = Jins("Баяти", listOf(3, 3, 4))
    val Hijaz = Jins("Хиджаз", listOf(2, 6, 2))
    val Nahawand = Jins("Нахаванд", listOf(4, 2, 4, 4))
    val Kurd = Jins("Курд", listOf(2, 4, 4))
    val Ajam = Jins("Аджам", listOf(4, 4, 2))
    val Saba = Jins("Саба", listOf(3, 3, 2))
    val Sikah = Jins("Сикях", listOf(3, 4))
}

/** How a note is written: a letter (0 = C … 6 = B) and an alteration in quarter tones. */
data class Spelling(val letter: Int, val alter: Int) {
    fun label(style: NoteLabels): String {
        val name = if (style == NoteLabels.Latin) LATIN_LETTERS[letter] else SOLFEGE_LETTERS[letter]
        val sign = when (alter) {
            -2 -> "♭"
            -1 -> "½♭"
            1 -> "½♯"
            2 -> "♯"
            else -> ""
        }
        return name + sign
    }

    companion object {
        private val SOLFEGE_LETTERS = listOf("До", "Ре", "Ми", "Фа", "Соль", "Ля", "Си")
        private val LATIN_LETTERS = listOf("C", "D", "E", "F", "G", "A", "B")

        /** Quarter-tone steps of C D E F G A B within the octave. */
        val NATURAL_STEPS = listOf(0, 4, 8, 10, 14, 18, 22)

        /** Usual spelling of a pitch on its own: flats for E♭ A♭ B♭, sharps for C♯ F♯. */
        fun of(step: Int): Spelling {
            val pc = step.mod(24)
            NATURAL_STEPS.indexOf(pc).takeIf { it >= 0 }?.let { return Spelling(it, 0) }
            return when (pc) {
                2 -> Spelling(0, 2) // C♯
                6 -> Spelling(2, -2) // E♭
                12 -> Spelling(3, 2) // F♯
                16 -> Spelling(5, -2) // A♭
                20 -> Spelling(6, -2) // B♭
                else -> {
                    // Quarter tone: half-flat of the natural above if there is one, else half-sharp below.
                    val upper = NATURAL_STEPS.indexOf((pc + 1).mod(24))
                    if (upper >= 0) Spelling(upper, -1) else Spelling(NATURAL_STEPS.indexOf(pc - 1), 1)
                }
            }
        }
    }
}

/** One degree of a maqam built on a given tonic. */
data class ScaleNote(
    val degree: Int,
    /** Quarter tones above the tonic. */
    val offset: Int,
    val spelling: Spelling,
    /** Degrees below the ghammaz belong to the lower jins, the rest to the upper one. */
    val upper: Boolean,
)

/**
 * A maqam as a lower jins on the tonic and an upper jins on the ghammaz (the second pillar
 * of the maqam), with [intervals] giving the whole ascending octave in quarter tones.
 */
data class Maqam(
    val name: String,
    /** Usual tonic as a grid step (2 × MIDI). */
    val tonic: Int,
    val lower: Jins,
    /** Degree (0 = tonic) where the upper jins starts. */
    val ghammaz: Int,
    val upper: Jins,
    val intervals: List<Int>,
) {
    init {
        require(intervals.size == 7 && intervals.sum() == 24) { "$name: an octave is 7 steps, 24 quarter tones" }
        require(intervals.take(lower.intervals.size) == lower.intervals) { "$name: lower jins doesn't match" }
        require(intervals.drop(ghammaz).take(upper.intervals.size) == upper.intervals) {
            "$name: upper jins doesn't match"
        }
    }

    /**
     * The seven degrees on [tonic], each spelled with the next letter after the previous one,
     * so that e.g. Hijaz on D reads D E♭ F♯ G rather than D D♯ F♯ G.
     */
    fun notes(tonic: Int = this.tonic): List<ScaleNote> {
        val tonicSpelling = Spelling.of(tonic)
        var offset = 0
        return (0 until 7).map { degree ->
            if (degree > 0) offset += intervals[degree - 1]
            val pc = (tonic + offset).mod(24)
            val letter = (tonicSpelling.letter + degree) % 7
            val alter = (pc - Spelling.NATURAL_STEPS[letter] + 12).mod(24) - 12
            val spelling = if (alter in -2..2) Spelling(letter, alter) else Spelling.of(pc)
            ScaleNote(degree, offset, spelling, upper = degree >= ghammaz)
        }
    }
}

object Maqamat {
    val all = listOf(
        // Tonics follow the usual Arabic practice on an oud in C–C tuning.
        Maqam("Раст", 96, Ajnas.Rast, 4, Ajnas.Rast, listOf(4, 3, 3, 4, 4, 3, 3)), // C3
        Maqam("Баяти", 100, Ajnas.Bayati, 3, Ajnas.Nahawand, listOf(3, 3, 4, 4, 2, 4, 4)), // D3
        Maqam("Хиджаз", 100, Ajnas.Hijaz, 3, Ajnas.Rast, listOf(2, 6, 2, 4, 3, 3, 4)), // D3
        Maqam("Саба", 100, Ajnas.Saba, 2, Ajnas.Hijaz, listOf(3, 3, 2, 6, 2, 4, 4)), // D3
        Maqam("Нахаванд", 96, Ajnas.Nahawand, 4, Ajnas.Hijaz, listOf(4, 2, 4, 4, 2, 6, 2)), // C3
        Maqam("Курд", 100, Ajnas.Kurd, 3, Ajnas.Nahawand, listOf(2, 4, 4, 4, 2, 4, 4)), // D3
        Maqam("Аджам", 92, Ajnas.Ajam, 4, Ajnas.Ajam, listOf(4, 4, 2, 4, 4, 4, 2)), // B♭2
        Maqam("Сикях", 103, Ajnas.Sikah, 2, Ajnas.Rast, listOf(3, 4, 4, 3, 3, 4, 3)), // E½♭3
    )
}

/** An interval in quarter tones as tones: 1, ¾, ½, 1½. */
fun intervalLabel(quarters: Int): String = when (quarters) {
    2 -> "½"
    3 -> "¾"
    4 -> "1"
    6 -> "1½"
    else -> (quarters / 4.0).toString()
}
