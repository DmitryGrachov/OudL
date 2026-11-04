package com.udl.oud.jins

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
import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Pitch
import com.udl.oud.fretboard.SteadyNote
import com.udl.oud.taqsim.AnswerCheck
import com.udl.oud.taqsim.AnswerPhase
import com.udl.oud.taqsim.AnswerVerdict
import com.udl.oud.taqsim.HeardNote
import com.udl.oud.taqsim.NoteCollector
import com.udl.oud.taqsim.OrnamentLevel
import com.udl.oud.taqsim.Ornamenter
import com.udl.oud.taqsim.PhraseRole
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.taqsim.TaqsimRenderer
import com.udl.oud.taqsim.JinsGrammar
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.Motif
import com.udl.oud.taqsim.Station
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

data class JinsUiState(
    val jins: Jins = JinsCatalog.ajnas.first(),
    val root: Int = JinsCatalog.defaultRoot(JinsCatalog.ajnas.first()),
    val tuning: Tuning = OudSettings.tuning.value,
    val a4: Int = OudSettings.a4.value,
    /** Grid step sounding now, to light it up. */
    val playing: Int? = null,
    val live: Boolean = true,
    val frequency: Double? = null,
    /** Motif drill: steps are the motif's grid pitches. */
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

    /** The etude's question and the answer after it: the answer mode asks that question. */
    val question: Int get() = etude.phrases.indexOfFirst { it.role == PhraseRole.Question }
    val answer: Int get() = question + 1

    /** Phrase of the etude sounding now. */
    val etudePhrase: Int? get() = etudeNote?.let { n -> etude.phrases.runningFold(0) { a, p -> a + p.notes.size }.indexOfLast { it <= n } }
    val grammar: JinsGrammar get() = JinsGrammars.of(jins)

    /** The jins' own notes, root to top. */
    val degrees: IntRange get() = 0..jins.intervals.size

    val colour: List<Int> get() = grammar.characteristic.toList().sorted()

    fun motifSteps(m: Motif): List<Int> = m.degrees.map { station.step(it) }

    val target: Int? get() = exercise?.target

    /** The heard note on the quarter-tone grid and how far off it is. */
    val heard: Pair<Pitch, Double>?
        get() = frequency?.let {
            val p = Pitch.nearest(it, a4.toDouble(), quarterTones = true)
            p to centsBetween(it, p.frequency(a4.toDouble()))
        }

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
            it.copy(etude = JinsEtude.generate(s.jins, s.root, System.nanoTime()), phase = AnswerPhase.Idle, verdict = null, answerNotes = emptyList())
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
            val rendered = withContext(Dispatchers.Default) {
                TaqsimRenderer.render(
                    synth, s.etude, phrases, ETUDE_PULSE_MS, s.a4.toDouble(), s.tuning,
                    Ornamenter.decorate(s.etude, OrnamentLevel.Light), SAMPLE_RATE,
                )
            }
            ignoreMicUntil = Long.MAX_VALUE
            val ticker = launch {
                while (isActive) {
                    player.positionMs()?.let { pos ->
                        val i = rendered.starts.indexOfLast { it <= pos }.takeIf { it >= 0 && pos < rendered.totalMs - rendered.tailMs + 400 }
                        _state.update { it.copy(etudeNote = i?.let { first + it }, playing = i?.let { notes[it].step }) }
                    }
                    delay(25)
                }
            }
            player.play(rendered.pcm)
            ticker.cancel()
            ignoreMicUntil = SystemClock.elapsedRealtime() + MIC_GRACE_MS
            tracker.reset()
            _state.update { it.copy(etudeNote = null, playing = null) }
            onFinished?.invoke()
        }
    }

    fun toggleCommon() = _state.update { it.copy(showCommon = !it.showCommon) }

    fun playNote(step: Int) = play(listOf(step), listOf(900.0))

    /** The jins up from its root and back down. */
    fun playScale() {
        val s = _state.value
        val up = s.degrees.map { s.station.step(it) }
        val steps = up + up.reversed().drop(1)
        play(steps, steps.indices.map { if (it == steps.lastIndex) 1100.0 else 420.0 })
    }

    fun playMotif(m: Motif) {
        val steps = _state.value.motifSteps(m)
        play(steps, steps.indices.map { if (it == steps.lastIndex) 1000.0 else 360.0 })
    }

    /** "Play the motif": note by note through it, at the player's own pace. */
    fun startMotifDrill(m: Motif) {
        stop()
        steady.reset()
        awaitingPluck = false
        _state.update { it.copy(exercise = Drill(it.motifSteps(m)), drillMotif = m, live = true) }
    }

    fun restartDrill() = _state.value.drillMotif?.let(::startMotifDrill)
    fun stopDrill() = _state.update { it.copy(exercise = null, drillMotif = null) }

    fun stop() {
        playJob?.cancel()
        playJob = null
        ignoreMicUntil = SystemClock.elapsedRealtime() + MIC_GRACE_MS
        _state.update { it.copy(playing = null, etudeNote = null) }
    }

    private fun play(steps: List<Int>, lengthsMs: List<Double>) {
        stop()
        val a4 = _state.value.a4.toDouble()
        val starts = lengthsMs.runningFold(0.0) { acc, l -> acc + l }.dropLast(1)
        val totalMs = starts.last() + lengthsMs.last() + TAIL_MS
        playJob = viewModelScope.launch {
            val pcm = withContext(Dispatchers.Default) {
                val plucks = steps.indices.map { i ->
                    PluckSynth.Pluck(Pitch(steps[i]).frequency(a4), (starts[i] * SAMPLE_RATE / 1000).toInt(), (lengthsMs[i] * SAMPLE_RATE / 1000).toInt())
                }
                PluckSynth.toPcm(synth.render(plucks, (totalMs * SAMPLE_RATE / 1000).toInt()))
            }
            ignoreMicUntil = Long.MAX_VALUE
            val ticker = launch {
                while (isActive) {
                    player.positionMs()?.let { pos ->
                        val i = starts.indexOfLast { it <= pos }
                        _state.update { it.copy(playing = steps.getOrNull(i)?.takeIf { pos < totalMs - TAIL_MS + 300 }) }
                    }
                    delay(25)
                }
            }
            player.play(pcm)
            ticker.cancel()
            ignoreMicUntil = SystemClock.elapsedRealtime() + MIC_GRACE_MS
            tracker.reset()
            _state.update { it.copy(playing = null) }
        }
    }

    private fun onReading(reading: PitchReading) {
        val now = SystemClock.elapsedRealtime()
        if (now < ignoreMicUntil) return
        val s = _state.value
        val targetFrequency = s.target?.let { Pitch(it).frequency(s.a4.toDouble()) }
        val update = tracker.update(reading, now) {
            val f = it.frequency.toDouble()
            if (targetFrequency != null && it.canBeOctaveLower &&
                abs(centsBetween(f / 2, targetFrequency)) < abs(centsBetween(f, targetFrequency))
            ) f / 2 else f
        }
        if (update.lost) _state.update { it.copy(frequency = null) }
        update.frequency?.let { f -> _state.update { it.copy(frequency = f) } }
        // Answer: gather the notes played; a pause after a couple of real notes ends it.
        val c = collector
        if (s.phase == AnswerPhase.Listening && c != null) {
            if (update.pluck) c.pluck()
            update.steady?.let { c.steady(it, now) }
            if (update.lost) c.silence()
            if (c.notes.size != s.answerNotes.size) _state.update { it.copy(answerNotes = c.notes) }
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
        const val TAIL_MS = 900.0
        const val MIC_GRACE_MS = 600L
        const val END_PAUSE_MS = 2200L
        const val ETUDE_PULSE_MS = 200.0
        const val ROOT_MIN = 84 // F♯2
        const val ROOT_MAX = 120 // C4
    }
}
