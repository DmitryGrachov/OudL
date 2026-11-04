package com.udl.oud.taqsim

import kotlin.random.Random

/**
 * How a taqsim breathes, beyond its notes: an arc over the whole course — a calm, unhurried
 * opening, growing and quickening towards the peak, a broad qafla — and inside each phrase a
 * swell to its highest note and an easing into the resting one, with the jins' colour note
 * leaned on and quick runs played lighter.
 */
object Expression {
    /** How much slower (above 1) or quicker (below 1) a section goes than the base pulse. */
    fun tempo(kind: SectionKind): Double = when (kind) {
        SectionKind.Opening -> 1.12
        SectionKind.Ghammaz -> 1.0
        SectionKind.Upper -> 0.97
        SectionKind.Peak -> 0.88
        SectionKind.Modulation -> 0.97
        SectionKind.Revisit -> 1.05
        SectionKind.Return -> 1.0
        SectionKind.Qafla -> 1.2
    }

    /** Loudness of a section, the peak loudest. */
    fun level(kind: SectionKind): Float = when (kind) {
        SectionKind.Opening -> 0.72f
        SectionKind.Ghammaz -> 0.8f
        SectionKind.Upper -> 0.86f
        SectionKind.Peak -> 1.0f
        SectionKind.Modulation -> 0.84f
        SectionKind.Revisit -> 0.76f
        SectionKind.Return -> 0.8f
        SectionKind.Qafla -> 0.78f
    }

    /** Loudness of each note of a phrase, 0..1. */
    fun velocities(degrees: List<Int>, beats: List<Double>, kind: SectionKind, station: Station, random: Random): List<Float> {
        val level = level(kind)
        val top = degrees.indices.maxBy { degrees[it] }
        val last = degrees.lastIndex
        val colour = JinsGrammars.of(station.jins).characteristic.map { it + station.jinsRoot }
        return degrees.indices.map { i ->
            // Swell to the highest note, ease off into the rest.
            var v = when {
                i <= top -> 0.82f + 0.18f * (if (top == 0) 1f else i.toFloat() / top)
                else -> 1f - 0.22f * (i - top).toFloat() / maxOf(1, last - top)
            }
            if (beats[i] < 0.8) v *= 0.88f // quick notes are lighter
            if (degrees[i] in colour) v += 0.08f // lean on the colour
            v *= level * random.nextDouble(0.94, 1.06).toFloat()
            v.coerceIn(0.3f, 1f)
        }
    }
}
