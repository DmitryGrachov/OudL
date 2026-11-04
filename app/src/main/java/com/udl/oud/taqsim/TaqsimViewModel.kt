package com.udl.oud.taqsim

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udl.oud.audio.DrillTaker
import com.udl.oud.audio.MicListener
import com.udl.oud.audio.PitchTracker
import com.udl.oud.fretboard.Drill
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Pitch
import com.udl.oud.tuner.OudSettings
import com.udl.oud.tuner.Tuning
import com.udl.oud.tuner.centsBetween
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Playback speed: how long the base pulse lasts. */
enum class TaqsimTempo(val title: String, val pulseMs: Double) {
    Normal("Обычно", 190.0),
    Slow("Медленнее", 260.0),
    VerySlow("Совсем медленно", 380.0),
}

/** Listen to a whole taqsim, or answer its questions yourself. */
enum class TaqsimMode(val title: String) {
    Listen("Слушать"),
    Answer("Ответь"),
    Free("Свободно"),
}

/** Where the question is asked: low (answer comes home to the tonic) or high (to the ghammaz). */
enum class AnswerLevel(val title: String, val section: SectionKind?) {
    Low("Внизу: ответ на тонику", SectionKind.Opening),
    High("Наверху: ответ на гаммаз", SectionKind.Upper),
    Any("Где угодно", null),
}

enum class AnswerPhase { Idle, Question, Listening, Done }

