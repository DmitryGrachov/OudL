package com.udl.oud.jins

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.DrillTaker
import com.udl.oud.audio.MicListener
import com.udl.oud.audio.PitchTracker
import com.udl.oud.fretboard.Drill
import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Pitch
import com.udl.oud.taqsim.AnswerCheck
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.AnswerVerdict
import com.udl.oud.taqsim.HeardNote
import com.udl.oud.taqsim.NoteCollector
import com.udl.oud.taqsim.OrnamentLevel
import com.udl.oud.taqsim.Ornamenter
import com.udl.oud.taqsim.PhraseRole
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.taqsim.PhrasePlayer
import com.udl.oud.taqsim.JinsGrammar
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.Motif
import com.udl.oud.taqsim.Station
import com.udl.oud.tuner.OudSettings
import com.udl.oud.tuner.Tuning
import com.udl.oud.tuner.centsBetween
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JinsUiState(
    val jins: Jins = JinsCatalog.ajnas.first(),
    val root: Int = JinsCatalog.defaultRoot(JinsCatalog.ajnas.first()),
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    /** Degree sounding now (with its octave), to light it up. */
    val playing: Int? = null,
    val live: Boolean = true,
    val frequency: Double? = null,
    /** Motif drill: steps are the motif's degrees. */
    val exercise: Drill? = null,
    val drillMotif: Motif? = null,
    val showCommon: Boolean = false,
    /** A short taqsim inside this jins, and the note of it sounding. */
    val etude: Taqsim = JinsEtude.generate(JinsCatalog.ajnas.first(), JinsCatalog.defaultRoot(JinsCatalog.ajnas.first()), 1),
    val etudeNote: Int? = null,
    val phase: AnswerPhase = AnswerPhase.Idle,
    val answerNotes: List<HeardNote> = emptyList(),
    val verdict: AnswerVerdict? = null,
    val error: String? = null,
) {
    val station: Station get() = JinsCatalog.station(jins, root)

    val title: String get() = jins.name

    /** Steps of the jins, in quarter tones. */
    val intervals: List<Int> get() = jins.intervals

    /** From the station's pitches at A4 = 440 to the A4 chosen. */
    val pitchScale: Double get() = a4 / 440.0

    /** The frequency a degree sounds at. */
    fun hz(degree: Int): Double = station.hz(degree) * pitchScale

    /** The etude's question and the answer after it: the answer mode asks that question. */
    val question: Int get() = etude.phrases.indexOfFirst { it.role == PhraseRole.Question }
    val answer: Int get() = question + 1

    /** Phrase of the etude sounding now. */
    val etudePhrase: Int? get() = etudeNote?.let { n -> etude.phrases.runningFold(0) { a, p -> a + p.notes.size }.indexOfLast { it <= n } }
    val grammar: JinsGrammar get() = JinsGrammars.of(station.jins)

    /** The jins' own notes, root to top. */
    val degrees: IntRange get() = 0..intervals.size

    val colour: List<Int> get() = grammar.characteristic.toList().sorted()

    val target: Int? get() = exercise?.target

    /** The heard note on the quarter-tone grid and how far off it is. */
    val heard: Pair<Pitch, Double>?
        get() = frequency?.let {
            val p = Pitch.nearest(it, a4.toDouble(), quarterTones = true)
            p to centsBetween(it, p.frequency(a4.toDouble()))
        }

    /** The heard note as the nearest degree of the scale (with its octave) and how far off it is. */
    val heardDegree: Pair<Int, Double>? get() = frequency?.let { station.degreeOfHz(it / pitchScale) }

    /** Degree of the jins (0..n) a grid step belongs to, or null for the notes around it. */
    fun degreeOf(step: Int): Int? = degrees.firstOrNull { Math.floorMod(station.step(it) - step, 24) == 0 }

    companion object {
        const val IN_TUNE_CENTS = 12.0
    }
}

/** The jins mode of the maqam tab: one jins on a root, its notes, motifs and drill. */
class JinsViewModel : ViewModel() {
    private val _state = MutableStateFlow(JinsUiState())
    val state = _state.asStateFlow()

    private val sound = PhrasePlayer()
    private val mic = MicListener(
        viewModelScope,
        resolve = MicListener.towards { _state.value.let { s -> s.target?.let(s::hz) } },
        onError = { e -> _state.update { it.copy(error = e) } },
        onUpdate = ::onUpdate,
    )
    private val drill = DrillTaker()
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            combine(OudSettings.tuning, OudSettings.a4, ::Pair).collect { (tuning, a4) ->
                _state.update { it.copy(tuning = tuning, a4 = a4) }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (!_state.value.live) return
        _state.update { it.copy(error = null) }
        mic.start()
    }

    fun stopListening() {
        mic.stop()
        _state.update { it.copy(frequency = null) }
    }

    fun setLive(live: Boolean) {
        _state.update { it.copy(live = live) }
        if (!live) stopListening()
    }

    fun select(jins: Jins) {
        _state.update { it.copy(jins = jins, root = JinsCatalog.defaultRoot(jins), exercise = null, drillMotif = null) }
        newEtude()
    }

