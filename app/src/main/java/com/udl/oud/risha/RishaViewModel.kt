package com.udl.oud.risha

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.LoopTrack
import com.udl.oud.audio.Metronome
import com.udl.oud.audio.MicOnsetSource
import com.udl.oud.rhythm.RhythmPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor

enum class RishaMode(val title: String) {
    Tremolo("Тремоло"),
    Strokes("Штрих"),
}

/** Strokes per beat in the picking exercise. */
enum class Subdivision(val title: String, val perBeat: Int) {
    Eighths("Восьмые", 2),
    Triplets("Триоли", 3),
    Sixteenths("Шестнадцатые", 4),
}

/** Whether the metronome clicks only one bar in four (the mic would hear it) or always (in headphones). */
enum class ClickMode(val title: String) {
    Reminder("Щелчки раз в 4 такта"),
    Always("Щелчки всегда (в наушниках)"),
}

data class RishaUiState(
    val mode: RishaMode = RishaMode.Tremolo,
    val error: String? = null,
    /** Tremolo: the last run heard, and the fastest even one. */
    val tremolo: TremoloStats? = null,
    val bestRate: Double = 0.0,
    /** Picking: tempo, strokes per beat, clicks, and the speed ladder. */
    val bpm: Int = 80,
    val subdivision: Subdivision = Subdivision.Eighths,
    val clickMode: ClickMode = ClickMode.Reminder,
    val ladder: Boolean = false,
    val playing: Boolean = false,
    val bar: Int = 0,
    val step: Int? = null,
    val clicksNow: Boolean = true,
    val lastBar: GridStats? = null,
    val bars: Int = 0,
    val onTime: Int = 0,
    val expected: Int = 0,
    val bestBpm: Int = 0,
    val passes: Int = 0,
    val latency: Double = RhythmPrefs.micLatency,
) {
    val stepsPerBar: Int get() = BEATS * subdivision.perBeat
    fun stepMs(): Double = 60_000.0 / bpm / subdivision.perBeat

    /** Bars checked: after the first, and without clicks unless they are in headphones. */
    fun checked(bar: Int): Boolean = bar >= 1 && (clickMode == ClickMode.Always || bar % BARS != 0)
    fun clicksIn(bar: Int): Boolean = clickMode == ClickMode.Always || bar % BARS == 0

    companion object {
        const val BEATS = 4
        const val BARS = 4
        const val BPM_MIN = 40
        const val BPM_MAX = 200
    }
}

/**
 * The risha tab: the evenness of a tremolo, and alternate picking against the metronome — the
 * microphone hears each stroke's time and strength; down and up are told apart by their place.
 */
class RishaViewModel : ViewModel() {
    private val _state = MutableStateFlow(RishaUiState())
    val state = _state.asStateFlow()

