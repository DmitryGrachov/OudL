package com.udl.oud.talwin

import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Modulation
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.fretboard.Spelling
import com.udl.oud.jins.JinsEtude
import com.udl.oud.taqsim.PhraseRole
import com.udl.oud.taqsim.Section
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.Station
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.taqsim.TaqsimForm
import com.udl.oud.taqsim.TaqsimGenerator
import com.udl.oud.taqsim.TaqsimSayrs
import kotlin.math.abs
import kotlin.random.Random

/** A note the new jins changes: the maqam's own note on that letter, and what it becomes. */
data class NoteChange(val from: Spelling?, val to: Spelling, val quarterTones: Int, val inDescent: Boolean = false) {
    val text: String
        get() {
            val name = to.label(NoteLabels.Solfege)
            if (from == null) return "$name — новая нота"
            val way = if (quarterTones < 0) "ниже" else "выше"
            return "${from.label(NoteLabels.Solfege)} → $name (на ${amount(abs(quarterTones))} $way)" + if (inDescent) " — эта нота уже есть в макаме на спуске" else ""
        }

    private fun amount(q: Int) = when (q) {
        1 -> "¼ тона"
        2 -> "½ тона"
        3 -> "¾ тона"
        4 -> "тон"
        else -> "${q / 4.0} тона"
    }
}

/**
 * A talwin (تلوين, "colouring"): a move from a maqam to another jins for a while and back. The
 * new jins stands on a note of the maqam (the pivot); some of its notes are the maqam's own, the
 * others replace the maqam's notes on the same letters — those changes are what the ear hears.
 */
class Talwin(val from: Maqam, val move: Modulation) {
    val target: Maqam = Maqamat.all.first { it.name == move.maqam }
    val tonic: Int get() = from.tonic

    /** Grid step of the new jins' root. */
    val root: Int = from.tonic + move.offset

    private val probe = Station("", from, tonic, false, from.lower, 0, 0, -7, 14)

    /** The degree of the maqam the move stands on (−1 is the note under the tonic). */
    val pivot: Int = (-7..14).first { probe.step(it) == root }

    /** The new jins on its root, with a note of room on either side. */
    val station: Station = target.lower.intervals.size.let { top ->
        Station("", target, root, false, target.lower, 0, 0, -1, top + 1).let { it.copy(name = "${target.lower.name} на ${it.label(0)}") }
    }

    val title: String get() = "${target.name} на ${station.label(0)}"

    /** The maqam's notes, by pitch class, with their spelling. */
    private val home: Map<Int, Spelling> = from.notes(tonic)
        .associate { Math.floorMod(tonic + it.offset, 24) to it.spelling }

    /** Pitch classes the maqam takes only on the way down. */
    private val descent: Set<Int> = if (!from.hasDescent) emptySet() else
        from.notes(tonic, descending = true).map { Math.floorMod(tonic + it.offset, 24) }.toSet() - home.keys

    private val jinsDegrees = 0..target.lower.intervals.size

    /** What the new jins changes: each of its notes the maqam doesn't have. */
    val changes: List<NoteChange> = jinsDegrees.mapNotNull { d ->
        val pc = Math.floorMod(station.step(d), 24)
        if (pc in home) return@mapNotNull null
        val to = station.spelling(d)
        val same = home.entries.firstOrNull { it.value.letter == to.letter }
        val diff = same?.let { Math.floorMod(pc - it.key + 12, 24) - 12 } ?: 0
        NoteChange(same?.value, to, diff, inDescent = pc in descent)
    }

    /** The new jins' notes the maqam already has: where the ear still feels at home. */
    val common: List<String> = jinsDegrees.filter { Math.floorMod(station.step(it), 24) in home }.map(station::label)

    /** Which degree of the maqam that is, in words. */
    val where: String
        get() {
            val name = probe.label(pivot)
            val d = Math.floorMod(pivot, 7)
            val role = when {
                pivot == 0 -> "на самой тонике"
                pivot < 0 -> "под тоникой"
                d == from.ghammaz -> "на гаммазе"
                else -> "на ${ROMAN[d]} ступени"
            }
            return "$role ($name)"
        }

    /** The whole move in a few lines: where, what changes, what stays, how to get back. */
    val explanation: List<String>
        get() = buildList {
            add("Из ${from.name} ${where} встаёт джинс ${target.lower.name}.")
            if (changes.isEmpty()) {
                add("Ни одна нота не меняется — меняется центр: опора теперь ${station.label(0)}, и те же ноты звучат как ${target.lower.name}.")
            } else {
                add("Меняется: " + changes.joinToString("; ") { it.text } + ".")
            }
            if (common.isNotEmpty()) add("Общие ноты — мост между ними: ${common.joinToString(", ")}.")
            val back = changes.mapNotNull { it.from?.label(NoteLabels.Solfege) }
            add(
                "Назад: " + (if (back.isNotEmpty()) "верни ${back.joinToString(", ")}, " else "сдвинь опору обратно, ") +
                    "пройди через ${probe.label(pivot)} и приди на ${probe.label(0)}.",
            )
        }

    /**
     * The move as a short taqsim: home in the maqam, a climb (or a fall) to the pivot, the new
     * jins — a phrase, a question, an answer on its root — and back home to the qafla.
     */
    fun plan(random: Random): TaqsimSayrs.Plan {
        val s = TaqsimSayrs.Stations(from, tonic)
        val g = from.ghammaz
        val inUpper = pivot >= g
        val pivotStation = Station(
            "к ${probe.label(pivot)}", from, tonic, false,
            if (inUpper) from.upper else from.lower, if (inUpper) g else 0, pivot,
            minOf(pivot - 1, -1), maxOf(pivot + 1, TaqsimSayrs.Stations(from, tonic).lowerQuestion, if (inUpper) g + from.upper.intervals.size else from.lower.intervals.size),
        )
        val q = JinsEtude.questionRest(target.lower)
        val sections = mutableListOf(
            Section(SectionKind.Opening, s.lower, listOf(PhraseRole.Open), listOf(s.lower.focus)),
            Section(
                SectionKind.Ghammaz, pivotStation,
                listOf(if (pivot > 0) PhraseRole.Climb else if (pivot < 0) PhraseRole.Descend else PhraseRole.Question),
                listOf(if (pivot == 0) s.lowerQuestion else pivot),
            ),
            Section(SectionKind.Modulation, station, listOf(PhraseRole.Develop, PhraseRole.Question, PhraseRole.Answer), listOf(0, q, 0)),
            Section(SectionKind.Return, pivotStation, listOf(PhraseRole.Descend), listOf(if (pivot > 0) pivot else s.lowerQuestion)),
            Section(SectionKind.Qafla, s.qafla, listOf(PhraseRole.Cadence), listOf(0)),
        )
        return TaqsimSayrs.Plan(TaqsimForm.EarlyMove, sections)
    }

    fun generate(seed: Long): Taqsim = TaqsimGenerator(seed).generateFrom(from, plan(Random(seed xor 0x7A1L)), detailed = true)

    companion object {
        private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII")

        /** Every maqam's usual moves that make a talwin. */
        fun of(maqam: Maqam): List<Talwin> = Sayrs.of(maqam)?.modulations.orEmpty()
            .filter { m -> Maqamat.all.any { it.name == m.maqam } }
            .mapNotNull { runCatching { Talwin(maqam, it) }.getOrNull() }

        val maqamatWithMoves: List<Maqam> get() = Maqamat.all.filter { of(it).isNotEmpty() }
    }
}
