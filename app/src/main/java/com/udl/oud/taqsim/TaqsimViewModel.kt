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
            combine(OudSettings.tuning, OudSettings.a4, ::Pair).collect { (tuning, a4) ->
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

    // --- Free mode: you play, the app follows and reviews. ---

    fun startFree() {
        stop()
        collector = NoteCollector(_state.value.a4.toDouble())
        ignoreMicUntil = 0
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
            val rendered = withContext(Dispatchers.Default) {
                TaqsimRenderer.render(synth, s.taqsim, phrases, s.tempo.pulseMs, s.a4.toDouble(), s.tuning, s.ornaments, SAMPLE_RATE, s.pitchScale)
            }
            val pcm = rendered.pcm
            val starts = rendered.starts
            val totalMs = rendered.totalMs
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
            onFinished?.invoke()
        }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val targetFrequency = s.target?.let { it.hz * s.pitchScale }
        val update = tracker.update(reading, now) {
            val f = it.frequency.toDouble()
            if (targetFrequency != null && it.canBeOctaveLower &&
                abs(centsBetween(f / 2, targetFrequency)) < abs(centsBetween(f, targetFrequency))
            ) f / 2 else f
        }
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
        // Free mode: every note goes to the collector until it is stopped.
        val free = collector
        if (s.mode == TaqsimMode.Free && s.freeOn && free != null) {
            if (update.pluck) free.pluck()
            update.steady?.let { free.steady(it, now) }
            if (update.lost) free.silence()
            if (free.notes.size != s.freeNotes.size) _state.update { it.copy(freeNotes = free.notes) }
            return
        }
        // Answer mode: gather the notes played; a pause of [END_PAUSE_MS] ends the answer.
        val c = collector
        if (s.phase == AnswerPhase.Listening && c != null) {
            if (update.pluck) c.pluck()
            update.steady?.let { c.steady(it, now) }
            if (update.lost) c.silence()
            if (c.notes.size != s.answerNotes.size) _state.update { it.copy(answerNotes = c.notes) }
            // A stray sound in a quiet room is not an answer: wait for a couple of real notes first.
            if (c.notes.size >= 2 && now - c.lastSoundAt > END_PAUSE_MS) finishAnswer()
            return
        }
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
        const val END_PAUSE_MS = 2200L
    }
}
