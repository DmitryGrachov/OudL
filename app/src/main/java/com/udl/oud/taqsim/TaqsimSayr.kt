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
    class Stations(val maqam: Maqam, val tonic: Int) {
        private val g = maqam.ghammaz
        private val lowerTop = maqam.lower.intervals.size
        private val start = Sayrs.of(maqam)?.start ?: 0

        private fun station(name: String, jins: com.udl.oud.fretboard.Jins, root: Int, focus: Int, low: Int, high: Int, descending: Boolean = false) =
            Station(name, maqam, tonic, descending, jins, root, focus, low, high)

        private val probe = station("", maqam.lower, 0, 0, -3, 9)

        /** Highest degree that stays on the neck. */
        private val ceiling = (9 downTo 7).first { probe.step(it) <= TOP_STEP || it == 7 }
        private val floor = (-3..0).first { probe.step(it) >= BOTTOM_STEP }

        val lower = station("", maqam.lower, 0, start, maxOf(-2, floor), maxOf(lowerTop + 1, start + 1)).let {
            it.copy(name = "${maqam.lower.name} на ${noteName(it, 0)}")
        }
        val ghammaz = station("", maqam.upper, g, g, 0, minOf(g + 2, ceiling)).let { it.copy(name = "гаммаз ${noteName(it, g)}") }
        val upper = station("", maqam.upper, g, g, g - 1, minOf(g + maqam.upper.intervals.size + 1, ceiling)).let {
            it.copy(name = "${maqam.upper.name} на ${noteName(it, g)}")
        }
        val peak = station("", maqam.upper, g, 7, g, ceiling).let { it.copy(name = "октава ${noteName(it, 7)}") }
        val back = station("", maqam.upperJins(true), g, g, minOf(lowerTop, g) - 1, 7, descending = maqam.hasDescent).let {
            it.copy(name = if (maqam.hasDescent) "спуск: ${maqam.upperJins(true).name} на ${noteName(it, g)}" else "спуск к ${noteName(it, g)}")
        }
        val qafla = lower.copy(focus = 0, name = "кафла на ${noteName(lower, 0)}")

        /** The sayr's usual modulations that fit on the neck, as stations. */
        val modulations: List<Station> = Sayrs.of(maqam)?.modulations.orEmpty().mapNotNull { m ->
            val target = Maqamat.all.firstOrNull { it.name == m.maqam } ?: return@mapNotNull null
            val t = tonic + m.offset
            val top = target.lower.intervals.size + 1
            val s = Station("", target, t, false, target.lower, 0, 0, -1, top)
            if (s.step(-1) < BOTTOM_STEP || s.step(top) > TOP_STEP) null else s.copy(name = "${target.name} на ${noteName(s, 0)}")
        }

        /** A stop for a "question" in the lower jins: its top, or the centre when that is higher. */
        val lowerQuestion: Int get() = if (start > 0) start + 1 else lowerTop
    }

    /** The sections of one taqsim on [maqam] at its usual tonic. */
    fun plan(maqam: Maqam, length: TaqsimLength, random: Random, tonic: Int = maqam.tonic): List<Section> {
        val s = Stations(maqam, tonic)
        val g = maqam.ghammaz
        val sections = mutableListOf<Section>()
        fun add(kind: SectionKind, station: Station, vararg parts: Pair<PhraseRole, Int>) {
            sections += Section(kind, station, parts.map { it.first }, parts.map { it.second.coerceIn(station.low, station.high) })
        }
        val target = length.phrases.random(random)
        val wantsPeak = length != TaqsimLength.Short || random.nextDouble() < 0.3

        // Opening: settle on the centre, then a question and its answer.
        add(
            SectionKind.Opening, s.lower,
            PhraseRole.Open to s.lower.focus,
            PhraseRole.Question to s.lowerQuestion,
            PhraseRole.Answer to s.lower.focus,
        )
        if (length == TaqsimLength.Long) add(SectionKind.Opening, s.lower, PhraseRole.Develop to s.lower.focus)

        add(SectionKind.Ghammaz, s.ghammaz, PhraseRole.Climb to g)

        val upperParts = mutableListOf(PhraseRole.Develop to g)
        if (length != TaqsimLength.Short) upperParts += listOf(PhraseRole.Question to minOf(g + 2, s.upper.high), PhraseRole.Answer to g)
        add(SectionKind.Upper, s.upper, *upperParts.toTypedArray())

        if (wantsPeak) add(SectionKind.Peak, s.peak, PhraseRole.Climb to 7, PhraseRole.Descend to g)

        // A written sayr may step aside to one of its modulations (two in a long taqsim) and come back.
        val moves = s.modulations.shuffled(random).take(
            when (length) {
                TaqsimLength.Short -> if (random.nextDouble() < 0.3) 1 else 0
                TaqsimLength.Medium -> 1
                TaqsimLength.Long -> 2
            },
        )
        moves.forEach { m ->
            val parts = if (length == TaqsimLength.Long) arrayOf(PhraseRole.Develop to 0, PhraseRole.Answer to 0) else arrayOf(PhraseRole.Develop to 0)
            add(SectionKind.Modulation, m, *parts)
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
        return sections
    }
}
