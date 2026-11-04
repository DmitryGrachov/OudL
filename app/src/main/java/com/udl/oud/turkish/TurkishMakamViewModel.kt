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
import com.udl.oud.tuner.Ahenk
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

/** Where a geçki left from: the makam picked and the durak it was moved to, if any. */
data class GeckiStop(val base: TurkishMakam, val durak: Int?)

data class TurkishMakamUiState(
    /** The makam as picked from the list. */
    val base: TurkishMakam = TurkishMakams.all.first(),
    /** Şed: the durak it is moved to, or null on its own durak. */
    val durak: Int? = null,
    val descending: Boolean = false,
    val seyirOpen: Boolean = false,
    /** Makams the geçkis came from, to step back. */
    val trail: List<GeckiStop> = emptyList(),
    val labels: TurkishLabels = TurkishLabels.Perde,
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    val ahenk: Ahenk = OudSettings.ahenk.value,
    /** Show the note being played on a staff beside the board. */
    val showStaff: Boolean = true,
    val extended: Boolean = false,
    val live: Boolean = true,
    val frequency: Double? = null,
    /** Tone the app is sounding now, to light it up. */
    val playing: TurkishTone? = null,
    /** Drill: steps index into [drillTones]. */
    val exercise: Drill? = null,
    val drillTones: List<TurkishTone> = emptyList(),
    /** The karar being drilled, to run it again; null for a scale drill. */
    val drillKarar: TurkishKarar? = null,
    val error: String? = null,
) {
    val makam: TurkishMakam get() = durak?.let { base.onDurak(it) } ?: base

    /** The named şed makam the moved scale is, when it has a name (Zirgüleli Hicaz on Rast: Hicazkâr). */
    val sedName: TurkishMakam? get() = durak?.let { TurkishMakams.namedSed(base, it) }

    val seyir: TurkishSeyir get() = TurkishSeyirs.of(makam)
    val degrees: List<TurkishDegree> get() = makam.degrees()

    /** The seven degrees in the chosen direction: going down, with the inici changes. */
    val scaleTones: List<TurkishTone> get() = TurkishScale.degreeTones(makam, descending)

    /** The scale walked through in the chosen direction, durak to octave or back. */
    val steps: List<TurkishTone> get() = if (descending) TurkishScale.descending(makam) else TurkishScale.ascending(makam)

    val yeden: TurkishTone get() = TurkishScale.yeden(makam)

    /** Tones drawn on the neck: the scale, and with the seyir open the yeden when it isn't a degree. */
    val boardTones: List<TurkishTone>
        get() {
            val tones = scaleTones
            val y = yeden
            return if (seyirOpen && tones.none { it.sameClass(y) }) tones + y else tones
        }

    /** Nearest drawn tone to what the mic hears, or null when silent. */
    val heard: TurkishHeard?
        get() = frequency?.let { TurkishBoard.nearest(boardTones, it, a4.toDouble(), ahenk) }

    val target: TurkishTone? get() = exercise?.target?.let { drillTones.getOrNull(it) }

    val exerciseCents: Double?
        get() {
            val t = target ?: return null
            val f = frequency ?: return null
            return centsBetween(f, frequencyOf(t))
        }

    fun frequencyOf(tone: TurkishTone): Double = tone.frequency(a4.toDouble(), ahenk)

    /** Degree (0..6) of a tone in the chosen direction, or null for the yeden and other tones. */
    fun degreeOf(tone: TurkishTone): Int? = scaleTones.indexOfFirst { it.sameClass(tone) }.takeIf { it >= 0 }

    /** Name of a tone in the chosen style: perde, written note, or the note as it sounds. */
    fun labelOf(tone: TurkishTone): String = when (labels) {
        TurkishLabels.Perde -> Perdes.of(tone.written) ?: tone.spelling.label()
        TurkishLabels.Written -> tone.spelling.label()
        TurkishLabels.Sounding -> tone.spelling.sounding(tone.written, ahenk).label()
    }

    companion object {
        const val IN_TUNE_CENTS = 10.0

        /** Further than this from every degree, the note isn't in the makam. */
        const val OUTSIDE_CENTS = 40.0
    }
}

