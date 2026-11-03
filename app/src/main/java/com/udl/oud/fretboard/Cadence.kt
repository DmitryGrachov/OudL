package com.udl.oud.fretboard

/**
 * One note of a phrase: a [degree] of the maqam (0 = tonic, 7 = octave above, −1 = the seventh
 * below the tonic) held for [beats].
 */
data class PhraseNote(val degree: Int, val beats: Double)

/**
 * A teaching cadence (qafla): a short typical turn that closes on the tonic through the maqam's
 * characteristic degrees. Built from the lower jins and the tonic's neighbours; these are
 * study phrases, not quotations from the repertoire.
 */
data class Cadence(val name: String, val notes: List<PhraseNote>) {
    init {
        require(notes.isNotEmpty() && notes.all { it.degree in -3..7 && it.beats > 0 }) { "$name: bad notes" }
        require(notes.last().degree % 7 == 0) { "$name: a cadence ends on the tonic" }
    }

    /** Grid pitches of the phrase for [maqam] on [tonic], in its ascending form. */
    fun steps(maqam: Maqam, tonic: Int): List<Int> {
        val offsets = maqam.notes(tonic).map { it.offset }
        return notes.map { n ->
            tonic + when {
                n.degree == 7 -> 24
                n.degree < 0 -> offsets[n.degree + 7] - 24
                else -> offsets[n.degree]
            }
        }
    }
}

/** Builds a phrase from degree/beats pairs: phrase(4 to .5, 3 to .5, 0 to 2.0). */
private fun phrase(name: String, vararg notes: Pair<Int, Double>) =
    Cadence(name, notes.map { (degree, beats) -> PhraseNote(degree, beats) })

object Cadences {
    private val byName = mapOf(
        "Раст" to listOf(
            phrase("Спуск по Расту", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0), // G F E½♭ D C
            phrase("Через нижний Си½♭", 1 to .5, 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Баяти" to listOf(
            phrase("Спуск от гаммаза", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0), // G F E½♭ D
            phrase("Снизу через До", -1 to .5, 0 to .5, 1 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Хиджаз" to listOf(
            phrase("Спуск через полтора тона", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0), // G F♯ E♭ D
            phrase("Вокруг Фа♯", 1 to .5, 2 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Саба" to listOf(
            phrase("Через Соль♭", 3 to .5, 2 to 1.0, 1 to .5, 0 to 2.0), // G♭ F E½♭ D
            phrase("Вокруг Фа", 1 to .5, 2 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Нахаванд" to listOf(
            phrase("Минорный спуск", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0), // G F E♭ D C
            phrase("С вводным Си", 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Курд" to listOf(
            phrase("Спуск от гаммаза", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0), // G F E♭ D
            phrase("Полутон над тоникой", 2 to .5, 1 to .5, 0 to .5, 1 to .5, 0 to 2.0),
        ),
        "Аджам" to listOf(
            phrase("Мажорный спуск", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0), // F E♭ D C B♭
            phrase("С вводным Ля", 1 to .5, -1 to .5, 0 to 2.0),
        ),
        "Сикях" to listOf(
            phrase("Сверху на тонику", 2 to .5, 1 to 1.0, 0 to 2.0), // G F E½♭
            phrase("Снизу через Ре", -1 to .5, 0 to .5, 1 to .5, 0 to 2.0),
        ),
    )

    fun of(maqam: Maqam): List<Cadence> = byName[maqam.name].orEmpty()

    internal val all: Map<String, List<Cadence>> get() = byName
}
