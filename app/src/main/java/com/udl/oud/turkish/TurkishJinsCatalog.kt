package com.udl.oud.turkish

import com.udl.oud.taqsim.Station

/** Where a Turkish jins is found: in which makam, below (on the durak) or above, and on which perde. */
data class TurkishAppearance(val makam: TurkishMakam, val upper: Boolean, val durak: Int)

/**
 * The Turkish ajnas (dörtlü, beşli, üçlü) as things to study on their own: each on a perde, with
 * the notes around it taken from a makam that uses it, and every makam it is found in.
 */
object TurkishJinsCatalog {
    val ajnas: List<TurkishJins> = with(TurkishAjnas) {
        listOf(
            RastBesli, RastDortlu, UssakDortlu, HuseyniBesli, BuselikBesli, KurdiDortlu,
            HicazBesli, HicazDortlu, HicazUzzal, SabaDortlu, SegahUclu, CargahBesli, CargahDortlu,
        )
    }

    fun appearances(jins: TurkishJins): List<TurkishAppearance> = TurkishMakams.all.flatMap { m ->
        listOfNotNull(
            TurkishAppearance(m, false, m.tonic).takeIf { m.lower == jins },
            TurkishAppearance(m, true, m.degrees()[m.upperFrom].written).takeIf { m.upper == jins },
        )
    }

    /** The perdes the jins is found on, lowest first. */
    fun roots(jins: TurkishJins): List<Int> = appearances(jins).map { it.durak }.distinct().sorted()

    fun defaultRoot(jins: TurkishJins): Int = appearances(jins).let { a -> (a.firstOrNull { !it.upper } ?: a.first()).durak }

    /**
     * The scale around the jins on [durak]: the makam that has it below, moved there; or, for a
     * jins found only above, that makam's scale turned to start on it.
     */
    fun scaleMakam(jins: TurkishJins, durak: Int): TurkishMakam {
        val a = appearances(jins).let { list -> list.firstOrNull { !it.upper } ?: list.first() }
        val m = a.makam
        if (!a.upper) return m.copy(name = jins.name, turkishName = jins.name, tonic = durak, base = null)
        val u = m.upperFrom
        return TurkishMakam(jins.name, jins.name, durak, jins, 7 - u, m.lower, m.intervals.drop(u) + m.intervals.take(u))
    }

    /** The jins on [durak] as a station: its degrees 0..n, the scale around it from −3 to 7. */
    fun station(jins: TurkishJins, durak: Int, low: Int = -3, high: Int = 7): Station {
        val scale = TurkishJinsScale(scaleMakam(jins, durak))
        return Station("${jins.name} на ${scale.label(0)}", scale, TurkishGrammar.analog(jins), 0, 0, low, high, jins.name)
    }
}

/** A jins' scale by its degrees only: below the root, the scale's own seventh (no seyir needed). */
data class TurkishJinsScale(val makam: TurkishMakam) : com.udl.oud.taqsim.StationScale {
    override val perOctave: Int get() = Koma.PER_OCTAVE
    private val degrees = makam.degrees()

    fun tone(degree: Int): TurkishTone =
        degrees[Math.floorMod(degree, 7)].let { TurkishTone(it.written, it.spelling) }.octave(Math.floorDiv(degree, 7))

    override fun step(degree: Int): Int = tone(degree).written
    override fun label(degree: Int): String = tone(degree).let { Perdes.of(it.written) ?: it.spelling.label() }
    override fun staff(degree: Int) = tone(degree).staffNote()
    override fun hz(degree: Int): Double = tone(degree).frequency(440.0, com.udl.oud.tuner.Ahenk.Bolahenk)
}
