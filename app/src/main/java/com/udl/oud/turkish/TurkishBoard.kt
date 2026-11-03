package com.udl.oud.turkish

import com.udl.oud.tuner.Tuning
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** A makam degree on the neck: [cents] above the open [course], in [octave] relative to the durak's. */
data class TurkishMark(val course: Int, val cents: Double, val degree: Int, val octave: Int)

/** What the mic hears against a makam: the nearest degree, its octave, and how far off. */
data class TurkishHeard(val degree: Int, val octave: Int, val cents: Double)

/** Where a Turkish makam lies on the neck and what a heard pitch is in it — on the koma grid. */
object TurkishBoard {
    /** Marks this close to an open string are drawn as the open string itself. */
    private const val OPEN_SNAP_CENTS = 12.0

    fun degreeFrequencies(makam: TurkishMakam, a4: Double): List<Double> =
        makam.degrees().map { Koma.frequency(it.written, it.spelling, a4) }

    /** Every place each degree can be played, in any octave, from the open string to [maxCents]. */
    fun marks(makam: TurkishMakam, tuning: Tuning, a4: Double, maxCents: Int): List<TurkishMark> {
        val degrees = degreeFrequencies(makam, a4)
        return tuning.courses.flatMapIndexed { course, open ->
            val openFrequency = open.frequency(a4)
            degrees.flatMapIndexed { degree, f ->
                (-3..3).mapNotNull { octave ->
                    val cents = 1200 * log2(f * 2.0.pow(octave) / openFrequency)
                    when {
                        abs(cents) < OPEN_SNAP_CENTS -> TurkishMark(course, 0.0, degree, octave)
                        cents > 0 && cents < maxCents + OPEN_SNAP_CENTS -> TurkishMark(course, cents, degree, octave)
                        else -> null
                    }
                }
            }
        }
    }

    /** The makam degree nearest to [frequency], in whichever octave it is played. */
    fun nearest(makam: TurkishMakam, frequency: Double, a4: Double): TurkishHeard =
        degreeFrequencies(makam, a4).mapIndexed { degree, f ->
            val above = 1200 * log2(frequency / f)
            val octave = (above / 1200).roundToInt()
            TurkishHeard(degree, octave, above - 1200 * octave)
        }.minBy { abs(it.cents) }

    /** The scale from the durak to its octave, as (degree, octave) pairs. */
    fun ascending(): List<Pair<Int, Int>> = (0 until 7).map { it to 0 } + (0 to 1)
}
