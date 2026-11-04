package com.udl.oud.taqsim

import com.udl.oud.audio.PcmPlayer
import com.udl.oud.audio.PluckSynth
import com.udl.oud.tuner.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Sounds what the practice screens play: phrases of a taqsim as the renderer gives them, or a
 * plain run of notes; tells which note is sounding as it goes. Cancel the calling job to stop.
 */
class PhrasePlayer(private val sampleRate: Int = SAMPLE_RATE) {
    private val synth = PluckSynth(sampleRate)
    private val player = PcmPlayer(sampleRate)

    /**
     * Plays [phrases] of [taqsim]; [onNote] gets the index of the note sounding among those
     * played (null between and after). Plays again after a pause while [loop] says so.
     */
    suspend fun play(
        taqsim: Taqsim,
        phrases: IntRange,
        pulseMs: Double,
        a4: Double,
        tuning: Tuning,
        ornaments: Map<Int, Ornament>,
        pitchScale: Double,
        onNote: (Int?) -> Unit,
        loop: () -> Boolean = { false },
    ) {
        val rendered = withContext(Dispatchers.Default) {
            TaqsimRenderer.render(synth, taqsim, phrases, pulseMs, a4, tuning, ornaments, sampleRate, pitchScale)
        }
        coroutineScope {
            do {
                follow(rendered.pcm, rendered.starts, rendered.totalMs - rendered.tailMs + 400, onNote)
                if (loop() && isActive) delay(LOOP_PAUSE_MS)
            } while (loop() && isActive)
        }
    }

    /** A run of notes, [hz] as they sound, each held for its length. */
    suspend fun playTones(hz: List<Double>, lengthsMs: List<Double>, onTone: (Int?) -> Unit) {
        val starts = lengthsMs.runningFold(0.0) { acc, l -> acc + l }.dropLast(1)
        val totalMs = starts.last() + lengthsMs.last() + TONE_TAIL_MS
        val pcm = withContext(Dispatchers.Default) {
            val plucks = hz.indices.map { i ->
                PluckSynth.Pluck(hz[i], (starts[i] * sampleRate / 1000).toInt(), (lengthsMs[i] * sampleRate / 1000).toInt())
            }
            PluckSynth.toPcm(synth.render(plucks, (totalMs * sampleRate / 1000).toInt()))
        }
        follow(pcm, starts, totalMs - TONE_TAIL_MS + 300, onTone)
    }

    /** Plays [pcm], reporting the index of the start last passed until [litUntilMs]. */
    private suspend fun follow(pcm: ShortArray, starts: List<Double>, litUntilMs: Double, onIndex: (Int?) -> Unit) = coroutineScope {
        val ticker = launch {
            while (isActive) {
                player.positionMs()?.let { pos ->
                    onIndex(starts.indexOfLast { it <= pos }.takeIf { it >= 0 && pos < litUntilMs })
                }
                delay(TICK_MS)
            }
        }
        try {
            player.play(pcm)
        } finally {
            ticker.cancel()
            onIndex(null)
        }
    }

    companion object {
        const val SAMPLE_RATE = 44100
        private const val TICK_MS = 25L
        private const val LOOP_PAUSE_MS = 900L
        private const val TONE_TAIL_MS = 900.0
    }
}
