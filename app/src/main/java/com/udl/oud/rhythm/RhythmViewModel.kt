package com.udl.oud.rhythm

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.Drum
import com.udl.oud.audio.Iqa
import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.LoopTrack
import com.udl.oud.audio.MicOnsetSource
import com.udl.oud.audio.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.random.Random

enum class RhythmMode(val title: String) {
    Listen("Слушать"),
    Tap("Отстучать"),
    Oud("На уде"),
    Quiz("Угадай"),
}

/** Whether the drum plays along every cycle, or only one in four and the player keeps it going. */
enum class DrumMode(val title: String) {
    Always("Барабан всегда"),
    EveryFourth("Барабан раз в 4 цикла"),
}

/** Guess the iqa' by ear: the one playing, four to choose from, and the score. */
data class RhythmQuiz(val answer: Iqa, val options: List<Iqa>, val picked: Iqa? = null, val right: Int = 0, val asked: Int = 1)

data class RhythmUiState(
    val iqa: Iqa = Iqaat.all.first(),
    val bpm: Int = Iqaat.all.first().bpm,
    val mode: RhythmMode = RhythmMode.Listen,
    val drumMode: DrumMode = DrumMode.Always,
    val clicks: Boolean = false,
    val playing: Boolean = false,
    /** Step of the cycle sounding now, and which cycle of the run it is. */
    val step: Int? = null,
    val cycle: Int = 0,
    /** Whether the drum sounds in this cycle (in "every fourth" the others are the player's). */
    val drumNow: Boolean = true,
    val last: CycleResult? = null,
    val stats: RhythmStats = RhythmStats(),
    /** Calibrating: strokes heard so far against the clicks. */
    val calibrating: Boolean = false,
    val calibrationHits: Int = 0,
    val tapLatency: Double = RhythmPrefs.tapLatency,
    val micLatency: Double = RhythmPrefs.micLatency,
    /** The stroke just tapped, to flash its pad. */
    val flash: Stroke? = null,
    val quiz: RhythmQuiz? = null,
    val error: String? = null,
) {
    /** Cycles that are checked: after the first (the count-in), and with the drum silent when playing on the oud. */
    fun checked(cycle: Int): Boolean = when (mode) {
        RhythmMode.Tap -> cycle >= 1
        RhythmMode.Oud -> cycle >= 1 && cycle % LOOP_CYCLES != 0
        else -> false
    }

    fun drumIn(cycleInLoop: Int): Boolean = mode == RhythmMode.Listen || mode == RhythmMode.Quiz ||
        effectiveDrumMode == DrumMode.Always || cycleInLoop == 0

    /** On the oud the drum would be heard by the microphone, so it plays only one cycle in four. */
    val effectiveDrumMode: DrumMode get() = if (mode == RhythmMode.Oud) DrumMode.EveryFourth else drumMode

    companion object {
        const val LOOP_CYCLES = 4
        const val BPM_MIN = 40
        const val BPM_MAX = 320
    }
}

/** The rhythm tab: learn an iqa' by ear and by eye, then play it — on the screen or on the oud. */
class RhythmViewModel : ViewModel() {
    private val _state = MutableStateFlow(RhythmUiState())
    val state = _state.asStateFlow()

    private val loop = LoopTrack()
    private val onsets = MicOnsetSource()
    private var tickJob: Job? = null
    private var micJob: Job? = null
    private var flashJob: Job? = null
    private var runStartNs = 0L
    private var started = false
    private val hits = mutableListOf<Hit>()
    private val rawHits = mutableListOf<Double>()
    private var evaluatedUpTo = -1

    fun select(iqa: Iqa) {
        _state.update { it.copy(iqa = iqa, bpm = iqa.bpm, last = null, stats = RhythmStats()) }
        restartIfPlaying()
    }

    fun setMode(mode: RhythmMode) {
        stop()
        _state.update { it.copy(mode = mode, last = null, stats = RhythmStats(), quiz = null) }
        if (mode == RhythmMode.Quiz) nextQuestion()
    }

    fun setDrumMode(mode: DrumMode) {
        _state.update { it.copy(drumMode = mode) }
        restartIfPlaying()
    }

    fun toggleClicks() {
        _state.update { it.copy(clicks = !it.clicks) }
        restartIfPlaying()
    }

    fun changeBpm(delta: Int) = setBpm(_state.value.bpm + delta)

    fun setBpm(bpm: Int) {
        val b = bpm.coerceIn(RhythmUiState.BPM_MIN, RhythmUiState.BPM_MAX)
        if (b == _state.value.bpm) return
        _state.update { it.copy(bpm = b, last = null, stats = RhythmStats()) }
        restartIfPlaying()
    }

    private fun restartIfPlaying() {
        if (_state.value.playing && !_state.value.calibrating) play()
    }

    fun toggle() = if (_state.value.playing) stop() else play()

