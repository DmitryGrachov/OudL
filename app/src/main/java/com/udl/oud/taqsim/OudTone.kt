package com.udl.oud.taqsim

import com.udl.oud.tuner.Tuning

/**
 * How a note sounds on the oud depending on where it is played: the same pitch is darker and
 * rings longer on a bass course than on a treble one. Notes go where a player in first position
 * takes them: on the highest course whose open string is at or below the note.
 */
object OudTone {
    /** Notes held at least this long get the left hand's vibrato. */
    const val VIBRATO_FROM_MS = 650.0
    const val VIBRATO_CENTS = 11.0
    const val VIBRATO_DELAY_MS = 280.0

    /** Course (0 = bass) the note on grid step [step] is played on. */
    fun course(step: Int, tuning: Tuning): Int =
        tuning.courses.indices.lastOrNull { tuning.courses[it].midi * 2 <= step } ?: 0

    /** 0 dark … 1 bright: bass courses darker. */
    fun brightness(course: Int, tuning: Tuning): Float = 0.25f + 0.6f * course / (tuning.courses.size - 1).coerceAtLeast(1)

    /** Seconds to fall silent: bass courses ring longer. */
    fun ring(course: Int, tuning: Tuning): Double = 3.3 - 1.3 * course / (tuning.courses.size - 1).coerceAtLeast(1)
}
