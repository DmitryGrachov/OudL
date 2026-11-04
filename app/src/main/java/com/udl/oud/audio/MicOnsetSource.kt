package com.udl.oud.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hears strokes rather than notes: a sudden rise in loudness over short [hopSize] frames is an
 * onset. Each is stamped in [System.nanoTime] from the recorder's own clock, so its time does
 * not depend on how late the buffer reaches us.
 */
class MicOnsetSource(private val sampleRate: Int = 44100, private val hopSize: Int = 256) {
    /** A stroke heard: when (in [System.nanoTime]) and how loud its attack was (peak RMS, 0..1). */
    data class Onset(val ns: Long, val level: Double)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun onsets(): Flow<Long> = strokes().map { it.ns }

    /** Onsets with their loudness: the peak of the first frames after each, so the attack is caught whole. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun strokes(): Flow<Onset> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, hopSize * 16),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Микрофон недоступен" }
        val hop = ShortArray(hopSize)
        val stamp = AudioTimestamp()
        val detector = OnsetDetector()
        var frames = 0L
        // Without the recorder's clock, fall back to when the buffer arrived.
        val startedNs = System.nanoTime()
        var pendingNs = 0L
        var pendingPeak = 0.0
        var pendingFrames = 0
        record.startRecording()
        try {
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < hopSize) {
                    val n = record.read(hop, read, hopSize - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < hopSize) continue
                var sum = 0.0
                for (s in hop) sum += (s / 32768.0) * (s / 32768.0)
                val rms = sqrt(sum / hopSize)
                if (pendingFrames > 0) {
                    pendingPeak = maxOf(pendingPeak, rms)
                    if (--pendingFrames == 0) emit(Onset(pendingNs, pendingPeak))
                }
                if (detector.feed(rms)) {
                    if (pendingFrames > 0) emit(Onset(pendingNs, pendingPeak))
                    pendingNs = if (record.getTimestamp(stamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        stamp.nanoTime + (frames - stamp.framePosition) * 1_000_000_000L / sampleRate
                    } else {
                        startedNs + frames * 1_000_000_000L / sampleRate
                    }
                    pendingPeak = rms
                    pendingFrames = PEAK_FRAMES
                }
                frames += hopSize
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** Frames after an onset over which its loudness is taken (about 17 ms). */
        const val PEAK_FRAMES = 3
    }
}

/**
 * An onset is a frame clearly louder than both the noise floor and the frames just before it,
 * at least [refractoryFrames] after the last one (a string or a drum rings on after its attack).
 */
class OnsetDetector(
    private val ratio: Double = 2.2,
    private val minRms: Double = 0.015,
    private val refractoryFrames: Int = 14,
) {
    private var floor = 0.002
    private var previous = 0.0
    private var sinceLast = Int.MAX_VALUE / 2

    fun feed(rms: Double): Boolean {
        sinceLast++
        val onset = rms > minRms && rms > floor * 4 && rms > previous * ratio && sinceLast >= refractoryFrames
        if (onset) sinceLast = 0
        // The floor follows the quiet frames slowly, the loud ones barely.
        floor = if (rms < floor) floor * 0.9 + rms * 0.1 else floor * 0.999 + rms * 0.001
        previous = rms
        return onset
    }
}
