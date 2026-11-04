package com.udl.oud.ear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.PcmPlayer
import com.udl.oud.audio.PluckSynth
import com.udl.oud.fretboard.Pitch
import com.udl.oud.tuner.OudSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class EarUiState(
    /** The exercise open, or null on the list of exercises. */
    val exercise: EarExercise? = null,
    val level: EarLevel = EarLevel.Easy,
    val question: EarQuestion? = null,
    /** The option picked for this question, or null while it is open. */
    val chosen: Int? = null,
    /** Questions answered and right in this series. */
    val asked: Int = 0,
    val right: Int = 0,
    val playing: Boolean = false,
    /** Option being played for comparison. */
    val playingOption: Int? = null,
    val loading: Boolean = false,
    val stats: Map<Pair<EarExercise, EarLevel>, EarStats> = emptyMap(),
) {
    val seriesDone: Boolean get() = asked >= SERIES && chosen != null

    companion object {
        const val SERIES = 10
    }
}

/** The ear tab: series of questions per exercise and level, the answers kept as progress. */
class EarViewModel : ViewModel() {
    private val _state = MutableStateFlow(EarUiState())
    val state = _state.asStateFlow()

    private val synth = PluckSynth(SAMPLE_RATE)
    private val player = PcmPlayer(SAMPLE_RATE)
    private var playJob: Job? = null
    private val random = Random(System.nanoTime())

    init {
        viewModelScope.launch { EarProgress.stats.collect { s -> _state.update { it.copy(stats = s) } } }
    }

    fun open(exercise: EarExercise) {
        _state.update { it.copy(exercise = exercise, asked = 0, right = 0, question = null, chosen = null) }
        next()
    }

    fun close() {
        stop()
        _state.update { it.copy(exercise = null, question = null, chosen = null) }
    }

    fun setLevel(level: EarLevel) {
        _state.update { it.copy(level = level, asked = 0, right = 0) }
        if (_state.value.exercise != null) next()
    }

    /** A new question, played at once; after a finished series, a new series. */
    fun next() {
        val s = _state.value
        val exercise = s.exercise ?: return
        stop()
        if (s.seriesDone) _state.update { it.copy(asked = 0, right = 0) }
        _state.update { it.copy(loading = true, chosen = null) }
        viewModelScope.launch {
            val q = withContext(Dispatchers.Default) { EarQuestions.make(exercise, _state.value.level, random) }
            _state.update { it.copy(question = q, loading = false) }
            play(q.sound, null)
        }
    }

    fun replay() = _state.value.question?.let { play(it.sound, null) }

    fun choose(option: Int) {
        val s = _state.value
        val q = s.question ?: return
        if (s.chosen != null) return
        val isRight = option == q.correct
        EarProgress.record(q.exercise, q.level, isRight)
        _state.update { it.copy(chosen = option, asked = it.asked + 1, right = it.right + if (isRight) 1 else 0) }
    }

    /** Plays one option the same way, to hear the difference after answering. */
    fun playOption(option: Int) {
        val q = _state.value.question ?: return
        q.compare.getOrNull(option)?.let { play(it, option) }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _state.update { it.copy(playing = false, playingOption = null) }
    }

    private fun play(notes: List<EarNote>, option: Int?) {
        stop()
        val a4 = OudSettings.a4.value.toDouble()
        _state.update { it.copy(playing = true, playingOption = option) }
        playJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                fun samples(ms: Double) = (ms * SAMPLE_RATE / 1000).toInt()
                val plucks = notes.map { n -> PluckSynth.Pluck(Pitch(n.step).frequency(a4), samples(n.startMs), samples(n.lengthMs), n.velocity) }
                val total = notes.maxOf { it.startMs + it.lengthMs } + 900
                PluckSynth.toPcm(synth.render(plucks, samples(total)))
            }
            player.play(pcm)
            _state.update { it.copy(playing = false, playingOption = null) }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
