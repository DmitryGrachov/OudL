package com.udl.oud.taqsim

import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Spelling
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.jins.JinsCatalog
import kotlin.math.abs

/** A phrase of free playing: notes between two pauses. */
data class FreePhrase(val notes: List<HeardNote>) {
    val last: HeardNote get() = notes.last()
}

/** The jins a phrase is heard in, on [root], and how well it fits (share of the time inside it). */
data class JinsFit(val jins: Jins, val root: Int, val fit: Double)

/** How well a maqam on its usual tonic explains the whole playing. */
data class MaqamFit(val maqam: Maqam, val score: Double, val share: Double)

/** A phrase with what it was: its jins, where it stood in the maqam's sayr, where it rested. */
data class FreePhraseReview(val phrase: FreePhrase, val jins: JinsFit?, val place: String, val rest: String)

data class FreeReview(
    val maqam: Maqam?,
    val fits: List<MaqamFit>,
    val phrases: List<FreePhraseReview>,
    val points: List<AnswerPoint>,
    val tips: List<String>,
)

/**
 * Reading free playing as a teacher would: split it into phrases at the pauses, hear which jins
 * each phrase is in and where it rests, find the maqam that explains it all best (on its usual
 * tonic), follow the sayr through the phrases, and say what went well and what to try.
 */
object FreePlay {
    /** A pause this long ends a phrase. */
    const val PHRASE_GAP_MS = 1100L

    private fun pc(step: Int) = Math.floorMod(step, 24)
    private fun weight(n: HeardNote) = maxOf(n.durationMs, 80L).toDouble()
    /** A note's name as maqam music spells it on its own: E♭ rather than D♯. */
    fun name(step: Int) = Spelling.of(step).label(NoteLabels.Solfege)

    fun phrases(notes: List<HeardNote>, gapMs: Long = PHRASE_GAP_MS): List<FreePhrase> {
        if (notes.isEmpty()) return emptyList()
        val out = mutableListOf(mutableListOf(notes.first()))
        notes.zipWithNext().forEach { (a, b) ->
            if (b.startMs - (a.startMs + a.durationMs) >= gapMs) out += mutableListOf(b) else out.last() += b
        }
        return out.map { FreePhrase(it) }
    }

    /** Pitch classes of a jins on [root] (its own notes, 0..n). */
    private fun jinsClasses(j: Jins, root: Int): Set<Int> {
        val s = JinsCatalog.station(j, root)
        return (0..j.intervals.size).map { pc(s.step(it)) }.toSet()
    }

    /**
     * The jins of a phrase: tried on the notes it rests on and its lowest notes as root; a fit is
     * the time spent inside the jins, helped by resting on its root and hearing its colour.
     */
    fun jinsOf(phrase: FreePhrase): JinsFit? {
        val total = phrase.notes.sumOf(::weight)
        val roots = (listOf(phrase.last.step) + phrase.notes.sortedBy { it.step }.take(3).map { it.step }).distinct()
        return JinsCatalog.ajnas.flatMap { j ->
            roots.map { r ->
                val classes = jinsClasses(j, r)
                val station = JinsCatalog.station(j, r)
                // The note under the root is its neighbour, met on the way home — half inside.
                val below = pc(station.step(-1))
                val inside = phrase.notes.sumOf { n ->
                    when (pc(n.step)) {
                        in classes -> weight(n)
                        below -> weight(n) / 2
                        else -> 0.0
                    }
                }
                val colour = JinsGrammars.of(j).characteristic.map { pc(station.step(it)) }
                var score = inside / total
                // Where the phrase rests is the strongest sign of its jins.
                if (pc(phrase.last.step) == pc(r)) score += 0.3
                if (phrase.notes.any { pc(it.step) in colour }) score += 0.05
                // A small jins explaining a big phrase is no explanation.
                score -= 0.04 * (phrase.notes.map { pc(it.step) }.toSet() - classes - below).size
                JinsFit(j, r, score)
            }
        }.maxByOrNull { it.fit }
    }

    /** All the maqamat on their usual tonics, best fitting first. */
    fun maqamFits(phrases: List<FreePhrase>): List<MaqamFit> {
        val notes = phrases.flatMap { it.notes }
        if (notes.isEmpty()) return emptyList()
        val total = notes.sumOf(::weight)
        return Maqamat.all.map { m ->
            val classes = (m.notes(m.tonic) + m.notes(m.tonic, descending = true)).map { pc(m.tonic + it.offset) }.toSet()
            val inside = notes.filter { pc(it.step) in classes }.sumOf(::weight) / total
            val tonic = pc(m.tonic)
            val ghammaz = pc(m.tonic + m.notes()[m.ghammaz].offset)
            val colour = JinsGrammars.of(m.lower).characteristic.map { pc(m.tonic + m.notes()[it].offset) }
            var score = inside * 10
            // Where the phrases rest says most about the maqam: home on its tonic, a stop on its ghammaz.
            phrases.forEach { p ->
                when (pc(p.last.step)) {
                    tonic -> score += 1.5
                    ghammaz -> score += 0.6
                }
            }
            if (pc(phrases.last().last.step) == tonic) score += 2.0
            if (notes.any { pc(it.step) in colour }) score += 1.0
            MaqamFit(m, score, inside)
        }.sortedByDescending { it.score }
    }

