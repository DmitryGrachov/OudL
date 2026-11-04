package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.NoteLabels
import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds a taqsim from rules: the sayr plan gives the sections; each phrase is assembled from
 * the jins' motifs and changed the way players change them (sequence a step lower, inversion,
 * truncation, repeating the question's opening in the answer), led to its resting note, and
 * the best of many tries by the style rules is kept. The same [seed] gives the same taqsim.
 */
class TaqsimGenerator(private val seed: Long) {
    private val random = Random(seed)

    fun generate(maqam: Maqam, length: TaqsimLength, tonic: Int = maqam.tonic): Taqsim {
        val plan = TaqsimSayrs.plan(maqam, length, random, tonic)
        val phrases = mutableListOf<TaqsimPhrase>()
        var lastStep = plan.first().station.step(plan.first().station.focus)
        var question: List<Int>? = null
        for (section in plan) {
            section.roles.forEachIndexed { i, role ->
                val station = section.station
                val from = station.degreeNear(lastStep).coerceIn(station.low, station.high)
                val draft = bestPhrase(section, role, section.endsOn[i], from, phrases.lastOrNull(), question)
                if (role == PhraseRole.Question) question = draft.degrees
                if (role == PhraseRole.Answer) question = null
                val beats = Timing.beats(draft.degrees, role, section.kind, station, random)
                val notes = draft.degrees.mapIndexed { n, d -> TaqsimNote(station.step(d), station.spelling(d), d, beats[n]) }
                phrases += TaqsimPhrase(section.kind, role, station, draft.how, notes, Timing.rest(section.kind, role, random))
                lastStep = notes.last().step
            }
        }
        return Taqsim(maqam, seed, phrases, TaqsimSayrs.isDetailed(maqam))
    }

    /** A phrase being built: degrees in the station's scale and how it was made. */
    data class Draft(val degrees: List<Int>, val how: String)

    private fun bestPhrase(
        section: Section,
        role: PhraseRole,
        end: Int,
        from: Int,
        previous: TaqsimPhrase?,
        question: List<Int>?,
    ): Draft {
        val station = section.station
        val grammar = JinsGrammars.of(station.jins)
        val candidates = (0 until TRIES).mapNotNull { draft(station, grammar, role, end, from, question) }
            .filter { StyleRules.allowed(it.degrees, station) }
            .distinctBy { it.degrees }
        if (candidates.isEmpty()) return fallback(station, from, end)
        // A little noise on the score: near-equal tries take turns instead of the same one winning.
        val scored = candidates.map { it to StyleRules.score(it.degrees, station, grammar, role, end, previous, question) + random.nextDouble(-NOISE, NOISE) }
            .sortedByDescending { it.second }
        // Pick among the best few, not always the very best: that is where the variety comes from.
        val pool = scored.take(TOP)
        val weights = pool.mapIndexed { i, _ -> (TOP - i).toDouble() }
        var r = random.nextDouble() * weights.sum()
        pool.forEachIndexed { i, (d, _) ->
            r -= weights[i]
            if (r <= 0) return d
        }
        return pool.first().first
    }

