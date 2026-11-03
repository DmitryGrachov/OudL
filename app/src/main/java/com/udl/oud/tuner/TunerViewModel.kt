package com.udl.oud.tuner

import android.Manifest
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.MicPitchSource
import com.udl.oud.audio.PitchReading
import com.udl.oud.audio.ReferenceTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

enum class TunerMode {
    /** Each string against the tuning. */
    Tuning,

    /** The two strings of one course against each other. */
    Course,
}

data class TunerUiState(
    val tuning: Tuning = Tunings.all.first(),
    val a4: Int = 440,
    val mode: TunerMode = TunerMode.Tuning,
    /** Course chosen by the user; null means the nearest course is picked automatically. */
    val lockedCourse: Int? = null,
    val frequency: Double? = null,
    /** Course mode: pitch of the first string of the pair, once it has been captured. */
    val firstString: Double? = null,
    val error: String? = null,
) {
    val targetCourse: Int?
        get() = lockedCourse ?: frequency?.let { tuning.nearestCourse(it, a4.toDouble()) }

    /** Offset of the heard pitch: from the first string once captured, else from the target course. */
    val cents: Double?
        get() {
            val f = frequency ?: return null
            firstString?.let { return centsBetween(f, it) }
            return centsFromTarget(f)
        }

    /** Course mode: how far the captured first string is from the tuning. */
    val firstStringCents: Double?
        get() = firstString?.let { centsFromTarget(it) }

    /** Matching two strings of a course needs a tighter tolerance than tuning to the scale. */
    val toleranceCents: Double
        get() = if (firstString != null) COURSE_TOLERANCE_CENTS else TUNING_TOLERANCE_CENTS

    val inTune: Boolean
        get() = cents?.let { abs(it) <= toleranceCents } ?: false

    val heardNote: Note?
        get() = frequency?.let { Note.nearest(it, a4.toDouble()) }

    private fun centsFromTarget(f: Double): Double? {
        val course = targetCourse ?: return null
        return centsBetween(f, tuning.courses[course].frequency(a4.toDouble()))
    }

    companion object {
        const val TUNING_TOLERANCE_CENTS = 5.0
        const val COURSE_TOLERANCE_CENTS = 3.0
    }
}

class TunerViewModel : ViewModel() {
    private val _state = MutableStateFlow(TunerUiState())
    val state = _state.asStateFlow()

    private val mic = MicPitchSource()
    private val tone = ReferenceTone()
    private var listenJob: Job? = null

    private val recent = ArrayDeque<Double>()
    private var smoothed: Double? = null
    private var previousRms = 0f
    private var onsetAt = 0L
    private var lastHeardAt = 0L
    private var ignoreMicUntil = 0L

    /** Course mode: steady readings of the first string, captured once they agree. */
    private val captureWindow = ArrayDeque<Double>()

