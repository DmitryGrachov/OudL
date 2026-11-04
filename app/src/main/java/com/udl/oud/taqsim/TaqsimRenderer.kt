package com.udl.oud.taqsim

import com.udl.oud.audio.PluckSynth
import com.udl.oud.tuner.Tuning

/** A rendered stretch of a taqsim: the sound, and when each of its notes starts (ms from the start). */
class RenderedTaqsim(val pcm: ShortArray, val starts: List<Double>, val totalMs: Double, val tailMs: Double)

/**
 * Turns phrases of a taqsim into sound as a player would give them: each phrase at its own pace on
 * the arc, breaths and rests as silence, each note on its course with its ornament, its strength
 * and, when held, the left hand's vibrato. Shared by the taqsim tab and the jins etudes.
 */
object TaqsimRenderer {
    const val TAIL_MS = 1200.0

    fun render(
        synth: PluckSynth,
        taqsim: Taqsim,
        phrases: IntRange,
        pulseMs: Double,
        a4: Double,
        tuning: Tuning,
        ornaments: Map<Int, Ornament>,
        sampleRate: Int,
        /** Frequency factor against A4 = 440: the A4 chosen. */
        pitchScale: Double = a4 / 440.0,
    ): RenderedTaqsim {
        val first = taqsim.phrases.take(phrases.first).sumOf { it.notes.size }
        val notes = phrases.flatMap { taqsim.phrases[it].notes }
        val starts = mutableListOf<Double>()
        val lengths = mutableListOf<Double>()
        var t = 0.0
        phrases.forEach { pi ->
            val phrase = taqsim.phrases[pi]
            val p = pulseMs * phrase.tempo
            phrase.notes.forEach { n ->
                starts += t
                lengths += n.beats * p
                // A breath inside the phrase is silence after the note.
                t += (n.beats + n.pauseAfter) * p
            }
            if (pi != phrases.last) t += phrase.restBeats * p
        }
        val totalMs = t + TAIL_MS
        fun samples(ms: Double) = (ms * sampleRate / 1000).toInt()
        val plucks = notes.flatMapIndexed { i, n ->
            val course = OudTone.course(n.hz * pitchScale, tuning, a4)
            OrnamentSound.events(n.hz, ornaments[first + i], starts[i], lengths[i]).map { e ->
                // The left hand's vibrato on a held note, coming in after it has sounded a while.
                val vibrato = e.lengthMs >= OudTone.VIBRATO_FROM_MS
                PluckSynth.Pluck(
                    e.hz * pitchScale, samples(e.startMs), samples(e.lengthMs), e.gain * n.velocity, e.legato,
                    e.slideFromCents, samples(e.slideMs),
                    brightness = OudTone.brightness(course, tuning),
                    ringSeconds = OudTone.ring(course, tuning),
                    vibratoCents = if (vibrato) OudTone.VIBRATO_CENTS else 0.0,
                    vibratoDelaySamples = samples(OudTone.VIBRATO_DELAY_MS),
                )
            }
        }
        val pcm = PluckSynth.toPcm(synth.render(plucks, samples(totalMs), taqsim.seed))
        return RenderedTaqsim(pcm, starts, totalMs, TAIL_MS)
    }
}
