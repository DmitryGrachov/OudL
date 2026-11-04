package com.udl.oud.ear

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Spelling
import com.udl.oud.jins.JinsCatalog
import com.udl.oud.jins.JinsEtude
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.TaqsimGenerator
import com.udl.oud.taqsim.TaqsimLength
import com.udl.oud.taqsim.TaqsimPhrase
import kotlin.random.Random

/** The ear exercises: hearing without the oud in hand. */
enum class EarExercise(val title: String, val about: String) {
    Jins("Узнай джинс", "Звучит джинс — какой это?"),
    Maqam("Узнай макам", "Звучит макам — какой?"),
    Interval("Интервал", "Две ноты подряд — какое между ними расстояние?"),
    QuarterTone("Четвертитон", "Какая нота прозвучала в середине: ниже, посередине или выше?"),
    Rest("На чём остановилась", "Сначала звукоряд макама, потом фраза — на какой ступени она остановилась?"),
}

enum class EarLevel(val title: String) {
    Easy("Легко"),
    Medium("Средне"),
    Hard("Трудно"),
}

/** A sound of a question: [step] on the quarter-tone grid, from [startMs] for [lengthMs]. */
data class EarNote(val step: Int, val startMs: Double, val lengthMs: Double, val velocity: Float = 0.85f)

/**
 * One question: what plays, the options, which is right, and what to say after. [compare] plays
 * each option the same way, so a wrong answer can be heard next to the right one.
 */
data class EarQuestion(
    val exercise: EarExercise,
    val level: EarLevel,
    val prompt: String,
    val options: List<String>,
    val correct: Int,
    val sound: List<EarNote>,
    val explanation: String,
    val compare: List<List<EarNote>> = emptyList(),
) {
    init {
        require(options.size >= 2 && correct in options.indices && options.distinct().size == options.size)
        require(sound.isNotEmpty())
        require(compare.isEmpty() || compare.size == options.size)
    }
}

/**
 * Makes ear questions from what the app already knows: jins grammars and etudes, maqam scales
 * and taqasim, the quarter-tone grid. Easy levels play plain scales and few, very different
 * options; hard ones play phrases and ask between close relatives.
 */
object EarQuestions {
    private const val NOTE_MS = 430.0
    private const val PULSE_MS = 200.0

    fun make(exercise: EarExercise, level: EarLevel, random: Random): EarQuestion = when (exercise) {
        EarExercise.Jins -> jins(level, random)
        EarExercise.Maqam -> maqam(level, random)
        EarExercise.Interval -> interval(level, random)
        EarExercise.QuarterTone -> quarterTone(level, random)
        EarExercise.Rest -> rest(level, random)
    }

    // --- Sounds ---

    /** Notes one after another, the last one held. */
    fun line(steps: List<Int>, noteMs: Double = NOTE_MS, from: Double = 0.0): List<EarNote> =
        steps.mapIndexed { i, s -> EarNote(s, from + i * noteMs, if (i == steps.lastIndex) noteMs * 2.5 else noteMs) }

    /** Phrases of a taqsim or etude with their own rhythm and breaths. */
    fun phrases(phrases: List<TaqsimPhrase>, from: Double = 0.0): List<EarNote> {
        val out = mutableListOf<EarNote>()
        var t = from
        phrases.forEachIndexed { k, p ->
            val pulse = PULSE_MS * p.tempo
            p.notes.forEach { n ->
                out += EarNote(n.step, t, n.beats * pulse, 0.6f + 0.35f * n.velocity)
                t += (n.beats + n.pauseAfter) * pulse
            }
            if (k != phrases.lastIndex) t += p.restBeats * pulse
        }
        return out
    }

    private fun end(sound: List<EarNote>) = sound.maxOf { it.startMs + it.lengthMs }

    // --- Jins ---

    private val easyAjnas = listOf(Ajnas.Rast, Ajnas.Hijaz, Ajnas.Saba, Ajnas.Ajam)
    private val mediumAjnas = listOf(Ajnas.Rast, Ajnas.Bayati, Ajnas.Hijaz, Ajnas.Saba, Ajnas.Kurd, Ajnas.Nahawand, Ajnas.Ajam, Ajnas.Sikah)

    private fun jinsScale(j: Jins, root: Int): List<EarNote> {
        val s = JinsCatalog.station(j, root)
        val up = (0..j.intervals.size).map { s.step(it) }
        return line(up + up.reversed().drop(1))
    }

