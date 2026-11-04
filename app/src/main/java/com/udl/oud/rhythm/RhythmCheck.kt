package com.udl.oud.rhythm

import com.udl.oud.audio.Iqa
import com.udl.oud.audio.Stroke
import kotlin.math.abs

/** A stroke the player made: when (ms from the start of the run) and which, if it is known. */
data class Hit(val atMs: Double, val stroke: Stroke? = null)

/** How one stroke of the cycle went. */
enum class HitKind { Good, Ok, WrongStroke, Missed }

/** One stroke of the cycle: its step, what it should be, how it went, and how early (−) or late (+). */
data class StrokeResult(val step: Int, val expected: Stroke, val kind: HitKind, val errorMs: Double? = null)

/** A cycle checked: each dum and tak, and the strokes that matched nothing. */
data class CycleResult(val cycle: Int, val strokes: List<StrokeResult>, val extra: Int) {
    val onTime: Int get() = strokes.count { it.kind == HitKind.Good || it.kind == HitKind.Ok }
}

/** The run so far, over every cycle checked. */
data class RhythmStats(val cycles: Int = 0, val strokes: Int = 0, val onTime: Int = 0, val good: Int = 0, val wrong: Int = 0, val missed: Int = 0, val extra: Int = 0, val errorSum: Double = 0.0, val matched: Int = 0) {
    val accuracy: Double get() = if (strokes == 0) 0.0 else onTime.toDouble() / strokes

    /** Average lead (−) or lag (+) in ms of the strokes that matched. */
    val meanError: Double get() = if (matched == 0) 0.0 else errorSum / matched

    operator fun plus(c: CycleResult): RhythmStats {
        val errors = c.strokes.mapNotNull { it.errorMs }
        return copy(
            cycles = cycles + 1,
            strokes = strokes + c.strokes.size,
            onTime = onTime + c.onTime,
            good = good + c.strokes.count { it.kind == HitKind.Good },
            wrong = wrong + c.strokes.count { it.kind == HitKind.WrongStroke },
            missed = missed + c.strokes.count { it.kind == HitKind.Missed },
            extra = extra + c.extra,
            errorSum = errorSum + errors.sum(),
            matched = matched + errors.size,
        )
    }
}

/**
 * Checks played strokes against an iqa' at a tempo: each dum and tak of a cycle wants a stroke
 * near its time; light taks (ka) may be played or left out; anything else is extra. Without the
 * stroke's kind (from the microphone) only the timing counts.
 */
object RhythmCheck {
    /** A stroke this close (ms) is right on. */
    const val GOOD_MS = 35.0

    /** Furthest a stroke can be from its place and still count: [MAX_WINDOW_MS], or less on a dense grid. */
    const val MAX_WINDOW_MS = 120.0

    fun window(stepMs: Double): Double = minOf(MAX_WINDOW_MS, stepMs * 0.5)

    fun evaluate(iqa: Iqa, bpm: Int, cycle: Int, hits: List<Hit>): CycleResult {
        val stepMs = iqa.stepMs(bpm)
        val n = iqa.strokes.size
        val start = cycle * n * stepMs
        val window = window(stepMs)
        // The cycle owns the hits from half a step before its first stroke to half a step before the next cycle.
        val mine = hits.filter { it.atMs >= start - stepMs / 2 && it.atMs < start + n * stepMs - stepMs / 2 }.sortedBy { it.atMs }
        val used = BooleanArray(mine.size)
        fun nearest(t: Double): Int? = mine.indices.filter { !used[it] && abs(mine[it].atMs - t) <= window }.minByOrNull { abs(mine[it].atMs - t) }

        val results = iqa.strokes.withIndex().filter { it.value == Stroke.Dum || it.value == Stroke.Tak }.map { (i, s) ->
            val t = start + i * stepMs
            val h = nearest(t)
            if (h == null) {
                StrokeResult(i, s, HitKind.Missed)
            } else {
                used[h] = true
                val error = mine[h].atMs - t
                val played = mine[h].stroke
                val kind = when {
                    played != null && played != s && !(s == Stroke.Tak && played == Stroke.Ka) -> HitKind.WrongStroke
                    abs(error) <= GOOD_MS -> HitKind.Good
                    else -> HitKind.Ok
                }
                StrokeResult(i, s, kind, error)
            }
        }
        // Light taks soak up the strokes played on them.
        iqa.strokes.withIndex().filter { it.value == Stroke.Ka }.forEach { (i, _) -> nearest(start + i * stepMs)?.let { used[it] = true } }
        return CycleResult(cycle, results, used.count { !it })
    }

    /** Latency from a calibration: the median of how late each stroke came after its click. */
    fun latency(clicksMs: List<Double>, hits: List<Double>, maxMs: Double = 300.0): Double? {
        val offsets = hits.mapNotNull { h -> clicksMs.minByOrNull { abs(h - it) }?.let { h - it }?.takeIf { abs(it) <= maxMs } }
        if (offsets.size < 4) return null
        return offsets.sorted()[offsets.size / 2]
    }
}
