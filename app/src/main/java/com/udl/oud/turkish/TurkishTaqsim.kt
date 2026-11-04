package com.udl.oud.turkish

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.StaffNote
import com.udl.oud.taqsim.SayrStations
import com.udl.oud.taqsim.Station
import com.udl.oud.taqsim.StationScale
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.taqsim.TaqsimForm
import com.udl.oud.taqsim.TaqsimGenerator
import com.udl.oud.taqsim.TaqsimLength
import com.udl.oud.taqsim.TaqsimSayrs
import com.udl.oud.tuner.Ahenk
import kotlin.random.Random

/**
 * A Turkish makam's degrees as a station scale: written pitches in komas, named by perde, with
 * koma-counted accidentals; going down, the inici degrees; under the durak, the yeden.
 */
data class TurkishStationScale(val makam: TurkishMakam, val descending: Boolean = false) : StationScale, TurkishTones {
    override val perOctave: Int get() = Koma.PER_OCTAVE

    override fun tone(degree: Int): TurkishTone =
        if (degree == -1) TurkishScale.yeden(makam)
        else TurkishScale.tone(makam, Math.floorMod(degree, 7), Math.floorDiv(degree, 7), descending)

    override fun step(degree: Int): Int = tone(degree).written
    override fun label(degree: Int): String = tone(degree).let { Perdes.of(it.written) ?: it.spelling.label() }
    override fun staff(degree: Int): StaffNote = tone(degree).staffNote()
    override fun hz(degree: Int): Double = tone(degree).frequency(440.0, Ahenk.Bolahenk)
}

/**
 * Turkish ajnas and the Arabic ajnas of the same shape whose motifs they borrow: the motifs are
 * written in degrees, so a Uşşak tetrachord moves like Bayati, a Bûselik pentachord like
 * Nahawand, a Segâh trichord like Sikah. The koma sizes stay the makam's own.
 */
object TurkishGrammar {
    fun analog(jins: TurkishJins): Jins = when {
        jins.name.startsWith("Rast") -> Ajnas.Rast
        jins.name.startsWith("Uşşak") || jins.name.startsWith("Hüseynî") -> Ajnas.Bayati
        jins.name.startsWith("Bûselik") -> Ajnas.Nahawand
        jins.name.startsWith("Kürdî") -> Ajnas.Kurd
        jins.name.startsWith("Hicaz") -> Ajnas.Hijaz
        jins.name.startsWith("Saba") -> Ajnas.Saba
        jins.name.startsWith("Segâh") -> Ajnas.Sikah
        jins.name.startsWith("Çârgâh") -> Ajnas.Ajam
        else -> Ajnas.Rast
    }

    /** A line on each Turkish jins, as a learner first meets it. */
    fun about(name: String): String = when {
        name.startsWith("Rast") -> "Rast: светлый, с Segâh — нотой на комму ниже Си; опоры — Rast и Nevâ."
        name.startsWith("Uşşak") -> "Uşşak: мягкий, с низким Segâh над Dügâh — почти как Баяти."
        name.startsWith("Hüseynî") -> "Hüseynî: Uşşak, продолженный до Hüseynî — опора на квинте."
        name.startsWith("Bûselik") -> "Bûselik: «минорный» с чистым Bûselik (Си) — ровные тоны и бакийе."
        name.startsWith("Kürdî") -> "Kürdî: тёмный, с бакийе (4 коммы) сразу над основой."
        name.startsWith("Hicaz") -> "Hicaz: увеличенная секунда (12–13 комм) между второй и третьей ступенью."
        name.startsWith("Saba") -> "Saba: тесная ступень над третьей — щемящий цвет."
        name.startsWith("Segâh") -> "Segâh: основа на комму ниже Си — на ней задерживаются."
        name.startsWith("Çârgâh") -> "Çârgâh: «мажорный», чистые тоны."
        else -> name
    }
}

/** The stations of a Turkish makam's seyir, for the taqsim planner. */
class TurkishSayrStations(val makam: TurkishMakam) : SayrStations {
    private val seyir = TurkishSeyirs.of(makam)
    private val g = makam.upperFrom
    private val lowerTop = makam.lower.intervals.size
    private val up = TurkishStationScale(makam)
    private val down = TurkishStationScale(makam, descending = true)