/** The Turkish makam tab: the board on the koma grid, seyir, şed, live pitch, playback, drills. */
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
            combine(OudSettings.tuning, OudSettings.a4, OudSettings.ahenk, ::Triple).collect { (tuning, a4, ahenk) ->
                _state.update { it.copy(tuning = tuning, a4 = a4, ahenk = ahenk) }
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

    // Changing the makam, durak or direction ends a drill: its notes no longer match.

    /** Picking a makam by hand starts afresh: back on its own durak, the geçki trail dropped. */
    fun select(makam: TurkishMakam) = _state.update {
        it.copy(base = makam, durak = null, trail = emptyList(), exercise = null)
    }

    /** Şed: moves the makam to [written]; its own durak clears the move. */
    fun setDurak(written: Int) = _state.update {
        it.copy(durak = written.takeIf { w -> w != it.base.tonic }, exercise = null)
    }

    fun setDescending(descending: Boolean) = _state.update { it.copy(descending = descending, exercise = null) }
    fun toggleSeyir() = _state.update { it.copy(seyirOpen = !it.seyirOpen) }
    fun setLabels(labels: TurkishLabels) = _state.update { it.copy(labels = labels) }
    fun toggleExtended() = _state.update { it.copy(extended = !it.extended) }
    fun toggleStaff() = _state.update { it.copy(showStaff = !it.showStaff) }
    fun setAhenk(ahenk: Ahenk) = OudSettings.setAhenk(ahenk)

    /** Follows a geçki from the seyir, remembering where it came from. */
    fun gecki(gecki: Gecki) = _state.update {
        val target = TurkishMakams.named(gecki.makam) ?: return@update it
        val durak = it.makam.tonic + gecki.offset
        val named = TurkishMakams.namedSed(target, durak)
        val stop = GeckiStop(it.base, it.durak)
        if (named != null) {
            it.copy(base = named, durak = null, trail = it.trail + stop, descending = false, exercise = null)
        } else {
            it.copy(base = target, durak = durak.takeIf { d -> d != target.tonic }, trail = it.trail + stop, descending = false, exercise = null)
        }
    }

    /** Returns to the makam the last geçki left. */
    fun geckiBack() = _state.update {
        val stop = it.trail.lastOrNull() ?: return@update it
        it.copy(base = stop.base, durak = stop.durak, trail = it.trail.dropLast(1), descending = false, exercise = null)
    }

    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stop()
    }

    fun play(tone: TurkishTone) = playSequence(listOf(tone), listOf(NOTE_MS), listOf(ReferenceTone.DURATION_MS.toLong()))

    /** Plays the scale in the chosen direction. */
    fun playScale() {
        _state.update { it.copy(exercise = null) }
        val steps = _state.value.steps
        playSequence(steps, List(steps.size) { SCALE_NOTE_MS }, List(steps.size) { SCALE_TONE_MS })
    }

    /** Plays a karar with its rhythm; each tone rings a little past its length so it sounds joined. */
    fun playKarar(karar: TurkishKarar) {
        _state.update { it.copy(exercise = null) }
        val beatMs = 60_000.0 / KARAR_BPM
        val lengths = karar.notes.map { (it.beats * beatMs).toLong() }
        playSequence(TurkishScale.phrase(_state.value.makam, karar), lengths, lengths.map { (it * 1.4).toLong().coerceAtLeast(MIN_TONE_MS) })
    }

    /** "Play the scale" in the chosen direction. */
    fun startExercise() = beginDrill(_state.value.steps, null)

    /** "Repeat the karar": the same drill, note by note through the phrase. */
    fun startKararDrill(karar: TurkishKarar) = beginDrill(TurkishScale.phrase(_state.value.makam, karar), karar)

    fun restartExercise() {
        val karar = _state.value.drillKarar
        if (karar != null) startKararDrill(karar) else startExercise()
    }

    fun stopExercise() = _state.update { it.copy(exercise = null) }

    /** Drills need the mic, so starting one turns it on. */
    private fun beginDrill(tones: List<TurkishTone>, karar: TurkishKarar?) {
        steady.reset()
        awaitingPluck = false
        _state.update { it.copy(exercise = Drill(tones.indices.toList()), drillTones = tones, drillKarar = karar, live = true) }
    }

    /** Plays [tones] one after another: tone i lasts [noteMs]`[i]` and rings for [toneMs]`[i]`. */
    private fun playSequence(tones: List<TurkishTone>, noteMs: List<Long>, toneMs: List<Long>) {
        val s = _state.value
        // Don't light up the speaker's own tone as if it were played on the oud.
        ignoreMicUntil = SystemClock.elapsedRealtime() + noteMs.sum() + toneMs.last()
        tracker.reset()
        _state.update { it.copy(frequency = null) }
        playJob?.cancel()
        // A cancelled run must not clear the highlight of the run that replaced it.
        val run = ++playRun
        playJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                tones.forEachIndexed { i, t ->
                    _state.update { it.copy(playing = t) }
                    tone.play(s.frequencyOf(t), toneMs[i].toInt())
                    delay(noteMs[i])
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
        val targetFrequency = s.target?.let(s::frequencyOf)
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
        const val KARAR_BPM = 80
        const val MIN_TONE_MS = 400L
    }
}
