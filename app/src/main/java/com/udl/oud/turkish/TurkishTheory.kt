package com.udl.oud.turkish

import com.udl.oud.tuner.Ahenk
import kotlin.math.pow

/**
 * Turkish makam theory in the Arel–Ezgi–Uzdilek system: the octave is 53 komas (Holdrian commas),
 * a whole tone 9 komas. Pitches are written as komas from C0 in Turkish notation; on an oud in
 * Bolahenk they sound an octave and a fourth lower than written (Rast, written G4, sounds D3); other
 * ahenks sound lower still.
 */
object Koma {
    const val PER_OCTAVE = 53
    const val CENTS = 1200.0 / PER_OCTAVE

    /** Written minus sounding, in komas: an octave and a perfect fourth (22 komas). */
    const val BOLAHENK = PER_OCTAVE + 22

    /** Komas of C D E F G A B within the octave. */
    val NATURALS = listOf(0, 9, 18, 22, 31, 40, 49)

    /** 12-TET semitones of C D E F G A B, to tie the natural notes to the A4 reference. */
    private val SEMITONES = listOf(0, 2, 4, 5, 7, 9, 11)

    /**
     * Sounding frequency of a written pitch spelled [spelling] at [written] komas: its natural
     * note sits on equal temperament (so open strings and naturals agree with the tuner) and the
     * alteration moves it by whole komas. The [ahenk] moves everything down with the oud.
     */
    fun frequency(written: Int, spelling: TurkishSpelling, a4: Double, ahenk: Ahenk = Ahenk.Bolahenk): Double {
        val natural = written - spelling.alter
        val octave = Math.floorDiv(natural, PER_OCTAVE)
        val writtenMidi = 12 * (octave + 1) + SEMITONES[spelling.letter]
        val soundingMidi = writtenMidi - 17 + ahenk.semitones // Bolahenk: an octave and a fourth down
        return a4 * 2.0.pow((soundingMidi - 69) / 12.0) * 2.0.pow(spelling.alter * CENTS / 1200)
    }
}

enum class TurkishLabels(val title: String) {
    Perde("Перде"),
    Written("Запись"),
    Sounding("Как звучит"),
}

/** A note as Turkish notation writes it: a letter (0 = C … 6 = B) and an alteration in komas. */
data class TurkishSpelling(val letter: Int, val alter: Int) {
    /** "Си♭¹": the number says how many komas the sharp or flat is worth (1, 4, 5 or 8). */
    fun label(): String {
        val name = SOLFEGE[letter]
        if (alter == 0) return name
        val sign = if (alter > 0) "♯" else "♭"
        return name + sign + superscript(kotlin.math.abs(alter))
    }

    /** The note as it sounds: in Bolahenk an octave and a fourth lower, in other ahenks lower still. */
    fun sounding(written: Int, ahenk: Ahenk = Ahenk.Bolahenk): TurkishSpelling {
        val letter = Math.floorMod(this.letter + 4 + ahenk.letters, 7) // down a fourth = up a fifth, letter-wise
        val pc = Math.floorMod(written - Koma.BOLAHENK + ahenk.komas, Koma.PER_OCTAVE)
        val alter = Math.floorMod(pc - Koma.NATURALS[letter] + 26, Koma.PER_OCTAVE) - 26
        return TurkishSpelling(letter, alter)
    }

    private companion object {
        val SOLFEGE = listOf("До", "Ре", "Ми", "Фа", "Соль", "Ля", "Си")
        val DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        fun superscript(n: Int) = n.toString().map { DIGITS[it - '0'] }.joinToString("")
    }
}

/**
 * Names of the perdes (degrees of the Turkish gamut) by written pitch, from Yegâh (D4) up to
 * Tîz Nevâ (D6). Other pitches go by letter.
 */
