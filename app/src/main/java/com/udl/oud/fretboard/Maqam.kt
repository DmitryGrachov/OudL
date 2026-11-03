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
    val SabaZamzam = Jins("Саба замзам", listOf(2, 4, 2))
    val Sikah = Jins("Сикях", listOf(3, 4))
    val Nikriz = Jins("Никриз", listOf(4, 2, 6, 2))
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

/** One degree of a scale built on a given tonic. */
data class ScaleNote(
    val degree: Int,
    /** Quarter tones above the tonic. */
    val offset: Int,
    val spelling: Spelling,
    /** In a maqam, degrees from the ghammaz up belong to the upper jins; always false otherwise. */
    val upper: Boolean,
)

/** A scale the board can show: an Arabic maqam or a European scale. */
sealed interface Scale {
    val name: String

    /** Usual tonic as a grid step (2 × MIDI). */
    val tonic: Int

    /** Whether the scale comes down differently from how it goes up. */
    val hasDescent: Boolean

    /** Steps from the tonic up to the octave, in quarter tones, for the chosen direction. */
    fun intervals(descending: Boolean): List<Int>

    /** Degrees on [tonic], bottom-up, for the chosen direction. */
    fun notes(tonic: Int = this.tonic, descending: Boolean = false): List<ScaleNote>
}

/**
 * Builds and spells the degrees of a scale. Each degree takes the letter [letters] gives it,
 * counted from the tonic's letter (the next letter per step for seven-note scales), so that
 * e.g. Hijaz on D reads D E♭ F♯ G rather than D D♯ F♯ G.
 */
internal fun spellScale(
    tonic: Int,
    intervals: List<Int>,
    letters: List<Int> = (0..6).toList(),
    upperFrom: Int = Int.MAX_VALUE,
): List<ScaleNote> {
    val tonicSpelling = Spelling.of(tonic)
    var offset = 0
    return intervals.indices.map { degree ->
        if (degree > 0) offset += intervals[degree - 1]
        val pc = (tonic + offset).mod(24)
        val letter = (tonicSpelling.letter + letters[degree]) % 7
        val alter = (pc - Spelling.NATURAL_STEPS[letter] + 12).mod(24) - 12
        val spelling = if (alter in -2..2) Spelling(letter, alter) else Spelling.of(pc)
        ScaleNote(degree, offset, spelling, upper = degree >= upperFrom)
    }
}

/**
 * How a maqam comes down when that differs from going up: the upper jins on the ghammaz is
 * replaced by another one, e.g. Rast descends through B♭ (Nahawand on G) instead of B½♭.
 * [intervals] are still listed bottom-up from the tonic.
 */
data class Descent(val upper: Jins, val intervals: List<Int>)

/**
 * A maqam as a lower jins on the tonic and an upper jins on the ghammaz (the second pillar
 * of the maqam), with [intervals] giving the whole ascending octave in quarter tones.
 */
data class Maqam(
    override val name: String,
    override val tonic: Int,
    val lower: Jins,
    /** Degree (0 = tonic) where the upper jins starts. */
    val ghammaz: Int,
    val upper: Jins,
    val intervals: List<Int>,
    /** Descending form, or null when the maqam comes down the way it goes up. */
    val descent: Descent? = null,
) : Scale {
    init {
        checkOctave(intervals, upper)
        descent?.let { checkOctave(it.intervals, it.upper) }
    }

    private fun checkOctave(intervals: List<Int>, upper: Jins) {
        require(intervals.size == 7 && intervals.sum() == 24) { "$name: an octave is 7 steps, 24 quarter tones" }
        require(intervals.take(lower.intervals.size) == lower.intervals) { "$name: lower jins doesn't match" }
        // The upper jins may reach past the octave; only the part inside it is checked.
        val top = intervals.drop(ghammaz)
        require(top.take(upper.intervals.size) == upper.intervals.take(top.size)) { "$name: upper jins doesn't match" }
    }

    fun upperJins(descending: Boolean): Jins = if (descending) descent?.upper ?: upper else upper

    override val hasDescent: Boolean get() = descent != null

    override fun intervals(descending: Boolean): List<Int> =
        if (descending) descent?.intervals ?: intervals else intervals

    override fun notes(tonic: Int, descending: Boolean): List<ScaleNote> =
        spellScale(tonic, intervals(descending), upperFrom = ghammaz)
}

/** Scales shown together under one name, e.g. a maqam family (fasila). */
data class ScaleGroup(val name: String, val scales: List<Scale>)

/**
 * Maqamat by family (fasila): a family shares the lower jins on the tonic and differs in what
 * sits on top. Tonics follow the usual Arabic practice on an oud in C–C tuning.
 */
object Maqamat {
    private const val C3 = 96
    private const val D3 = 100
    private const val E_HALF_FLAT3 = 103
    private const val B_FLAT2 = 92
    private const val B_HALF_FLAT2 = 93