data class TaqsimUiState(
    val mode: TaqsimMode = TaqsimMode.Listen,
    val answerLevel: AnswerLevel = AnswerLevel.Low,
    /** The question phrase and the answer that follows it in [taqsim]. */
    val question: Int? = null,
    val answer: Int? = null,
    val phase: AnswerPhase = AnswerPhase.Idle,
    /** Notes heard so far in the player's answer. */
    val answerNotes: List<HeardNote> = emptyList(),
    val verdict: AnswerVerdict? = null,
    /** Free mode: listening, the notes so far, the review once stopped, and whether the chosen maqam is meant. */
    val freeOn: Boolean = false,
    val freeNotes: List<HeardNote> = emptyList(),
    val freeReview: FreeReview? = null,
    val freeInChosen: Boolean = true,
    val maqam: Maqam = Maqamat.all.first(),
    val length: TaqsimLength = TaqsimLength.Medium,
    val tempo: TaqsimTempo = TaqsimTempo.Normal,
    val ornamentLevel: OrnamentLevel = OrnamentLevel.Light,
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
    /** Ornament on each note of [Taqsim.notes] by index, for the chosen level. */
    val ornaments: Map<Int, Ornament> get() = Ornamenter.decorate(taqsim, ornamentLevel)

    /** Phrase of each note of [Taqsim.notes]. */
    val phraseOfNote: List<Int> get() = taqsim.phrases.flatMapIndexed { i, p -> List(p.notes.size) { i } }

    /** First note index of each phrase. */
    val phraseStarts: List<Int> get() = taqsim.phrases.runningFold(0) { acc, p -> acc + p.notes.size }.dropLast(1)

    val playingPhrase: Int? get() = playingNote?.let { phraseOfNote.getOrNull(it) }

    /** The phrase to show: the one sounding, else the one picked, else the first. */
    val shownPhrase: Int
        get() = playingPhrase ?: drillPhrase?.takeIf { exercise != null }
            ?: answer?.takeIf { mode == TaqsimMode.Answer } ?: selected ?: 0

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
            return centsBetween(f, t.hz * pitchScale)
        }

    /** Frequency factor against A4 = 440. */
    val pitchScale: Double get() = a4 / 440.0

    /** What the taqsim is in, for titles. */
    val title: String get() = maqam.name

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

    private val sound = PhrasePlayer()
    private val mic = MicListener(
        viewModelScope,
        resolve = MicListener.towards { _state.value.let { s -> s.target?.let { it.hz * s.pitchScale } } },
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
        regenerate()
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

    fun select(maqam: Maqam) {
        _state.update { it.copy(maqam = maqam) }
        regenerate()
    }

    /** A new taqsim in the chosen maqam. */
    private fun makeTaqsim(s: TaqsimUiState, seed: Long, length: TaqsimLength): Taqsim =
        TaqsimGenerator(seed).generate(s.maqam, length)

    fun setLength(length: TaqsimLength) {
        _state.update { it.copy(length = length) }
        regenerate()
    }

    fun setTempo(tempo: TaqsimTempo) = _state.update { it.copy(tempo = tempo) }
    fun setOrnamentLevel(level: OrnamentLevel) = _state.update { it.copy(ornamentLevel = level) }

    /** A new taqsim on the same maqam. */
    fun regenerate() {
        stop()
        val s = _state.value
        val seed = System.nanoTime()
        _state.update { it.copy(taqsim = makeTaqsim(s, seed, s.length), selected = null, exercise = null, drillPhrase = null) }
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
        mic.unmute()
        _state.update { it.copy(playing = false, playingNote = null) }
    }

    /** "Repeat the phrase": the drill goes note by note through it, at the player's own pace. */
    fun startRepeat(index: Int) {
        stop()
        drill.reset()
        val steps = _state.value.taqsim.phrases[index].notes.map { it.step }
        _state.update { it.copy(exercise = Drill(steps), drillPhrase = index, selected = index, live = true) }
    }

    fun restartRepeat() = _state.value.drillPhrase?.let(::startRepeat)
    fun stopRepeat() = _state.update { it.copy(exercise = null, drillPhrase = null) }

    // --- Free mode: you play, the app follows and reviews. ---

    fun startFree() {
        stop()
        collector = NoteCollector(_state.value.a4.toDouble())
        mic.unmute(graceMs = 0)
        _state.update { it.copy(freeOn = true, freeNotes = emptyList(), freeReview = null, live = true) }
    }

    /** Stops listening and reviews what was played. */
    fun stopFree() {
        val notes = collector?.finish().orEmpty()
        collector = null
        val s = _state.value
        _state.update { it.copy(freeOn = false, freeNotes = notes, freeReview = FreePlay.review(notes, if (s.freeInChosen) s.maqam else null)) }
    }

    fun setFreeInChosen(inChosen: Boolean) = _state.update {
        val next = inChosen
        val review = if (!it.freeOn && it.freeNotes.isNotEmpty()) FreePlay.review(it.freeNotes, if (next) it.maqam else null) else it.freeReview
        it.copy(freeInChosen = next, freeReview = review)
    }

    // --- Answer mode: the app asks, the player answers. ---

    fun setMode(mode: TaqsimMode) {
        stop()
        collector = null
        _state.update {
            it.copy(
                mode = mode, exercise = null, drillPhrase = null, phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList(),
                freeOn = false,
            )
        }
        if (mode == TaqsimMode.Answer) nextQuestion(play = false)
    }

    fun setAnswerLevel(level: AnswerLevel) {
        _state.update { it.copy(answerLevel = level) }
        nextQuestion(play = false)
    }

    /**
     * A fresh question: a new taqsim, and in it a question phrase of the chosen kind with the
     * answer that follows it, kept as the example.
     */
    fun nextQuestion(play: Boolean = true) {
        stop()
        val s = _state.value
        var pair: Pair<Int, Int>? = null
        var taqsim = s.taqsim
        // A medium taqsim always has a low and a high question; try a few seeds to be sure.
        repeat(8) {
            if (pair != null) return@repeat
            taqsim = makeTaqsim(s, System.nanoTime(), TaqsimLength.Medium)
            val questions = taqsim.phrases.indices.filter { i ->
                val p = taqsim.phrases[i]
                p.role == PhraseRole.Question && (s.answerLevel.section == null || p.section == s.answerLevel.section) &&
                    taqsim.phrases.getOrNull(i + 1)?.role == PhraseRole.Answer
            }
            questions.randomOrNull()?.let { pair = it to it + 1 }
        }
        val (q, a) = pair ?: return
        _state.update {
            it.copy(
                taqsim = taqsim, question = q, answer = a, selected = null, live = true,
                phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList(),
            )
        }
        if (play) playQuestion()
    }

    /** Plays the question, then listens for the answer. */
    fun playQuestion() {
        val q = _state.value.question ?: return
        _state.update { it.copy(phase = AnswerPhase.Question, verdict = null, answerNotes = emptyList()) }
        playRange(q..q, loop = false) {
            collector = NoteCollector(_state.value.a4.toDouble())
            _state.update { it.copy(phase = AnswerPhase.Listening) }
        }
    }

    /** The generator's own answer, to compare. */
    fun playExample() {
        val a = _state.value.answer ?: return
        val back = _state.value.phase
        playRange(a..a, loop = false) { _state.update { it.copy(phase = back) } }
    }

    /** Ends the answer now instead of waiting for the pause. */
    fun finishAnswer() {
        val s = _state.value
        val a = s.answer ?: return
        val notes = collector?.finish().orEmpty()
        collector = null
        val station = s.taqsim.phrases[a].station
        val target = s.taqsim.phrases[a].notes.last().degree
        _state.update { it.copy(phase = AnswerPhase.Done, answerNotes = notes, verdict = AnswerCheck.evaluate(notes, station, target)) }
    }

    private var collector: NoteCollector? = null

    private fun playRange(phrases: IntRange, loop: Boolean, onFinished: (() -> Unit)? = null) {
        stop()
        val s = _state.value
        val first = s.phraseStarts[phrases.first]
        _state.update { it.copy(playing = true, exercise = null) }
        playJob = viewModelScope.launch {
            mic.mute()
            sound.play(
                s.taqsim, phrases, s.tempo.pulseMs, s.a4.toDouble(), s.tuning, s.ornaments, s.pitchScale,
                onNote = { i -> _state.update { it.copy(playingNote = i?.let { first + it }) } },
                loop = { loop && _state.value.loop },
            )
            mic.unmute()
            _state.update { it.copy(playing = false, playingNote = null) }
            onFinished?.invoke()
        }
    }

    private fun onUpdate(update: PitchTracker.Update, now: Long) {
        val s = _state.value
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
        // Free mode: every note goes to the collector until it is stopped.
        val c = collector
        if (s.mode == TaqsimMode.Free && s.freeOn && c != null) {
            c.feed(update, now)
            if (c.notes.size != s.freeNotes.size) _state.update { it.copy(freeNotes = c.notes) }
            return
        }
        // Answer mode: gather the notes played; a pause ends the answer.
        if (s.phase == AnswerPhase.Listening && c != null) {
            c.feed(update, now)
            if (c.notes.size != s.answerNotes.size) _state.update { it.copy(answerNotes = c.notes) }
            if (c.ended(now)) finishAnswer()
            return
        }
        drill.feed(update, s.target?.let { it.hz * s.pitchScale })?.let { cents ->
            _state.update { it.copy(exercise = it.exercise?.record(cents)) }
        }
    }

    override fun onCleared() {
        playJob?.cancel()
    }
}
