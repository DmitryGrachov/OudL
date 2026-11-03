package com.udl.oud.practice

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.LoopTrack
import com.udl.oud.audio.Metronome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MetronomeUiState(
    val bpm: Int = 80,
    val beats: Int = 4,
    /** Clicks per beat: 1 quarters, 2 eighths, 3 triplets, 4 sixteenths. */
    val subdivision: Int = 1,
    val accent: Boolean = true,
    val volume: Float = 0.8f,
    val on: Boolean = false,
    /** Click of the bar sounding now (0 until beats × subdivision), while playing. */
    val click: Int? = null,
) {
    /** Beat of the bar sounding now, while playing. */
    val beat: Int? get() = click?.let { it / subdivision }
}

class MetronomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(MetronomeUiState())
    val state = _state.asStateFlow()

    private val track = LoopTrack()
    private var playJob: Job? = null
    private val taps = ArrayDeque<Long>()

    fun toggle() = if (_state.value.on) stop() else start()

    fun changeBpm(delta: Int) = setBpm(_state.value.bpm + delta)

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(BPM_MIN, BPM_MAX)
        if (clamped == _state.value.bpm) return
        _state.update { it.copy(bpm = clamped) }
        restartIfOn()
    }

    fun setBeats(beats: Int) {
        _state.update { it.copy(beats = beats) }
        restartIfOn()
    }

    fun setSubdivision(subdivision: Int) {
        _state.update { it.copy(subdivision = subdivision) }
        restartIfOn()
    }

    fun toggleAccent() {
        _state.update { it.copy(accent = !it.accent) }
        restartIfOn()
    }

    fun setVolume(volume: Float) {
        _state.update { it.copy(volume = volume) }
        track.setVolume(volume)
    }

    /** Tap tempo: the tempo follows the average gap between the last few taps. */
    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
        taps.addLast(now)
        if (taps.size > TAPS_KEPT) taps.removeFirst()
        tapTempo(taps)?.let(::setBpm)
    }

    private fun restartIfOn() {
        if (_state.value.on) start()
    }

    private fun start() {
        val s = _state.value
        _state.update { it.copy(on = true) }
        playJob?.cancel()
        playJob = viewModelScope.launch(Dispatchers.Default) {
            track.play(Metronome.render(s.bpm, s.beats, s.subdivision, s.accent), _state.value.volume)
            // Follow the playback head so the screen can flash the beat that is sounding.
            val step = Metronome.clickSamples(s.bpm, s.subdivision)
            val bar = step * s.beats * s.subdivision
            while (isActive) {
                val position = track.position() ?: break
                _state.update { it.copy(click = (position % bar) / step) }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        track.stop()
        _state.update { it.copy(on = false, click = null) }
    }

    override fun onCleared() = stop()

    companion object {
        const val BPM_MIN = 30
        const val BPM_MAX = 260
        private const val POLL_MS = 15L
        private const val TAP_RESET_MS = 2000L
        private const val TAPS_KEPT = 5
    }
}

/** Tempo from tap times in milliseconds: needs two taps, uses the mean gap. */
internal fun tapTempo(taps: List<Long>): Int? {
    if (taps.size < 2) return null
    val gap = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
    return (60_000 / gap).roundToInt()
}
