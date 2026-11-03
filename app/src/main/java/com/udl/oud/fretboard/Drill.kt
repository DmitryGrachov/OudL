package com.udl.oud.fretboard

import kotlin.math.abs
import kotlin.random.Random

/**
 * A drill: the player plays [steps] (grid pitches) in order; each note is taken once it rings
 * steadily near its target, and its offset in cents is kept as the result. A scale drill walks
 * up or down the scale; a fretboard drill also names the [places] where each note is to be found.
 */
data class Drill(
    val steps: List<Int>,
    /** Offset in cents of each note played so far, in order. */
    val results: List<Double> = emptyList(),
    /** For a fretboard drill, the spot on the neck asked for with each step. */
    val places: List<Position>? = null,
    /** For a cadence drill, the phrase being repeated. */
    val cadence: Cadence? = null,
) {
    val target: Int? get() = steps.getOrNull(results.size)
    val targetPlace: Position? get() = places?.getOrNull(results.size)
    val done: Boolean get() = results.size == steps.size

    val inTuneCount: Int get() = results.count { abs(it) <= FretboardUiState.IN_TUNE_CENTS }
    val meanError: Double get() = if (results.isEmpty()) 0.0 else results.sumOf { abs(it) } / results.size

    /** Index of the note played furthest off, or null before any. */
    val worst: Int? get() = results.indices.maxByOrNull { abs(results[it]) }

    fun record(cents: Double) = copy(results = results + cents)

    companion object {
        /**
         * A fretboard drill of [count] spots drawn from [choices], never the same pitch twice in
         * a row (the previous note would still be ringing).
         */
        fun onFretboard(choices: List<Position>, count: Int = FRETBOARD_NOTES, random: Random = Random): Drill {
            require(choices.isNotEmpty())
            val places = mutableListOf<Position>()
            repeat(count) {
                val fresh = choices.filter { it.pitch != places.lastOrNull()?.pitch }.ifEmpty { choices }
                places += fresh.random(random)
            }
            return Drill(places.map { it.pitch.step }, places = places)
        }

        const val FRETBOARD_NOTES = 10
    }
}

/**
 * Decides when the note being played counts: after enough consecutive readings close to the
 * target that agree among themselves, it reports their median offset.
 */
class SteadyNote(
    private val readings: Int = READINGS,
    private val spreadCents: Double = SPREAD_CENTS,
    private val sameNoteCents: Double = SAME_NOTE_CENTS,
) {
    private val window = ArrayDeque<Double>()

    /** Wrong note (or nothing usable yet) gives null; a steady note near the target gives its offset. */
    fun add(centsFromTarget: Double): Double? {
        if (abs(centsFromTarget) > sameNoteCents) {
            window.clear()
            return null
        }
        window.addLast(centsFromTarget)
        if (window.size > readings) window.removeFirst()
        if (window.size < readings || window.max() - window.min() > spreadCents) return null
        val median = window.sorted()[window.size / 2]
        window.clear()
        return median
    }

    fun reset() = window.clear()

    private companion object {
        /** About 140 ms of readings after the attack has been skipped. */
        const val READINGS = 6
        const val SPREAD_CENTS = 15.0

        /** Further than this from the target it is another note, not an out-of-tune one. */
        const val SAME_NOTE_CENTS = 50.0
    }
}