    /** Where a phrase stands in [maqam]'s sayr, from its jins and root. */
    fun place(maqam: Maqam, fit: JinsFit?): String {
        fit ?: return "не разобрать"
        val tonic = pc(maqam.tonic)
        val ghammaz = pc(maqam.tonic + maqam.notes()[maqam.ghammaz].offset)
        val root = pc(fit.root)
        val on = name(fit.root)
        return when {
            root == tonic && fit.jins == maqam.lower -> "нижний джинс ${fit.jins.name} на $on"
            root == ghammaz && (fit.jins == maqam.upper || fit.jins == maqam.upperJins(true)) -> "верхний джинс ${fit.jins.name} на $on"
            else -> Sayrs.of(maqam)?.modulations.orEmpty().firstNotNullOfOrNull { mod ->
                val target = Maqamat.all.firstOrNull { it.name == mod.maqam } ?: return@firstNotNullOfOrNull null
                if (target.lower == fit.jins && pc(maqam.tonic + mod.offset) == root) "переход: ${target.name} на $on" else null
            } ?: "${fit.jins.name} на $on — вне обычного сайра"
        }
    }

    fun review(notes: List<HeardNote>, expected: Maqam?): FreeReview {
        val phrases = phrases(notes)
        val fits = maqamFits(phrases)
        val maqam = expected ?: fits.firstOrNull()?.maqam
        if (maqam == null || phrases.isEmpty()) {
            return FreeReview(null, fits, emptyList(), listOf(AnswerPoint(false, "Пока ничего не сыграно")), emptyList())
        }
        val tonic = pc(maqam.tonic)
        val ghammaz = pc(maqam.tonic + maqam.notes()[maqam.ghammaz].offset)
        val classes = (maqam.notes(maqam.tonic) + maqam.notes(maqam.tonic, descending = true)).map { pc(maqam.tonic + it.offset) }.toSet()
        val reviewed = phrases.map { p ->
            val j = jinsOf(p)
            val rest = when (pc(p.last.step)) {
                tonic -> "${name(p.last.step)} (тоника)"
                ghammaz -> "${name(p.last.step)} (гаммаз)"
                else -> name(p.last.step)
            }
            FreePhraseReview(p, j, place(maqam, j), rest)
        }

        val points = mutableListOf<AnswerPoint>()
        val all = phrases.flatMap { it.notes }
        val outside = all.filter { pc(it.step) !in classes }
        points += if (outside.isEmpty()) {
            AnswerPoint(true, "Всё сыграно в ${maqam.name}")
        } else {
            val names = outside.map { name(it.step) }.distinct().take(4)
            AnswerPoint(false, "Мимо ${maqam.name}: ${names.joinToString()} — ${outside.size} из ${all.size} нот")
        }
        val colour = JinsGrammars.of(maqam.lower).characteristic.map { pc(maqam.tonic + maqam.notes()[it].offset) }
        points += if (all.any { pc(it.step) in colour }) {
            AnswerPoint(true, "Цветная ступень звучала")
        } else {
            AnswerPoint(false, "Не прозвучала цветная ступень ${colour.joinToString { name(maqam.tonic + Math.floorMod(it - tonic, 24)) }}")
        }
        val reachedGhammaz = reviewed.any { it.place.startsWith("верхний") } || all.any { pc(it.step) == ghammaz }
        points += if (reachedGhammaz) AnswerPoint(true, "Дошёл до гаммаза и верхнего джинса") else AnswerPoint(false, "Всё внизу: гаммаз и верхний джинс не прозвучали")
        points += if (pc(phrases.last().last.step) == tonic) {
            AnswerPoint(true, "Закончил на тонике ${name(maqam.tonic)}")
        } else {
            AnswerPoint(false, "Закончил на ${name(phrases.last().last.step)}, а не на тонике ${name(maqam.tonic)}")
        }

        val tips = mutableListOf<String>()
        val moves = all.zipWithNext { a, b -> abs(b.step - a.step) }
        if (moves.isNotEmpty() && moves.count { it > 6 } > moves.size / 3) tips += "Много больших скачков — в таксиме чаще идут по соседним ступеням."
        if (phrases.size == 1 && all.size > 12) tips += "Одна длинная фраза: делай паузы — фразы должны дышать."
        if (phrases.size >= 3 && phrases.none { pc(it.last.step) == ghammaz }) tips += "Попробуй «вопрос»: фразу, которая останавливается на гаммазе ${name(maqam.tonic + maqam.notes()[maqam.ghammaz].offset)}."
        if (phrases.size >= 3 && reviewed.none { it.place.startsWith("переход") } && Sayrs.of(maqam) != null) {
            val m = Sayrs.of(maqam)!!.modulations.first()
            tips += "Следующий шаг — переход: например, в ${m.maqam} на ${name(maqam.tonic + m.offset)}."
        }
        val first = fits.firstOrNull()?.maqam
        if (expected != null && first != null && first != expected) tips += "По звучанию больше похоже на ${first.name}, чем на ${expected.name}."
        if (tips.isEmpty()) tips += "Хорошо выстроено. Попробуй дольше задерживаться на опорах."
        return FreeReview(maqam, fits, reviewed, points, tips)
    }
}
