package com.udl.oud.talwin

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.MicListener
import com.udl.oud.audio.PitchTracker
import com.udl.oud.fretboard.Maqam
import com.udl.oud.taqsim.AnswerCheck
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.AnswerVerdict
import com.udl.oud.taqsim.HeardNote
import com.udl.oud.taqsim.NoteCollector
import com.udl.oud.taqsim.OrnamentLevel
import com.udl.oud.taqsim.Ornamenter
import com.udl.oud.taqsim.PhrasePlayer
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.tuner.OudSettings
import com.udl.oud.tuner.Tuning
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TalwinMode(val title: String) {
    Listen("Слушать"),
    Answer("Ответь"),
    Quiz("Куда ушли?"),
}

/** "Where did it go?": a move plays, the player names the jins it went to. */
data class TalwinQuiz(val answer: Talwin, val options: List<String>, val picked: String? = null, val right: Int = 0, val asked: Int = 1)

data class TalwinUiState(
    val maqam: Maqam = Talwin.maqamatWithMoves.first(),
    val move: Int = 0,
    val talwin: Talwin = Talwin.of(Talwin.maqamatWithMoves.first()).first(),
    val taqsim: Taqsim = Talwin.of(Talwin.maqamatWithMoves.first()).first().generate(1),
    val mode: TalwinMode = TalwinMode.Listen,
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    /** Index of the taqsim's note sounding, and the frequency sounding now (also for the scale runs). */
    val playingNote: Int? = null,
    val playingHz: Double? = null,
    val playing: Boolean = false,
    val live: Boolean = true,
    val frequency: Double? = null,
    val phase: AnswerPhase = AnswerPhase.Idle,
    val answerNotes: List<HeardNote> = emptyList(),
    val verdict: AnswerVerdict? = null,
    val quiz: TalwinQuiz? = null,
    val error: String? = null,
) {
    val pitchScale: Double get() = a4 / 440.0

    /** Phrases of the move itself (in the new jins), and those before it (home and the way to the pivot). */
    val movePhrases: List<Int> get() = taqsim.phrases.indices.filter { taqsim.phrases[it].section == SectionKind.Modulation }
    val beforeMove: IntRange get() = 0 until (movePhrases.firstOrNull() ?: 0)

    /** Phrase sounding now. */
    val playingPhrase: Int? get() = playingNote?.let { n -> taqsim.phrases.runningFold(0) { a, p -> a + p.notes.size }.indexOfLast { it <= n } }
}

/** The talwin mode: see and hear a modulation, answer in it, and tell by ear where a taqsim went. */
class TalwinViewModel : ViewModel() {
    private val _state = MutableStateFlow(TalwinUiState())
    val state = _state.asStateFlow()

    private val sound = PhrasePlayer()
    private val mic = MicListener(viewModelScope, onError = { e -> _state.update { it.copy(error = e) } }, onUpdate = ::onUpdate)
    private var playJob: Job? = null
    private var collector: NoteCollector? = null

    init {
        viewModelScope.launch {
            combine(OudSettings.tuning, OudSettings.a4, ::Pair).collect { (t, a) -> _state.update { it.copy(tuning = t, a4 = a) } }
        }
    }

    fun selectMaqam(m: Maqam) {
        _state.update { it.copy(maqam = m) }
        selectMove(0)
    }

    fun selectMove(i: Int) {
        val moves = Talwin.of(_state.value.maqam)
        val t = moves.getOrNull(i) ?: return
        stop()
        _state.update { it.copy(move = i, talwin = t, taqsim = t.generate(System.nanoTime()), phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList()) }
    }

    /** Another taqsim over the same move. */
    fun regenerate() {
        stop()
        _state.update { it.copy(taqsim = it.talwin.generate(System.nanoTime()), phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList()) }
    }

    fun setMode(mode: TalwinMode) {
        stop()
        _state.update { it.copy(mode = mode, quiz = null, phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList()) }
        if (mode == TalwinMode.Quiz) nextQuiz()
    }

    fun playAll() = playTaqsim(0 until _state.value.taqsim.phrases.size)
    fun playPhrase(i: Int) = playTaqsim(i..i)

    /** Before: the maqam's own notes where the new jins will stand. After: the new jins on the same notes. */
    fun playBefore() {
        val t = _state.value.talwin
        // As many notes as the new jins has, from the pivot up, in the maqam's own tuning.
        val n = t.target.lower.intervals.size
        val home = com.udl.oud.taqsim.TaqsimSayrs.Stations(t.from, t.tonic).lower
        val notes = (t.pivot..t.pivot + n).map { home.hz(it) }
        playRun(notes + notes.reversed().drop(1))
    }

