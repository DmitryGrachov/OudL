package com.udl.oud.audio

import android.Manifest
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.udl.oud.fretboard.SteadyNote
import com.udl.oud.tuner.centsBetween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The microphone for a practice screen: readings become steady pitches ([PitchTracker]), and it
 * hears nothing while the app itself is sounding, nor for a moment after (the speaker's last
 * note still rings). [resolve] settles the octave of each reading; [onUpdate] gets the rest.
 */
class MicListener(
    private val scope: CoroutineScope,
    private val resolve: (PitchEstimate) -> Double = { it.frequency.toDouble() },
    private val onError: (String) -> Unit,
    private val onUpdate: (PitchTracker.Update, Long) -> Unit,
) {
    private val source = MicPitchSource()
    private val tracker = PitchTracker()
    private var job: Job? = null
    @Volatile private var quietUntil = 0L

    val listening: Boolean get() = job?.isActive == true

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (listening) return
        job = scope.launch {
            source.readings()
                .catch { e -> onError(e.message ?: "Ошибка микрофона") }
                .collect { r ->
                    val now = SystemClock.elapsedRealtime()
                    if (now >= quietUntil) onUpdate(tracker.update(r, now, resolve), now)
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tracker.reset()
    }

    /** The app starts sounding: hear nothing until [unmute]. */
    fun mute() {
        quietUntil = Long.MAX_VALUE
    }

    /** The app sounds for about [ms] from now (a reference tone): hear nothing until it is over. */
    fun quietFor(ms: Long) {
        quietUntil = SystemClock.elapsedRealtime() + ms
        tracker.reset()
    }

    /** The app has stopped: wait out the ring of its last note, then listen afresh. */
    fun unmute(graceMs: Long = GRACE_MS) {
        quietUntil = SystemClock.elapsedRealtime() + graceMs
        tracker.reset()
    }

    companion object {
        const val GRACE_MS = 600L

        /** An octave resolver for a known note due: a reading that may be an octave high is taken nearer to it. */
        fun towards(expected: () -> Double?): (PitchEstimate) -> Double = { p ->
            val f = p.frequency.toDouble()
            val t = expected()
            if (t != null && p.canBeOctaveLower && abs(centsBetween(f / 2, t)) < abs(centsBetween(f, t))) f / 2 else f
        }
    }
}

/**
 * A drill taking one note at a time: a note counts once it rings steady near the [target], and
 * the next only after a new pluck (the last one keeps ringing).
 */
class DrillTaker {
    private val steady = SteadyNote()
    private var awaitingPluck = false

    fun reset() {
        steady.reset()
        awaitingPluck = false
    }

    /** The offset in cents of the note just taken, or null while there is none. */
    fun feed(update: PitchTracker.Update, target: Double?): Double? {
        if (update.pluck) {
            awaitingPluck = false
            steady.reset()
        }
        if (target == null || awaitingPluck) return null
        val f = update.steady ?: return null
        return steady.add(centsBetween(f, target))?.also { awaitingPluck = true }
    }
}
