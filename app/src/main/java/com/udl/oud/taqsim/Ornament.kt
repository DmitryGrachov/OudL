package com.udl.oud.taqsim

import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Spelling
import kotlin.random.Random

/** The ornaments an oud player puts on the skeleton of a phrase. */
enum class OrnamentKind(val sign: String, val title: String) {
    Tremolo("≋", "тремоло ришей"),
    Trill("ᵗʳ", "трель"),
    Mordent("∽", "мордент"),
    GraceAbove("↘", "форшлаг сверху"),
    GraceBelow("↗", "форшлаг снизу"),
    Slide("⤴", "подъезд снизу"),
    Turn("∞", "группетто"),
}

/**
 * An ornament on one skeleton note, with the neighbour it uses: the degree above or below in the
 * station's own scale, so a trill on Ре in Bayati goes through Ми½♭ and keeps the maqam's colour.
 */
data class Ornament(
    val kind: OrnamentKind,
    val neighbourStep: Int?,
    val neighbourSpelling: Spelling?,
    /** For a turn, the degree below as well. */
    val lowerStep: Int? = null,
) {
    fun describe(on: String): String {
        val via = neighbourSpelling?.let { " (${it.label(NoteLabels.Solfege)})" } ?: ""
        return "${kind.title}$via на $on"
    }
}

/** How much ornament: none (the skeleton), a little (the long notes), a lot. */
enum class OrnamentLevel(val title: String, val perPhrase: Int) {
    None("Скелет", 0),
    Light("Немного украшений", 1),
    Rich("Много украшений", 3),
}

/**
 * Puts ornaments where players put them: the long resting note rings with the risha's tremolo
 * or a trill, a phrase may start with a slide or a grace note, the jins' colour note gets a
 * mordent or a grace from above. Short running notes stay plain. Which ornament and where is
 * drawn from [seed], so a phrase sounds the same each time it is replayed.
 */
object Ornamenter {
    /** Ornament per note of [Taqsim.notes] (by index), for [level]. */
    fun decorate(taqsim: Taqsim, level: OrnamentLevel): Map<Int, Ornament> {
        if (level == OrnamentLevel.None) return emptyMap()
        val random = Random(taqsim.seed * 31 + level.ordinal)
        val result = mutableMapOf<Int, Ornament>()
        var offset = 0
        taqsim.phrases.forEach { phrase ->
            val station = phrase.station
            val colour = JinsGrammars.of(station.jins).characteristic.map { it + station.jinsRoot }
            val notes = phrase.notes
            val finalQafla = phrase.section == SectionKind.Qafla
            // Candidates in order of how naturally they take an ornament.
            val candidates = mutableListOf<Pair<Int, OrnamentKind>>()
            notes.indices.reversed().forEach { i ->
                val n = notes[i]
                val last = i == notes.lastIndex
                val kind: OrnamentKind? = when {
                    last && finalQafla -> pick(random, OrnamentKind.Tremolo to 0.65, null to 0.35)
                    last && n.beats >= 2.5 -> pick(
                        random,
                        OrnamentKind.Tremolo to 0.4, OrnamentKind.Trill to 0.15, OrnamentKind.Slide to 0.15, null to 0.3,
                    )
                    i == 0 && n.beats >= 1.4 -> pick(random, OrnamentKind.Slide to 0.35, OrnamentKind.GraceBelow to 0.2, null to 0.45)
                    n.degree in colour && n.beats >= 1.2 -> pick(
                        random,
                        OrnamentKind.Mordent to 0.3, OrnamentKind.GraceAbove to 0.3, OrnamentKind.Turn to 0.15, null to 0.25,
                    )
                    n.beats >= 1.4 -> pick(random, OrnamentKind.Mordent to 0.15, OrnamentKind.GraceAbove to 0.1, null to 0.75)
                    else -> null
                }
                if (kind != null) candidates += i to kind
            }
            // The phrase's last note first, then the rest in random order, up to the level's count.
            val (lastOnes, others) = candidates.partition { it.first == notes.lastIndex }
            val chosen = (lastOnes + others.shuffled(random)).take(level.perPhrase + if (finalQafla) 1 else 0)
            chosen.forEach { (i, kind) ->
                val n = notes[i]
                val neighbour = when (kind) {
                    OrnamentKind.GraceBelow, OrnamentKind.Slide -> n.degree - 1
                    OrnamentKind.Tremolo -> null
                    else -> n.degree + 1
                }
                result[offset + i] = Ornament(
                    kind, neighbour?.let(station::step), neighbour?.let(station::spelling),
                    lowerStep = if (kind == OrnamentKind.Turn) station.step(n.degree - 1) else null,
                )
            }
            offset += notes.size
        }
        return result
    }