    /** Starts the loop of the iqa' (four cycles, the drum in those the mode lets through). */
    fun play() {
        stopSound()
        val s = _state.value
        val iqa = s.quiz?.answer ?: s.iqa
        val bpm = s.quiz?.answer?.bpm ?: s.bpm
        hits.clear()
        evaluatedUpTo = 0
        _state.update { it.copy(playing = true, last = null, stats = RhythmStats(), calibrating = false) }
        tickJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                Drum.render(iqa, bpm, RhythmUiState.LOOP_CYCLES, drumIn = s::drumIn, clicks = s.clicks)
            }
            startLoop(pcm)
            tick(iqa, bpm)
        }
    }

    private fun startLoop(pcm: ShortArray) {
        started = false
        loop.play(pcm, 1f)
    }

    /** Where the run is, in ms from its first sample; found from the track's position once it moves. */
    private fun runMs(nowNs: Long = System.nanoTime()): Double? {
        if (!started) {
            val pos = loop.position() ?: return null
            if (pos <= 0) return null
            runStartNs = nowNs - pos * 1_000_000_000L / LoopTrack.SAMPLE_RATE
            started = true
        }
        return (nowNs - runStartNs) / 1_000_000.0
    }

    private suspend fun tick(iqa: Iqa, bpm: Int) {
        val stepMs = iqa.stepMs(bpm)
        val n = iqa.strokes.size
        val cycleMs = stepMs * n
        val window = RhythmCheck.window(stepMs)
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val ms = runMs()
            if (ms != null) {
                val global = floor(ms / stepMs).toInt()
                val cycle = global / n
                val step = global % n
                val s = _state.value
                if (s.step != step || s.cycle != cycle) {
                    _state.update { it.copy(step = step, cycle = cycle, drumNow = it.drumIn(cycle % RhythmUiState.LOOP_CYCLES)) }
                }
                // A cycle is checked once the strokes for its last note can no longer come.
                while (ms > (evaluatedUpTo + 1) * cycleMs - stepMs / 2 + window) {
                    val c = evaluatedUpTo
                    if (_state.value.checked(c)) {
                        val result = RhythmCheck.evaluate(iqa, bpm, c, hits.toList())
                        _state.update { it.copy(last = result, stats = it.stats + result) }
                    }
                    hits.removeAll { it.atMs < (c - 1) * cycleMs }
                    evaluatedUpTo++
                }
            }
            delay(12)
        }
    }

    /** A stroke tapped on a pad. */
    fun tap(stroke: Stroke) {
        val ms = runMs() ?: return
        val s = _state.value
        if (s.calibrating) {
            rawHits += ms
            _state.update { it.copy(calibrationHits = rawHits.size) }
        } else if (s.mode == RhythmMode.Tap) {
            hits += Hit(ms - s.tapLatency, stroke)
        }
        flashJob?.cancel()
        _state.update { it.copy(flash = stroke) }
        flashJob = viewModelScope.launch {
            delay(120)
            _state.update { it.copy(flash = null) }
        }
    }

    /** Listens for strokes on the oud while the mode wants them. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startMic() {
        if (micJob?.isActive == true) return
        micJob = viewModelScope.launch {
            onsets.onsets()
                .catch { e -> _state.update { it.copy(error = e.message ?: "Ошибка микрофона") } }
                .collect { ns ->
                    val ms = runMs(ns) ?: return@collect
                    val s = _state.value
                    if (s.calibrating) {
                        rawHits += ms
                        _state.update { it.copy(calibrationHits = rawHits.size) }
                    } else if (s.mode == RhythmMode.Oud && s.playing) {
                        hits += Hit(ms - s.micLatency)
                    }
                }
        }
    }

    fun stopMic() {
        micJob?.cancel()
        micJob = null
    }

    /**
     * Calibration: clicks on every beat; the player strikes with each (on the pads or on the
     * oud). The median lag is how late the screen or the microphone is, and comes off every stroke.
     */
    fun calibrate() {
        stopSound()
        rawHits.clear()
        _state.update { it.copy(playing = true, calibrating = true, calibrationHits = 0, step = null) }
        tickJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                Drum.render(CALIBRATION, CALIBRATION_BPM, RhythmUiState.LOOP_CYCLES, drumIn = { false }, clicks = true)
            }
            startLoop(pcm)
            val beatMs = 60_000.0 / CALIBRATION_BPM
            val totalMs = beatMs * CALIBRATION_BEATS
            while (isActive) {
                val ms = runMs()
                if (ms != null && ms > totalMs + beatMs / 2) break
                delay(20)
            }
            val clicks = (0..CALIBRATION_BEATS).map { it * beatMs }
            // The first beats are for catching the pulse.
            val latency = RhythmCheck.latency(clicks, rawHits.filter { it > beatMs * 2.5 })
            val mic = _state.value.mode == RhythmMode.Oud
            if (latency != null) {
                if (mic) RhythmPrefs.setMic(latency) else RhythmPrefs.setTap(latency)
            }
            stopSound()
            _state.update {
                it.copy(
                    playing = false,
                    calibrating = false,
                    tapLatency = RhythmPrefs.tapLatency,
                    micLatency = RhythmPrefs.micLatency,
                    error = if (latency == null) "Калибровка не удалась: ударь хотя бы 4 раза в такт щелчкам" else null,
                )
            }
        }
    }

    // --- Guess the iqa' ---

    fun nextQuestion() {
        val previous = _state.value.quiz
        val pool = Iqaat.all.filter { it != previous?.answer }
        val answer = pool.random()
        val options = (Iqaat.all.filter { it != answer }.shuffled().take(3) + answer).shuffled(Random(System.nanoTime()))
        _state.update {
            it.copy(quiz = RhythmQuiz(answer, options, right = previous?.right ?: 0, asked = (previous?.asked ?: 0) + 1))
        }
        play()
    }

    fun pick(iqa: Iqa) {
        val q = _state.value.quiz ?: return
        if (q.picked != null) return
        _state.update { it.copy(quiz = q.copy(picked = iqa, right = q.right + if (iqa == q.answer) 1 else 0)) }
    }

    private fun stopSound() {
        tickJob?.cancel()
        tickJob = null
        loop.stop()
        started = false
    }

    fun stop() {
        stopSound()
        _state.update { it.copy(playing = false, step = null, calibrating = false) }
    }

    override fun onCleared() {
        stopSound()
        stopMic()
    }

    private companion object {
        /** An empty 4/4 bar: only the clicks sound. */
        val CALIBRATION = Iqa("Щелчки", "4/4", 1, ". . . .")
        const val CALIBRATION_BPM = 90
        const val CALIBRATION_BEATS = 14
    }
}
