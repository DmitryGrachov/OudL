package com.udl.oud.turkish

import com.udl.oud.fretboard.Accidental
import com.udl.oud.fretboard.PhraseNote
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.tuner.Ahenk

/** A written pitch with its spelling: a makam degree in some octave, its descending form, or a yeden. */
data class TurkishTone(val written: Int, val spelling: TurkishSpelling) {
    fun octave(n: Int) = copy(written = written + n * Koma.PER_OCTAVE)

    fun frequency(a4: Double, ahenk: Ahenk) = Koma.frequency(written, spelling, a4, ahenk)

    /** Same pitch class, whatever the octave. */
    fun sameClass(other: TurkishTone) = Math.floorMod(written - other.written, Koma.PER_OCTAVE) == 0

    /** As Turkish notation writes it: the written pitch, with ♭ or ♯ and the number of komas. */
    fun staffNote(): StaffNote {
        val octave = Math.floorDiv(written - spelling.alter, Koma.PER_OCTAVE)
        val accidental = when {
            spelling.alter < 0 -> Accidental.Flat
            spelling.alter > 0 -> Accidental.Sharp
            else -> Accidental.None
        }
        return StaffNote(spelling.letter, octave, accidental, komas = kotlin.math.abs(spelling.alter).takeIf { it != 0 })
    }
}

/** Seyir direction: where development starts and which way it goes. */
enum class SeyirDirection(val title: String, val turkish: String) {
    Up("восходящий: от дурака вверх", "çıkıcı"),
    Down("нисходящий: сверху, от тиз дурака", "inici"),
    DownUp("начинается у гючлю, спускается и поднимается", "inici-çıkıcı"),
}

/** A usual geçki (passing modulation): [makam] on the perde [offset] komas from the durak. */
data class Gecki(val makam: String, val offset: Int)

/**
 * Seyir of a makam: its [direction], the güçlü (dominant) as a degree (7 = the tîz durak, an
 * octave up), the yeden (leading tone) [yeden] komas under the durak, the usual course in short
 * steps, the usual geçkis, and [inici]: degrees that are lowered on the way down, by komas (Rast
 * ascends through Eviç and descends through Acem).
 */
data class TurkishSeyir(
    val direction: SeyirDirection,
    val guclu: Int,
    val yeden: Int,
    val path: List<String>,
    val gecki: List<Gecki> = emptyList(),
    val inici: Map<Int, Int> = emptyMap(),
) {
    init {
        require(guclu in 1..7) { "güçlü is a degree above the durak" }
        require(yeden in 1..9) { "yeden lies a step under the durak" }
        require(inici.keys.all { it in 1..6 }) { "only inner degrees change on the way down" }
    }
}

/** A teaching karar (cadence) phrase; degree −1 is the yeden, 7 the octave, −2 and −3 the degrees under it. */
data class TurkishKarar(val name: String, val notes: List<PhraseNote>) {
    init {
        require(notes.isNotEmpty() && notes.all { it.degree in -3..7 && it.beats > 0 }) { "$name: bad notes" }
        require(notes.last().degree % 7 == 0) { "$name: a karar ends on the durak" }
    }
}

/**
 * Seyirs and kararlar of the makams in the usual AEU account. Schools and books differ in detail;
 * this is a shared minimum — durak, güçlü, yeden, the usual course and moves — not a full account.
 * The kararlar are study phrases built on the makam's own degrees, not quotations.
 */
