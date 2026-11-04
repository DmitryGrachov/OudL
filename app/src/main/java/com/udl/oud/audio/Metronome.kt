package com.udl.oud.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** A plain metronome bar: [beats] beats, each split into [subdivision] clicks. */
object Metronome {
    /** Samples between two clicks at [bpm] with [subdivision] clicks per beat. */
    fun clickSamples(bpm: Int, subdivision: Int, sampleRate: Int = LoopTrack.SAMPLE_RATE): Int =
        (sampleRate * 60.0 / bpm / subdivision).roundToInt()

    /**
     * One bar as a loop: a high click on the first beat (when [accent] is on), a middle click on
     * the other beats and a soft low tick on the subdivisions in between.
     */
    fun render(
        bpm: Int,
        beats: Int,
        subdivision: Int,
        accent: Boolean,
        sampleRate: Int = LoopTrack.SAMPLE_RATE,
    ): ShortArray {
        val step = clickSamples(bpm, subdivision, sampleRate)
        val mix = FloatArray(step * beats * subdivision)
        val strong = click(2000.0, 0.9, sampleRate)
        val beat = click(1300.0, 0.7, sampleRate)
        val tick = click(900.0, 0.35, sampleRate)
        for (i in 0 until beats * subdivision) {
            val sound = when {
                i == 0 && accent -> strong
                i % subdivision == 0 -> beat
                else -> tick
            }
            for (j in sound.indices) mix[(i * step + j) % mix.size] += sound[j]
        }
        return mix.toPcm()
    }

    /**
     * [bars] bars in one loop, the clicks only in the bars [clicksIn] lets through: to play on
     * one's own between reminders (and not be heard by the microphone).
     */
    fun renderBars(
        bpm: Int,
        beats: Int,
        subdivision: Int,
        bars: Int,
        clicksIn: (Int) -> Boolean,
        sampleRate: Int = LoopTrack.SAMPLE_RATE,
    ): ShortArray {
        val bar = render(bpm, beats, subdivision, accent = true, sampleRate)
        val out = ShortArray(bar.size * bars)
        for (b in 0 until bars) if (clicksIn(b)) bar.copyInto(out, b * bar.size)
        return out
    }

    /** A short woodblock-like click: a quickly decaying tone. */
    private fun click(frequency: Double, level: Double, sampleRate: Int): FloatArray =
        FloatArray((0.05 * sampleRate).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            (level * sin(2 * PI * frequency * t) * exp(-t * 90) * (t / 0.001).coerceAtMost(1.0)).toFloat()
        }
}
