package com.udl.oud.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sqrt

/** One analysis frame: detected pitch (null if none) and the loudness of the newest samples. */
data class PitchReading(val pitch: PitchEstimate?, val rms: Float)

/** Reads the microphone and emits a [PitchReading] every [hopSize] samples. */
class MicPitchSource(
    private val sampleRate: Int = 44100,
    private val windowSize: Int = 4096,
    private val hopSize: Int = 1024,
) {
    private val detector = YinPitchDetector(sampleRate, windowSize)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun readings(): Flow<PitchReading> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        // VOICE_RECOGNITION skips most of the voice-call processing (AGC, noise suppression).
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, hopSize * 2 * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Микрофон недоступен" }

        val window = FloatArray(windowSize)
        val hop = ShortArray(hopSize)
        record.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < hopSize) {
                    val n = record.read(hop, read, hopSize - read)
                    check(n >= 0) { "Ошибка чтения микрофона ($n)" }
                    read += n
                }

                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                val offset = windowSize - hopSize
                var sumSquares = 0f
                for (i in 0 until hopSize) {
                    val s = hop[i] / 32768f
                    window[offset + i] = s
                    sumSquares += s * s
                }
                emit(PitchReading(detector.detect(window), sqrt(sumSquares / hopSize)))
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
