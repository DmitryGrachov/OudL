package com.udl.oud.taqsim

import android.Manifest
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.MicPitchSource
import com.udl.oud.audio.PcmPlayer
import com.udl.oud.audio.PitchReading
import com.udl.oud.audio.PitchTracker
import com.udl.oud.audio.PluckSynth
import com.udl.oud.fretboard.Drill
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Pitch
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Playback speed: how long the base pulse lasts. */
enum class TaqsimTempo(val title: String, val pulseMs: Double) {
    Normal("Обычно", 190.0),
    Slow("Медленнее", 260.0),
    VerySlow("Совсем медленно", 380.0),
}

data class TaqsimUiState(
    val maqam: Maqam = Maqamat.all.first(),
    val length: TaqsimLength = TaqsimLength.Medium,
    val tempo: TaqsimTempo = TaqsimTempo.Normal,
    val taqsim: Taqsim = TaqsimGenerator(1).generate(Maqamat.all.first(), TaqsimLength.Medium),
    /** Index into [Taqsim.notes] of the note sounding, or null when silent. */
    val playingNote: Int? = null,
    val playing: Boolean = false,
    /** Phrase picked for a closer look, to play, loop or repeat. */
    val selected: Int? = null,
    val loop: Boolean = false,
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    val live: Boolean = true,
    val frequency: Double? = null,
    /** Repeat drill on the [drillPhrase]: steps are its grid pitches. */
    val exercise: Drill? = null,
    val drillPhrase: Int? = null,
    val error: String? = null,
) {
    /** Phrase of each note of [Taqsim.notes]. */
    val phraseOfNote: List<Int> get() = taqsim.phrases.flatMapIndexed { i, p -> List(p.notes.size) { i } }

    /** First note index of each phrase. */
    val phraseStarts: List<Int> get() = taqsim.phrases.runningFold(0) { acc, p -> acc + p.notes.size }.dropLast(1)

    val playingPhrase: Int? get() = playingNote?.let { phraseOfNote.getOrNull(it) }

    /** The phrase to show: the one sounding, else the one picked, else the first. */
    val shownPhrase: Int get() = playingPhrase ?: drillPhrase?.takeIf { exercise != null } ?: selected ?: 0

    val playingTone: TaqsimNote? get() = playingNote?.let { taqsim.notes.getOrNull(it) }

    val target: TaqsimNote? get() {
        val p = drillPhrase ?: return null
        val i = exercise?.target ?: return null
        return taqsim.phrases[p].notes.getOrNull(i)
    }

    val exerciseCents: Double?
        get() {
            val t = target ?: return null
            val f = frequency ?: return null
            return centsBetween(f, Pitch(t.step).frequency(a4.toDouble()))
        }

    /** The heard note on the quarter-tone grid and how far off it is. */
    val heard: Pair<Pitch, Double>?
        get() = frequency?.let {
            val p = Pitch.nearest(it, a4.toDouble(), quarterTones = true)
            p to centsBetween(it, p.frequency(a4.toDouble()))
        }

    companion object {
        const val IN_TUNE_CENTS = 12.0
    }
}

/** The taqsim tab: makes a taqsim, plays it with the board lit, loops and drills its phrases. */
class TaqsimViewModel : ViewModel() {
    private val _state = MutableStateFlow(TaqsimUiState())
    val state = _state.asStateFlow()

    private val synth = PluckSynth(SAMPLE_RATE)
    private val player = PcmPlayer(SAMPLE_RATE)
    private val mic = MicPitchSource()
    private val tracker = PitchTracker()
    private val steady = SteadyNote()
    private var listenJob: Job? = null
    private var playJob: Job? = null
    private var ignoreMicUntil = 0L
    private var awaitingPluck = false

