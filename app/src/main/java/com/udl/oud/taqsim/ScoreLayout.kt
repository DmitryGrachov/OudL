package com.udl.oud.taqsim

import com.udl.oud.fretboard.StaffNote
import kotlin.math.sqrt

/** How long a note looks on the staff: free rhythm written approximately. */
enum class NoteValue { Whole, Half, Quarter, Eighth }

/** One note placed on a line of the score; [x] is its head's centre, in half staff spaces. */
data class ScoreItem(
    /** Index into [Taqsim.notes]. */
    val note: Int,
    val phrase: Int,
    val x: Float,
    val staff: StaffNote,
    val value: NoteValue,
    val theme: Boolean,
    /** Ornament sign written above it, if any. */
    val sign: String?,
    /** A breath mark after it. */
    val breath: Boolean,
    /** First note of its phrase: the phrase's number goes above it. */
    val phraseStart: Boolean,
)

/** A line of the score: its notes, the bar lines between phrases, and the staff positions it spans. */
data class ScoreLine(val items: List<ScoreItem>, val bars: List<Float>, val low: Int, val high: Int)

/**
 * Lays the whole taqsim out on lines of treble staff, as oud music writes it (an octave above
 * the sound). A phrase stays on one line when it fits; a bar line closes each phrase. The rhythm
 * is free, so note values only sketch it: the qafla's last note whole, resting notes half,
 * ordinary ones quarters, quick ones eighths; spacing grows a little with length.
 */
object ScoreLayout {
    /** Room for the clef at the start of every line. */
    const val CLEF = 8f
    private const val GAP = 3f
    private const val ACCIDENTAL = 2.6f
    private const val BREATH = 2f

    fun value(beats: Double, last: Boolean): NoteValue = when {
        last -> NoteValue.Whole
        beats >= 2.5 -> NoteValue.Half
        beats >= 1.2 -> NoteValue.Quarter
        else -> NoteValue.Eighth
    }

    private fun width(n: TaqsimNote, staff: StaffNote): Float =
        4.5f + 2.5f * sqrt(minOf(n.beats, 4.0)).toFloat() +
            (if (staff.accidental != com.udl.oud.fretboard.Accidental.None) ACCIDENTAL else 0f) +
            (if (n.pauseAfter > 0) BREATH else 0f)

    /** The score for [width] half staff spaces of line. */
    fun layout(taqsim: Taqsim, ornaments: Map<Int, Ornament>, width: Float): List<ScoreLine> {
        val lines = mutableListOf<ScoreLine>()
        var items = mutableListOf<ScoreItem>()
        var bars = mutableListOf<Float>()
        var x = CLEF
        fun close() {
            if (items.isEmpty()) return
            val positions = items.flatMap { it.staff.ledgerLines + it.staff.position }
            lines += ScoreLine(items, bars, minOf(positions.min(), 0) - 3, maxOf(positions.max(), 8) + 6)
            items = mutableListOf()
            bars = mutableListOf()
            x = CLEF
        }
        var index = 0
        val lastNote = taqsim.notes.lastIndex
        taqsim.phrases.forEachIndexed { pi, phrase ->
            val staffs = phrase.notes.map { it.staff }
            val widths = phrase.notes.indices.map { width(phrase.notes[it], staffs[it]) }
            val total = widths.sum() + GAP
            // Start the phrase on a new line if it would fit there but not in what is left here.
            if (x + total > width && CLEF + total <= width) close()
            phrase.notes.forEachIndexed { i, n ->
                if (x + widths[i] > width) close()
                val accidental = staffs[i].accidental != com.udl.oud.fretboard.Accidental.None
                items += ScoreItem(
                    note = index,
                    phrase = pi,
                    x = x + (if (accidental) ACCIDENTAL else 0f) + 2f,
                    staff = staffs[i],
                    value = value(n.beats, index == lastNote),
                    theme = i in phrase.themeNotes,
                    sign = ornaments[index]?.kind?.sign,
                    breath = n.pauseAfter > 0,
                    phraseStart = i == 0,
                )
                x += widths[i]
                index++
            }
            bars += x + GAP / 2 - 1f
            x += GAP
        }
        close()
        return lines
    }
}