object Perdes {
    private const val C4 = 4 * Koma.PER_OCTAVE
    private val names = mapOf(
        C4 + 9 to "Yegâh",
        C4 + 18 to "Hüseynî aşîran",
        C4 + 22 to "Acem aşîran",
        C4 + 23 to "Dik acem aşîran",
        C4 + 26 to "Irak",
        C4 + 27 to "Geveşt",
        C4 + 31 to "Rast",
        C4 + 35 to "Nim zirgüle",
        C4 + 36 to "Zirgüle",
        C4 + 40 to "Dügâh",
        C4 + 44 to "Kürdî",
        C4 + 45 to "Dik kürdî",
        C4 + 48 to "Segâh",
        C4 + 49 to "Bûselik",
        C4 + 53 to "Çârgâh",
        C4 + 57 to "Nim hicaz",
        C4 + 58 to "Hicaz",
        C4 + 62 to "Nevâ",
        C4 + 66 to "Nim hisar",
        C4 + 67 to "Hisar",
        C4 + 71 to "Hüseynî",
        C4 + 75 to "Acem",
        C4 + 76 to "Dik acem",
        C4 + 79 to "Eviç",
        C4 + 80 to "Mâhur",
        C4 + 84 to "Gerdâniye",
        C4 + 88 to "Nim şehnaz",
        C4 + 89 to "Şehnaz",
        C4 + 93 to "Muhayyer",
        C4 + 97 to "Sünbüle",
        C4 + 98 to "Dik sünbüle",
        C4 + 101 to "Tîz segâh",
        C4 + 102 to "Tîz bûselik",
        C4 + 106 to "Tîz çârgâh",
        C4 + 115 to "Tîz nevâ",
    )

    fun of(written: Int): String? = names[written]
}

/** A Turkish jins (dörtlü = tetrachord, beşli = pentachord, üçlü = trichord), steps in komas. */
data class TurkishJins(val name: String, val intervals: List<Int>)

object TurkishAjnas {
    val RastBesli = TurkishJins("Rast beşlisi", listOf(9, 8, 5, 9))
    val RastDortlu = TurkishJins("Rast dörtlüsü", listOf(9, 8, 5))
    val CargahBesli = TurkishJins("Çârgâh beşlisi", listOf(9, 9, 4, 9))
    val CargahDortlu = TurkishJins("Çârgâh dörtlüsü", listOf(9, 9, 4))
    val BuselikBesli = TurkishJins("Bûselik beşlisi", listOf(9, 4, 9, 9))
    val UssakDortlu = TurkishJins("Uşşak dörtlüsü", listOf(8, 5, 9))
    val HuseyniBesli = TurkishJins("Hüseynî beşlisi", listOf(8, 5, 9, 9))
    val KurdiDortlu = TurkishJins("Kürdî dörtlüsü", listOf(4, 9, 9))
    val HicazBesli = TurkishJins("Hicaz beşlisi", listOf(5, 12, 5, 9))
    val HicazDortlu = TurkishJins("Hicaz dörtlüsü", listOf(5, 12, 5))
    val HicazUzzal = TurkishJins("Hicaz dörtlüsü (A13)", listOf(5, 13, 4))
    val SabaDortlu = TurkishJins("Saba dörtlüsü", listOf(8, 5, 5))
    val SegahUclu = TurkishJins("Segâh üçlüsü", listOf(5, 9))
}

/** One degree of a Turkish makam: written pitch in komas, its spelling, and which jins it is in. */
data class TurkishDegree(val degree: Int, val written: Int, val spelling: TurkishSpelling, val upper: Boolean)

/**
 * A Turkish makam: a lower jins on the tonic (durak) and an upper one from [upperFrom], with the
 * whole ascending octave in komas. [tonic] is the written pitch of the durak. A şed makam is
 * another makam's scale moved to a new durak: [base] names the makam it is taken from.
 */
