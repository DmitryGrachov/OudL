package com.udl.oud.fretboard

import android.Manifest
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.MicPitchSource
import com.udl.oud.audio.PitchReading
import com.udl.oud.audio.PitchTracker
import com.udl.oud.audio.ReferenceTone
import com.udl.oud.tuner.OudSettings
import com.udl.oud.tuner.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FretboardUiState(
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    val labels: NoteLabels = NoteLabels.Solfege,
    val showAccidentals: Boolean = true,
    val showQuarterTones: Boolean = false,
    val showFingers: Boolean = false,
    /** Bass course at the top, as the neck looks to the player holding the oud. */
    val bassOnTop: Boolean = true,
    /** Show positions past the neck, over the body, up to the octave. */
    val extended: Boolean = false,
    /** Light up what is being played. */
    val live: Boolean = true,
    /** Course the player says they are playing on; null means any. */
    val onlyCourse: Int? = null,
    /** Maqam whose notes are shown instead of the chromatic layers; null shows all notes. */
    val maqam: Maqam? = null,
    /** Tonic the maqam is built on, as a grid step (2 × MIDI). */
    val maqamTonic: Int = 0,
    val frequency: Double? = null,
    val error: String? = null,
) {
    val maxCents: Int
        get() = if (extended) Fretboard.EXTENDED_CENTS else Fretboard.NECK_CENTS

    /** Maqam degrees by pitch class (grid step mod 24), or null when no maqam is shown. */
    val scale: Map<Int, ScaleNote>?
        get() = maqam?.notes(maqamTonic)?.associateBy { (maqamTonic + it.offset).mod(24) }

    /** Where the heard note can be played, and its offset from the nearest grid pitch. */
    val heard: Pair<List<Position>, Double>?
        get() = frequency?.let {
            // Maqam notes include quarter tones, so measure against them even if that layer is off.
            val quarterTones = showQuarterTones || maqam != null
            Fretboard.matches(tuning, it, a4.toDouble(), quarterTones, onlyCourse, maxCents)
        }

    fun isVisible(position: Position, scale: Map<Int, ScaleNote>?): Boolean = when {
        scale != null -> position.pitch.step.mod(24) in scale
        position.pitch.isQuarterTone -> showQuarterTones
        !position.pitch.isNatural -> showAccidentals
        else -> true
    }

    companion object {
        /** Looser than the tuner: this is about the left hand landing in the right place. */
        const val IN_TUNE_CENTS = 10.0
    }
}

class FretboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(FretboardUiState())
    val state = _state.asStateFlow()

    private val mic = MicPitchSource()
    private val tone = ReferenceTone()
    private val tracker = PitchTracker()
    private var listenJob: Job? = null
    private var scaleJob: Job? = null
    private var ignoreMicUntil = 0L

    init {
        viewModelScope.launch {
            combine(OudSettings.tuning, OudSettings.a4) { tuning, a4 -> tuning to a4 }.collect { (tuning, a4) ->
                _state.update { it.copy(tuning = tuning, a4 = a4, onlyCourse = null) }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (listenJob?.isActive == true || !_state.value.live) return
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
        tracker.reset()
        _state.update { it.copy(frequency = null) }
    }

    fun setLabels(labels: NoteLabels) = _state.update { it.copy(labels = labels) }
    fun toggleAccidentals() = _state.update { it.copy(showAccidentals = !it.showAccidentals) }
    fun toggleQuarterTones() = _state.update { it.copy(showQuarterTones = !it.showQuarterTones) }
    fun toggleFingers() = _state.update { it.copy(showFingers = !it.showFingers) }
    fun flip() = _state.update { it.copy(bassOnTop = !it.bassOnTop) }
    fun toggleExtended() = _state.update { it.copy(extended = !it.extended) }
    fun setOnlyCourse(course: Int?) = _state.update { it.copy(onlyCourse = course) }

    fun setMaqam(maqam: Maqam?) = _state.update { it.copy(maqam = maqam, maqamTonic = maqam?.tonic ?: 0) }

    /** Moves the maqam to another tonic, [semitones] up or down. */
    fun shiftTonic(semitones: Int) = _state.update {
        it.copy(maqamTonic = (it.maqamTonic + semitones * 2).coerceIn(TONIC_MIN, TONIC_MAX))
    }

    /** Plays the maqam up from its tonic to the octave. */
    fun playScale() {
        val s = _state.value
        val maqam = s.maqam ?: return
        val steps = maqam.notes(s.maqamTonic).map { s.maqamTonic + it.offset } + (s.maqamTonic + 24)
        ignoreMicUntil = SystemClock.elapsedRealtime() + steps.size * SCALE_NOTE_MS + SCALE_TONE_MS
        tracker.reset()
        _state.update { it.copy(frequency = null) }
        scaleJob?.cancel()
        scaleJob = viewModelScope.launch(Dispatchers.Default) {
            steps.forEach { step ->
                tone.play(Pitch(step).frequency(s.a4.toDouble()), SCALE_TONE_MS.toInt())
                delay(SCALE_NOTE_MS)
            }
        }
    }

    /** Turning live mode off releases the microphone; the screen calls [start] again when it is on. */
    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stop()
    }

    fun play(position: Position) {
        val frequency = position.pitch.frequency(_state.value.a4.toDouble())
        // Don't light up the speaker's own tone.
        ignoreMicUntil = SystemClock.elapsedRealtime() + ReferenceTone.DURATION_MS + 200
        tracker.reset()
        _state.update { it.copy(frequency = null) }
        viewModelScope.launch(Dispatchers.Default) { tone.play(frequency) }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val update = tracker.update(reading, now) {
            Fretboard.resolveOctave(
                s.tuning, it.frequency.toDouble(), it.canBeOctaveLower, s.a4.toDouble(), s.onlyCourse, s.maxCents,
            )
        }
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
    }

    override fun onCleared() {
        tone.stop()
    }

    private companion object {
        const val TONIC_MIN = 72 // C2
        const val TONIC_MAX = 120 // C4
        const val SCALE_NOTE_MS = 550L
        const val SCALE_TONE_MS = 900L
    }
}