    /** One attempt: an opening, one to three motifs moved and changed, then the way to [end]. */
    private fun draft(station: Station, grammar: JinsGrammar, role: PhraseRole, end: Int, from: Int, question: List<Int>?): Draft? {
        val notes = mutableListOf<Int>()
        val how = mutableListOf<String>()
        val root = station.jinsRoot

        // The answer takes up the question's opening and then goes its own way.
        if (role == PhraseRole.Answer && question != null && question.size >= 4) {
            val keep = random.nextInt(2, minOf(4, question.size - 1) + 1)
            notes += question.take(keep)
            how += "начало как в вопросе"
        } else {
            notes += from
            if (role == PhraseRole.Open && random.nextDouble() < 0.5 && from - 1 >= station.low) {
                notes.add(0, from - 1)
            }
        }

        val motifCount = when (role) {
            PhraseRole.Open, PhraseRole.Cadence -> random.nextInt(1, 3)
            else -> random.nextInt(1, 4)
        }
        repeat(motifCount) { k ->
            val last = k == motifCount - 1
            val use = when {
                k == 0 && role == PhraseRole.Open -> MotifUse.Open
                last && (role == PhraseRole.Cadence || role == PhraseRole.Answer || end == root) -> MotifUse.Close
                else -> MotifUse.Develop
            }
            val motif = pick(grammar.motifsFor(use)) ?: return null
            var shape = motif.degrees
            var name = "«${motif.name}»"
            // Transformations, rarer than the plain motif.
            when (random.nextInt(10)) {
                0, 1 -> {
                    shape = shape + shape.map { it - 1 }
                    name += " с секвенцией вниз"
                }
                2 -> {
                    shape = shape.map { 2 * shape.first() - it }
                    name += " в обращении"
                }
                3 -> if (shape.size > 3) {
                    shape = shape.dropLast(1)
                    name += " укороченный"
                }
            }
            // Place it: on the jins' root, or moved so it starts next to where the line is.
            val here = notes.last()
            val shift = if (random.nextDouble() < 0.5) root else here - shape.first() + listOf(-1, 0, 1).random(random)
            val placed = shape.map { it + shift }
            connect(notes, placed.first())
            notes += placed
            how += name
        }

        approach(notes, end, JinsGrammars.of(station.jins).fromAbove)
        // Same note twice in a row is held, not repeated, unless it is a motif's own repeat.
        val merged = notes.fold(mutableListOf<Int>()) { acc, d -> if (acc.size >= 2 && acc.last() == d && acc[acc.size - 2] == d) acc else acc.apply { add(d) } }
        if (merged.size < 3 || merged.size > MAX_NOTES) return null
        return Draft(merged, how.joinToString(", "))
    }

    /** Steps from the line's last note to [target], so motifs join without jumps. */
    private fun connect(notes: MutableList<Int>, target: Int) {
        var d = notes.last()
        while (abs(target - d) > 2) {
            d += if (target > d) 1 else -1
            notes += d
        }
        if (notes.last() == target) notes.removeAt(notes.lastIndex)
    }

    /** Leads to [end]: stepwise, often through the note above (or below) it, as the jins does. */
    private fun approach(notes: MutableList<Int>, end: Int, fromAbove: Double) {
        if (notes.last() == end) return
        val above = random.nextDouble() < fromAbove
        val via = if (above) end + 1 else end - 1
        var d = notes.last()
        val target = if (abs(d - end) <= 1) end else via
        while (d != target) {
            d += if (target > d) 1 else -1
            notes += d
        }
        if (notes.last() != end) notes += end
    }

    private fun pick(motifs: List<Motif>): Motif? {
        if (motifs.isEmpty()) return null
        var r = random.nextDouble() * motifs.sumOf { it.weight }
        for (m in motifs) {
            r -= m.weight
            if (r <= 0) return m
        }
        return motifs.last()
    }

    /** When no try passes the rules: a plain stepwise line to the resting note. */
    private fun fallback(station: Station, from: Int, end: Int): Draft {
        val notes = mutableListOf(from)
        approach(notes, end, 0.5)
        if (notes.size < 3) notes.add(0, (from + 1).coerceAtMost(station.high))
        return Draft(notes, "простой ход к ${station.spelling(end).label(NoteLabels.Solfege)}")
    }

    private companion object {
        const val TRIES = 40
        const val TOP = 6
        const val NOISE = 1.2
        const val MAX_NOTES = 16
    }
}

/**
 * The style rules a phrase is judged by. [allowed] throws out what a teacher would never play
 * here; [score] prefers steps, a leap turned back, the jins' colour note, resting on pillars,
 * and something new against the phrase before.
 */