object TurkishSeyirs {
    private val byName: Map<String, TurkishSeyir> = mapOf(
        "Rast" to TurkishSeyir(
            SeyirDirection.Up, guclu = 4, yeden = 5,
            path = listOf(
                "начало у дурака Rast: Rast beşlisi, Dügâh и Segâh",
                "подъём к гючлю Nevâ, полукаданс на Nevâ",
                "наверху Rast на Nevâ: Hüseynî, Eviç, Gerdâniye",
                "на спуске Eviç меняется на Acem",
                "карар: Nevâ → Çârgâh → Segâh → Dügâh → Rast, снизу вводный Irak",
            ),
            gecki = listOf(Gecki("Uşşak", 9), Gecki("Segâh", 17), Gecki("Bûselik", 31), Gecki("Hicaz", 31)),
            inici = mapOf(6 to -4),
        ),
        "Mâhur" to TurkishSeyir(
            SeyirDirection.Down, guclu = 4, yeden = 4,
            path = listOf(
                "начало сверху, у тиз дурака Gerdâniye",
                "Çârgâh dörtlüsü на Gerdâniye, Mâhur — вводный к ней",
                "спуск к гючлю Nevâ, Çârgâh beşlisi на Rast",
                "карар на Rast через Bûselik, снизу вводный Geveşt",
            ),
            gecki = listOf(Gecki("Rast", 0), Gecki("Uşşak", 9)),
        ),
        "Nihâvend" to TurkishSeyir(
            SeyirDirection.DownUp, guclu = 4, yeden = 5,
            path = listOf(
                "начало около гючлю Nevâ или сразу у дурака",
                "Bûselik beşlisi на Rast: Dügâh, Kürdî, Çârgâh",
                "наверху Hicaz на Nevâ: Hisar, Eviç",
                "на спуске Eviç меняется на Acem (Kürdî на Nevâ)",
                "карар на Rast через Kürdî и Dügâh, снизу вводный Irak",
            ),
            gecki = listOf(Gecki("Kürdî", 9), Gecki("Hicaz", 31)),
            inici = mapOf(6 to -4),
        ),
        "Uşşak" to TurkishSeyir(
            SeyirDirection.Up, guclu = 3, yeden = 9,
            path = listOf(
                "начало у Dügâh: Uşşak dörtlüsü, низкий Segâh",
                "подъём к гючлю Nevâ",
                "наверху Bûselik на Nevâ: Hüseynî, Acem, Gerdâniye",
                "карар: Nevâ → Çârgâh → Segâh → Dügâh, часто через вводный Rast",
            ),
            gecki = listOf(Gecki("Rast", -9), Gecki("Hicaz", 0), Gecki("Saba", 0)),
        ),
        "Hüseynî" to TurkishSeyir(
            SeyirDirection.DownUp, guclu = 4, yeden = 9,
            path = listOf(
                "начало около гючлю Hüseynî",
                "Hüseynî beşlisi на Dügâh, наверху Uşşak на Hüseynî",
                "выход к Muhayyer (тиз дурак) через Eviç",
                "на спуске Eviç меняется на Acem",
                "карар на Dügâh, как в Uşşak",
            ),
            gecki = listOf(Gecki("Uşşak", 0), Gecki("Rast", -9), Gecki("Hicaz", 0)),
            inici = mapOf(5 to -4),
        ),
        "Bûselik" to TurkishSeyir(
            SeyirDirection.Up, guclu = 4, yeden = 5,
            path = listOf(
                "начало у Dügâh: Bûselik beşlisi, чистый Bûselik (Си)",
                "подъём к гючлю Hüseynî",
                "наверху Kürdî на Hüseynî, бывает Hicaz на Hüseynî (Şehnaz)",
                "карар на Dügâh, вводный Nim zirgüle (Соль♯⁴)",
            ),
            gecki = listOf(Gecki("Nihâvend", -9), Gecki("Hicaz", 31)),
        ),
        "Kürdî" to TurkishSeyir(
            SeyirDirection.Up, guclu = 3, yeden = 9,
            path = listOf(
                "начало у Dügâh: Kürdî — бакийе над дураком",
                "подъём к гючлю Nevâ",
                "наверху Bûselik на Nevâ",
                "карар на Dügâh через Kürdî, вводный Rast",
            ),
            gecki = listOf(Gecki("Bûselik", 0), Gecki("Hicaz", 0), Gecki("Rast", -9)),
        ),
        "Hicaz" to TurkishSeyir(
            SeyirDirection.DownUp, guclu = 3, yeden = 9,
            path = listOf(
                "начало у гючлю Nevâ или около дурака",
                "Hicaz dörtlüsü на Dügâh: Dik kürdî — Nim hicaz, артык",
                "наверху Rast на Nevâ (Eviç)",
                "на спуске Eviç меняется на Acem: Uşşak или Bûselik на Nevâ",
                "карар: Nevâ → Nim hicaz → Dik kürdî → Dügâh",
            ),
            gecki = listOf(Gecki("Uşşak", 0), Gecki("Rast", 22)),
            inici = mapOf(5 to -4),
        ),
        "Zirgüleli Hicaz" to TurkishSeyir(
            SeyirDirection.DownUp, guclu = 4, yeden = 5,
            path = listOf(
                "Hicaz beşlisi на Dügâh: Dik kürdî — Nim hicaz",
                "гючлю Hüseynî, наверху Hicaz на Hüseynî: Dik acem, Nim şehnaz",
                "карар на Dügâh, вводный Nim zirgüle — от него и имя",
            ),
            gecki = listOf(Gecki("Hicaz", 0), Gecki("Uşşak", 0)),
        ),
        "Saba" to TurkishSeyir(
            SeyirDirection.Up, guclu = 2, yeden = 9,
            path = listOf(
                "начало у Dügâh, сразу к Segâh и Çârgâh",
                "гючлю Çârgâh, над ним тесный Hicaz перде",
                "наверху Hicaz на Çârgâh, до Acem",
                "карар: Çârgâh → Segâh → Dügâh, выше октавы редко",
            ),
            gecki = listOf(Gecki("Uşşak", 0), Gecki("Hicaz", 13)),
        ),
        "Segâh" to TurkishSeyir(
            SeyirDirection.Up, guclu = 2, yeden = 8,
            path = listOf(
                "начало у Segâh: Segâh üçlüsü Segâh — Çârgâh — Nevâ",
                "гючлю Nevâ, наверху Rast на Nevâ",
                "карар на Segâh через Çârgâh, вводный Dügâh",
            ),
            gecki = listOf(Gecki("Rast", -17), Gecki("Uşşak", -8), Gecki("Hüzzam", 0)),
        ),
        "Hüzzam" to TurkishSeyir(
            SeyirDirection.Up, guclu = 2, yeden = 8,
            path = listOf(
                "начало у Segâh: Segâh üçlüsü",
                "гючлю Nevâ, над ним Hicaz на Nevâ: Hisar, Eviç",
                "карар на Segâh, вводный Dügâh",
            ),
            gecki = listOf(Gecki("Segâh", 0), Gecki("Hicaz", 14)),
        ),
        "Çârgâh" to TurkishSeyir(
            SeyirDirection.Up, guclu = 4, yeden = 4,
            path = listOf(
                "начало у Çârgâh: Çârgâh beşlisi, мажорный склад",
                "гючлю Gerdâniye, Çârgâh dörtlüsü на ней",
                "карар на Çârgâh, вводный Bûselik",
            ),
            gecki = listOf(Gecki("Mâhur", -22)),
        ),
        "Hicazkâr" to TurkishSeyir(
            SeyirDirection.Down, guclu = 4, yeden = 5,
            path = listOf(
                "начало сверху, около Gerdâniye",
                "Hicaz на Nevâ: Hisar, Eviç",
                "спуск к гючлю Nevâ, Hicaz beşlisi на Rast: Zirgüle — Segâh",
                "карар на Rast, вводный Irak",
            ),
            gecki = listOf(Gecki("Rast", 0), Gecki("Hicaz", 9)),
        ),
        "Şedaraban" to TurkishSeyir(
            SeyirDirection.DownUp, guclu = 4, yeden = 5,
            path = listOf(
                "строй Zirgüleli Hicaz октавой ниже, на Yegâh",
                "опора на Dügâh — верх Hicaz beşlisi",
                "карар на Yegâh, вводный Kaba nim hicaz",
            ),
            gecki = listOf(Gecki("Hicaz", 31)),
        ),
        "Sûzidil" to TurkishSeyir(
            SeyirDirection.Down, guclu = 4, yeden = 5,
            path = listOf(
                "начало у тиз дурака Hüseynî",
                "спуск по строю Zirgüleli Hicaz на Hüseynî aşîran",
                "карар на Hüseynî aşîran",
            ),
            gecki = listOf(Gecki("Hicaz", 22)),
        ),
        "Kürdîlihicazkâr" to TurkishSeyir(
            SeyirDirection.Down, guclu = 7, yeden = 9,
            path = listOf(
                "начало у тиз дурака Gerdâniye — она же гючлю",
                "спуск: Kürdî на Gerdâniye, Bûselik на Nevâ",
                "вторая опора Çârgâh",
                "карар: Kürdî dörtlüsü на Rast через Nim zirgüle, вводный Acem aşîran",
            ),
            gecki = listOf(Gecki("Nihâvend", 0), Gecki("Hicaz", 31)),
        ),
        "Acemaşîran" to TurkishSeyir(
            SeyirDirection.Down, guclu = 7, yeden = 4,
            path = listOf(
                "начало у тиз дурака Acem — главный гючлю",
                "вторая опора Çârgâh, третья Dügâh",
                "спуск Çârgâh beşlisi на Acem aşîran",
                "карар на Acem aşîran, вводный Hüseynî aşîran",
            ),
            gecki = listOf(Gecki("Çârgâh", 31)),
        ),
    )

