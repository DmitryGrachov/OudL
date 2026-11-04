package com.udl.oud.taqsim

import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.fretboard.Pitch
import com.udl.oud.tuner.centsBetween
import kotlin.math.abs

/** A note the player played: its pitch on the quarter-tone grid, how far off, when, how long, and its frequency. */
data class HeardNote(val step: Int, val cents: Double, val startMs: Long = 0, val durationMs: Long = 0, val hz: Double = 0.0)

/**
 * Turns the mic's stream into separate notes: a new pluck, a slide of more than [newNoteCents]
 * or silence ends a note; a note counts once it has rung for [minReadings] steady readings.
 */
class NoteCollector(
    private val a4: Double,
    private val minReadings: Int = 3,
    private val newNoteCents: Double = 70.0,
) {
    private val current = mutableListOf<Double>()
    private var startedAt = 0L
    private var lastAt = 0L
    private val done = mutableListOf<HeardNote>()

    /** When a note last sounded, to tell the answer is over after a pause. */
    var lastSoundAt: Long = 0
        private set

    val notes: List<HeardNote> get() = done.toList()

    fun pluck() = close()

    fun steady(frequency: Double, now: Long) {
        lastSoundAt = now
        if (current.size >= minReadings && abs(centsBetween(frequency, median())) > newNoteCents) close()
        if (current.isEmpty()) startedAt = now
        lastAt = now
        current += frequency
    }

    fun silence() = close()

    /** Takes one update from the microphone: a pluck or silence ends a note, a steady pitch adds to it. */
    fun feed(update: com.udl.oud.audio.PitchTracker.Update, now: Long) {
        if (update.pluck) pluck()
        update.steady?.let { steady(it, now) }
        if (update.lost) silence()
    }

    /** The answer is over: a couple of real notes, then a pause of [pauseMs] (a stray sound in a quiet room is not an answer). */
    fun ended(now: Long, pauseMs: Long = END_PAUSE_MS): Boolean = done.size >= 2 && now - lastSoundAt > pauseMs

    /** Closes the note still sounding and returns everything played. */
    fun finish(): List<HeardNote> {
        close()
        return notes
    }

    fun reset() {
        current.clear()
        done.clear()
        lastSoundAt = 0
    }

    private fun median() = current.sorted()[current.size / 2]

    private companion object {
        /** About how long one reading covers (the mic's hop). */
        const val READING_MS = 23L

        /** The pause that ends an answer. */
        const val END_PAUSE_MS = 2200L
    }

    private fun close() {
        if (current.size >= minReadings) {
            val f = median()
            val p = Pitch.nearest(f, a4, quarterTones = true)
            done += HeardNote(p.step, centsBetween(f, p.frequency(a4)), startedAt, lastAt - startedAt + READING_MS, f)
        }
        current.clear()
    }
}

/** One thing the answer was checked for. */
data class AnswerPoint(val passed: Boolean, val text: String)

/** How the answer went: the notes, which were outside the maqam, and the four checks. */
data class AnswerVerdict(val notes: List<HeardNote>, val outside: Set<Int>, val points: List<AnswerPoint>) {
    val passed: Int get() = points.count { it.passed }
}

/**
 * Checks an improvised answer against what was asked: stay in the station's scale, let the
 * jins' colour note be heard, move mostly by step, and come to rest on the [target] degree.
 * Notes are compared by pitch class, so any octave counts.
 */
object AnswerCheck {
    const val MIN_NOTES = 3

    fun evaluate(notes: List<HeardNote>, station: Station, target: Int): AnswerVerdict {
        val grammar = JinsGrammars.of(station.jins)
        val classes = (0 until 7).associateBy { Math.floorMod(station.step(it), 24) }
        fun name(degree: Int) = station.spelling(degree).label(NoteLabels.Solfege)
        fun pc(step: Int) = Math.floorMod(step, 24)

        val outside = notes.indices.filter { pc(notes[it].step) !in classes }.toSet()
        val points = mutableListOf<AnswerPoint>()

        if (notes.size < MIN_NOTES) {
            points += AnswerPoint(false, "Слишком коротко: сыграй хотя бы $MIN_NOTES ноты")
            return AnswerVerdict(notes, outside, points)
        }

        // In the maqam; a note a quarter tone off a degree gets a hint which way to go.
        points += if (outside.isEmpty()) {
            AnswerPoint(true, "Все ноты из макама")
        } else {
            val hints = outside.map { i ->
                val s = notes[i].step
                val near = listOf(s + 1, s - 1).firstOrNull { pc(it) in classes }
                val played = Pitch(s).label(NoteLabels.Solfege)
                if (near != null) {
                    "$played вместо ${name(classes.getValue(pc(near)))} — ${if (near > s) "чуть выше" else "чуть ниже"}"
                } else {
                    "$played не из макама"
                }
            }.distinct()
            AnswerPoint(false, hints.joinToString("; "))
        }

        val colour = grammar.characteristic.map { it + station.jinsRoot }
        val colourClasses = colour.map { pc(station.step(it)) }.toSet()
        points += if (notes.any { pc(it.step) in colourClasses }) {
            AnswerPoint(true, "Цвет джинса слышен: ${colour.joinToString { name(it) }}")
        } else {
            AnswerPoint(false, "Не прозвучала характерная ступень ${colour.joinToString { name(it) }} — без неё не слышно ${station.jins.name}")
        }

        val moves = notes.zipWithNext { a, b ->
            val da = classes[pc(a.step)]
            val db = classes[pc(b.step)]
            if (da == null || db == null) null else {
                // Degrees in the octave, the shorter way round.
                val d = Math.floorMod(db - da, 7)
                minOf(d, 7 - d)
            }
        }.filterNotNull()
        val steps = moves.count { it <= 1 }
        points += if (moves.isEmpty() || steps >= moves.size * 0.6) {
            AnswerPoint(true, "Движение в основном шагами")
        } else {
            AnswerPoint(false, "Много скачков: в таксиме чаще идут по соседним ступеням")
        }

        val last = notes.last()
        val lastDegree = classes[pc(last.step)]
        val targetName = name(target)
        points += when {
            lastDegree != null && Math.floorMod(lastDegree - target, 7) == 0 -> AnswerPoint(true, "Пришёл на $targetName")
            lastDegree != null && Math.floorMod(lastDegree - station.jinsRoot, 7) in grammar.rests ->
                AnswerPoint(false, "Остановился на опоре ${name(lastDegree)}, а нужно было прийти на $targetName")
            else -> AnswerPoint(false, "Закончил на ${Pitch(last.step).label(NoteLabels.Solfege)} — ответ должен прийти на $targetName")
        }
        return AnswerVerdict(notes, outside, points)
    }
}
