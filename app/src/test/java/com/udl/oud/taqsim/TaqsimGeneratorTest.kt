package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TaqsimGeneratorTest {
    private val seeds = 0L until 60L

    @Test fun everyJinsOfEveryMaqamHasAGrammar() {
        Maqamat.all.forEach { m ->
            JinsGrammars.of(m.lower)
            JinsGrammars.of(m.upper)
            m.descent?.let { JinsGrammars.of(it.upper) }
        }
    }

    @Test fun everyMaqamMakesATaqsimForAnySeedAndLength() {
        Maqamat.all.forEach { m ->
            TaqsimLength.entries.forEach { length ->
                seeds.forEach { seed -> TaqsimGenerator(seed).generate(m, length) }
            }
        }
    }

    @Test fun theTaqsimEndsOnTheTonicWithTheLongestNote() {
        Maqamat.all.forEach { m ->
            seeds.forEach { seed ->
                val t = TaqsimGenerator(seed).generate(m, TaqsimLength.Medium)
                val last = t.notes.last()
                assertEquals("${m.name} $seed", 0, Math.floorMod(last.step - m.tonic, 24))
                assertEquals("${m.name} $seed", t.phrases.last().section, SectionKind.Qafla)
                assertTrue(last.beats >= t.notes.dropLast(1).maxOf { it.beats } - 1e-9)
            }
        }
    }

    @Test fun phrasesRestWhereTheirSectionSays() {
        Maqamat.all.forEach { m ->
            seeds.forEach { seed ->
                val gen = TaqsimGenerator(seed)
                val t = gen.generate(m, TaqsimLength.Long)
                t.phrases.forEach { p ->
                    val d = p.notes.last().degree
                    assertTrue("${m.name} $seed ${p.section}: ends on $d", d in p.station.low..p.station.high)
                }
            }
        }
    }

    @Test fun melodyMovesMostlyByStepAndStaysOnTheNeck() {
        Maqamat.all.forEach { m ->
            seeds.forEach { seed ->
                val t = TaqsimGenerator(seed).generate(m, TaqsimLength.Medium)
                t.phrases.forEach { p ->
                    val moves = p.notes.zipWithNext { a, b -> abs(b.degree - a.degree) }
                    assertTrue("${m.name} $seed: leap too wide", moves.all { it <= 3 })
                    assertTrue(p.notes.all { it.step in 72..134 })
                }
                val all = t.phrases.flatMap { p -> p.notes.zipWithNext { a, b -> abs(b.degree - a.degree) } }
                assertTrue("${m.name} $seed: too jumpy", all.count { it <= 1 } >= all.size * 0.6)
            }
        }
    }

    @Test fun theSayrGoesThroughTheGhammaz() {
        Maqamat.all.forEach { m ->
            seeds.forEach { seed ->
                val t = TaqsimGenerator(seed).generate(m, TaqsimLength.Short)
                val ghammaz = Math.floorMod(m.notes()[m.ghammaz].offset + m.tonic, 24)
                assertTrue("${m.name} $seed", t.notes.any { Math.floorMod(it.step, 24) == ghammaz && it.beats >= 2.5 })
            }
        }
    }

    @Test fun lengthsDiffer() {
        val m = Maqamat.all.first()
        val short = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Short).phrases.size }.average()
        val long = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Long).phrases.size }.average()
        assertTrue(long > short + 4)
    }

    @Test fun sameSeedSameTaqsimOtherSeedOther() {
        val m = Maqamat.all.first { it.name == "Баяти" }
        assertEquals(TaqsimGenerator(7).generate(m, TaqsimLength.Medium).notes, TaqsimGenerator(7).generate(m, TaqsimLength.Medium).notes)
        assertNotEquals(TaqsimGenerator(7).generate(m, TaqsimLength.Medium).notes, TaqsimGenerator(8).generate(m, TaqsimLength.Medium).notes)
    }

    @Test fun manySeedsGiveManyDifferentOpenings() {
        val m = Maqamat.all.first { it.name == "Хиджаз" }
        val openings = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium).phrases.first().notes.map { n -> n.degree } }.toSet()
        assertTrue("only ${openings.size}", openings.size >= 25)
    }

    @Test fun writtenSayrsModulateAndStartWhereDevelopmentCentres() {
        val rast = Maqamat.all.first { it.name == "Раст" }
        assertTrue(seeds.any { s -> TaqsimGenerator(s).generate(rast, TaqsimLength.Medium).phrases.any { it.section == SectionKind.Modulation } })
        val saba = Maqamat.all.first { it.name == "Саба" }
        seeds.forEach { s ->
            // However it opens, the opening's answer settles on Saba's third degree, F.
            val answer = TaqsimGenerator(s).generate(saba, TaqsimLength.Medium).phrases
                .first { it.section == SectionKind.Opening && it.role == PhraseRole.Answer }
            assertEquals(2, answer.notes.last().degree)
        }
    }

    @Test fun theThemeOpensTheTaqsimAndComesBack() {
        Maqamat.all.forEach { m ->
            val withTheme = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium) }
            // Heard in at least three phrases in most taqasim, and nearly always in the opening.
            assertTrue(m.name, withTheme.count { it.themePhrases.size >= 3 } >= seeds.count() * 0.6)
            assertTrue(m.name, withTheme.count { t -> t.themePhrases.any { t.phrases[it].section == SectionKind.Opening } } >= seeds.count() * 0.8)
        }
    }

    @Test fun theThemeKeepsItsRhythm() {
        val t = (0L until 200L).map { TaqsimGenerator(it).generate(Maqamat.all.first { m -> m.name == "Раст" }, TaqsimLength.Medium) }
            .first { it.themePhrases.size >= 2 }
        val theme = t.theme!!
        t.themePhrases.forEach { pi ->
            val p = t.phrases[pi]
            p.themeNotes.sorted().withIndex().filter { (_, i) -> i != p.notes.lastIndex }.forEach { (k, i) ->
                assertEquals(theme.rhythm[k % theme.rhythm.size], p.notes[i].beats, theme.rhythm[k % theme.rhythm.size] * 0.06)
            }
        }
    }

    @Test fun formsVary() {
        val m = Maqamat.all.first { it.name == "Баяти" }
        val forms = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium).form }.toSet()
        assertTrue("$forms", forms.size >= 5)
        // A form that opens from the ghammaz really starts there.
        seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium) }.filter { it.form == TaqsimForm.FromGhammaz }.forEach {
            assertEquals(m.ghammaz, it.phrases.first().notes.last().degree)
        }
    }

    @Test fun twoMovesMeansTwoModulations() {
        val m = Maqamat.all.first { it.name == "Раст" }
        seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium) }.filter { it.form == TaqsimForm.TwoMoves }.forEach { t ->
            val stations = t.phrases.filter { it.section == SectionKind.Modulation }.map { it.station.name }.toSet()
            assertEquals(2, stations.size)
        }
    }

    @Test fun everyMaqamHasAWrittenSayrAndUsesItsModulations() {
        Maqamat.all.forEach { m ->
            assertTrue(m.name, TaqsimSayrs.isDetailed(m))
            val stations = TaqsimSayrs.Stations(m, m.tonic)
            assertTrue("${m.name}: no modulation fits the neck", stations.modulations.isNotEmpty())
            assertTrue(m.name, seeds.any { s -> TaqsimGenerator(s).generate(m, TaqsimLength.Medium).phrases.any { it.section == SectionKind.Modulation } })
        }
    }

    @Test fun theColourNoteIsHeard() {
        val bayati = Maqamat.all.first { it.name == "Баяти" }
        seeds.forEach { s ->
            val t = TaqsimGenerator(s).generate(bayati, TaqsimLength.Short)
            assertTrue(t.notes.any { Math.floorMod(it.step, 24) == 103 % 24 }) // E½♭
        }
    }

    @Test fun answersPickUpTheQuestion() {
        val m = Maqamat.all.first { it.name == "Раст" }
        val shared = seeds.count { s ->
            val ps = TaqsimGenerator(s).generate(m, TaqsimLength.Medium).phrases
            val q = ps.indexOfFirst { it.role == PhraseRole.Question }
            ps[q + 1].role == PhraseRole.Answer && ps[q + 1].notes.take(2).map { it.degree } == ps[q].notes.take(2).map { it.degree }
        }
        assertTrue("$shared", shared >= seeds.count() / 2)
    }
}