    val families = listOf(
        ScaleGroup(
            "Раст",
            listOf(
                Maqam(
                    "Раст", C3, Ajnas.Rast, 4, Ajnas.Rast, listOf(4, 3, 3, 4, 4, 3, 3),
                    Descent(Ajnas.Nahawand, listOf(4, 3, 3, 4, 4, 2, 4)), // down through B♭
                ),
                Maqam("Махур", C3, Ajnas.Rast, 4, Ajnas.Ajam, listOf(4, 3, 3, 4, 4, 4, 2)), // B natural
                Maqam("Сузнак", C3, Ajnas.Rast, 4, Ajnas.Hijaz, listOf(4, 3, 3, 4, 2, 6, 2)),
                Maqam("Найруз", C3, Ajnas.Rast, 4, Ajnas.Bayati, listOf(4, 3, 3, 4, 3, 3, 4)),
            ),
        ),
        ScaleGroup(
            "Баяти",
            listOf(
                Maqam("Баяти", D3, Ajnas.Bayati, 3, Ajnas.Nahawand, listOf(3, 3, 4, 4, 2, 4, 4)),
                Maqam("Баяти шури", D3, Ajnas.Bayati, 3, Ajnas.Hijaz, listOf(3, 3, 4, 2, 6, 2, 4)),
                Maqam("Хусейни", D3, Ajnas.Bayati, 4, Ajnas.Bayati, listOf(3, 3, 4, 4, 3, 3, 4)),
            ),
        ),
        ScaleGroup(
            "Хиджаз",
            listOf(
                Maqam(
                    "Хиджаз", D3, Ajnas.Hijaz, 3, Ajnas.Rast, listOf(2, 6, 2, 4, 3, 3, 4),
                    Descent(Ajnas.Nahawand, listOf(2, 6, 2, 4, 2, 4, 4)), // down through B♭
                ),
                Maqam("Хиджазкар", C3, Ajnas.Hijaz, 4, Ajnas.Hijaz, listOf(2, 6, 2, 4, 2, 6, 2)),
                Maqam("Занджаран", C3, Ajnas.Hijaz, 4, Ajnas.Ajam, listOf(2, 6, 2, 4, 4, 4, 2)),
            ),
        ),
        ScaleGroup(
            "Саба",
            listOf(
                Maqam("Саба", D3, Ajnas.Saba, 2, Ajnas.Hijaz, listOf(3, 3, 2, 6, 2, 4, 4)),
                Maqam("Саба замзам", D3, Ajnas.SabaZamzam, 2, Ajnas.Hijaz, listOf(2, 4, 2, 6, 2, 4, 4)),
            ),
        ),
        ScaleGroup(
            "Нахаванд",
            listOf(
                Maqam(
                    "Нахаванд", C3, Ajnas.Nahawand, 4, Ajnas.Hijaz, listOf(4, 2, 4, 4, 2, 6, 2),
                    Descent(Ajnas.Kurd, listOf(4, 2, 4, 4, 2, 4, 4)), // down through A♭ B♭
                ),
            ),
        ),
        ScaleGroup(
            "Курд",
            listOf(
                Maqam("Курд", D3, Ajnas.Kurd, 3, Ajnas.Nahawand, listOf(2, 4, 4, 4, 2, 4, 4)),
                Maqam("Хиджазкар-курд", C3, Ajnas.Kurd, 4, Ajnas.Hijaz, listOf(2, 4, 4, 4, 2, 6, 2)),
            ),
        ),
        ScaleGroup(
            "Аджам",
            listOf(Maqam("Аджам", B_FLAT2, Ajnas.Ajam, 4, Ajnas.Ajam, listOf(4, 4, 2, 4, 4, 4, 2))),
        ),
        ScaleGroup(
            "Сикях",
            listOf(
                Maqam("Сикях", E_HALF_FLAT3, Ajnas.Sikah, 2, Ajnas.Rast, listOf(3, 4, 4, 3, 3, 4, 3)),
                Maqam("Хузам", E_HALF_FLAT3, Ajnas.Sikah, 2, Ajnas.Hijaz, listOf(3, 4, 2, 6, 2, 4, 3)),
                Maqam("Ирак", B_HALF_FLAT2, Ajnas.Sikah, 2, Ajnas.Bayati, listOf(3, 4, 3, 3, 4, 4, 3)),
            ),
        ),
        ScaleGroup(
            "Никриз",
            listOf(
                Maqam("Никриз", C3, Ajnas.Nikriz, 4, Ajnas.Nahawand, listOf(4, 2, 6, 2, 4, 2, 4)),
                Maqam("Нава асар", C3, Ajnas.Nikriz, 4, Ajnas.Hijaz, listOf(4, 2, 6, 2, 2, 6, 2)),
            ),
        ),
    )

    val all: List<Maqam> = families.flatMap { group -> group.scales.map { it as Maqam } }
}

/** An interval in quarter tones as tones: 1, ¾, ½, 1½. */
fun intervalLabel(quarters: Int): String = when (quarters) {
    2 -> "½"
    3 -> "¾"
    4 -> "1"
    6 -> "1½"
    else -> (quarters / 4.0).toString()
}
