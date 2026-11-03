package com.udl.oud.fretboard

/**
 * A European scale: plain semitone-based steps (2 = semitone, 4 = tone, in quarter tones like
 * maqamat), optionally with a different way down, as the melodic minor has.
 */
data class WesternScale(
    override val name: String,
    override val tonic: Int,
    val intervals: List<Int>,
    /** One line on what makes this scale what it is. */
    val summary: String,
    val descendingIntervals: List<Int>? = null,
    /** Letter of each degree counted from the tonic's letter; pentatonics skip letters. */
    val letters: List<Int> = (0..6).toList(),
) : Scale {
    init {
        listOfNotNull(intervals, descendingIntervals).forEach {
            require(it.size == letters.size && it.sum() == 24) { "$name: steps must fill one octave" }
        }
    }

    override val hasDescent: Boolean get() = descendingIntervals != null

    override fun intervals(descending: Boolean): List<Int> =
        if (descending) descendingIntervals ?: intervals else intervals

    override fun notes(tonic: Int, descending: Boolean): List<ScaleNote> =
        spellScale(tonic, intervals(descending), letters)
}

object WesternScales {
    private val PENTATONIC_MAJOR = listOf(0, 1, 2, 4, 5) // 1 2 3 5 6
    private val PENTATONIC_MINOR = listOf(0, 2, 3, 4, 6) // 1 ♭3 4 5 ♭7

    val all = listOf(
        // Tonics are picked so the scale sits comfortably on an oud in C–C tuning.
        WesternScale("Мажор", 96, listOf(4, 4, 2, 4, 4, 4, 2), "Ионийский лад: полутоны между III–IV и VII–I"), // C3
        WesternScale("Минор", 90, listOf(4, 2, 4, 4, 2, 4, 4), "Натуральный (эолийский): полутоны между II–III и V–VI"), // A2
        WesternScale(
            "Гармонический минор", 90, listOf(4, 2, 4, 4, 2, 6, 2), // A2
            "Повышенная VII ступень: между VI и VII полтора тона",
        ),
        WesternScale(
            "Мелодический минор", 90, listOf(4, 2, 4, 4, 4, 4, 2), // A2
            "Вверх с повышенными VI и VII, вниз — как натуральный минор",
            descendingIntervals = listOf(4, 2, 4, 4, 2, 4, 4),
        ),
        WesternScale("Дорийский", 100, listOf(4, 2, 4, 4, 4, 2, 4), "Минор с большой VI ступенью"), // D3
        WesternScale("Фригийский", 104, listOf(2, 4, 4, 4, 2, 4, 4), "Минор с малой II ступенью"), // E3
        WesternScale("Лидийский", 106, listOf(4, 4, 4, 2, 4, 4, 2), "Мажор с увеличенной IV ступенью"), // F3
        WesternScale("Миксолидийский", 110, listOf(4, 4, 2, 4, 4, 2, 4), "Мажор с малой VII ступенью"), // G3
        WesternScale("Локрийский", 94, listOf(2, 4, 4, 2, 4, 4, 4), "Малая II и уменьшённая V ступени"), // B2
        WesternScale(
            "Пентатоника мажор", 96, listOf(4, 4, 6, 4, 6), // C3
            "Мажор без IV и VII ступеней — без полутонов",
            letters = PENTATONIC_MAJOR,
        ),
        WesternScale(
            "Пентатоника минор", 90, listOf(6, 4, 4, 6, 4), // A2
            "Минор без II и VI ступеней — без полутонов",
            letters = PENTATONIC_MINOR,
        ),
    )
}