    private fun station(name: String, scale: TurkishStationScale, jins: TurkishJins, root: Int, focus: Int, low: Int, high: Int) =
        Station(name, scale, TurkishGrammar.analog(jins), root, focus, low, high, jins.name)

    /** Highest degree that stays on a Turkish oud's neck (about A4 sounding in Bolâhenk). */
    private val ceiling = (9 downTo 7).first { up.hz(it) <= TOP_HZ || it == 7 }

    override val ghammazDegree: Int get() = g

    override val lower = station("${makam.lower.name} на ${up.label(0)}", up, makam.lower, 0, 0, -2, maxOf(lowerTop + 1, 3))

    /** The climb ends on the güçlü when it lies inside the octave, otherwise on the upper jins. */
    private val guclu = seyir.guclu.takeIf { it in 1..6 } ?: g
    override val ghammaz = station("гючлю ${up.label(guclu)}", up, makam.upper, g, guclu, 0, minOf(maxOf(g, guclu) + 2, ceiling))
    override val upper = station("${makam.upper.name} на ${up.label(g)}", up, makam.upper, g, g, g - 1, minOf(g + makam.upper.intervals.size + 1, ceiling))
    override val peak = station("тиз дурак ${up.label(7)}", up, makam.upper, g, 7, g, ceiling)
    override val back = station(
        if (seyir.inici.isNotEmpty()) "спуск по иничи-ступеням к ${down.label(g)}" else "спуск к ${up.label(g)}",
        if (seyir.inici.isNotEmpty()) down else up, makam.upper, g, g, minOf(lowerTop, g) - 1, 7,
    )
    override val qafla = lower.copy(name = "карар на ${up.label(0)}")

    /** The seyir's usual geçkis, as stations on the perde they are made on. */
    override val modulations: List<Station> = seyir.gecki.mapNotNull { gk ->
        val target = TurkishMakams.named(gk.makam) ?: return@mapNotNull null
        val durak = makam.tonic + gk.offset
        val moved = TurkishMakams.namedSed(target, durak) ?: target.onDurak(durak)
        val scale = TurkishStationScale(moved)
        val top = moved.lower.intervals.size + 1
        if (scale.hz(top) > TOP_HZ || scale.hz(-1) < BOTTOM_HZ) return@mapNotNull null
        station("${moved.turkishName} на ${scale.label(0)}", scale, moved.lower, 0, 0, -1, top)
    }

    override val lowerQuestion: Int get() = lowerTop

    /** The form the seyir's direction asks for: up from the durak, down from above, or from the güçlü. */
    fun form(length: TaqsimLength, random: Random): TaqsimForm {
        val fits = when (seyir.direction) {
            SeyirDirection.Up -> listOf(TaqsimForm.Classic, TaqsimForm.Revisit, TaqsimForm.EarlyMove, TaqsimForm.TwoMoves)
            SeyirDirection.Down -> listOf(TaqsimForm.FromAbove)
            SeyirDirection.DownUp -> listOf(TaqsimForm.FromGhammaz, TaqsimForm.GhammazAndBack)
        }.filter { f ->
            when (f) {
                TaqsimForm.TwoMoves -> modulations.size >= 2 && length != TaqsimLength.Short
                TaqsimForm.EarlyMove -> modulations.isNotEmpty()
                TaqsimForm.Revisit, TaqsimForm.GhammazAndBack -> length != TaqsimLength.Short
                else -> true
            }
        }.ifEmpty { listOf(if (seyir.direction == SeyirDirection.Down) TaqsimForm.FromAbove else TaqsimForm.Classic) }
        // Plain forms are commoner than the elaborate ones.
        return if (random.nextDouble() < 0.5) fits.first() else fits.random(random)
    }

    private companion object {
        const val TOP_HZ = 445.0
        const val BOTTOM_HZ = 68.0
    }
}

/** A Turkish taqsim: the same generator, over the makam's seyir and its koma pitches. */
object TurkishTaqsim {
    fun generate(makam: TurkishMakam, length: TaqsimLength, seed: Long): Taqsim {
        val stations = TurkishSayrStations(makam)
        val random = Random(seed xor 0x7AB3L)
        val plan = TaqsimSayrs.planFrom(stations, length, random) { stations.form(length, random) }
        return TaqsimGenerator(seed).generateFrom(plan, TurkishGrammar.analog(makam.lower), makam.turkishName, null, detailed = true)
    }
}
