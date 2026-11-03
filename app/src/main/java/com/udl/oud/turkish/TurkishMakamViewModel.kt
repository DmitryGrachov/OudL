package com.udl.oud.turkish

import android.Manifest
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.MicPitchSource
import com.udl.oud.audio.PitchReading
import com.udl.oud.audio.PitchTracker
import com.udl.oud.audio.ReferenceTone
import com.udl.oud.fretboard.Drill
import com.udl.oud.fretboard.SteadyNote
import com.udl.oud.tuner.OudSettings
import com.udl.oud.tuner.Tuning
import com.udl.oud.tuner.centsBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

data class TurkishMakamUiState(
    val makam: TurkishMakam = TurkishMakams.all.first(),
    val labels: TurkishLabels = TurkishLabels.Perde,
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    val extended: Boolean = false,
    val live: Boolean = true,
    val frequency: Double? = null,
    /** Degree and octave the app is sounding now, to light it up. */
    val playing: Pair<Int, Int>? = null,
    /** Scale drill: steps index into [TurkishBoard.ascending]. */
    val exercise: Drill? = null,
    val error: String? = null,
) {
    val degrees: List<TurkishDegree> get() = makam.degrees()

    /** Nearest makam degree to what the mic hears, or null when silent or far from every degree. */
    val heard: TurkishHeard?
        get() = frequency?.let { TurkishBoard.nearest(makam, it, a4.toDouble()) }

    /** The drill's due note as (degree, octave). */
    val target: Pair<Int, Int>? get() = exercise?.target?.let { TurkishBoard.ascending()[it] }

    val exerciseCents: Double?
        get() {
            val (degree, octave) = target ?: return null
            val f = frequency ?: return null
            return centsBetween(f, frequencyOf(degree, octave))
        }

    fun frequencyOf(degree: Int, octave: Int): Double =
        TurkishBoard.degreeFrequencies(makam, a4.toDouble())[degree] * 2.0.pow(octave)

    /** Name of a degree in the chosen style: perde, written note, or the note as it sounds. */
    fun labelOf(degree: Int, octave: Int): String {
        val d = degrees[degree]
        val written = d.written + octave * Koma.PER_OCTAVE
        return when (labels) {
            TurkishLabels.Perde -> Perdes.of(written) ?: d.spelling.label()
            TurkishLabels.Written -> d.spelling.label()
            TurkishLabels.Sounding -> d.spelling.sounding(written).label()
        }
    }

    companion object {
        const val IN_TUNE_CENTS = 10.0

        /** Further than this from every degree, the note isn't in the makam. */
        const val OUTSIDE_CENTS = 40.0
    }
}

/** The Turkish makam tab: the board on the koma grid, live pitch against the makam, playback, drill. */
class TurkishMakamViewModel : ViewModel() {
    private val _state = MutableStateFlow(TurkishMakamUiState())
    val state = _state.asStateFlow()

    private val mic = MicPitchSource()
    private val tone = ReferenceTone()
    private val tracker = PitchTracker()
    private val steady = SteadyNote()
    private var listenJob: Job? = null
    private var playJob: Job? = null
    @Volatile private var playRun = 0
    private var ignoreMicUntil = 0L
    private var awaitingPluck = false

    init {
        viewModelScope.launch {
            combine(OudSettings.tuning, OudSettings.a4) { tuning, a4 -> tuning to a4 }.collect { (tuning, a4) ->
                _state.update { it.copy(tuning = tuning, a4 = a4) }
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

    fun select(makam: TurkishMakam) = _state.update { it.copy(makam = makam, exercise = null) }
    fun setLabels(labels: TurkishLabels) = _state.update { it.copy(labels = labels) }
    fun toggleExtended() = _state.update { it.copy(extended = !it.extended) }

    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stop()
    }

    fun play(degree: Int, octave: Int) = playSequence(listOf(degree to octave), NOTE_MS, ReferenceTone.DURATION_MS.toLong())

    fun playScale() {
        _state.update { it.copy(exercise = null) }
        playSequence(TurkishBoard.ascending(), SCALE_NOTE_MS, SCALE_TONE_MS)
    }

    fun startExercise() {
        steady.reset()
        awaitingPluck = false
        _state.update { it.copy(exercise = Drill(TurkishBoard.ascending().indices.toList()), live = true) }
    }

    fun stopExercise() = _state.update { it.copy(exercise = null) }

    private fun playSequence(notes: List<Pair<Int, Int>>, noteMs: Long, toneMs: Long) {
        val s = _state.value
        ignoreMicUntil = SystemClock.elapsedRealtime() + notes.size * noteMs + toneMs
        tracker.reset()
        _state.update { it.copy(frequency = null) }
        playJob?.cancel()
        // A cancelled run must not clear the highlight of the run that replaced it.
        val run = ++playRun
        playJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                notes.forEach { (degree, octave) ->
                    _state.update { it.copy(playing = degree to octave) }
                    tone.play(s.frequencyOf(degree, octave), toneMs.toInt())
                    delay(noteMs)
                }
                delay(HIGHLIGHT_TAIL_MS)
            } finally {
                if (run == playRun) _state.update { it.copy(playing = null) }
            }
        }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val targetFrequency = s.target?.let { (degree, octave) -> s.frequencyOf(degree, octave) }
        val update = tracker.update(reading, now) {
            val f = it.frequency.toDouble()
            // With a known target the octave question has a sure answer; otherwise keep the reading.
            if (targetFrequency != null && it.canBeOctaveLower &&
                abs(centsBetween(f / 2, targetFrequency)) < abs(centsBetween(f, targetFrequency))
            ) {
                f / 2
            } else {
                f
            }
        }
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }

        if (update.pluck) {
            awaitingPluck = false
            steady.reset()
        }
        if (targetFrequency == null || awaitingPluck) return
        val f = update.steady ?: return
        steady.add(centsBetween(f, targetFrequency))?.let { cents ->
            _state.update { it.copy(exercise = it.exercise?.record(cents)) }
            awaitingPluck = true
        }
    }

    override fun onCleared() {
        tone.stop()
    }

    private companion object {
        const val NOTE_MS = 900L
        const val SCALE_NOTE_MS = 550L
        const val SCALE_TONE_MS = 900L
        const val HIGHLIGHT_TAIL_MS = 300L
    }
}
