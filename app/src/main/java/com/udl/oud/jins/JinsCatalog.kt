package com.udl.oud.jins

import com.udl.oud.fretboard.Ajnas
import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.Maqam
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Sayrs
import com.udl.oud.taqsim.Station

/** Where a jins is found: in which maqam, in which role, on which note. */
data class Appearance(val maqam: Maqam, val where: Where, val root: Int, val via: Maqam? = null) {
    enum class Where(val title: String) {
        Lower("внизу, на тонике"),
        Upper("наверху, на гаммазе"),
        Descent("на спуске"),
        Move("переходом"),
    }
}

/**
 * The ten ajnas as things to study on their own: the notes on any root (with the neighbours
 * the jins leans on, taken from the maqam it is the base of), and every place the maqamat use
 * it — as the lower jins, the upper one, on the way down, or as a modulation in a sayr.
 */
object JinsCatalog {
    val ajnas: List<Jins> = listOf(
        Ajnas.Rast, Ajnas.Bayati, Ajnas.Sikah, Ajnas.Hijaz, Ajnas.Saba,
        Ajnas.SabaZamzam, Ajnas.Kurd, Ajnas.Nahawand, Ajnas.Ajam, Ajnas.Nikriz,
    )

    /** The maqam built on this jins: it gives the notes around the jins and their spelling. */
    fun host(jins: Jins): Maqam = Maqamat.all.first { it.lower == jins }

    fun defaultRoot(jins: Jins): Int = host(jins).tonic

    /** The jins on [root] as a station: its degrees 0..n, and the scale around it from −3 to 7. */
    fun station(jins: Jins, root: Int): Station = Station(jins.name, host(jins), root, false, jins, 0, 0, -3, 7)

    private fun ghammazStep(m: Maqam) = m.tonic + m.notes()[m.ghammaz].offset

    fun appearances(jins: Jins): List<Appearance> {
        val own = Maqamat.all.flatMap { m ->
            listOfNotNull(
                Appearance(m, Appearance.Where.Lower, m.tonic).takeIf { m.lower == jins },
                Appearance(m, Appearance.Where.Upper, ghammazStep(m)).takeIf { m.upper == jins },
                Appearance(m, Appearance.Where.Descent, ghammazStep(m)).takeIf { m.descent?.upper == jins && m.upper != jins },
            )
        }
        val moves = Maqamat.all.flatMap { m ->
            Sayrs.of(m)?.modulations.orEmpty().mapNotNull { mod ->
                val target = Maqamat.all.firstOrNull { it.name == mod.maqam } ?: return@mapNotNull null
                Appearance(m, Appearance.Where.Move, m.tonic + mod.offset, via = target).takeIf { target.lower == jins }
            }
        }
        return own + moves
    }

    /** The notes the jins is found on, lowest first, one per pitch class, kept on the oud's neck. */
    fun roots(jins: Jins): List<Int> =
        (appearances(jins).map { it.root } + defaultRoot(jins))
            .map { r -> generateSequence(r) { it - 24 }.first { it < BASE + 24 }.let { if (it < BASE) it + 24 else it } }
            .distinct()
            .sorted()

    /** Roots are placed between B♭2 and A3, where every jins fits on the neck with room around it. */
    private const val BASE = 92
}
