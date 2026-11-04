package com.udl.oud.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/**
 * A plucked-string synth (Karplus–Strong): a burst of noise circulating in a delay line one
 * period long, softened a little on every pass, as a plucked string loses its brightness. A
 * fractional-delay allpass keeps the pitch exact enough for quarter tones. Each note is a pair
 * of strings a hair apart, like an oud course. Notes are monophonic: the next sound damps the
 * one before, as the finger leaves the string.
 */
class PluckSynth(private val sampleRate: Int = 44100) {
    /**
     * A note: [frequency], starting at sample [start], held for [length] samples before it is
     * damped. A [legato] note is sounded by the left hand alone (hammer-on, pull-off): softer and
     * darker than a pluck. A slide starts [slideFromCents] below and glides up over [slideSamples].
     */
    data class Pluck(
        val frequency: Double,
        val start: Int,
        val length: Int,
        val gain: Float = 1f,
        val legato: Boolean = false,
        val slideFromCents: Double = 0.0,
        val slideSamples: Int = 0,
    )

    fun render(plucks: List<Pluck>, totalSamples: Int, seed: Long = 1): FloatArray {
        val out = FloatArray(totalSamples)
        val random = Random(seed)
        plucks.forEach { p ->
            for (detune in listOf(-COURSE_CENTS, COURSE_CENTS)) {
                renderString(out, p.copy(frequency = p.frequency * 2.0.pow(detune / 1200)), random)
            }
        }
        // One-pole high-pass against rumble, then normalise.
        var prevIn = 0f
        var prevOut = 0f
        for (i in out.indices) {
            val y = out[i] - prevIn + 0.995f * prevOut
            prevIn = out[i]
            prevOut = y
            out[i] = y
        }
        val peak = out.maxOfOrNull { abs(it) } ?: 0f
        if (peak > 0f) {
            val gain = PEAK / peak
            for (i in out.indices) out[i] *= gain
        }
        return out
    }

    private fun renderString(out: FloatArray, p: Pluck, random: Random) {
        val fade = (FADE_SECONDS * sampleRate).toInt()
        val total = minOf(out.size - p.start, p.length + fade)
        if (total <= 0) return
        // A slide reads the note slower at first; render enough to cover what is read.
        val voice = string(p.frequency, p.length, total + p.slideSamples, p.legato, random)

        if (p.slideSamples <= 0 || p.slideFromCents <= 0.0) {
            for (t in 0 until total) out[p.start + t] += voice[t] * envelope(t, p.length, fade) * p.gain * 0.5f
            return
        }
        var read = 0.0
        for (t in 0 until total) {
            // Ease out: most of the glide happens early, as the finger slides and settles.
            val progress = (t.toDouble() / p.slideSamples).coerceAtMost(1.0)
            val cents = p.slideFromCents * (1 - progress) * (1 - progress)
            val i = read.toInt()
            if (i + 1 >= voice.size) break
            val frac = (read - i).toFloat()
            val s = voice[i] * (1 - frac) + voice[i + 1] * frac
            out[p.start + t] += s * envelope(t, p.length, fade) * p.gain * 0.5f
            read += 2.0.pow(-cents / 1200)
        }
    }

    private fun envelope(t: Int, length: Int, fade: Int): Float = if (t < length) 1f else 1f - (t - length).toFloat() / fade

    /** One string sounding [frequency] for [samples] samples, damped after [held]. */
    private fun string(frequency: Double, held: Int, samples: Int, legato: Boolean, random: Random): FloatArray {
        val period = sampleRate / frequency
        // Loop delay: N samples of buffer, half a sample in the averaging filter, the rest in the allpass.
        var n = floor(period - 0.5).toInt()
        var frac = period - 0.5 - n
        if (frac < 0.1) {
            n -= 1
            frac += 1.0
        }
        val c = ((1 - frac) / (1 + frac)).toFloat()
        val buffer = FloatArray(n)
        // Excitation: noise, softened — a fingertip / risha; a left-hand note is darker still.
        val smooth = if (legato) 0.8f else 0.55f
        var lp = 0f
        for (i in 0 until n) {
            lp = smooth * lp + (1 - smooth) * (random.nextFloat() * 2 - 1)
            buffer[i] = lp
        }
        val mean = buffer.average().toFloat()
        for (i in 0 until n) buffer[i] -= mean

        // Ringing loss per pass: about RING_SECONDS to fall 60 dB, faster once damped.
        val ring = 10.0.pow(-3.0 * period / (RING_SECONDS * sampleRate)).toFloat()
        val damped = 10.0.pow(-3.0 * period / (DAMP_SECONDS * sampleRate)).toFloat()
        val voice = FloatArray(samples)
        var idx = 0
        var prev = 0f
        var apIn = 0f
        var apOut = 0f
        for (t in 0 until samples) {
            val y = buffer[idx]
            val avg = 0.5f * (y + prev) * (if (t < held) ring else damped)
            val ap = c * avg + apIn - c * apOut
            apIn = avg
            apOut = ap
            buffer[idx] = ap
            prev = y
            idx = if (idx + 1 == n) 0 else idx + 1
            voice[t] = y
        }
        return voice
    }

    companion object {
        /** Half the spread between the two strings of a course. */
        const val COURSE_CENTS = 1.5
        const val RING_SECONDS = 2.6
        const val DAMP_SECONDS = 0.12
        const val FADE_SECONDS = 0.05
        const val PEAK = 0.8f

        fun toPcm(samples: FloatArray): ShortArray =
            ShortArray(samples.size) { (samples[it] * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
