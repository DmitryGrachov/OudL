package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.NoteLabels
import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds a taqsim from rules: the sayr plan gives the form and the sections; a theme is chosen
 * and carried through them; each phrase is assembled from the jins' motifs and changed the way
 * players change them (sequence a step lower, inversion, truncation, repeating the question's
 * opening in the answer), led to its resting note, and the best of many tries by the style rules
 * is kept. The same [seed] gives the same taqsim.
 */
class TaqsimGenerator(private val seed: Long) {
    private val random = Random(seed)

    fun generate(maqam: Maqam, length: TaqsimLength, tonic: Int = maqam.tonic): Taqsim {
        val plan = TaqsimSayrs.plan(maqam, length, random, tonic)
        val theme = makeTheme(maqam)
        val phrases = mutableListOf<TaqsimPhrase>()
        var lastStep = plan.sections.first().station.step(plan.sections.first().station.focus)
        var question: List<Int>? = null
        for (section in plan.sections) {
            section.roles.forEachIndexed { i, role ->
                val station = section.station
                val from = station.degreeNear(lastStep).coerceIn(station.low, station.high)
                val themeUse = themeFor(theme, section.kind, role, phrases.count { it.themeNotes.isNotEmpty() })
                val draft = bestPhrase(section, role, section.endsOn[i], from, phrases.lastOrNull(), question, themeUse)
                if (role == PhraseRole.Question) question = draft.degrees
                if (role == PhraseRole.Answer) question = null
                val themeBeats = draft.theme.withIndex().associate { (k, index) -> index to theme.rhythm[k % theme.rhythm.size] }
                val beats = Timing.beats(draft.degrees, role, section.kind, station, random, themeBeats)
                val notes = draft.degrees.mapIndexed { n, d -> TaqsimNote(station.step(d), station.spelling(d), d, beats[n]) }
                phrases += TaqsimPhrase(
                    section.kind, role, station, draft.how, notes, Timing.rest(section.kind, role, random), draft.theme.toSet(),
                )
                lastStep = notes.last().step
            }
        }
        return Taqsim(maqam, seed, phrases, TaqsimSayrs.isDetailed(maqam), plan.form, theme)
    }

    /** A phrase being built: degrees in the station's scale, how it was made, which notes are the theme. */
    data class Draft(val degrees: List<Int>, val how: String, val theme: List<Int> = emptyList())

    /** How the theme is brought into one phrase: its notes (from the jins' root) and what was done to it. */
    data class ThemeUse(val shape: List<Int>, val name: String)

    // --- The theme ---

    /**
     * The theme: one of the lower jins' own motifs (its colour is in it), four to six notes,
     * with a rhythm of its own that makes it recognisable when it comes back: a held first
     * note, a figure of short notes or a dotted pair, a longer last note.
     */
    private fun makeTheme(maqam: Maqam): Theme {
        val grammar = JinsGrammars.of(maqam.lower)
        val pool = grammar.motifs.filter { MotifUse.Open in it.uses || MotifUse.Develop in it.uses }.ifEmpty { grammar.motifs }
        val motif = pool.random(random)
        var shape = motif.degrees
        while (shape.size < 4) shape = shape + (shape.last() + if (shape.last() > 0) -1 else 1)
        shape = shape.take(6)
        val cells = listOf(listOf(0.6, 0.6), listOf(1.0), listOf(0.9, 0.45), listOf(0.5, 0.5, 1.0))
        val rhythm = mutableListOf(random.nextDouble(1.4, 2.0))
        while (rhythm.size < shape.size - 1) rhythm += cells.random(random)
        while (rhythm.size > shape.size - 1) rhythm.removeAt(rhythm.lastIndex)
        rhythm += 1.6
        return Theme("«${motif.name}»", shape, rhythm)
    }