object StyleRules {
    fun allowed(degrees: List<Int>, station: Station): Boolean =
        degrees.all { it in station.low..station.high } &&
            degrees.zipWithNext().all { (a, b) -> abs(a - b) <= 3 } &&
            // No note four times running.
            degrees.windowed(4).none { w -> w.all { it == w[0] } }

    fun score(
        degrees: List<Int>,
        station: Station,
        grammar: JinsGrammar,
        role: PhraseRole,
        end: Int,
        previous: TaqsimPhrase?,
        question: List<Int>?,
    ): Double {
        var s = 0.0
        val moves = degrees.zipWithNext { a, b -> b - a }
        s -= moves.count { abs(it) == 2 } * 0.5
        s -= moves.count { abs(it) == 3 } * 1.5
        // A leap is answered by a step the other way.
        moves.zipWithNext().forEach { (a, b) -> if (abs(a) >= 2 && (b == 0 || b.sign() == a.sign())) s -= 1.0 }
        // The jins' colour is heard.
        val colour = grammar.characteristic.map { it + station.jinsRoot }
        if (degrees.any { it in colour }) s += 2.0
        // Time spent near the focus and inside the jins.
        s += degrees.count { abs(it - station.focus) <= 2 } * 0.15
        s += degrees.count { it in station.jinsDegrees } * 0.1
        // Ends where it should, and the note before it leads in by step.
        if (degrees.last() == end) s += 3.0
        if (degrees.size >= 2 && abs(degrees[degrees.size - 2] - end) == 1) s += 1.0
        // Length that suits the role.
        val ideal = when (role) {
            PhraseRole.Open -> 5
            PhraseRole.Cadence -> 6
            PhraseRole.Climb, PhraseRole.Descend -> 8
            else -> 9
        }
        s -= abs(degrees.size - ideal) * 0.25
        // Climbs go up, descents come down.
        val rise = degrees.max() - degrees.first()
        if (role == PhraseRole.Climb && rise < 2) s -= 2.0
        if (role == PhraseRole.Descend && degrees.first() - degrees.last() < 2) s -= 1.5
        // Something new against the phrase before; the answer keeps the question's start.
        previous?.let { p -> if (p.notes.map { it.degree } == degrees) s -= 5.0 }
        if (role == PhraseRole.Answer && question != null && degrees.take(2) == question.take(2)) s += 1.5
        return s
    }

    private fun Int.sign() = if (this > 0) 1 else if (this < 0) -1 else 0
}

/**
 * Free rhythm in beats of the base pulse: runs go quick, the colour note and the first note
 * may linger, the resting note at the end is long, the last qafla longest; a little rubato.
 */
object Timing {
    fun beats(degrees: List<Int>, role: PhraseRole, kind: SectionKind, station: Station, random: Random): List<Double> {
        val colour = JinsGrammars.of(station.jins).characteristic.map { it + station.jinsRoot }
        return degrees.mapIndexed { i, d ->
            val base = when {
                i == degrees.lastIndex -> if (kind == SectionKind.Qafla) 8.0 else random.nextDouble(3.0, 5.5)
                i == 0 && (role == PhraseRole.Open || random.nextDouble() < 0.25) -> random.nextDouble(1.5, 3.0)
                d in colour && random.nextDouble() < 0.35 -> random.nextDouble(1.5, 2.2)
                inRun(degrees, i) -> 0.7
                else -> 1.0
            }
            base * random.nextDouble(0.9, 1.1)
        }
    }

    /** Pause after a phrase, longer after a section's last one. */
    fun rest(kind: SectionKind, role: PhraseRole, random: Random): Double = when {
        kind == SectionKind.Qafla -> 0.0
        role == PhraseRole.Question -> random.nextDouble(1.0, 1.8)
        else -> random.nextDouble(1.8, 3.2)
    }

    /** Inside three or more steps the same way. */
    private fun inRun(d: List<Int>, i: Int): Boolean {
        if (i == 0 || i >= d.lastIndex) return false
        val a = d[i] - d[i - 1]
        val b = d[i + 1] - d[i]
        return abs(a) == 1 && a == b
    }
}
