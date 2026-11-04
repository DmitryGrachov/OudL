package com.udl.oud.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A plucked-string synth (Karplus–Strong): a burst of noise circulating in a delay line one
 * period long, softened a little on every pass, as a plucked string loses its brightness. A
 * fractional-delay allpass keeps the pitch exact enough for quarter tones. Each note is a pair
 * of strings a hair apart, like an oud course, and the sum goes through two body resonances.
 * Notes are monophonic: the next sound damps the one before, as the finger leaves the string.
 */
class PluckSynth(private val sampleRate: Int = 44100) {
    /**
     * A note: [frequency], starting at sample [start], held for [length] samples before it is
     * damped, played with [gain]. A [legato] note is sounded by the left hand alone (hammer-on,
     * pull-off): softer and darker than a pluck. A slide starts [slideFromCents] below and glides
     * up over [slideSamples]. [brightness] (0 dark … 1 bright) and [ringSeconds] follow the
     * course it is played on: bass strings are darker and ring longer. [vibratoCents] is the
     * depth of the left hand's vibrato, starting after [vibratoDelaySamples].
     */
    data class Pluck(
        val frequency: Double,
        val start: Int,
        val length: Int,
        val gain: Float = 1f,
        val legato: Boolean = false,
        val slideFromCents: Double = 0.0,
        val slideSamples: Int = 0,
        val brightness: Float = 0.5f,
        val ringSeconds: Double = RING_SECONDS,
        val vibratoCents: Double = 0.0,
        val vibratoHz: Double = VIBRATO_HZ,
        val vibratoDelaySamples: Int = 0,
    )

    fun render(plucks: List<Pluck>, totalSamples: Int, seed: Long = 1): FloatArray {
        val dry = FloatArray(totalSamples)
        val random = Random(seed)
        plucks.forEach { p ->
            for (detune in listOf(-COURSE_CENTS, COURSE_CENTS)) {
                renderString(dry, p.copy(frequency = p.frequency * 2.0.pow(detune / 1200)), random)
            }
        }
        // The body: two broad resonances (the air cavity and the top plate), mixed under the strings.
        val air = bandPass(dry, 115.0, 2.0)
        val plate = bandPass(dry, 290.0, 2.5)
        val out = FloatArray(totalSamples) { dry[it] + 0.25f * air[it] + 0.18f * plate[it] }
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

    /** A biquad band-pass (RBJ cookbook, constant peak gain). */
    private fun bandPass(x: FloatArray, centre: Double, q: Double): FloatArray {
        val w = 2 * PI * centre / sampleRate
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        val b0 = (alpha / a0).toFloat()
        val b2 = (-alpha / a0).toFloat()
        val a1 = (-2 * cos(w) / a0).toFloat()
        val a2 = ((1 - alpha) / a0).toFloat()
        val y = FloatArray(x.size)
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        for (i in x.indices) {
            val v = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x[i]
            y2 = y1
            y1 = v
            y[i] = v
        }
        return y
    }

    private fun renderString(out: FloatArray, p: Pluck, random: Random) {
        val fade = (FADE_SECONDS * sampleRate).toInt()
        val total = minOf(out.size - p.start, p.length + fade)
        if (total <= 0) return
        val warped = (p.slideSamples > 0 && p.slideFromCents > 0.0) || p.vibratoCents > 0.0
        // Reading slower (a slide, the low half of a vibrato) needs a little more of the voice.
        val voice = string(p, total + p.slideSamples + total / 50 + 2, random)

        if (!warped) {
            for (t in 0 until total) out[p.start + t] += voice[t] * envelope(t, p.length, fade) * p.gain * 0.5f
            return
        }
        // Pitch moves by reading the string faster or slower: a slide eases up into the note, a
        // vibrato swings round it, coming in gently after its delay.
        var read = 0.0
        val vibratoRamp = 0.3 * sampleRate
        for (t in 0 until total) {
            var cents = 0.0
            if (p.slideSamples > 0) {
                val progress = (t.toDouble() / p.slideSamples).coerceAtMost(1.0)
                cents -= p.slideFromCents * (1 - progress) * (1 - progress)
            }
            if (p.vibratoCents > 0 && t > p.vibratoDelaySamples) {
                val ramp = ((t - p.vibratoDelaySamples) / vibratoRamp).coerceAtMost(1.0)
                cents += p.vibratoCents * ramp * sin(2 * PI * p.vibratoHz * (t - p.vibratoDelaySamples) / sampleRate)
            }
            val i = read.toInt()
            if (i + 1 >= voice.size) break
            val frac = (read - i).toFloat()
            val s = voice[i] * (1 - frac) + voice[i + 1] * frac
            out[p.start + t] += s * envelope(t, p.length, fade) * p.gain * 0.5f
            read += 2.0.pow(cents / 1200)
        }
    }

    private fun envelope(t: Int, length: Int, fade: Int): Float = if (t < length) 1f else 1f - (t - length).toFloat() / fade

    /** One string sounding the pluck's frequency for [samples] samples, damped after its length. */
    private fun string(p: Pluck, samples: Int, random: Random): FloatArray {
        val period = sampleRate / p.frequency
        // Loop delay: N samples of buffer, half a sample in the averaging filter, the rest in the allpass.
        var n = floor(period - 0.5).toInt()
        var frac = period - 0.5 - n
        if (frac < 0.1) {
            n -= 1
            frac += 1.0
        }
        val c = ((1 - frac) / (1 + frac)).toFloat()
        val buffer = FloatArray(n)
        // Excitation: noise, softened — more for a dark bass course or a left-hand note.
        val smooth = ((0.78f - 0.35f * p.brightness) + if (p.legato) 0.15f else 0f).coerceIn(0.3f, 0.92f)
        var lp = 0f
        for (i in 0 until n) {
            lp = smooth * lp + (1 - smooth) * (random.nextFloat() * 2 - 1)
            buffer[i] = lp
        }
        val mean = buffer.average().toFloat()
        for (i in 0 until n) buffer[i] -= mean

        // Ringing loss per pass: about ringSeconds to fall 60 dB, faster once damped.
        val ring = 10.0.pow(-3.0 * period / (p.ringSeconds * sampleRate)).toFloat()
        val damped = 10.0.pow(-3.0 * period / (DAMP_SECONDS * sampleRate)).toFloat()
        val voice = FloatArray(samples)
        var idx = 0
        var prev = 0f
        var apIn = 0f
        var apOut = 0f
        for (t in 0 until samples) {
            val y = buffer[idx]
            val avg = 0.5f * (y + prev) * (if (t < p.length) ring else damped)
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
        const val VIBRATO_HZ = 5.5
        const val PEAK = 0.8f

        fun toPcm(samples: FloatArray): ShortArray =
            ShortArray(samples.size) { (samples[it] * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
