package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Sayrs
import kotlin.random.Random

/**
 * Plans the course of a taqsim through the maqam's stations. Every maqam gets the basic sayr
 * read from its structure: around the tonic → up to the ghammaz → the upper jins (→ the octave)
 * → back down → qafla. A maqam with a written sayr also starts where development centres and
 * may move to its usual modulations on the way.
 */
object TaqsimSayrs {
    /** Highest grid step a phrase goes to: about the top of the neck on the treble course. */
    private const val TOP_STEP = 132

    /** Lowest grid step: the bass course of a C–C oud. */
    private const val BOTTOM_STEP = 72

    fun isDetailed(maqam: Maqam): Boolean = Sayrs.of(maqam) != null

    private fun noteName(station: Station, degree: Int) = station.spelling(degree).label(NoteLabels.Solfege)

    /** The stations of [maqam] on [tonic]. */
    class Stations(val maqam: Maqam, val tonic: Int) : SayrStations {
        override val ghammazDegree: Int get() = maqam.ghammaz
        private val g = maqam.ghammaz
        private val lowerTop = maqam.lower.intervals.size
        private val start = Sayrs.of(maqam)?.start ?: 0

        private fun station(name: String, jins: com.udl.oud.fretboard.Jins, root: Int, focus: Int, low: Int, high: Int, descending: Boolean = false) =
            Station(name, maqam, tonic, descending, jins, root, focus, low, high)

        private val probe = station("", maqam.lower, 0, 0, -3, 9)

        /** Highest degree that stays on the neck. */
        private val ceiling = (9 downTo 7).first { probe.step(it) <= TOP_STEP || it == 7 }
        private val floor = (-3..0).first { probe.step(it) >= BOTTOM_STEP }

        override val lower = station("", maqam.lower, 0, start, maxOf(-2, floor), maxOf(lowerTop + 1, start + 1)).let {
            it.copy(name = "${maqam.lower.name} на ${noteName(it, 0)}")
        }
        override val ghammaz = station("", maqam.upper, g, g, 0, minOf(g + 2, ceiling)).let { it.copy(name = "гаммаз ${noteName(it, g)}") }
        override val upper = station("", maqam.upper, g, g, g - 1, minOf(g + maqam.upper.intervals.size + 1, ceiling)).let {
            it.copy(name = "${maqam.upper.name} на ${noteName(it, g)}")
        }
        override val peak = station("", maqam.upper, g, 7, g, ceiling).let { it.copy(name = "октава ${noteName(it, 7)}") }
        override val back = station("", maqam.upperJins(true), g, g, minOf(lowerTop, g) - 1, 7, descending = maqam.hasDescent).let {
            it.copy(name = if (maqam.hasDescent) "спуск: ${maqam.upperJins(true).name} на ${noteName(it, g)}" else "спуск к ${noteName(it, g)}")
        }
        override val qafla = lower.copy(focus = 0, name = "кафла на ${noteName(lower, 0)}")

        /** The sayr's usual modulations that fit on the neck, as stations. */
        override val modulations: List<Station> = Sayrs.of(maqam)?.modulations.orEmpty().mapNotNull { m ->
            val target = Maqamat.all.firstOrNull { it.name == m.maqam } ?: return@mapNotNull null
            val t = tonic + m.offset
            val top = target.lower.intervals.size + 1
            val s = Station("", target, t, false, target.lower, 0, 0, -1, top)
            if (s.step(-1) < BOTTOM_STEP || s.step(top) > TOP_STEP) null else s.copy(name = "${target.name} на ${noteName(s, 0)}")
        }

        /** A stop for a "question" in the lower jins: its top, or the centre when that is higher. */
        override val lowerQuestion: Int get() = if (start > 0) start + 1 else lowerTop
    }


    /** A plan: the form chosen and the sections in order. */
    data class Plan(val form: TaqsimForm, val sections: List<Section>)

    fun plan(maqam: Maqam, length: TaqsimLength, random: Random, tonic: Int = maqam.tonic): Plan {
        val s = Stations(maqam, tonic)
        return planFrom(s, length, random) { TaqsimForm.choose(length, s.modulations.size, random) }
    }