    fun setRoot(step: Int) {
        _state.update { it.copy(root = step, exercise = null, drillMotif = null) }
        newEtude()
    }

    /** Moves the root by [quarterTones]; within the neck. */
    fun shiftRoot(quarterTones: Int) {
        _state.update { it.copy(root = (it.root + quarterTones).coerceIn(ROOT_MIN, ROOT_MAX), exercise = null, drillMotif = null) }
        newEtude()
    }

    // --- The etude and the answer in the jins ---

    /** A new etude on the jins as it stands; the answer starts over. */
    fun newEtude() {
        stop()
        val s = _state.value
        _state.update {
            val seed = System.nanoTime()
            val etude = JinsEtude.generate(s.jins, s.root, seed)
            it.copy(etude = etude, phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList())
        }
    }

    fun playEtude() = playTaqsim(0 until _state.value.etude.phrases.size)
    fun playEtudePhrase(i: Int) = playTaqsim(i..i)

    /** Plays the etude's question, then listens for the answer. */
    fun playQuestion() {
        val q = _state.value.question.takeIf { it >= 0 } ?: return
        _state.update { it.copy(phase = AnswerPhase.Question, verdict = null, answerNotes = emptyList(), exercise = null) }
        playTaqsim(q..q) {
            collector = NoteCollector(_state.value.a4.toDouble())
            _state.update { it.copy(phase = AnswerPhase.Listening) }
        }
    }

    /** The etude's own answer, to compare. */
    fun playExample() {
        val back = _state.value.phase
        playTaqsim(_state.value.answer.._state.value.answer) { _state.update { it.copy(phase = back) } }
    }

    fun finishAnswer() {
        val s = _state.value
        val answer = s.etude.phrases.getOrNull(s.answer) ?: return
        val notes = collector?.finish().orEmpty()
        collector = null
        _state.update { it.copy(phase = AnswerPhase.Done, answerNotes = notes, verdict = AnswerCheck.evaluate(notes, answer.station, answer.notes.last().degree)) }
    }

    private var collector: NoteCollector? = null

    private fun playTaqsim(phrases: IntRange, onFinished: (() -> Unit)? = null) {
        stop()
        val s = _state.value
        val first = s.etude.phrases.take(phrases.first).sumOf { it.notes.size }
        val notes = phrases.flatMap { s.etude.phrases[it].notes }
        playJob = viewModelScope.launch {
            mic.mute()
            sound.play(
                s.etude, phrases, ETUDE_PULSE_MS, s.a4.toDouble(), s.tuning, Ornamenter.decorate(s.etude, OrnamentLevel.Light), s.pitchScale,
                onNote = { i -> _state.update { it.copy(etudeNote = i?.let { first + it }, playing = i?.let { notes[it].degree }) } },
            )
            mic.unmute()
            onFinished?.invoke()
        }
    }

    fun toggleCommon() = _state.update { it.copy(showCommon = !it.showCommon) }

    fun playNote(degree: Int) = play(listOf(degree), listOf(900.0))

    /** The jins up from its root and back down. */
    fun playScale() {
        val s = _state.value
        val up = s.degrees.toList()
        val steps = up + up.reversed().drop(1)
        play(steps, steps.indices.map { if (it == steps.lastIndex) 1100.0 else 420.0 })
    }

    fun playMotif(m: Motif) {
        val steps = m.degrees
        play(steps, steps.indices.map { if (it == steps.lastIndex) 1000.0 else 360.0 })
    }

    /** "Play the motif": note by note through it, at the player's own pace. */
    fun startMotifDrill(m: Motif) {
        stop()
        drill.reset()
        _state.update { it.copy(exercise = Drill(m.degrees), drillMotif = m, live = true) }
    }

    fun restartDrill() = _state.value.drillMotif?.let(::startMotifDrill)
    fun stopDrill() = _state.update { it.copy(exercise = null, drillMotif = null) }

    fun stop() {
        playJob?.cancel()
        playJob = null
        mic.unmute()
        _state.update { it.copy(playing = null, etudeNote = null) }
    }

    /** Plays [steps] — degrees of the station — for [lengthsMs] each. */
    private fun play(steps: List<Int>, lengthsMs: List<Double>) {
        stop()
        val s = _state.value
        playJob = viewModelScope.launch {
            mic.mute()
            sound.playTones(steps.map(s::hz), lengthsMs) { i -> _state.update { it.copy(playing = i?.let(steps::get)) } }
            mic.unmute()
        }
    }

    private fun onUpdate(update: PitchTracker.Update, now: Long) {
        val s = _state.value
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
        // Answer: gather the notes played; a pause after a couple of real notes ends it.
        val c = collector
        if (s.phase == AnswerPhase.Listening && c != null) {
            c.feed(update, now)
            if (c.notes.size != s.answerNotes.size) _state.update { it.copy(answerNotes = c.notes) }
            if (c.ended(now)) finishAnswer()
            return
        }
        drill.feed(update, s.target?.let(s::hz))?.let { cents -> _state.update { it.copy(exercise = it.exercise?.record(cents)) } }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private companion object {
        const val ETUDE_PULSE_MS = 200.0
        const val ROOT_MIN = 84 // F♯2
        const val ROOT_MAX = 120 // C4
    }
}
