package com.udl.oud.taqsim

import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.fretboard.Spelling
import kotlin.math.abs

/**
 * A station of the sayr: where the taqsim dwells for a while. Notes are counted in degrees of
 * the station's own scale ([maqam] on [tonic], in its ascending or descending form): 0 is that
 * tonic, 7 its octave, −1 the degree under it. The [jins] sitting on degree [jinsRoot] gives the
 * motifs; phrases rest on [focus] and stay within [low]..[high].
 */
data class Station(
    val name: String,
    val maqam: Maqam,
    val tonic: Int,
    val descending: Boolean,
    val jins: Jins,
    val jinsRoot: Int,
    val focus: Int,
    val low: Int,
    val high: Int,
) {
    init {
        require(low <= focus && focus <= high) { "$name: focus outside the range" }
        require(jinsRoot in low - 1..high) { "$name: jins outside the range" }
    }

    private val notes: List<ScaleNote> = maqam.notes(tonic, descending)

    /** Grid step (2 × MIDI) of [degree]. */
    fun step(degree: Int): Int = tonic + notes[Math.floorMod(degree, 7)].offset + 24 * Math.floorDiv(degree, 7)

    fun spelling(degree: Int): Spelling = notes[Math.floorMod(degree, 7)].spelling

    /** The degree whose pitch is nearest to grid step [step]: how a phrase carries on after a change of station. */
    fun degreeNear(step: Int): Int = (low - 7..high + 7).minBy { abs(step(it) - step) }

    /** Degrees of the jins itself, counted in the station's scale. */
    val jinsDegrees: IntRange get() = jinsRoot..jinsRoot + jins.intervals.size
}

/** Parts of a taqsim in the order the sayr goes through them. */
enum class SectionKind(val title: String) {
    Opening("Вступление"),
    Ghammaz("Подъём к гаммазу"),
    Upper("Развитие наверху"),
    Peak("Кульминация"),
    Revisit("Снова у начала"),
    Modulation("Переход"),
    Return("Возвращение"),
    Qafla("Кафла"),
}

/** What a phrase does in its section. */
enum class PhraseRole(val title: String) {
    Open("начало"),
    Question("вопрос"),
    Answer("ответ"),
    Develop("развитие"),
    Climb("подъём"),
    Descend("спуск"),
    Cadence("каданс"),
}

/** One part of the plan: [phrases] phrases at [station], the last of them resting on [endOn]. */
data class Section(
    val kind: SectionKind,
    val station: Station,
    val roles: List<PhraseRole>,
    /** Degree each phrase ends on, one per role. */
    val endsOn: List<Int>,
) {
    init {
        require(roles.isNotEmpty() && roles.size == endsOn.size)
        require(endsOn.all { it in station.low..station.high }) { "${station.name}: end outside the range" }
    }
}

/** A note of the taqsim: [beats] long, in units of the base pulse; [degree] in its station's scale. */
data class TaqsimNote(val step: Int, val spelling: Spelling, val degree: Int, val beats: Double)

/** A phrase with what it is for, how it was made, and the pause after it. */
data class TaqsimPhrase(
    val section: SectionKind,
    val role: PhraseRole,
    val station: Station,
    /** How it was built: motifs and the moves applied to them. */
    val how: String,
    val notes: List<TaqsimNote>,
    val restBeats: Double,
    /** Indices of the notes that state the taqsim's theme here, if it is heard in this phrase. */
    val themeNotes: Set<Int> = emptySet(),
) {
    val beats: Double get() = notes.sumOf { it.beats } + restBeats
}

enum class TaqsimLength(val title: String, val phrases: IntRange) {
    Short("Короткий", 7..9),
    Medium("Средний", 11..13),
    Long("Длинный", 15..18),
}

/**
 * The taqsim's theme: a short motif with its own rhythm, stated at the start and brought back
 * through the sayr — in the upper jins, in a modulation, at the octave — changed each time.
 */
data class Theme(val name: String, val shape: List<Int>, val rhythm: List<Double>) {
    init {
        require(shape.size == rhythm.size && shape.size in 3..7)
    }
}

/** A whole taqsim: phrases in order, the form of its course, and the theme carried through it. */
data class Taqsim(
    val maqam: Maqam,
    val seed: Long,
    val phrases: List<TaqsimPhrase>,
    val detailed: Boolean,
    val form: TaqsimForm = TaqsimForm.Classic,
    val theme: Theme? = null,
) {
    /** Phrases where the theme is heard. */
    val themePhrases: List<Int> get() = phrases.indices.filter { phrases[it].themeNotes.isNotEmpty() }

    val notes: List<TaqsimNote> get() = phrases.flatMap { it.notes }
    val beats: Double get() = phrases.sumOf { it.beats }
}
