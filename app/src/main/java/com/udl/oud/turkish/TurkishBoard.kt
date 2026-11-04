package com.udl.oud.turkish

import com.udl.oud.tuner.Ahenk
import com.udl.oud.tuner.Tuning
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** A tone on the neck: [cents] above the open [course]; [tone] carries its octave. */
data class TurkishMark(val course: Int, val cents: Double, val tone: TurkishTone)

/** What the mic hears against a set of tones: the nearest, in the octave it was played, and how far off. */
data class TurkishHeard(val tone: TurkishTone, val cents: Double)

/** Where tones lie on the neck and what a heard pitch is among them — on the koma grid. */
object TurkishBoard {
    /** Marks this close to an open string are drawn as the open string itself. */
    private const val OPEN_SNAP_CENTS = 12.0

    /** Every place each of [tones] can be played, in any octave, from the open string to [maxCents]. */
    fun marks(tones: List<TurkishTone>, tuning: Tuning, a4: Double, ahenk: Ahenk, maxCents: Int): List<TurkishMark> =
        tuning.courses.flatMapIndexed { course, open ->
            val openFrequency = open.frequency(a4)
            tones.flatMap { tone ->
                val f = tone.frequency(a4, ahenk)
                (-3..3).mapNotNull { octave ->
                    val cents = 1200 * log2(f * 2.0.pow(octave) / openFrequency)
                    when {
                        abs(cents) < OPEN_SNAP_CENTS -> TurkishMark(course, 0.0, tone.octave(octave))
                        cents > 0 && cents < maxCents + OPEN_SNAP_CENTS -> TurkishMark(course, cents, tone.octave(octave))
                        else -> null
                    }
                }
            }
        }

    /** The tone of [tones] nearest to [frequency], moved to the octave it is played in. */
    fun nearest(tones: List<TurkishTone>, frequency: Double, a4: Double, ahenk: Ahenk): TurkishHeard =
        tones.map { tone ->
            val above = 1200 * log2(frequency / tone.frequency(a4, ahenk))
            val octave = (above / 1200).roundToInt()
            TurkishHeard(tone.octave(octave), above - 1200 * octave)
        }.minBy { abs(it.cents) }
}