    init {
        viewModelScope.launch {
            combine(OudSettings.tuning, OudSettings.a4) { t, a -> t to a }.collect { (tuning, a4) ->
                _state.update { it.copy(tuning = tuning, a4 = a4) }
            }
        }
        regenerate()
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

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        tracker.reset()
        _state.update { it.copy(frequency = null) }
    }

    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stopListening()
    }

    fun select(maqam: Maqam) {
        _state.update { it.copy(maqam = maqam) }
        regenerate()
    }

    fun setLength(length: TaqsimLength) {
        _state.update { it.copy(length = length) }
        regenerate()
    }

    fun setTempo(tempo: TaqsimTempo) = _state.update { it.copy(tempo = tempo) }

    /** A new taqsim on the same maqam. */
    fun regenerate() {
        stop()
        val s = _state.value
        val seed = System.nanoTime()
        _state.update { it.copy(taqsim = TaqsimGenerator(seed).generate(s.maqam, s.length), selected = null, exercise = null, drillPhrase = null) }
    }

    fun selectPhrase(index: Int) = _state.update { it.copy(selected = if (it.selected == index) null else index) }
    fun toggleLoop() = _state.update { it.copy(loop = !it.loop) }

    fun playAll() = playRange(0 until _state.value.taqsim.phrases.size, loop = false)

    /** Plays one phrase; with the loop on, over and over with a pause between. */
    fun playPhrase(index: Int) {
        _state.update { it.copy(selected = index) }
        playRange(index..index, loop = _state.value.loop)
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        // The speaker's last note still rings for a moment; don't take it for the oud.
        ignoreMicUntil = SystemClock.elapsedRealtime() + MIC_GRACE_MS
        _state.update { it.copy(playing = false, playingNote = null) }
    }

    /** "Repeat the phrase": the drill goes note by note through it, at the player's own pace. */
    fun startRepeat(index: Int) {
        stop()
        steady.reset()
        awaitingPluck = false
        val steps = _state.value.taqsim.phrases[index].notes.map { it.step }
        _state.update { it.copy(exercise = Drill(steps), drillPhrase = index, selected = index, live = true) }
    }

    fun restartRepeat() = _state.value.drillPhrase?.let(::startRepeat)
    fun stopRepeat() = _state.update { it.copy(exercise = null, drillPhrase = null) }

    private fun playRange(phrases: IntRange, loop: Boolean) {
        stop()
        val s = _state.value
        val pulse = s.tempo.pulseMs
        val a4 = s.a4.toDouble()
        val first = s.phraseStarts[phrases.first]
        val notes = phrases.flatMap { s.taqsim.phrases[it].notes }
        // Start time and length of each note in ms, pauses between phrases included.
        val starts = mutableListOf<Double>()
        var t = 0.0
        phrases.forEach { pi ->
            val phrase = s.taqsim.phrases[pi]
            phrase.notes.forEach { n ->
                starts += t
                t += n.beats * pulse
            }
            if (pi != phrases.last) t += phrase.restBeats * pulse
        }
        val totalMs = t + TAIL_MS
        _state.update { it.copy(playing = true, exercise = null) }
        playJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                val plucks = notes.mapIndexed { i, n ->
                    val start = (starts[i] * SAMPLE_RATE / 1000).toInt()
                    val held = (n.beats * pulse * SAMPLE_RATE / 1000).toInt()
                    PluckSynth.Pluck(Pitch(n.step).frequency(a4), start, held)
                }
                PluckSynth.toPcm(synth.render(plucks, (totalMs * SAMPLE_RATE / 1000).toInt(), s.taqsim.seed))
            }
            do {
                ignoreMicUntil = Long.MAX_VALUE
                val ticker = launch {
                    while (isActive) {
                        val pos = player.positionMs()
                        if (pos != null) {
                            val i = starts.indexOfLast { it <= pos }.takeIf { it >= 0 && pos < totalMs - TAIL_MS + 400 }
                            _state.update { it.copy(playingNote = i?.let { first + it }) }
                        }
                        delay(TICK_MS)
                    }
                }
                player.play(pcm)
                ticker.cancel()
                _state.update { it.copy(playingNote = null) }
                if (loop && isActive) delay(LOOP_PAUSE_MS)
            } while (loop && isActive && _state.value.loop)
            ignoreMicUntil = SystemClock.elapsedRealtime() + MIC_GRACE_MS
            tracker.reset()
            _state.update { it.copy(playing = false, playingNote = null) }
        }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val targetFrequency = s.target?.let { Pitch(it.step).frequency(s.a4.toDouble()) }
        val update = tracker.update(reading, now) {
            val f = it.frequency.toDouble()
            if (targetFrequency != null && it.canBeOctaveLower &&
                abs(centsBetween(f / 2, targetFrequency)) < abs(centsBetween(f, targetFrequency))
            ) f / 2 else f
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
        playJob?.cancel()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val TICK_MS = 25L
        const val TAIL_MS = 1200.0
        const val LOOP_PAUSE_MS = 900L
        const val MIC_GRACE_MS = 600L
    }
}
