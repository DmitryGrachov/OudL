package com.udl.oud.taqsim

import com.udl.oud.audio.PluckSynth
import com.udl.oud.audio.YinPitchDetector
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.fretboard.Pitch
import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrnamentTest {
    private val seeds = 0L until 40L
    private fun taqsim(name: String, seed: Long) = TaqsimGenerator(seed).generate(Maqamat.all.first { it.name == name }, TaqsimLength.Medium)

    @Test fun skeletonHasNone() = assertTrue(Ornamenter.decorate(taqsim("Раст", 1), OrnamentLevel.None).isEmpty())

    @Test fun aLittleMeansAtMostOnePerPhraseAndTheQaflaRings() {
        Maqamat.all.forEach { m ->
            seeds.forEach { s ->
                val t = TaqsimGenerator(s).generate(m, TaqsimLength.Medium)
                val o = Ornamenter.decorate(t, OrnamentLevel.Light)
                var offset = 0
                t.phrases.forEach { p ->
                    val count = p.notes.indices.count { offset + it in o }
                    val limit = if (p.section == SectionKind.Qafla) 2 else 1
                    assertTrue("${m.name} $s: $count", count <= limit)
                    offset += p.notes.size
                }
            }
        }
    }

    @Test fun richIsRicher() {
        val light = seeds.sumOf { Ornamenter.decorate(taqsim("Баяти", it), OrnamentLevel.Light).size }
        val rich = seeds.sumOf { Ornamenter.decorate(taqsim("Баяти", it), OrnamentLevel.Rich).size }
        assertTrue("$light vs $rich", rich > light * 1.5)
    }

    @Test fun neighboursStayInTheMaqam() {
        Maqamat.all.forEach { m ->
            seeds.forEach { s ->
                val t = TaqsimGenerator(s).generate(m, TaqsimLength.Medium)
                val o = Ornamenter.decorate(t, OrnamentLevel.Rich)
                var offset = 0
                t.phrases.forEach { p ->
                    val classes = (0 until 7).map { Math.floorMod(p.station.step(it), 24) }.toSet()
                    p.notes.indices.forEach { i ->
                        o[offset + i]?.let { orn ->
                            listOfNotNull(orn.neighbourStep, orn.lowerStep).forEach { step ->
                                assertTrue("${m.name} $s: ${orn.kind}", Math.floorMod(step, 24) in classes)
                            }
                        }
                    }
                    offset += p.notes.size
                }
            }
        }
    }

    @Test fun sameTaqsimSameOrnaments() =
        assertEquals(Ornamenter.decorate(taqsim("Хиджаз", 5), OrnamentLevel.Rich), Ornamenter.decorate(taqsim("Хиджаз", 5), OrnamentLevel.Rich))

    @Test fun eventsFillTheNoteAndNoMore() {
        OrnamentKind.entries.forEach { kind ->
            val o = Ornament(kind, 104, null, lowerStep = 96)
            val events = OrnamentSound.events(100, o, 1000.0, 1200.0)
            assertTrue(kind.name, events.first().startMs == 1000.0)
            val end = events.maxOf { it.startMs + it.lengthMs }
            assertEquals(kind.name, 2200.0, end, 0.5)
            assertTrue(kind.name, events.zipWithNext().all { (a, b) -> b.startMs >= a.startMs })
        }
    }

    @Test fun tremoloIsQuickRepeatedPlucksAndTrillAlternates() {
        val tremolo = OrnamentSound.events(100, Ornament(OrnamentKind.Tremolo, null, null), 0.0, 1500.0)
        assertTrue(tremolo.size >= 12)
        assertTrue(tremolo.all { it.step == 100 && !it.legato })
        val trill = OrnamentSound.events(100, Ornament(OrnamentKind.Trill, 103, null), 0.0, 1500.0)
        assertTrue(trill.drop(1).all { it.legato })
        assertTrue(trill.map { it.step }.zipWithNext().count { (a, b) -> a != b } >= 6)
    }

    @Test fun shortNotesStayPlain() {
        val e = OrnamentSound.events(100, Ornament(OrnamentKind.Trill, 103, null), 0.0, 200.0)
        assertEquals(1, e.size)
    }

    @Test fun aSlideEndsInTune() {
        val rate = 44100
        val target = Pitch(110).frequency(440.0) // G3
        val samples = PluckSynth(rate).render(
            listOf(PluckSynth.Pluck(target, 0, rate, slideFromCents = 200.0, slideSamples = rate / 8)),
            rate,
        )
        val detector = YinPitchDetector(rate, 4096)
        // Early on the pitch is still moving: a short window, just past the attack.
        val early = YinPitchDetector(rate, 2048, threshold = 0.3f).detect(samples.copyOfRange(1000, 1000 + 2048))!!.frequency.toDouble()
        val late = detector.detect(samples.copyOfRange(rate / 2, rate / 2 + 4096))!!.frequency.toDouble()
        assertTrue("late ${centsBetween(late, target)}", abs(centsBetween(late, target)) < 5)
        assertTrue("early ${centsBetween(early, target)}", centsBetween(early, target) < -15)
    }
}