    private val kararlar: Map<String, List<TurkishKarar>> = mapOf(
        "Rast" to listOf(
            karar("Спуск от Nevâ", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("Через вводный Irak", 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Mâhur" to listOf(
            karar("Сверху от Gerdâniye", 7 to .5, 6 to .5, 5 to .5, 4 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным Geveşt", 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Nihâvend" to listOf(
            karar("Спуск через Acem", 7 to .5, 6 to .5, 5 to .5, 4 to 1.0, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным Irak", 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Uşşak" to listOf(
            karar("Спуск от Nevâ", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0),
            karar("С вводным Rast", 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Hüseynî" to listOf(
            karar("От Hüseynî вниз", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("Через Acem", 6 to .5, 5 to .5, 4 to .5, 3 to 1.0, 1 to .5, 0 to 2.0),
        ),
        "Bûselik" to listOf(
            karar("От Hüseynî вниз", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным Nim zirgüle", 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Kürdî" to listOf(
            karar("Спуск от Nevâ", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0),
            karar("Kürdî над дураком", 2 to .5, 1 to .5, 0 to .5, 1 to .5, 0 to 2.0),
        ),
        "Hicaz" to listOf(
            karar("Спуск от Nevâ", 3 to .5, 2 to .5, 1 to 1.0, 0 to 2.0),
            karar("Вокруг Nim hicaz", 1 to .5, 2 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Zirgüleli Hicaz" to listOf(
            karar("Спуск от гючлю", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным", 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Saba" to listOf(
            karar("От Çârgâh", 2 to 1.0, 1 to .5, 0 to 2.0),
            karar("Вокруг Hicaz перде", 2 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Segâh" to listOf(
            karar("От Nevâ", 2 to .5, 1 to .5, 0 to 2.0),
            karar("Снизу через Dügâh", -1 to .5, 0 to .5, 1 to .5, 0 to 2.0),
        ),
        "Hüzzam" to listOf(
            karar("От Nevâ", 2 to .5, 1 to .5, 0 to 2.0),
            karar("Через Hisar", 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
        ),
        "Çârgâh" to listOf(
            karar("Спуск от Gerdâniye", 4 to .5, 3 to .5, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным Bûselik", 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Kürdîlihicazkâr" to listOf(
            karar("Сверху от Gerdâniye", 7 to .5, 6 to .5, 5 to .5, 4 to .5, 3 to 1.0, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным Acem aşîran", 2 to .5, 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
        "Acemaşîran" to listOf(
            karar("Сверху от Acem", 7 to .5, 6 to .5, 5 to .5, 4 to .5, 3 to 1.0, 2 to .5, 1 to .5, 0 to 2.0),
            karar("С вводным", 1 to .5, 0 to .5, -1 to .5, 0 to 2.0),
        ),
    )

    private fun karar(name: String, vararg notes: Pair<Int, Double>) =
        TurkishKarar(name, notes.map { (degree, beats) -> PhraseNote(degree, beats) })

    /** A şed makam without an entry of its own follows the makam it is taken from. */
    private fun <T> lookup(map: Map<String, T>, makam: TurkishMakam): T? =
        map[makam.turkishName] ?: makam.base?.let { map[it] }

    fun of(makam: TurkishMakam): TurkishSeyir = lookup(byName, makam) ?: error("no seyir for ${makam.turkishName}")

    fun kararlar(makam: TurkishMakam): List<TurkishKarar> = lookup(kararlar, makam).orEmpty()

    internal val all: Map<String, TurkishSeyir> get() = byName
}

/** The makam's notes as tones: degrees in either direction, the yeden, and karar phrases. */
object TurkishScale {
    /** [degree] (0..6) of [makam] in [octave]; on the way [down] its inici form, if it has one. */
    fun tone(makam: TurkishMakam, degree: Int, octave: Int = 0, down: Boolean = false): TurkishTone {
        val d = makam.degrees()[degree]
        val change = if (down) TurkishSeyirs.of(makam).inici[degree] ?: 0 else 0
        return TurkishTone(d.written + change, d.spelling.copy(alter = d.spelling.alter + change)).octave(octave)
    }

    /** The yeden: the leading tone under the durak, written on the letter below it. */
    fun yeden(makam: TurkishMakam): TurkishTone {
        val written = makam.tonic - TurkishSeyirs.of(makam).yeden
        val letter = Math.floorMod(makam.tonicLetter - 1, 7)
        val pc = Math.floorMod(written, Koma.PER_OCTAVE)
        val alter = Math.floorMod(pc - Koma.NATURALS[letter] + 26, Koma.PER_OCTAVE) - 26
        return TurkishTone(written, TurkishSpelling(letter, alter))
    }

    /** The güçlü as a tone (7 is the tîz durak). */
    fun guclu(makam: TurkishMakam): TurkishTone {
        val g = TurkishSeyirs.of(makam).guclu
        return if (g == 7) tone(makam, 0, 1) else tone(makam, g)
    }

    /** Durak up to its octave. */
    fun ascending(makam: TurkishMakam): List<TurkishTone> = (0 until 7).map { tone(makam, it) } + tone(makam, 0, 1)

    /** Octave down to the durak, through the inici degrees. */
    fun descending(makam: TurkishMakam): List<TurkishTone> =
        listOf(tone(makam, 0, 1)) + (6 downTo 0).map { tone(makam, it, down = true) }

    /** The seven degree tones of one direction, in the durak's octave. */
    fun degreeTones(makam: TurkishMakam, down: Boolean): List<TurkishTone> = (0 until 7).map { tone(makam, it, down = down) }

    /**
     * A karar as tones: each degree in its plain form, except that a degree left downwards takes
     * its inici form, as the makam does on the way down.
     */
    fun phrase(makam: TurkishMakam, karar: TurkishKarar): List<TurkishTone> {
        fun plain(degree: Int, down: Boolean): TurkishTone = when {
            degree == -1 -> yeden(makam)
            degree == 7 -> tone(makam, 0, 1)
            degree < 0 -> tone(makam, degree + 7, -1, down)
            else -> tone(makam, degree, 0, down)
        }
        val notes = karar.notes
        return notes.mapIndexed { i, n ->
            val next = notes.getOrNull(i + 1)
            val down = next != null && plain(next.degree, false).written < plain(n.degree, false).written
            plain(n.degree, down)
        }
    }
}