    private fun pick(random: Random, vararg options: Pair<OrnamentKind?, Double>): OrnamentKind? {
        var r = random.nextDouble() * options.sumOf { it.second }
        for ((kind, w) in options) {
            r -= w
            if (r <= 0) return kind
        }
        return options.last().first
    }
}

/** One sound of a performed note: a pluck, or a note slurred in by the left hand without one. */
data class SoundEvent(
    val step: Int,
    val startMs: Double,
    val lengthMs: Double,
    val gain: Float = 1f,
    val legato: Boolean = false,
    /** Starts this many cents below and slides up into the note. */
    val slideFromCents: Double = 0.0,
    val slideMs: Double = 0.0,
)

/**
 * Turns a skeleton note with its ornament into what the hands do: the risha's tremolo is quick
 * repeated plucks; trill, mordent, grace notes and turns are one pluck and the rest slurred by
 * the left hand; a slide is one pluck gliding up into the note. A note too short for its
 * ornament is played plain.
 */
object OrnamentSound {
    const val TREMOLO_MS = 75.0
    private const val QUICK_MS = 70.0
    private const val GRACE_MS = 85.0
    private const val SLIDE_MS = 120.0

    fun events(step: Int, ornament: Ornament?, startMs: Double, lengthMs: Double): List<SoundEvent> {
        val plain = listOf(SoundEvent(step, startMs, lengthMs))
        if (ornament == null) return plain
        val n = ornament.neighbourStep
        fun at(offset: Double) = startMs + offset
        return when (ornament.kind) {
            OrnamentKind.Tremolo -> {
                if (lengthMs < 6 * TREMOLO_MS) return plain
                val count = (lengthMs * 0.85 / TREMOLO_MS).toInt()
                val strokes = (0 until count).map { k ->
                    SoundEvent(step, at(k * TREMOLO_MS), TREMOLO_MS, gain = if (k == 0) 1f else if (k % 2 == 1) 0.6f else 0.72f)
                }
                // The last stroke rings on to the end of the note.
                strokes.dropLast(1) + strokes.last().copy(lengthMs = lengthMs - (count - 1) * TREMOLO_MS)
            }
            OrnamentKind.Trill -> {
                if (n == null || lengthMs < 8 * QUICK_MS) return plain
                val shakes = ((lengthMs * 0.6) / QUICK_MS).toInt().let { if (it % 2 == 1) it + 1 else it }
                val head = SoundEvent(step, at(0.0), QUICK_MS * 1.5)
                val alternation = (0 until shakes).map { k ->
                    SoundEvent(if (k % 2 == 0) n else step, at(QUICK_MS * 1.5 + k * QUICK_MS), QUICK_MS, gain = 0.45f, legato = true)
                }
                val rest = QUICK_MS * 1.5 + shakes * QUICK_MS
                listOf(head) + alternation + SoundEvent(step, at(rest), lengthMs - rest, gain = 0.45f, legato = true)
            }
            OrnamentKind.Mordent -> {
                if (n == null || lengthMs < 4 * QUICK_MS) return plain
                listOf(
                    SoundEvent(step, at(0.0), QUICK_MS),
                    SoundEvent(n, at(QUICK_MS), QUICK_MS, gain = 0.5f, legato = true),
                    SoundEvent(step, at(2 * QUICK_MS), lengthMs - 2 * QUICK_MS, gain = 0.5f, legato = true),
                )
            }
            OrnamentKind.GraceAbove, OrnamentKind.GraceBelow -> {
                if (n == null || lengthMs < 3 * GRACE_MS) return plain
                listOf(
                    SoundEvent(n, at(0.0), GRACE_MS),
                    SoundEvent(step, at(GRACE_MS), lengthMs - GRACE_MS, gain = 0.55f, legato = true),
                )
            }
            OrnamentKind.Turn -> {
                if (n == null || lengthMs < 5 * QUICK_MS) return plain
                val below = ornament.lowerStep ?: return plain
                listOf(
                    SoundEvent(n, at(0.0), QUICK_MS),
                    SoundEvent(step, at(QUICK_MS), QUICK_MS, gain = 0.5f, legato = true),
                    SoundEvent(below, at(2 * QUICK_MS), QUICK_MS, gain = 0.5f, legato = true),
                    SoundEvent(step, at(3 * QUICK_MS), lengthMs - 3 * QUICK_MS, gain = 0.5f, legato = true),
                )
            }
            OrnamentKind.Slide -> {
                if (n == null || lengthMs < 2.5 * SLIDE_MS) return plain
                // From the degree below, in cents: a quarter-tone grid step is 50 cents.
                val cents = ((step - n) * 50.0).coerceIn(50.0, 200.0)
                listOf(SoundEvent(step, at(0.0), lengthMs, slideFromCents = cents, slideMs = SLIDE_MS))
            }
        }
    }
}