    private fun jins(level: EarLevel, random: Random): EarQuestion {
        val pool = when (level) {
            EarLevel.Easy -> easyAjnas
            EarLevel.Medium -> mediumAjnas
            EarLevel.Hard -> JinsCatalog.ajnas
        }
        val options = when (level) {
            EarLevel.Easy -> pool.shuffled(random).take(3)
            EarLevel.Medium -> pool.shuffled(random).take(4)
            EarLevel.Hard -> pool
        }
        val answer = options.random(random)
        val root = JinsCatalog.roots(answer).random(random)
        // Easy: the plain scale; harder: a phrase of an etude, as the jins is really heard.
        fun soundOf(j: Jins, r: Int) = if (level == EarLevel.Easy) jinsScale(j, r) else phrases(JinsEtude.generate(j, r, random.nextLong()).phrases.take(2))
        val sound = soundOf(answer, root)
        return EarQuestion(
            EarExercise.Jins, level, "Какой это джинс?",
            options.map { it.name }, options.indexOf(answer), sound,
            explanation = JinsGrammars.of(answer).about,
            compare = options.map { jinsScale(it, root) },
        )
    }

    // --- Maqam ---

    private val heads = Maqamat.families.map { it.scales.first() as Maqam }

    private fun maqamScale(m: Maqam, descending: Boolean = false): List<EarNote> {
        val up = m.notes(m.tonic).map { m.tonic + it.offset } + (m.tonic + 24)
        val down = m.notes(m.tonic, descending = true).map { m.tonic + it.offset }.reversed()
        return line(up + if (descending) down else emptyList(), noteMs = 380.0)
    }

    private fun maqam(level: EarLevel, random: Random): EarQuestion {
        val options: List<Maqam> = when (level) {
            EarLevel.Easy -> heads.shuffled(random).take(3)
            EarLevel.Medium -> heads.shuffled(random).take(4)
            // Hard: relatives of one family, told apart by what sits on top.
            EarLevel.Hard -> Maqamat.families.filter { it.scales.size >= 2 }.random(random).scales.map { it as Maqam }.shuffled(random).take(4)
        }
        val answer = options.random(random)
        val sound = when (level) {
            EarLevel.Easy -> maqamScale(answer, descending = true)
            EarLevel.Medium -> phrases(TaqsimGenerator(random.nextLong()).generate(answer, TaqsimLength.Short).phrases.take(3))
            EarLevel.Hard -> {
                // The scale to the octave, then a phrase in the upper jins, where relatives differ.
                val scale = maqamScale(answer)
                val t = TaqsimGenerator(random.nextLong()).generate(answer, TaqsimLength.Medium)
                val upper = t.phrases.filter { it.section == SectionKind.Upper }.take(1).ifEmpty { t.phrases.take(1) }
                scale + phrases(upper, from = end(scale) + 700)
            }
        }
        val family = Maqamat.families.first { answer in it.scales }.name
        val about = "${answer.name}: ${answer.lower.name} на тонике, ${answer.upper.name} наверху" +
            (if (answer.hasDescent) ", на спуске ${answer.upperJins(true).name}" else "") + ". Семейство $family."
        return EarQuestion(
            EarExercise.Maqam, level, "Какой это макам?",
            options.map { it.name }, options.indexOf(answer), sound, about,
            compare = options.map { maqamScale(it) },
        )
    }

    // --- Interval ---

    /** An interval in quarter tones with its name. */
    data class IntervalKind(val quarters: Int, val name: String)

    private val intervals = mapOf(
        EarLevel.Easy to listOf(IntervalKind(2, "полутон"), IntervalKind(4, "тон"), IntervalKind(6, "полтора тона")),
        EarLevel.Medium to listOf(IntervalKind(2, "полутон"), IntervalKind(3, "¾ тона"), IntervalKind(4, "тон"), IntervalKind(6, "полтора тона")),
        EarLevel.Hard to listOf(
            IntervalKind(3, "¾ тона"), IntervalKind(4, "тон"), IntervalKind(6, "малая терция"),
            IntervalKind(7, "нейтральная терция"), IntervalKind(8, "большая терция"),
        ),
    )

    fun intervalKinds(level: EarLevel) = intervals.getValue(level)

    private fun interval(level: EarLevel, random: Random): EarQuestion {
        val kinds = intervalKinds(level)
        val answer = kinds.random(random)
        val root = random.nextInt(96, 116)
        val down = level == EarLevel.Hard && random.nextBoolean()
        fun pair(q: Int) = if (down) listOf(root + q, root) else listOf(root, root + q)
        val sound = line(pair(answer.quarters), noteMs = 700.0).let { first -> first + line(pair(answer.quarters), 700.0, end(first) + 500) }
        val where = mapOf(
            3 to "¾ тона — Ре–Ми½♭ в Баяти, Ми½♭–Фа в Расте",
            4 to "тон — До–Ре в Расте",
            2 to "полутон — Ре–Ми♭ в Курде",
            6 to "полтора тона — Ми♭–Фа♯ в Хиджазе",
            7 to "нейтральная терция — До–Ми½♭ в Расте",
            8 to "большая терция — До–Ми в Аджаме",
        )
        return EarQuestion(
            EarExercise.Interval, level, if (down) "Какой интервал (вниз)?" else "Какой интервал?",
            kinds.map { it.name }, kinds.indexOf(answer), sound,
            explanation = where[answer.quarters] ?: answer.name,
            compare = kinds.map { line(pair(it.quarters), 700.0) },
        )
    }