    fun playAfter() {
        val t = _state.value.talwin
        val notes = (0..t.target.lower.intervals.size).map { t.station.hz(it) }
        playRun(notes + notes.reversed().drop(1))
    }

    // --- Answer: the app leads to the pivot, the player makes the move ---

    fun playQuestion() {
        val s = _state.value
        val before = s.beforeMove
        if (before.isEmpty()) return
        _state.update { it.copy(phase = AnswerPhase.Question, verdict = null, answerNotes = emptyList()) }
        playTaqsim(before) {
            collector = NoteCollector(_state.value.a4.toDouble())
            _state.update { it.copy(phase = AnswerPhase.Listening) }
        }
    }

    fun playExample() {
        val s = _state.value
        val m = s.movePhrases
        if (m.isEmpty()) return
        val back = s.phase
        playTaqsim(m.first()..m.last()) { _state.update { it.copy(phase = back) } }
    }

    fun finishAnswer() {
        val notes = collector?.finish().orEmpty()
        collector = null
        val station = _state.value.talwin.station
        _state.update { it.copy(phase = AnswerPhase.Done, answerNotes = notes, verdict = AnswerCheck.evaluate(notes, station, 0)) }
    }

    // --- Where did it go? ---

    fun nextQuiz() {
        val previous = _state.value.quiz
        val all = Talwin.maqamatWithMoves.flatMap { Talwin.of(it) }
        val answer = all.filter { it.title != previous?.answer?.title || it.from != previous.answer.from }.random()
        // The other moves of the same maqam, then other ajnas on the same note, to make four.
        val same = Talwin.of(answer.from).map { it.title }.filter { it != answer.title }
        val others = com.udl.oud.fretboard.Maqamat.all.map { it.lower.name }.distinct()
            .filter { it != answer.target.lower.name }
            .map { "$it на ${answer.station.label(0)}" }
            .shuffled()
        val options = (listOf(answer.title) + same.shuffled().take(2) + others).distinct().take(4).shuffled()
        _state.update {
            it.copy(
                quiz = TalwinQuiz(answer, options, right = previous?.right ?: 0, asked = (previous?.asked ?: 0) + 1),
                talwin = answer,
                maqam = answer.from,
                taqsim = answer.generate(System.nanoTime()),
            )
        }
        playQuizMove()
    }

    /** Home, the way to the pivot and the move: the return would give the answer away less, but it is the end. */
    fun playQuizMove() {
        val s = _state.value
        val last = s.movePhrases.lastOrNull() ?: return
        playTaqsim(0..last)
    }

    fun pickQuiz(option: String) {
        val q = _state.value.quiz ?: return
        if (q.picked != null) return
        _state.update { it.copy(quiz = q.copy(picked = option, right = q.right + if (option == q.answer.title) 1 else 0)) }
    }

    // --- Sound ---

    private fun playTaqsim(phrases: IntRange, onFinished: (() -> Unit)? = null) {
        stop()
        val s = _state.value
        val first = s.taqsim.phrases.take(phrases.first).sumOf { it.notes.size }
        val notes = phrases.flatMap { s.taqsim.phrases[it].notes }
        _state.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            mic.mute()
            sound.play(
                s.taqsim, phrases, PULSE_MS, s.a4.toDouble(), s.tuning, Ornamenter.decorate(s.taqsim, OrnamentLevel.Light), s.pitchScale,
                onNote = { i -> _state.update { it.copy(playingNote = i?.let { first + it }, playingHz = i?.let { notes[it].hz * s.pitchScale }) } },
            )
            done()
            onFinished?.invoke()
        }
    }

    /** A run of notes at an even pace, the last one long. */
    private fun playRun(hz440: List<Double>) {
        stop()
        val s = _state.value
        val hz = hz440.map { it * s.pitchScale }
        _state.update { it.copy(playing = true) }
        playJob = viewModelScope.launch {
            mic.mute()
            sound.playTones(hz, hz.indices.map { if (it == hz.lastIndex) 1100.0 else 420.0 }) { i ->
                _state.update { it.copy(playingHz = i?.let(hz::get)) }
            }
            done()
        }
    }

    private fun done() {
        mic.unmute()
        _state.update { it.copy(playingNote = null, playingHz = null, playing = false) }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        mic.unmute()
        _state.update { it.copy(playingNote = null, playingHz = null, playing = false) }
    }

    // --- Microphone ---

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

    private fun onUpdate(update: PitchTracker.Update, now: Long) {
        val s = _state.value
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
        val c = collector
        if (s.phase == AnswerPhase.Listening && c != null) {
            c.feed(update, now)
            if (c.notes.size != s.answerNotes.size) _state.update { it.copy(answerNotes = c.notes) }
            if (c.ended(now)) finishAnswer()
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }

    private companion object {
        const val PULSE_MS = 200.0
    }
}
