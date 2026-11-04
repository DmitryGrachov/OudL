package com.udl.oud.jins

import com.udl.oud.fretboard.Jins
import com.udl.oud.fretboard.NoteLabels
import com.udl.oud.taqsim.JinsGrammars
import com.udl.oud.taqsim.PhraseRole
import com.udl.oud.taqsim.Section
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.Station
import com.udl.oud.taqsim.Taqsim
import com.udl.oud.taqsim.TaqsimForm
import com.udl.oud.taqsim.TaqsimGenerator
import com.udl.oud.taqsim.TaqsimSayrs
import kotlin.random.Random

/**
 * A short taqsim inside one jins, for learning to improvise on a few notes before a whole maqam:
 * an opening on the root, a question resting on a pillar and its answer coming home, a phrase of
 * development, sometimes another question and answer, and a qafla. Everything the taqsim has —
 * theme, figures, breaths, ornaments — but in a space of four or five notes.
 */
object JinsEtude {
    /** The jins on [root], with one note of room below and one above. */
    fun station(jins: Jins, root: Int): Station {
        val top = jins.intervals.size
        val base = JinsCatalog.station(jins, root)
        return Station(
            "${jins.name} на ${base.spelling(0).label(NoteLabels.Solfege)}",
            JinsCatalog.host(jins), root, false, jins, 0, 0, -2, top + 1,
        )
    }

    /** Where a question in this jins rests: its highest pillar above the root that fits. */
    fun questionRest(jins: Jins): Int {
        val high = jins.intervals.size + 1
        return JinsGrammars.of(jins).rests.filter { it in 1..high }.maxOrNull() ?: jins.intervals.size
    }

    fun plan(jins: Jins, root: Int, random: Random): TaqsimSayrs.Plan {
        val s = station(jins, root)
        val q = questionRest(jins)
        val sections = mutableListOf(
            Section(SectionKind.Opening, s, listOf(PhraseRole.Open), listOf(0)),
            Section(SectionKind.Opening, s, listOf(PhraseRole.Question, PhraseRole.Answer), listOf(q, 0)),
            Section(SectionKind.Upper, s, listOf(PhraseRole.Develop), listOf(if (random.nextBoolean()) q else 0)),
        )
        if (random.nextDouble() < 0.6) {
            sections += Section(SectionKind.Revisit, s, listOf(PhraseRole.Question, PhraseRole.Answer), listOf(q, 0))
        }
        sections += Section(SectionKind.Qafla, s, listOf(PhraseRole.Cadence), listOf(0))
        return TaqsimSayrs.Plan(TaqsimForm.Classic, sections)
    }

    fun generate(jins: Jins, root: Int, seed: Long): Taqsim {
        val generator = TaqsimGenerator(seed)
        return generator.generateFrom(JinsCatalog.host(jins), plan(jins, root, Random(seed xor 0x5DEECE66DL)), detailed = false)
    }
}
