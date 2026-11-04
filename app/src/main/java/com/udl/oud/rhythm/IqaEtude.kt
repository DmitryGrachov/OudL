package com.udl.oud.rhythm

import com.udl.oud.audio.Iqa
import com.udl.oud.audio.Stroke
import com.udl.oud.fretboard.Maqam
import com.udl.oud.taqsim.PhraseRole
import com.udl.oud.taqsim.TaqsimGenerator
import com.udl.oud.taqsim.TaqsimLength
import com.udl.oud.taqsim.TaqsimNote
import com.udl.oud.taqsim.TaqsimPhrase
import kotlin.math.roundToInt

/** A note of a metered etude: which, on which step of the whole etude, for how many steps, in which phrase. */
data class EtudeNote(val note: TaqsimNote, val step: Int, val length: Int, val phrase: Int)

/** A phrase of the etude: what it does, and the cycles it spans. */
data class EtudePhrase(val title: String, val firstCycle: Int, val cycles: Int)

/** A melody in a maqam laid on an iqa': [cycles] cycles, the notes on its strokes. */
data class IqaEtude(val iqa: Iqa, val maqam: Maqam, val cycles: Int, val notes: List<EtudeNote>, val phrases: List<EtudePhrase>) {
    val stepsPerCycle: Int get() = iqa.strokes.size

    fun notesIn(cycle: Int): List<IndexedValue<EtudeNote>> =
        notes.withIndex().filter { it.value.step / stepsPerCycle == cycle }
}

/**
 * Melodies in rhythm: the taqsim generator's phrases (motifs, rests, the theme, the qafla) given
 * the iqa's time instead of a free one. A phrase spans about two bars; its notes go on the
 * cycle's strokes — the dums and teks, and the rests between when there are more notes than
 * strokes — and its last note lands on the last dum and rings to the end of the phrase.
 */
object IqaEtudes {
    /** Phrases in an etude: an opening, a question, its answer, the qafla. */
    const val PHRASES = 4

    /** About two bars of 4/4 (eight quarters) per phrase, in whole cycles. */
    fun cyclesPerPhrase(iqa: Iqa): Int {
        val quarters = if (iqa.beatUnit == "♪") iqa.beats / 2.0 else iqa.beats.toDouble()
        return (8 / quarters).roundToInt().coerceIn(1, 4)
    }

    fun generate(iqa: Iqa, maqam: Maqam, seed: Long): IqaEtude {
        val taqsim = TaqsimGenerator(seed).generate(maqam, TaqsimLength.Short)
        val all = taqsim.phrases
        val question = all.indexOfFirst { it.role == PhraseRole.Question }.takeIf { it > 0 }
        val picked = listOfNotNull(
            0,
            question,
            question?.let { q -> (q + 1 until all.size).firstOrNull { all[it].role == PhraseRole.Answer } },
            all.lastIndex,
        ).distinct().map { all[it] }
        val per = cyclesPerPhrase(iqa)
        val n = iqa.strokes.size
        val notes = mutableListOf<EtudeNote>()
        val phrases = picked.mapIndexed { p, phrase ->
            val start = p * per * n
            notes += place(iqa, phrase, per).map { it.copy(step = it.step + start, phrase = p) }
            EtudePhrase("${phrase.section.title} · ${phrase.role.title}", p * per, per)
        }
        return IqaEtude(iqa, maqam, picked.size * per, notes, phrases)
    }

    /** One phrase over [cycles] cycles, steps counted from the phrase's start. */
    internal fun place(iqa: Iqa, phrase: TaqsimPhrase, cycles: Int): List<EtudeNote> {
        val n = iqa.strokes.size
        val span = n * cycles
        val strokes = (0 until span).filter { iqa.strokes[it % n] != Stroke.Rest }
        // The phrase comes to rest on the last dum of its last cycle (or its last stroke).
        val lastCycle = (cycles - 1) * n until span
        val end = lastCycle.lastOrNull { iqa.strokes[it % n] == Stroke.Dum } ?: strokes.last()
        var slots = strokes.filter { it <= end }.toMutableList()
        var source = phrase.notes
        if (source.size > slots.size) {
            // More notes than strokes: the rests take notes too, the beats first.
            val rests = (0 until end).filter { it !in slots }.sortedBy { if (it % iqa.stepsPerBeat == 0) 0 else 1 }
            slots = (slots + rests.take(source.size - slots.size)).sorted().toMutableList()
        }
        if (source.size > slots.size) {
            // Still too many: keep the first and the last, thin the middle evenly.
            val keep = slots.size
            source = (0 until keep).map { i -> source[(i * (source.size - 1).toDouble() / (keep - 1).coerceAtLeast(1)).roundToInt()] }
        }
        // Fewer notes than strokes: spread them, the last always on the end; the others ring through.
        val chosen = if (source.size >= slots.size) slots else {
            val k = source.size
            (0 until k).map { i -> slots[if (k == 1) slots.lastIndex else (i * (slots.size - 1).toDouble() / (k - 1)).roundToInt()] }
                .distinct().let { if (it.size < k) slots.takeLast(k) else it }
        }
        return source.indices.map { i ->
            val at = chosen[i]
            val next = chosen.getOrNull(i + 1) ?: span
            EtudeNote(source[i], at, next - at, 0)
        }
    }
}