data class TurkishMakam(
    val name: String,
    val turkishName: String,
    val tonic: Int,
    val lower: TurkishJins,
    val upperFrom: Int,
    val upper: TurkishJins,
    val intervals: List<Int>,
    val base: String? = null,
) {
    /** The same scale on another durak (şed), keeping the name: its seyir moves along with it. */
    fun onDurak(written: Int): TurkishMakam = copy(tonic = written)

    /** A named şed of this makam: the same scale on [durak] under its own name. */
    fun sed(name: String, turkishName: String, durak: Int): TurkishMakam =
        copy(name = name, turkishName = turkishName, tonic = durak, base = this.turkishName)

    /** Letter (0 = C … 6 = B) the durak is written on. */
    val tonicLetter: Int
        get() {
            val pc = Math.floorMod(tonic, Koma.PER_OCTAVE)
            return Koma.NATURALS.indices.minBy { kotlin.math.abs(Koma.NATURALS[it] - pc) }
        }

    init {
        require(intervals.size == 7 && intervals.sum() == Koma.PER_OCTAVE) { "$name: an octave is 7 steps, 53 komas" }
        require(intervals.take(lower.intervals.size) == lower.intervals) { "$name: lower jins doesn't match" }
        val top = intervals.drop(upperFrom)
        require(top.take(upper.intervals.size) == upper.intervals.take(top.size)) { "$name: upper jins doesn't match" }
    }

    /** The seven degrees from the durak up, each with the next letter. */
    fun degrees(): List<TurkishDegree> {
        val tonicLetter = tonicLetter
        var written = tonic
        return (0 until 7).map { degree ->
            if (degree > 0) written += intervals[degree - 1]
            val letter = (tonicLetter + degree) % 7
            val pc = Math.floorMod(written, Koma.PER_OCTAVE)
            val alter = Math.floorMod(pc - Koma.NATURALS[letter] + 26, Koma.PER_OCTAVE) - 26
            TurkishDegree(degree, written, TurkishSpelling(letter, alter), upper = degree >= upperFrom)
        }
    }
}

/** Makams grouped by durak (final), the way Turkish practice files them. */
data class TurkishFamily(val name: String, val makams: List<TurkishMakam>)

object TurkishMakams {
    private const val C4 = 4 * Koma.PER_OCTAVE
    private const val YEGAH = C4 + 9
    private const val HUSEYNI_ASIRAN = C4 + 18
    private const val ACEM_ASIRAN = C4 + 22
    private const val IRAK = C4 + 26
    private const val RAST = C4 + 31
    private const val DUGAH = C4 + 40
    private const val SEGAH = C4 + 48
    private const val BUSELIK = C4 + 49
    private const val CARGAH = C4 + 53
    private const val NEVA = C4 + 62

    /** Perdes a makam can be moved to (şed): the karar perdes and Nevâ. */
    val duraks = listOf(YEGAH, HUSEYNI_ASIRAN, ACEM_ASIRAN, IRAK, RAST, DUGAH, SEGAH, BUSELIK, CARGAH, NEVA)

    private val zirguleliHicaz = TurkishMakam(
        "Зиргюлели Хиджаз", "Zirgüleli Hicaz", DUGAH, TurkishAjnas.HicazBesli, 4, TurkishAjnas.HicazDortlu,
        listOf(5, 12, 5, 9, 5, 12, 5),
    )
    private val kurdi = TurkishMakam(
        "Кюрди", "Kürdî", DUGAH, TurkishAjnas.KurdiDortlu, 3, TurkishAjnas.BuselikBesli, listOf(4, 9, 9, 9, 4, 9, 9),
    )
    private val cargah = TurkishMakam(
        "Чаргях", "Çârgâh", CARGAH, TurkishAjnas.CargahBesli, 4, TurkishAjnas.CargahDortlu, listOf(9, 9, 4, 9, 9, 9, 4),
    )