    // --- Quarter tone ---

    /** Spells [step] on the given [letter] (0 = C … 6 = B), so the three choices share a letter. */
    fun spellOn(letter: Int, step: Int): String {
        val natural = Spelling.NATURAL_STEPS[letter]
        val alter = Math.floorMod(step - natural + 12, 24) - 12
        return Spelling(letter, alter).label(NoteLabels.Solfege)
    }

    private fun quarterTone(level: EarLevel, random: Random): EarQuestion {
        // A third above a natural root: minor, neutral or major — as in Nahawand, Rast and Ajam.
        val roots = when (level) {
            EarLevel.Easy, EarLevel.Medium -> listOf(96) // C3: E♭ / E½♭ / E
            EarLevel.Hard -> listOf(96, 100, 110, 106) // C, D, G, F
        }
        val root = roots.random(random)
        val rootSpelling = Spelling.of(root)
        val third = (rootSpelling.letter + 2) % 7
        val candidates = when (level) {
            EarLevel.Easy -> listOf(7, 8) // neutral or major
            else -> listOf(6, 7, 8)
        }
        val answer = candidates.random(random)
        // Context: root and the tone above, the third, back down — the way the note is met in a jins.
        fun around(q: Int) = line(listOf(root, root + 4, root + q, root + 4, root), noteMs = 520.0)
        val names = candidates.map { spellOn(third, root + it) }
        val explain = mapOf(
            6 to "пониженная — как в Нахаванде (минорная терция)",
            7 to "четвертитон — как в Расте (нейтральная терция)",
            8 to "натуральная — как в Аджаме (большая терция)",
        )
        return EarQuestion(
            EarExercise.QuarterTone, level, "Какая нота посередине?",
            names, candidates.indexOf(answer), around(answer),
            explanation = "${names[candidates.indexOf(answer)]}: ${explain.getValue(answer)}",
            compare = candidates.map { around(it) },
        )
    }

    // --- Where the phrase rests ---

    private fun rest(level: EarLevel, random: Random): EarQuestion {
        val maqam = when (level) {
            EarLevel.Easy -> listOf("Раст", "Баяти", "Нахаванд", "Аджам").map { n -> Maqamat.all.first { it.name == n } }.random(random)
            else -> Maqamat.all.random(random)
        }
        val g = maqam.ghammaz
        val degrees = when (level) {
            EarLevel.Easy -> listOf(0, g)
            EarLevel.Medium -> listOf(0, 2, g, 7).distinct()
            EarLevel.Hard -> (0..7).toList()
        }
        val notes = maqam.notes(maqam.tonic)
        fun degreeOf(step: Int): Int? {
            val pc = Math.floorMod(step - maqam.tonic, 24)
            val d = notes.indexOfFirst { it.offset == pc }
            return if (d < 0) null else d
        }
        // Phrases of the maqam itself (no modulations) by the degree they end on; the octave apart.
        val pool = (0 until 6).flatMap { TaqsimGenerator(random.nextLong()).generate(maqam, TaqsimLength.Medium).phrases }
            .filter { it.section != SectionKind.Modulation && it.station.maqam == maqam }
        fun endDegree(p: TaqsimPhrase): Int? {
            val step = p.notes.last().step
            val d = degreeOf(step) ?: return null
            return if (d == 0 && step >= maqam.tonic + 24) 7 else d
        }
        val byDegree = pool.groupBy(::endDegree).filterKeys { it != null && it in degrees }
        val available = degrees.filter { byDegree[it].orEmpty().isNotEmpty() }
        val answer = available.ifEmpty { listOf(0) }.random(random)
        val phrase = byDegree[answer]?.random(random) ?: pool.first { endDegree(it) == 0 }
        val scale = maqamScale(maqam)
        val sound = scale + phrases(listOf(phrase), from = end(scale) + 900)
        fun name(d: Int) = notes[d % 7].spelling.label(NoteLabels.Solfege) + when (d) {
            0 -> " (тоника)"
            7 -> " (октава)"
            g -> " (гаммаз)"
            else -> " (${d + 1}-я)"
        }
        val options = degrees.filter { it in available || it == answer }.takeIf { it.size >= 2 } ?: degrees
        return EarQuestion(
            EarExercise.Rest, level, "${maqam.name}: на какой ступени остановилась фраза?",
            options.map(::name), options.indexOf(answer), sound,
            explanation = when (answer) {
                0 -> "Тоника — фраза пришла домой: так звучит ответ и кафла."
                7 -> "Октава — вершина, так часто звучит кульминация."
                g -> "Гаммаз — вторая опора макама: остановка «вопроса»."
                else -> "Промежуточная опора: фраза зависла, ждёт продолжения."
            },
        )
    }
}
