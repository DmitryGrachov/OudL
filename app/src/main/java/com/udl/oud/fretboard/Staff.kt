package com.udl.oud.fretboard

/** Sign before a note head: Western ♭ ♯, Arabic half-flat and half-sharp. */
enum class Accidental { None, Flat, Sharp, HalfFlat, HalfSharp }

/**
 * A note as written on a treble staff: [letter] (0 = C … 6 = B) in [octave], with its
 * [accidental]. Turkish notation writes koma-counted accidentals: [komas] is the number shown
 * with the ♭ or ♯ (1, 4, 5, 8…), null elsewhere.
 */
data class StaffNote(val letter: Int, val octave: Int, val accidental: Accidental, val komas: Int? = null) {
    init {
        require(letter in 0..6)
    }

    /** Diatonic steps above the bottom line (E4): 0 is that line, 1 the space above, 8 the top line. */
    val position: Int get() = octave * 7 + letter - BOTTOM_LINE

    /** Ledger lines the note needs, as staff positions (even, below −1 or above 9). */
    val ledgerLines: List<Int>
        get() = when {
            position <= -2 -> (-2 downTo position).filter { it % 2 == 0 }
            position >= 10 -> (10..position).filter { it % 2 == 0 }
            else -> emptyList()
        }

    companion object {
        /** E4 = 4·7 + 2. */
        private const val BOTTOM_LINE = 30

        /**
         * A grid pitch as oud music writes it: treble clef an octave above the sound, like the
         * guitar. [spelling] picks the letter (a maqam degree keeps its own; others the usual one).
         */
        fun arabic(step: Int, spelling: Spelling = Spelling.of(step)): StaffNote {
            // The natural the spelling alters, in quarter-tone steps, gives letter and octave.
            val natural = step - spelling.alter
            val octave = Math.floorDiv(natural, 24) - 1 + WRITTEN_OCTAVE_UP
            val accidental = when (spelling.alter) {
                -2 -> Accidental.Flat
                -1 -> Accidental.HalfFlat
                1 -> Accidental.HalfSharp
                2 -> Accidental.Sharp
                else -> Accidental.None
            }
            return StaffNote(spelling.letter, octave, accidental)
        }

        /** Oud music is written an octave above how it sounds. */
        const val WRITTEN_OCTAVE_UP = 1
    }
}