    val families = listOf(
        TurkishFamily(
            "Rast",
            listOf(
                TurkishMakam("Раст", "Rast", RAST, TurkishAjnas.RastBesli, 4, TurkishAjnas.RastDortlu, listOf(9, 8, 5, 9, 9, 8, 5)),
                TurkishMakam("Махур", "Mâhur", RAST, TurkishAjnas.CargahBesli, 4, TurkishAjnas.CargahDortlu, listOf(9, 9, 4, 9, 9, 9, 4)),
                TurkishMakam("Нихаванд", "Nihâvend", RAST, TurkishAjnas.BuselikBesli, 4, TurkishAjnas.HicazDortlu, listOf(9, 4, 9, 9, 5, 12, 5)),
            ),
        ),
        TurkishFamily(
            "Dügâh",
            listOf(
                TurkishMakam("Ушшак", "Uşşak", DUGAH, TurkishAjnas.UssakDortlu, 3, TurkishAjnas.BuselikBesli, listOf(8, 5, 9, 9, 4, 9, 9)),
                TurkishMakam("Хусейни", "Hüseynî", DUGAH, TurkishAjnas.HuseyniBesli, 4, TurkishAjnas.UssakDortlu, listOf(8, 5, 9, 9, 8, 5, 9)),
                TurkishMakam("Бусэлик", "Bûselik", DUGAH, TurkishAjnas.BuselikBesli, 4, TurkishAjnas.KurdiDortlu, listOf(9, 4, 9, 9, 4, 9, 9)),
                kurdi,
                TurkishMakam("Хиджаз", "Hicaz", DUGAH, TurkishAjnas.HicazDortlu, 3, TurkishAjnas.RastBesli, listOf(5, 12, 5, 9, 8, 5, 9)),
                zirguleliHicaz,
                TurkishMakam("Саба", "Saba", DUGAH, TurkishAjnas.SabaDortlu, 2, TurkishAjnas.HicazUzzal, listOf(8, 5, 5, 13, 4, 9, 9)),
            ),
        ),
        TurkishFamily(
            "Segâh",
            listOf(
                TurkishMakam("Сегях", "Segâh", SEGAH, TurkishAjnas.SegahUclu, 2, TurkishAjnas.RastDortlu, listOf(5, 9, 9, 8, 5, 9, 8)),
                TurkishMakam("Хюззам", "Hüzzam", SEGAH, TurkishAjnas.SegahUclu, 2, TurkishAjnas.HicazDortlu, listOf(5, 9, 5, 12, 5, 9, 8)),
            ),
        ),
        TurkishFamily("Çârgâh", listOf(cargah)),
        // Şed makams: a known scale on another durak, with a name and seyir of its own.
        TurkishFamily(
            "Şed",
            listOf(
                zirguleliHicaz.sed("Хиджазкяр", "Hicazkâr", RAST),
                zirguleliHicaz.sed("Шедарабан", "Şedaraban", YEGAH),
                zirguleliHicaz.sed("Сузидиль", "Sûzidil", HUSEYNI_ASIRAN),
                kurdi.sed("Кюрдилихиджазкяр", "Kürdîlihicazkâr", RAST),
                cargah.sed("Аджемаширан", "Acemaşîran", ACEM_ASIRAN),
            ),
        ),
    )

    val all: List<TurkishMakam> = families.flatMap { it.makams }

    fun named(turkishName: String): TurkishMakam? = all.firstOrNull { it.turkishName == turkishName }

    /** The named şed makam that [makam] moved to [durak] is, if any: Zirgüleli Hicaz on Rast is Hicazkâr. */
    fun namedSed(makam: TurkishMakam, durak: Int): TurkishMakam? {
        val source = makam.base ?: makam.turkishName
        return all.firstOrNull { (it.base ?: it.turkishName) == source && it.tonic == durak }
    }
}

/** A step in komas as Turkish theory names it: F (koma), B (bakiye), S, K (mücenneb), T (tanini), A (artık). */
fun komaStepLabel(komas: Int): String = when (komas) {
    1 -> "F"
    4 -> "B"
    5 -> "S"
    8 -> "K"
    9 -> "T"
    12 -> "A12"
    13 -> "A13"
    else -> komas.toString()
}
