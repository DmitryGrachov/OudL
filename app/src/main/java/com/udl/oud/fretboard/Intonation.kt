package com.udl.oud.fretboard

/**
 * Where the quarter tones really sit. The 24-tone grid (Cairo, 1932) is a notation: players put
 * the neutral notes a little lower or higher by region. Measured practice puts Sikah (E½♭ over C)
 * at about 342 cents in Egypt and 356 in Syria and Lebanon, against 350 in the theory; the
 * school shifts every quarter-tone note by that much. [Custom] is the player's own (or a
 * teacher's) height.
 */
enum class IntonationSchool(val title: String, val cents: Int?, val about: String) {
    Theory("Теория (Каир, 1932)", 0, "Ровно посередине: четвертитон — половина полутона. Так пишут ноты; так звучит любой электронный инструмент."),
    Egypt("Египет", -8, "Каирская школа: нейтральные ноты чуть ниже — Сикях около 342 ц над До. Ближе к бемолю, мягче."),
    Levant("Сирия и Ливан", 6, "Дамаск и Алеппо: нейтральные ноты чуть выше — Сикях около 356 ц над До. Ближе к бекару, светлее."),
    Custom("Своя", null, "Своя высота — как у твоего учителя или любимого исполнителя."),
}

/** The intonation in use, read wherever a pitch becomes a frequency. Set from [com.udl.oud.tuner.OudSettings]. */
object Intonation {
    @Volatile var school: IntonationSchool = IntonationSchool.Theory
    @Volatile var custom: Int = 0

    /**
     * Functional intonation: the same note sits differently by its role. In Bayati the half-flat
     * second is lower than the same note as Rast's third (Egyptian practice, after S. Marcus:
     * about 135–145 cents over the root instead of 150).
     */
    @Volatile var functional: Boolean = false

    /** How far every quarter-tone note moves from the grid, in cents. */
    val offset: Int get() = school.cents ?: custom

    /** The shift of a grid step: quarter tones only, the semitones stay. */
    fun cents(step: Int): Double = if (step % 2 != 0) offset.toDouble() else 0.0

    /** The extra lowering of Bayati's second, when functional intonation is on. */
    val bayatiSecond: Double get() = if (functional) BAYATI_SECOND else 0.0

    const val BAYATI_SECOND = -10.0
    const val CUSTOM_RANGE = 30
}
