package com.udl.oud.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Plays a short plucked-string-like tone to tune against by ear. */
class ReferenceTone(private val sampleRate: Int = 44100) {
    private var track: AudioTrack? = null

    // Relative harmonic amplitudes; higher harmonics decay faster, like a plucked string.
    private val harmonics = floatArrayOf(1f, 0.5f, 0.3f, 0.15f, 0.08f)

    @Synchronized
    fun play(frequency: Double, durationMs: Int = DURATION_MS) {
        stop()
        val n = sampleRate * durationMs / 1000
        val pcm = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var s = 0.0
            for (h in harmonics.indices) {
                s += harmonics[h] * sin(2 * PI * frequency * (h + 1) * t) * exp(-t * (1.5 + h))
            }
            val attack = min(1.0, t / 0.005)
            pcm[i] = (s * attack * 0.35 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        newTrack.write(pcm, 0, n)
        newTrack.play()
        track = newTrack
    }

    @Synchronized
    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    companion object {
        const val DURATION_MS = 2000
    }
}
