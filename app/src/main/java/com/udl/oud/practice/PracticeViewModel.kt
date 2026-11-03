package com.udl.oud.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.Drone
import com.udl.oud.audio.Drum
import com.udl.oud.audio.Iqa
import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.LoopTrack
import com.udl.oud.fretboard.Pitch
import com.udl.oud.tuner.OudSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PracticeUiState(
    /** Drone note as a grid step (2 × MIDI, odd = quarter tone). */
    val droneStep: Int = 96, // C3, the tonic of Rast on an oud in C–C tuning
    val droneFifth: Boolean = false,
    val droneVolume: Float = 0.6f,
    val droneOn: Boolean = false,
    val iqa: Iqa = Iqaat.all.first(),
    val bpm: Int = 90,
    val rhythmVolume: Float = 0.8f,
    val rhythmOn: Boolean = false,
    /** Step of the cycle sounding now, while the rhythm plays. */
    val beatStep: Int? = null,
    val a4: Int = OudSettings.a4.value,
)

/** The drone and the iqa' rhythm: two independent loops to practise against. */
class PracticeViewModel : ViewModel() {
    private val _state = MutableStateFlow(PracticeUiState())
    val state = _state.asStateFlow()

    private val drone = LoopTrack()
    private val drum = LoopTrack()
    private var droneJob: Job? = null
    private var drumJob: Job? = null
    private var beatJob: Job? = null

    init {
        viewModelScope.launch {
            OudSettings.a4.collect { a4 ->
                _state.update { it.copy(a4 = a4) }
                if (_state.value.droneOn) startDrone()
            }
        }
    }

    // Drone

    fun toggleDrone() = if (_state.value.droneOn) stopDrone() else startDrone()

    /** Moves the drone by [quarterTones] (2 = semitone). */
    fun shiftDrone(quarterTones: Int) {
        _state.update { it.copy(droneStep = (it.droneStep + quarterTones).coerceIn(DRONE_MIN, DRONE_MAX)) }
        if (_state.value.droneOn) startDrone()
    }

    fun setDroneStep(step: Int) {
        _state.update { it.copy(droneStep = step) }
        if (_state.value.droneOn) startDrone()
    }

    fun toggleFifth() {
        _state.update { it.copy(droneFifth = !it.droneFifth) }
        if (_state.value.droneOn) startDrone()
    }

    fun setDroneVolume(volume: Float) {
        _state.update { it.copy(droneVolume = volume) }
        drone.setVolume(volume)
    }

    private fun startDrone() {
        val s = _state.value
        _state.update { it.copy(droneOn = true) }
        droneJob?.cancel()
        droneJob = viewModelScope.launch(Dispatchers.Default) {
            val pcm = Drone.render(Pitch(s.droneStep).frequency(s.a4.toDouble()), s.droneFifth)
            drone.play(pcm, _state.value.droneVolume)
        }
    }

    private fun stopDrone() {
        droneJob?.cancel()
        drone.stop()
        _state.update { it.copy(droneOn = false) }
    }

    // Rhythm

    fun toggleRhythm() = if (_state.value.rhythmOn) stopRhythm() else startRhythm()

    fun selectIqa(iqa: Iqa) {
        _state.update { it.copy(iqa = iqa) }
        if (_state.value.rhythmOn) startRhythm()
    }

    fun changeBpm(delta: Int) = setBpm(_state.value.bpm + delta)

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(BPM_MIN, BPM_MAX)
        if (clamped == _state.value.bpm) return
        _state.update { it.copy(bpm = clamped) }
        if (_state.value.rhythmOn) startRhythm()
    }

    fun setRhythmVolume(volume: Float) {
        _state.update { it.copy(rhythmVolume = volume) }
        drum.setVolume(volume)
    }

    private fun startRhythm() {
        val s = _state.value
        _state.update { it.copy(rhythmOn = true) }
        drumJob?.cancel()
        beatJob?.cancel()
        drumJob = viewModelScope.launch(Dispatchers.Default) {
            drum.play(Drum.render(s.iqa, s.bpm), _state.value.rhythmVolume)
            // Follow the playback head so the screen can show which stroke is sounding.
            val step = s.iqa.stepSamples(s.bpm, LoopTrack.SAMPLE_RATE)
            val cycle = step * s.iqa.strokes.size
            beatJob = launch {
                while (isActive) {
                    val position = drum.position() ?: break
                    _state.update { it.copy(beatStep = (position % cycle) / step) }
                    delay(BEAT_POLL_MS)
                }
            }
        }
    }

    private fun stopRhythm() {
        drumJob?.cancel()
        beatJob?.cancel()
        drum.stop()
        _state.update { it.copy(rhythmOn = false, beatStep = null) }
    }

    /** Leaving the screen silences both, so nothing keeps playing under the other tabs. */
    fun stopAll() {
        stopDrone()
        stopRhythm()
    }

    override fun onCleared() = stopAll()

    private companion object {
        const val DRONE_MIN = 72 // C2
        const val DRONE_MAX = 120 // C4
        const val BPM_MIN = 40
        const val BPM_MAX = 240
        const val BEAT_POLL_MS = 20L
    }
}