    /**
     * Whether this phrase brings the theme back, and how: stated plainly at the start, in the
     * new colour of the upper jins or a modulation, in sequence, inverted on the way down, or
     * a fragment of it in the qafla. Kept for a few phrases so it stays a theme.
     */
    private fun themeFor(theme: Theme, kind: SectionKind, role: PhraseRole, heard: Int): ThemeUse? {
        val chance = when {
            kind == SectionKind.Opening && role == PhraseRole.Open -> 0.95
            kind == SectionKind.Opening && role == PhraseRole.Question -> if (heard == 0) 0.8 else 0.2
            kind == SectionKind.Upper && role == PhraseRole.Develop -> 0.7
            kind == SectionKind.Modulation && role == PhraseRole.Develop -> 0.65
            kind == SectionKind.Peak && role == PhraseRole.Climb -> 0.45
            kind == SectionKind.Revisit && role == PhraseRole.Question -> 0.5
            kind == SectionKind.Ghammaz -> 0.3
            kind == SectionKind.Return -> 0.3
            kind == SectionKind.Qafla -> 0.5
            else -> 0.15
        } * if (heard >= 6) 0.4 else 1.0
        if (random.nextDouble() >= chance) return null
        val s = theme.shape
        return when {
            kind == SectionKind.Qafla -> ThemeUse(s.take(3), "обрывок темы")
            kind == SectionKind.Return && random.nextDouble() < 0.4 -> ThemeUse(s.map { 2 * s.first() - it }, "тема в обращении")
            (kind == SectionKind.Upper || kind == SectionKind.Modulation) && random.nextDouble() < 0.3 ->
                ThemeUse(s + s.map { it - 1 }, "тема с секвенцией вниз")
            kind == SectionKind.Modulation -> ThemeUse(s, "тема в новом макаме")
            kind == SectionKind.Upper || kind == SectionKind.Peak -> ThemeUse(s, "тема наверху, в цвете верхнего джинса")
            heard == 0 -> ThemeUse(s, "тема таксима ${theme.name}")
            else -> ThemeUse(s, "снова тема")
        }
    }

