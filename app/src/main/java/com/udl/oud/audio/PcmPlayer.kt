package com.udl.oud.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Streams a rendered buffer to the speaker and tells how far it has got. */
class PcmPlayer(private val sampleRate: Int = 44100) {
    @Volatile private var track: AudioTrack? = null

    /** Milliseconds played so far, or null when nothing is playing. */
    fun positionMs(): Long? = track?.let { it.playbackHeadPosition.toLong() * 1000 / sampleRate }

    /** Plays [pcm] to the end; cancelling the caller stops it at once. */
    suspend fun play(pcm: ShortArray) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 4)
            .build()
        track = t
        try {
            t.play()
            var offset = 0
            while (offset < pcm.size && coroutineContext.isActive) {
                val written = t.write(pcm, offset, minOf(CHUNK, pcm.size - offset))
                if (written <= 0) break
                offset += written
            }
            while (coroutineContext.isActive && t.playbackHeadPosition < offset) delay(20)
        } finally {
            if (track === t) track = null
            runCatching { t.stop() }
            t.release()
        }
    }

    private companion object {
        const val CHUNK = 2048
    }
}
