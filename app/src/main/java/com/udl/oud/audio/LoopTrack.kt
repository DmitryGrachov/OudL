package com.udl.oud.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays a PCM buffer over and over without gaps, until stopped. */
class LoopTrack(private val sampleRate: Int = SAMPLE_RATE) {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(pcm: ShortArray, volume: Float) {
        stop()
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
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        newTrack.write(pcm, 0, pcm.size)
        newTrack.setLoopPoints(0, pcm.size, -1)
        newTrack.setVolume(volume)
        newTrack.play()
        track = newTrack
    }

    @Synchronized
    fun setVolume(volume: Float) {
        track?.setVolume(volume)
    }

    /** Samples played since [play], or null when stopped; wraps around with the loop. */
    @Synchronized
    fun position(): Int? = track?.playbackHeadPosition

    @Synchronized
    fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    companion object {
        const val SAMPLE_RATE = 44100
    }
}

/** Converts a mix in −1…1 to 16-bit PCM, clipping anything louder. */
internal fun FloatArray.toPcm(): ShortArray = ShortArray(size) {
    (this[it] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