    private fun bestPhrase(
        section: Section,
        role: PhraseRole,
        end: Int,
        from: Int,
        previous: TaqsimPhrase?,
        question: List<Int>?,
        theme: ThemeUse?,
    ): Draft {
        val station = section.station
        val grammar = JinsGrammars.of(station.jins)
        val candidates = (0 until TRIES).mapNotNull { draft(station, grammar, role, end, from, question, theme) }
            .filter { StyleRules.allowed(it.degrees, station) }
            .distinctBy { it.degrees }
        if (candidates.isEmpty()) {
            // The theme doesn't fit here: build the phrase without it.
            return if (theme != null) bestPhrase(section, role, end, from, previous, question, null) else fallback(station, from, end)
        }
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

    /** One attempt: an opening, the theme if it comes here, motifs moved and changed, then the way to [end]. */
    private fun draft(
        station: Station,
        grammar: JinsGrammar,
        role: PhraseRole,
        end: Int,
        from: Int,
        question: List<Int>?,
        theme: ThemeUse?,
    ): Draft? {
        val notes = mutableListOf<Int>()
        val isTheme = mutableListOf<Boolean>()
        fun add(d: Int, t: Boolean = false) {
            notes += d
            isTheme += t
        }
        val how = mutableListOf<String>()
        val root = station.jinsRoot

        // The answer takes up the question's opening and then goes its own way.
        if (role == PhraseRole.Answer && question != null && question.size >= 4) {
            val keep = random.nextInt(2, minOf(4, question.size - 1) + 1)
            question.take(keep).forEach { add(it) }
            how += "начало как в вопросе"
        } else {
            add(from)
            if (role == PhraseRole.Open && random.nextDouble() < 0.5 && from - 1 >= station.low) {
                notes.add(0, from - 1)
                isTheme.add(0, false)
            }
        }

        // The theme, placed on the jins' root or next to where the line is, kept inside the range.
        if (theme != null) {
            var shift = if (random.nextDouble() < 0.6) root else notes.last() - theme.shape.first()
            val low = theme.shape.min() + shift
            val high = theme.shape.max() + shift
            if (low < station.low) shift += station.low - low
            if (high > station.high) shift -= high - station.high
            val placed = theme.shape.map { it + shift }
            // When the line already stands on the theme's first note, that note becomes part of it.
            if (connect(notes, isTheme, placed.first())) isTheme[isTheme.lastIndex] = true else add(placed.first(), t = true)
            placed.drop(1).forEach { add(it, t = true) }
            how += theme.name
        }

        val motifCount = when {
            theme != null -> random.nextInt(0, 2)
            role == PhraseRole.Open || role == PhraseRole.Cadence -> random.nextInt(1, 3)
            else -> random.nextInt(1, 4)
        }
        repeat(motifCount) { k ->
            val last = k == motifCount - 1
            val use = when {
                k == 0 && theme == null && role == PhraseRole.Open -> MotifUse.Open
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
            val joined = connect(notes, isTheme, placed.first())
            placed.drop(if (joined) 1 else 0).forEach { add(it) }
            how += name
        }

        approach(notes, isTheme, end, JinsGrammars.of(station.jins).fromAbove)
        // Same note three times running is held, not repeated: drop the third.
        val keep = notes.indices.filter { i -> !(i >= 2 && notes[i] == notes[i - 1] && notes[i] == notes[i - 2]) }
        val merged = keep.map { notes[it] }
        if (merged.size < 3 || merged.size > MAX_NOTES) return null
        val themeAt = keep.indices.filter { isTheme[keep[it]] }
        return Draft(merged, how.joinToString(", "), themeAt)
    }

    /**
     * Steps from the line's last note towards [target], so motifs join without jumps. True when
     * the line ends on [target] itself: the motif then starts on that note instead of repeating it.
     */
    private fun connect(notes: MutableList<Int>, isTheme: MutableList<Boolean>, target: Int): Boolean {
        var d = notes.last()
        while (abs(target - d) > 2) {
            d += if (target > d) 1 else -1
            notes += d
            isTheme += false
        }
        return notes.last() == target
    }

    /** Leads to [end]: stepwise, often through the note above (or below) it, as the jins does. */
    private fun approach(notes: MutableList<Int>, isTheme: MutableList<Boolean>, end: Int, fromAbove: Double) {
        if (notes.last() == end) return
        val above = random.nextDouble() < fromAbove
        val via = if (above) end + 1 else end - 1
        var d = notes.last()
        val target = if (abs(d - end) <= 1) end else via
        while (d != target) {
            d += if (target > d) 1 else -1
            notes += d
            isTheme += false
        }
        if (notes.last() != end) {
            notes += end
            isTheme += false
        }
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
        val flags = mutableListOf(false)
        approach(notes, flags, end, 0.5)
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
 * may linger, the resting note at the end is long, the last qafla longest; the theme keeps its
 * own rhythm wherever it comes; a little rubato over all.
 */
object Timing {
    fun beats(
        degrees: List<Int>,
        role: PhraseRole,
        kind: SectionKind,
        station: Station,
        random: Random,
        theme: Map<Int, Double> = emptyMap(),
    ): List<Double> {
        val colour = JinsGrammars.of(station.jins).characteristic.map { it + station.jinsRoot }
        return degrees.mapIndexed { i, d ->
            val base = when {
                i == degrees.lastIndex -> if (kind == SectionKind.Qafla) 8.0 else random.nextDouble(3.0, 5.5)
                i in theme -> theme.getValue(i)
                i == 0 && (role == PhraseRole.Open || random.nextDouble() < 0.25) -> random.nextDouble(1.5, 3.0)
                d in colour && random.nextDouble() < 0.35 -> random.nextDouble(1.5, 2.2)
                inRun(degrees, i) -> 0.7
                else -> 1.0
            }
            // The theme's own rhythm gets less rubato, so it stays recognisable.
            base * if (i in theme) random.nextDouble(0.95, 1.05) else random.nextDouble(0.9, 1.1)
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
