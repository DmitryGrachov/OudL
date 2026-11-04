package com.udl.oud.risha

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A stroke of the risha heard: when (ms from the start of listening) and how loud (peak RMS). */
data class RishaStroke(val atMs: Double, val level: Double)

/**
 * A tremolo (rash) heard: how fast, and how even — the gaps between the strokes and their
 * loudness. [intervals] are the gaps of the run, in ms, for the chart.
 */
data class TremoloStats(val strokes: Int, val perSecond: Double, val evenness: Int, val levelSpreadDb: Double, val intervals: List<Double>)

/** Alternate picking against the metronome: timing per stroke, and down against up. */
data class GridStats(
    val expected: Int,
    val onTime: Int,
    val missed: Int,
    val extra: Int,
    /** Average |error| and the spread (standard deviation) of the errors, in ms. */
    val meanAbsMs: Double,
    val spreadMs: Double,
    /** Average lead (−) or lag (+) of the down strokes and of the up strokes. */
    val downMs: Double,
    val upMs: Double,
    /** How much louder (+) or quieter (−) the up strokes are than the down ones, in dB. */
    val upLevelDb: Double,
    /** Per grid point: its error in ms, or null when it was missed. */
    val errors: List<Double?>,
) {
    val accuracy: Double get() = if (expected == 0) 0.0 else onTime.toDouble() / expected
}

object RishaAnalysis {
    /** Strokes further apart than this end a tremolo run. */
    const val TREMOLO_GAP_MS = 300.0
    const val MIN_TREMOLO = 6

    /** A stroke this close to its place is on time. */
    const val GOOD_MS = 30.0

    /** The last run of strokes, close together, measured; null until there are enough. */
    fun tremolo(strokes: List<RishaStroke>): TremoloStats? {
        if (strokes.size < MIN_TREMOLO) return null
        var start = strokes.lastIndex
        while (start > 0 && strokes[start].atMs - strokes[start - 1].atMs <= TREMOLO_GAP_MS) start--
        val run = strokes.subList(start, strokes.size)
        if (run.size < MIN_TREMOLO) return null
        val gaps = run.zipWithNext { a, b -> b.atMs - a.atMs }
        val mean = gaps.average()
        val cv = sd(gaps) / mean
        val db = run.map { db(it.level) }
        return TremoloStats(
            strokes = run.size,
            perSecond = 1000.0 / mean,
            evenness = ((1 - cv) * 100).roundToInt().coerceIn(0, 100),
            levelSpreadDb = sd(db),
            intervals = gaps,
        )
    }

    /**
     * Strokes against a grid of [stepMs] from [fromMs] to [toMs]: each grid point wants a stroke
     * near it; even points are down strokes, odd ones up (the risha alternates).
     */
    fun grid(strokes: List<RishaStroke>, stepMs: Double, fromMs: Double, toMs: Double): GridStats {
        val window = minOf(80.0, stepMs * 0.45)
        val points = generateSequence(0) { it + 1 }.map { fromMs + it * stepMs }.takeWhile { it < toMs - 1e-6 }.toList()
        val mine = strokes.filter { it.atMs >= fromMs - window && it.atMs < toMs - window }.sortedBy { it.atMs }
        val used = BooleanArray(mine.size)
        val down = mutableListOf<Pair<Double, Double>>()
        val up = mutableListOf<Pair<Double, Double>>()
        val errors = points.mapIndexed { i, t ->
            val h = mine.indices.filter { !used[it] && abs(mine[it].atMs - t) <= window }.minByOrNull { abs(mine[it].atMs - t) }
                ?: return@mapIndexed null
            used[h] = true
            val e = mine[h].atMs - t
            (if (i % 2 == 0) down else up) += e to db(mine[h].level)
            e
        }
        val found = errors.filterNotNull()
        return GridStats(
            expected = points.size,
            onTime = found.count { abs(it) <= GOOD_MS },
            missed = errors.count { it == null },
            extra = used.count { !it },
            meanAbsMs = if (found.isEmpty()) 0.0 else found.map(::abs).average(),
            spreadMs = sd(found),
            downMs = down.map { it.first }.averageOr0(),
            upMs = up.map { it.first }.averageOr0(),
            upLevelDb = if (down.isEmpty() || up.isEmpty()) 0.0 else up.map { it.second }.average() - down.map { it.second }.average(),
            errors = errors,
        )
    }

    private fun db(level: Double) = 20 * log10(level.coerceAtLeast(1e-6))
    private fun List<Double>.averageOr0() = if (isEmpty()) 0.0 else average()
    private fun sd(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }
}
