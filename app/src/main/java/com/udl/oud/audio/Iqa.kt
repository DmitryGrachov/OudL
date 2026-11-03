package com.udl.oud.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A drum stroke: dum (low, centre of the drum), tak (high, rim), or a rest. */
enum class Stroke { Dum, Tak, Rest }

/**
 * An Arabic rhythmic cycle (iqa'): strokes on an even grid of [stepsPerBeat] steps per beat,
 * written like "D T . T D . T ." (D = dum, T = tak, . = rest).
 */
data class Iqa(val name: String, val meter: String, val stepsPerBeat: Int, val pattern: String) {
    val strokes: List<Stroke> = pattern.split(" ").filter { it.isNotEmpty() }.map {
        when (it) {
            "D" -> Stroke.Dum
            "T" -> Stroke.Tak
            "." -> Stroke.Rest
            else -> error("$name: unknown stroke '$it'")
        }
    }

    /** Samples per step at [bpm] beats per minute. */
    fun stepSamples(bpm: Int, sampleRate: Int): Int = (sampleRate * 60.0 / bpm / stepsPerBeat).roundToInt()
}

object Iqaat {
    val all = listOf(
        Iqa("Максум", "4/4", 2, "D T . T D . T ."),
        Iqa("Баляди", "4/4", 2, "D D . T D . T ."),
        Iqa("Саиди", "4/4", 2, "D T . D D . T ."),
        Iqa("Малфуф", "2/4", 4, "D . . T . . T ."),
        Iqa("Сама'и сакиль", "10/8", 2, "D . . . T . D D T ."),
        Iqa("Метроном", "4/4", 2, "D . T . T . T ."),
    )
}

/** Renders one cycle of an iqa' as a loop: each stroke is placed on its step, tails wrap around. */
object Drum {
    fun render(iqa: Iqa, bpm: Int, sampleRate: Int = LoopTrack.SAMPLE_RATE): ShortArray {
        val step = iqa.stepSamples(bpm, sampleRate)
        val mix = FloatArray(step * iqa.strokes.size)
        val dum = dum(sampleRate)
        val tak = tak(sampleRate)
        iqa.strokes.forEachIndexed { i, stroke ->
            val sound = when (stroke) {
                Stroke.Dum -> dum
                Stroke.Tak -> tak
                Stroke.Rest -> return@forEachIndexed
            }
            // A sound longer than what is left of the cycle rings on into its start.
            for (j in sound.indices) mix[(i * step + j) % mix.size] += sound[j]
        }
        return mix.toPcm()
    }

    /** Low thump whose pitch drops from 130 to 70 Hz as it decays, like a darbuka's centre. */
    private fun dum(sampleRate: Int): FloatArray = FloatArray((0.35 * sampleRate).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        // Phase is the integral of the gliding frequency 70 + 60·e^(−30t).
        val phase = 2 * PI * (70 * t + 60 * (1 - exp(-t * 30)) / 30)
        (0.9 * sin(phase) * exp(-t * 9) * attack(t)).toFloat()
    }

    /** Short bright crack from the rim: a ringing high partial over a burst of noise. */
    private fun tak(sampleRate: Int): FloatArray {
        val random = Random(1)
        return FloatArray((0.12 * sampleRate).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val ring = 0.5 * sin(2 * PI * 1800 * t) * exp(-t * 45)
            val noise = 0.45 * (random.nextDouble() * 2 - 1) * exp(-t * 70)
            ((ring + noise) * attack(t)).toFloat()
        }
    }

    private fun attack(t: Double) = (t / 0.002).coerceAtMost(1.0)
}
