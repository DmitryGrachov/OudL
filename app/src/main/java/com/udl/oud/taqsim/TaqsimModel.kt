package com.udl.oud.taqsim

import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.ScaleNote
import com.udl.oud.fretboard.Spelling
import com.udl.oud.fretboard.StaffNote
import kotlin.math.abs
import kotlin.math.pow

/**
 * How the degrees of a station sound and are written. Everything the taqsim does with degrees —
 * motifs, rules, rhythm, ornaments — goes through it.
 */
interface StationScale {
    /** Pitch units in an octave: 24 quarter tones. */
    val perOctave: Int

    /** Pitch of [degree] in units: a grid step (2 × MIDI). */
    fun step(degree: Int): Int

    /** The note's name as the tab shows it. */
    fun label(degree: Int): String

    /** The note as it is written on a staff. */
    fun staff(degree: Int): StaffNote

    /** Frequency at A4 = 440 Hz. */
    fun hz(degree: Int): Double
}

/** An Arabic maqam on [tonic] in its ascending or descending form. */
data class ArabicScale(val maqam: Maqam, val tonic: Int, val descending: Boolean) : StationScale {
    private val notes: List<ScaleNote> = maqam.notes(tonic, descending)
    override val perOctave: Int get() = 24
    override fun step(degree: Int): Int = tonic + notes[Math.floorMod(degree, 7)].offset + 24 * Math.floorDiv(degree, 7)
    fun spelling(degree: Int): Spelling = notes[Math.floorMod(degree, 7)].spelling
    override fun label(degree: Int): String = spelling(degree).label(NoteLabels.Solfege)
    override fun staff(degree: Int): StaffNote = StaffNote.arabic(step(degree), spelling(degree))
    /** With functional intonation, Bayati's half-flat second sits a little lower than the grid's. */
    override fun hz(degree: Int): Double {
        val step = step(degree)
        val bayati = maqam.lower == com.udl.oud.fretboard.Ajnas.Bayati && Math.floorMod(degree, 7) == 1 && step % 2 != 0
        return Pitch(step).frequency(440.0) * if (bayati) 2.0.pow(com.udl.oud.fretboard.Intonation.bayatiSecond / 1200) else 1.0
    }
}

/**
 * A station of the sayr: where the taqsim dwells for a while. Notes are counted in degrees of
 * the station's [scale]: 0 is its tonic, 7 its octave, −1 the degree under it. The [jins]
 * sitting on degree [jinsRoot] gives the motifs (named [jinsTitle]); phrases rest on [focus] and
 * stay within [low]..[high].
 */
data class Station(
    val name: String,
    val scale: StationScale,
    val jins: Jins,
    val jinsRoot: Int,
    val focus: Int,
    val low: Int,
    val high: Int,
    val jinsTitle: String = jins.name,
) {
    /** An Arabic station: [maqam] on [tonic]. */
    constructor(
        name: String,
        maqam: Maqam,
        tonic: Int,
        descending: Boolean,
        jins: Jins,
        jinsRoot: Int,
        focus: Int,
        low: Int,
        high: Int,
    ) : this(name, ArabicScale(maqam, tonic, descending), jins, jinsRoot, focus, low, high)

    init {
        require(low <= focus && focus <= high) { "$name: focus outside the range" }
        require(jinsRoot in low - 1..high) { "$name: jins outside the range" }
    }

    /** The maqam of the station, or null for an etude's own scale. */
    val maqam: Maqam? get() = (scale as? ArabicScale)?.maqam

    val tonic: Int get() = step(0)

    fun step(degree: Int): Int = scale.step(degree)
    fun label(degree: Int): String = scale.label(degree)
    fun staff(degree: Int): StaffNote = scale.staff(degree)
    fun hz(degree: Int): Double = scale.hz(degree)

    /** Arabic spelling of [degree]; only for Arabic stations. */
    fun spelling(degree: Int): Spelling = (scale as ArabicScale).spelling(degree)

    /** The degree whose pitch is nearest to [step]: how a phrase carries on after a change of station. */
    fun degreeNear(step: Int): Int = (low - 7..high + 7).minBy { abs(step(it) - step) }

    /** The degree nearest to a heard frequency (at A4 = 440), counted with its octave (7 is the octave above 0), and how far off in cents. */
    fun degreeOfHz(hz: Double): Pair<Int, Double> = (0 until 7).map { d ->
        val cents = 1200 * kotlin.math.log2(hz / hz(d))
        val octaves = kotlin.math.round(cents / 1200)
        d + 7 * octaves.toInt() to cents - 1200 * octaves
    }.minBy { abs(it.second) }

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

/**
 * A note of the taqsim: [beats] long, in units of the base pulse; [degree] in its station's
 * scale, [step] its pitch in the scale's units, [name] and [staff] how it is shown and written,
 * [hz] its frequency at A4 = 440.
 */
data class TaqsimNote(
    val step: Int,
    val name: String,
    val staff: StaffNote,
    val hz: Double,
    val degree: Int,
    val beats: Double,
    /** How strongly it is played, 0..1. */
    val velocity: Float = 1f,
    /** Silence after it, in beats: a breath inside the phrase. */
    val pauseAfter: Double = 0.0,
)

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
    val figure: RhythmFigure = RhythmFigure.Flowing,
    /** Pace of this phrase against the base pulse: above 1 slower, below quicker. */
    val tempo: Double = 1.0,
) {
    val beats: Double get() = notes.sumOf { it.beats + it.pauseAfter } + restBeats
}

/**
 * The rhythmic figure a phrase moves in. A taqsim has no beat, but it has figures: even
 * flowing notes, a dotted swing, a long note and two short ones, a run gathering speed, a
 * hesitation on the note before the rest, or lingering on the jins' colour note.
 */
enum class RhythmFigure(val title: String) {
    Flowing("ровно"),
    Dotted("пунктир"),
    Gallop("долгая и две короткие"),
    Run("пассаж с разгоном"),
    Hesitant("задержка перед опорой"),
    Lingering("раскачка на цветной ступени"),
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
    /** The maqam, or null for an etude. */
    val maqam: Maqam?,
    val seed: Long,
    val phrases: List<TaqsimPhrase>,
    val detailed: Boolean,
    val form: TaqsimForm = TaqsimForm.Classic,
    val theme: Theme? = null,
    /** What it is played in, for titles: "Раст", "Uşşak". */
    val title: String = maqam?.name.orEmpty(),
) {
    /** Phrases where the theme is heard. */
    val themePhrases: List<Int> get() = phrases.indices.filter { phrases[it].themeNotes.isNotEmpty() }

    val notes: List<TaqsimNote> get() = phrases.flatMap { it.notes }
    val beats: Double get() = phrases.sumOf { it.beats }
}