    /** Course mode: the first string still rings after capture, so wait for a fresh pluck. */
    private var awaitingSecondPluck = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (listenJob?.isActive == true) return
        _state.update { it.copy(error = null) }
        listenJob = viewModelScope.launch {
            mic.readings()
                .catch { e -> _state.update { it.copy(error = e.message ?: "Ошибка микрофона") } }
                .collect(::onReading)
        }
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
    }

    fun selectTuning(tuning: Tuning) {
        _state.update {
            it.copy(tuning = tuning, lockedCourse = if (it.mode == TunerMode.Course) 0 else null)
        }
        restartCourse()
    }

    fun setAuto() = _state.update { it.copy(lockedCourse = null) }

    fun changeA4(delta: Int) = _state.update { it.copy(a4 = (it.a4 + delta).coerceIn(A4_MIN, A4_MAX)) }

    fun setMode(mode: TunerMode) {
        _state.update {
            it.copy(
                mode = mode,
                // Course mode always works on one chosen course.
                lockedCourse = if (mode == TunerMode.Course) it.targetCourse ?: 0 else it.lockedCourse,
            )
        }
        restartCourse()
    }

    /** Course mode: forget the first string and capture it again. */
    fun restartCourse() {
        _state.update { it.copy(firstString = null, frequency = null) }
        captureWindow.clear()
        awaitingSecondPluck = false
        resetHistory()
    }

    /** Tuning mode: locks onto a course and plays its reference tone. Course mode: picks the course. */
    fun onCourseTap(course: Int) {
        _state.update { it.copy(lockedCourse = course, frequency = null) }
        if (_state.value.mode == TunerMode.Course) {
            restartCourse()
            return
        }
        resetHistory()
        // Don't let the speaker's own tone show up on the gauge.
        ignoreMicUntil = SystemClock.elapsedRealtime() + ReferenceTone.DURATION_MS + 200
        val s = _state.value
        val frequency = s.tuning.courses[course].frequency(s.a4.toDouble())
        viewModelScope.launch(Dispatchers.Default) { tone.play(frequency) }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return

        val loud = reading.rms >= SILENCE_RMS
        // A sudden jump in loudness is a new pluck. A string rings sharp for its first instants,
        // so readings from the attack are skipped instead of swinging the needle.
        if (loud && reading.rms > previousRms * ONSET_RATIO) {
            onsetAt = now
            resetHistory()
            awaitingSecondPluck = false
        }
        previousRms = reading.rms
        if (awaitingSecondPluck) return

        val pitch = reading.pitch
        val s = _state.value
        val f = pitch?.let {
            s.tuning.resolveOctave(it.frequency.toDouble(), it.canBeOctaveLower, s.lockedCourse, s.a4.toDouble())
        }
        if (!loud || f == null || f < MIN_FREQUENCY || f > MAX_FREQUENCY || now - onsetAt < ATTACK_SKIP_MS) {
            if (s.frequency != null && now - lastHeardAt > HOLD_MS) {
                resetHistory()
                _state.update { it.copy(frequency = null) }
            }
            return
        }
        lastHeardAt = now

        // A jump of more than ~a semitone is a new note: drop the old history.
        recent.lastOrNull()?.let { if (abs(centsBetween(f, it)) > 80) resetHistory() }
        recent.addLast(f)
        if (recent.size > MEDIAN_SIZE) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]

        // Exponential smoothing in cents, so the needle settles instead of jittering.
        val next = smoothed?.let { it * 2.0.pow(SMOOTHING * centsBetween(median, it) / 1200) } ?: median
        smoothed = next
        _state.update { it.copy(frequency = next) }

        if (s.mode == TunerMode.Course && s.firstString == null) captureFirstString(median)
    }

    /** Takes the first string's pitch once enough consecutive readings agree within a few cents. */
    private fun captureFirstString(reading: Double) {
        captureWindow.addLast(reading)
        if (captureWindow.size > CAPTURE_READINGS) captureWindow.removeFirst()
        if (captureWindow.size < CAPTURE_READINGS) return
        if (centsBetween(captureWindow.max(), captureWindow.min()) > CAPTURE_SPREAD_CENTS) return

        val captured = captureWindow.sorted()[captureWindow.size / 2]
        _state.update { it.copy(firstString = captured, frequency = null) }
        awaitingSecondPluck = true
        resetHistory()
    }

    private fun resetHistory() {
        recent.clear()
        smoothed = null
        captureWindow.clear()
    }

    override fun onCleared() {
        tone.stop()
    }

    companion object {
        private const val A4_MIN = 425
        private const val A4_MAX = 455
        private const val MIN_FREQUENCY = 55.0
        private const val MAX_FREQUENCY = 1200.0
        private const val SILENCE_RMS = 0.008f
        private const val HOLD_MS = 1500L
        private const val MEDIAN_SIZE = 5
        private const val ONSET_RATIO = 2f
        private const val ATTACK_SKIP_MS = 150L
        private const val SMOOTHING = 0.35
        private const val CAPTURE_READINGS = 12
        private const val CAPTURE_SPREAD_CENTS = 4.0
    }
}