    /**
     * The sections of a taqsim over any set of stations,
     * in the form [chooseForm] picks.
     */
    fun planFrom(s: SayrStations, length: TaqsimLength, random: Random, chooseForm: () -> TaqsimForm): Plan {
        val g = s.ghammazDegree
        val sections = mutableListOf<Section>()
        fun add(kind: SectionKind, station: Station, vararg parts: Pair<PhraseRole, Int>) {
            sections += Section(kind, station, parts.map { it.first }, parts.map { it.second.coerceIn(station.low, station.high) })
        }
        val target = length.phrases.random(random)
        val wantsPeak = length != TaqsimLength.Short || random.nextDouble() < 0.3
        val form = chooseForm()

        // Opening: settle on the centre with a question and its answer; some forms arrive there
        // from the ghammaz or from above first.
        when (form.opening) {
            TaqsimForm.Opening.Low -> add(SectionKind.Opening, s.lower, PhraseRole.Open to s.lower.focus)
            TaqsimForm.Opening.Ghammaz -> {
                add(SectionKind.Opening, s.ghammaz, PhraseRole.Open to g)
                add(SectionKind.Opening, s.lower, PhraseRole.Descend to s.lower.focus)
            }
            TaqsimForm.Opening.Above -> {
                add(SectionKind.Opening, s.upper, PhraseRole.Open to g)
                add(SectionKind.Opening, s.lower, PhraseRole.Descend to s.lower.focus)
            }
        }
        add(SectionKind.Opening, s.lower, PhraseRole.Question to s.lowerQuestion, PhraseRole.Answer to s.lower.focus)
        if (length == TaqsimLength.Long) add(SectionKind.Opening, s.lower, PhraseRole.Develop to s.lower.focus)

        val moves = s.modulations.shuffled(random).take(form.moves(length, random))
        fun move(i: Int) = moves.getOrNull(i)?.let { m ->
            val parts = if (length == TaqsimLength.Long) arrayOf(PhraseRole.Develop to 0, PhraseRole.Answer to 0) else arrayOf(PhraseRole.Develop to 0)
            add(SectionKind.Modulation, m, *parts)
        }
        fun upper(full: Boolean) {
            val parts = mutableListOf(PhraseRole.Develop to g)
            if (full && length != TaqsimLength.Short) parts += listOf(PhraseRole.Question to minOf(g + 2, s.upper.high), PhraseRole.Answer to g)
            add(SectionKind.Upper, s.upper, *parts.toTypedArray())
        }
        fun peak() = if (wantsPeak) add(SectionKind.Peak, s.peak, PhraseRole.Climb to 7, PhraseRole.Descend to g) else Unit

        add(SectionKind.Ghammaz, s.ghammaz, PhraseRole.Climb to g)
        when (form.middle) {
            TaqsimForm.Middle.Straight -> {
                upper(full = true)
                peak()
                move(0)
            }
            TaqsimForm.Middle.Revisit -> {
                upper(full = false)
                // Back down to where it started, a new question and answer, then up again.
                add(SectionKind.Revisit, s.lower, PhraseRole.Question to s.lowerQuestion, PhraseRole.Answer to s.lower.focus)
                add(SectionKind.Ghammaz, s.ghammaz, PhraseRole.Climb to g)
                peak()
                move(0)
            }
            TaqsimForm.Middle.EarlyMove -> {
                move(0)
                upper(full = true)
                peak()
            }
            TaqsimForm.Middle.TwoMoves -> {
                upper(full = true)
                move(0)
                // Home in the maqam between the two modulations.
                upper(full = false)
                move(1)
                peak()
            }
        }

        add(SectionKind.Return, s.back, PhraseRole.Descend to g)
        add(SectionKind.Return, s.lower, PhraseRole.Descend to s.lowerQuestion)

        // Fill up to the chosen length with more development in the lower or upper jins.
        var count = sections.sumOf { it.roles.size } + 1
        while (count < target) {
            val atUpper = random.nextBoolean()
            val extra = if (atUpper) Section(SectionKind.Upper, s.upper, listOf(PhraseRole.Develop), listOf(g))
            else Section(SectionKind.Opening, s.lower, listOf(PhraseRole.Develop), listOf(s.lower.focus))
            // Extra lower development goes after the opening, upper after the first upper section.
            val at = sections.indexOfFirst { it.kind == (if (atUpper) SectionKind.Upper else SectionKind.Opening) } + 1
            sections.add(at, extra)
            count++
        }

        add(SectionKind.Qafla, s.qafla, PhraseRole.Cadence to 0)
        return Plan(form, sections)
    }
}

/**
 * The shape of a taqsim's course: how it opens and what happens in the middle. Players don't
 * always climb straight from the tonic: some open at the ghammaz or from above and come down,
 * some go back to the start before rising again, modulate early, or move away twice.
 */
enum class TaqsimForm(val title: String, val opening: Opening, val middle: Middle, private val weight: Double) {
    Classic("снизу вверх", Opening.Low, Middle.Straight, 3.0),
    FromGhammaz("начало с гаммаза", Opening.Ghammaz, Middle.Straight, 1.5),
    FromAbove("начало сверху, спуск к тонике", Opening.Above, Middle.Straight, 1.2),
    Revisit("с возвращением к началу посередине", Opening.Low, Middle.Revisit, 1.5),
    EarlyMove("ранний переход", Opening.Low, Middle.EarlyMove, 1.2),
    GhammazAndBack("с гаммаза, с возвращением", Opening.Ghammaz, Middle.Revisit, 0.8),
    TwoMoves("два перехода", Opening.Low, Middle.TwoMoves, 1.2),
    ;

    enum class Opening { Low, Ghammaz, Above }
    enum class Middle { Straight, Revisit, EarlyMove, TwoMoves }

    /** How many modulations this form takes. */
    fun moves(length: TaqsimLength, random: Random): Int = when {
        middle == Middle.TwoMoves -> 2
        middle == Middle.EarlyMove -> 1
        length == TaqsimLength.Short -> if (random.nextDouble() < 0.3) 1 else 0
        else -> 1
    }

    companion object {
        /** A form that fits the length and the modulations the maqam has. */
        fun choose(length: TaqsimLength, modulations: Int, random: Random): TaqsimForm {
            val fits = entries.filter { f ->
                when {
                    f.middle == Middle.TwoMoves -> modulations >= 2 && length != TaqsimLength.Short
                    f.middle == Middle.EarlyMove -> modulations >= 1
                    f.middle == Middle.Revisit -> length != TaqsimLength.Short
                    else -> true
                }
            }
            var r = random.nextDouble() * fits.sumOf { it.weight }
            for (f in fits) {
                r -= f.weight
                if (r <= 0) return f
            }
            return fits.first()
        }
    }
}

/**
 * The stations a sayr goes through: the lower jins on the tonic, the climb to the ghammaz (or
 * güçlü), the upper jins, the octave, the way down, the qafla, and the usual modulations.
 */
interface SayrStations {
    val ghammazDegree: Int
    val lower: Station
    val ghammaz: Station
    val upper: Station
    val peak: Station
    val back: Station
    val qafla: Station
    val modulations: List<Station>

    /** Where a question in the lower jins rests. */
    val lowerQuestion: Int
}