    private val source = MicOnsetSource()
    private val loop = LoopTrack()
    private var micJob: Job? = null
    private var tickJob: Job? = null
    private val strokes = ArrayDeque<RishaStroke>()
    private var runStartNs = 0L
    private var started = false
    private var evaluatedUpTo = 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (micJob?.isActive == true) return
        _state.update { it.copy(error = null, latency = RhythmPrefs.micLatency) }
        micJob = viewModelScope.launch {
            source.strokes()
                .catch { e -> _state.update { it.copy(error = e.message ?: "Ошибка микрофона") } }
                .collect { o -> onStroke(RishaStroke(o.ns / 1_000_000.0, o.level)) }
        }
    }

    fun stopListening() {
        micJob?.cancel()
        micJob = null
        stop()
    }

    fun setMode(mode: RishaMode) {
        stop()
        strokes.clear()
        _state.update { it.copy(mode = mode, tremolo = null) }
    }

    private fun onStroke(s: RishaStroke) {
        strokes.addLast(s)
        while (strokes.size > MAX_STROKES) strokes.removeFirst()
        val st = _state.value
        if (st.mode == RishaMode.Tremolo) {
            val t = RishaAnalysis.tremolo(strokes.toList()) ?: return
            val best = if (t.evenness >= EVEN_ENOUGH && t.strokes >= 12) maxOf(st.bestRate, t.perSecond) else st.bestRate
            _state.update { it.copy(tremolo = t, bestRate = best) }
        }
    }

    fun resetTremolo() {
        strokes.clear()
        _state.update { it.copy(tremolo = null) }
    }

    // --- Picking against the metronome ---

    fun setSubdivision(s: Subdivision) = change { it.copy(subdivision = s) }
    fun setClickMode(m: ClickMode) = change { it.copy(clickMode = m) }
    fun toggleLadder() = change { it.copy(ladder = !it.ladder, passes = 0) }
    fun changeBpm(delta: Int) = setBpm(_state.value.bpm + delta)
    fun setBpm(bpm: Int) = change { it.copy(bpm = bpm.coerceIn(RishaUiState.BPM_MIN, RishaUiState.BPM_MAX)) }

    private fun change(f: (RishaUiState) -> RishaUiState) {
        val playing = _state.value.playing
        _state.update { f(it).copy(lastBar = null) }
        if (playing) play()
    }

    fun toggle() = if (_state.value.playing) stop() else play()

    fun play(keepTotals: Boolean = false) {
        stopSound()
        val s = _state.value
        evaluatedUpTo = 0
        _state.update {
            if (keepTotals) it.copy(playing = true, lastBar = null)
            else it.copy(playing = true, lastBar = null, bars = 0, onTime = 0, expected = 0, passes = 0)
        }
        tickJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                Metronome.renderBars(s.bpm, RishaUiState.BEATS, s.subdivision.perBeat, RishaUiState.BARS, s::clicksIn)
            }
            started = false
            loop.play(pcm, 1f)
            tick()
        }
    }

    private fun runMs(nowNs: Long = System.nanoTime()): Double? {
        if (!started) {
            val pos = loop.position() ?: return null
            if (pos <= 0) return null
            runStartNs = nowNs - pos * 1_000_000_000L / LoopTrack.SAMPLE_RATE
            started = true
        }
        return (nowNs - runStartNs) / 1_000_000.0
    }

    private suspend fun tick() {
        while (currentCoroutineContext().isActive) {
            val ms = runMs()
            if (ms != null) {
                val s = _state.value
                val stepMs = s.stepMs()
                val barMs = stepMs * s.stepsPerBar
                val global = floor(ms / stepMs).toInt()
                val bar = global / s.stepsPerBar
                if (bar != s.bar || s.step != global % s.stepsPerBar) {
                    _state.update { it.copy(bar = bar, step = global % s.stepsPerBar, clicksNow = it.clicksIn(bar)) }
                }
                // A bar is checked once its last stroke can no longer come.
                while (ms > (evaluatedUpTo + 1) * barMs + stepMs) {
                    val b = evaluatedUpTo
                    evaluatedUpTo++
                    if (s.checked(b)) {
                        val start = runStartNs / 1_000_000.0 + s.latency
                        val heard = strokes.map { it.copy(atMs = it.atMs - start) }
                        val g = RishaAnalysis.grid(heard, stepMs, b * barMs, (b + 1) * barMs)
                        if (onBar(g)) return
                    }
                }
            }
            delay(12)
        }
    }

    /** Totals, and the ladder: two clean bars in a row raise the tempo. True when the loop restarts. */
    private fun onBar(g: GridStats): Boolean {
        val s = _state.value
        val clean = g.accuracy >= LADDER_ACCURACY && g.missed == 0
        val passes = if (clean) s.passes + 1 else 0
        val best = if (clean) maxOf(s.bestBpm, s.bpm) else s.bestBpm
        _state.update {
            it.copy(lastBar = g, bars = it.bars + 1, onTime = it.onTime + g.onTime, expected = it.expected + g.expected, passes = passes, bestBpm = best)
        }
        if (s.ladder && passes >= LADDER_PASSES && s.bpm < RishaUiState.BPM_MAX) {
            _state.update { it.copy(bpm = minOf(it.bpm + LADDER_STEP, RishaUiState.BPM_MAX), passes = 0) }
            viewModelScope.launch { play(keepTotals = true) }
            return true
        }
        return false
    }

    private fun stopSound() {
        tickJob?.cancel()
        tickJob = null
        loop.stop()
        started = false
    }

    fun stop() {
        stopSound()
        _state.update { it.copy(playing = false, step = null) }
    }

    override fun onCleared() {
        stopSound()
        micJob?.cancel()
    }

    private companion object {
        const val MAX_STROKES = 256
        const val EVEN_ENOUGH = 85
        const val LADDER_ACCURACY = 0.85
        const val LADDER_PASSES = 2
        const val LADDER_STEP = 4
    }
}
