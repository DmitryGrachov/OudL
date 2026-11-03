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
import com.udl.oud.tuner.centsBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A maqam on a tonic, kept to come back to after a modulation. */
data class SayrStop(val scale: Scale, val tonic: Int)

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
    /** Maqam or scale whose notes are shown instead of the chromatic layers; null shows all notes. */
    val selected: Scale? = null,
    /** Tonic the selected scale is built on, as a grid step (2 × MIDI). */
    val tonic: Int = 0,
    /** Show the selected scale's descending form. */
    val descending: Boolean = false,
    /** Grid pitch the app is sounding right now (a tapped note or a scale), to light it up. */
    val playingStep: Int? = null,
    /** Scale or fretboard drill in progress or just finished; null when not drilling. */
    val exercise: Drill? = null,
    /** Fretboard drill: light up the spot asked for, not just name it. */
    val drillHint: Boolean = false,
    /** Show how the selected maqam unfolds: pillars on the board, path and modulations. */
    val sayrOpen: Boolean = false,
    /** Maqamat left by following modulations, most recent last, to step back through. */
    val sayrTrail: List<SayrStop> = emptyList(),
    val frequency: Double? = null,
    val error: String? = null,
) {
    /** How far the heard note is from the note the drill asks for. */
    val exerciseCents: Double?
        get() {
            val target = exercise?.target ?: return null
            val f = frequency ?: return null
            return centsBetween(f, Pitch(target).frequency(a4.toDouble()))
        }

    val maxCents: Int
        get() = if (extended) Fretboard.EXTENDED_CENTS else Fretboard.NECK_CENTS

    /** Degrees of the selected scale by pitch class (grid step mod 24), or null when none is shown. */
    val degrees: Map<Int, ScaleNote>?
        get() = selectedNotes?.associateBy { (tonic + it.offset).mod(24) }

    /** The selected scale's degrees bottom-up in the chosen direction, or null when none is shown. */
    val selectedNotes: List<ScaleNote>?
        get() = selected?.notes(tonic, descending)

    /** Maqam notes include quarter tones, so heard notes snap to them even if that layer is off. */
    val snapToQuarterTones: Boolean
        get() = showQuarterTones || selected is Maqam

    /** Where the heard note can be played, and its offset from the nearest grid pitch. */
    val heard: Pair<List<Position>, Double>?
        get() = frequency?.let {
            Fretboard.matches(tuning, it, a4.toDouble(), snapToQuarterTones, onlyCourse, maxCents)
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
    @Volatile private var playRun = 0
    private var ignoreMicUntil = 0L
    private val steady = SteadyNote()
    private var awaitingPluck = false

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

    // Changing the scale, tonic or direction ends a drill: its notes no longer match.

    /** Picking a scale by hand starts a fresh path: the modulation trail is dropped. */
    fun select(scale: Scale?) = _state.update {
        it.copy(selected = scale, tonic = scale?.tonic ?: 0, descending = false, exercise = null, sayrTrail = emptyList())
    }

    fun toggleSayr() = _state.update { it.copy(sayrOpen = !it.sayrOpen) }

    /** Follows a modulation from the sayr, remembering where it came from. */
    fun modulate(maqam: Maqam, tonic: Int) = _state.update {
        val from = it.selected ?: return@update it
        it.copy(
            selected = maqam,
            tonic = tonic,
            descending = false,
            exercise = null,
            sayrTrail = it.sayrTrail + SayrStop(from, it.tonic),
        )
    }

    /** Returns to the maqam the last modulation left. */
    fun modulateBack() = _state.update {
        val stop = it.sayrTrail.lastOrNull() ?: return@update it
        it.copy(selected = stop.scale, tonic = stop.tonic, descending = false, exercise = null, sayrTrail = it.sayrTrail.dropLast(1))
    }

    fun setDescending(descending: Boolean) = _state.update { it.copy(descending = descending, exercise = null) }

    /** Moves the scale to another tonic, [semitones] up or down. */
    fun shiftTonic(semitones: Int) = _state.update {
        it.copy(tonic = (it.tonic + semitones * 2).coerceIn(TONIC_MIN, TONIC_MAX), exercise = null)
    }

    /** Tonic to octave, or octave down to tonic in the descending form, as grid pitches. */
    private fun scaleSteps(s: FretboardUiState): List<Int>? {
        val notes = s.selectedNotes ?: return null
        val up = notes.map { s.tonic + it.offset } + (s.tonic + 24)
        return if (s.descending) up.reversed() else up
    }

    /** Plays the scale from tonic to octave, or from the octave down in its descending form. */
    fun playScale() {
        val steps = scaleSteps(_state.value) ?: return
        _state.update { it.copy(exercise = null) }
        playSequence(steps, SCALE_NOTE_MS, SCALE_TONE_MS)
    }

    /** Starts the "play the scale" drill in the current direction. */
    fun startExercise() {
        val steps = scaleSteps(_state.value) ?: return
        beginDrill(Drill(steps))
    }

    /**
     * Starts the fretboard drill: random spots among those shown right now (layers, range and
     * chosen course), open strings left out as too easy.
     */
    fun startFretboardDrill() {
        val s = _state.value
        val choices = Fretboard.positions(s.tuning, s.maxCents).filter {
            it.cents > 0 && s.isVisible(it, null) && (s.onlyCourse == null || it.course == s.onlyCourse)
        }
        if (choices.isEmpty()) return
        beginDrill(Drill.onFretboard(choices))
    }

    /** Plays a cadence of the selected maqam with its rhythm, lighting each note as it sounds. */
    fun playCadence(cadence: Cadence) {
        val s = _state.value
        val maqam = s.selected as? Maqam ?: return
        _state.update { it.copy(exercise = null) }
        val beatMs = 60_000.0 / CADENCE_BPM
        playTimed(cadence.steps(maqam, s.tonic), cadence.notes.map { (it.beats * beatMs).toLong() })
    }

    /** "Repeat the cadence": the same drill as for a scale, note by note through the phrase. */
    fun startCadenceDrill(cadence: Cadence) {
        val s = _state.value
        val maqam = s.selected as? Maqam ?: return
        beginDrill(Drill(cadence.steps(maqam, s.tonic), cadence = cadence))
    }

    /** Runs the same kind of drill again from the start. */
    fun restartExercise() {
        val drill = _state.value.exercise
        when {
            drill?.cadence != null -> startCadenceDrill(drill.cadence)
            drill?.places != null -> startFretboardDrill()
            else -> startExercise()
        }
    }

    /** Drills need the mic, so starting one turns it on. */
    private fun beginDrill(drill: Drill) {
        steady.reset()
        awaitingPluck = false
        _state.update { it.copy(exercise = drill, live = true) }
    }

    fun stopExercise() = _state.update { it.copy(exercise = null) }

    fun toggleDrillHint() = _state.update { it.copy(drillHint = !it.drillHint) }

    /**
     * Plays grid pitches one after another, [noteMs] apart, each ringing for [toneMs], and marks
     * the one sounding in [FretboardUiState.playingStep] so the board can light it up.
     */
    private fun playSequence(steps: List<Int>, noteMs: Long, toneMs: Long) =
        playTimed(steps, List(steps.size) { noteMs }, toneMs)

    /**
     * Like [playSequence] with a length per note: note i lasts [noteMs]`[i]`. Unless [toneMs] is
     * given, each tone rings a little past its length so a phrase sounds joined up.
     */
    private fun playTimed(steps: List<Int>, noteMs: List<Long>, toneMs: Long? = null) {
        val a4 = _state.value.a4.toDouble()
        val rings = noteMs.map { toneMs ?: (it * 1.4).toLong().coerceAtLeast(MIN_TONE_MS) }
        // Don't light up the speaker's own tone as if it were played on the oud.
        ignoreMicUntil = SystemClock.elapsedRealtime() + noteMs.sum() + rings.last()
        tracker.reset()
        _state.update { it.copy(frequency = null) }
        scaleJob?.cancel()
        // A cancelled run must not clear the highlight of the run that replaced it.
        val run = ++playRun
        scaleJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                steps.forEachIndexed { i, step ->
                    _state.update { it.copy(playingStep = step) }
                    tone.play(Pitch(step).frequency(a4), rings[i].toInt())
                    delay(noteMs[i])
                }
                delay(HIGHLIGHT_TAIL_MS)
            } finally {
                if (run == playRun) _state.update { it.copy(playingStep = null) }
            }
        }
    }

    /** Turning live mode off releases the microphone; the screen calls [start] again when it is on. */
    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stop()
    }

    fun play(position: Position) =
        playSequence(listOf(position.pitch.step), SINGLE_NOTE_MS, ReferenceTone.DURATION_MS.toLong())

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val targetFrequency = s.exercise?.target?.let { Pitch(it).frequency(s.a4.toDouble()) }
        val update = tracker.update(reading, now) {
            val f = it.frequency.toDouble()
            if (targetFrequency != null) {
                // The drill knows which note is due, so the octave question has a sure answer.
                if (it.canBeOctaveLower && abs(centsBetween(f / 2, targetFrequency)) < abs(centsBetween(f, targetFrequency))) {
                    f / 2
                } else {
                    f
                }
            } else {
                Fretboard.resolveOctave(s.tuning, f, it.canBeOctaveLower, s.a4.toDouble(), s.onlyCourse, s.maxCents)
            }
        }
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }

        if (update.pluck) {
            awaitingPluck = false
            steady.reset()
        }
        // After a note is taken it keeps ringing; only a new pluck may count for the next one.
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
        const val TONIC_MIN = 72 // C2
        const val TONIC_MAX = 120 // C4
        const val SCALE_NOTE_MS = 550L
        const val SCALE_TONE_MS = 900L
        const val SINGLE_NOTE_MS = 900L
        const val HIGHLIGHT_TAIL_MS = 300L
        const val CADENCE_BPM = 80
        const val MIN_TONE_MS = 400L
    }
}
