package com.udl.oud.taqsim

import com.udl.oud.audio.PluckSynth
import com.udl.oud.audio.YinPitchDetector
import com.udl.oud.fretboard.Maqamat
import com.udl.oud.tuner.Tunings
import com.udl.oud.tuner.centsBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExpressionTest {
    private val seeds = 0L until 40L
    private val rate = 44100

    @Test fun thePeakIsLoudestAndQuickestTheQaflaBroadest() {
        val m = Maqamat.all.first { it.name == "Баяти" }
        val all = seeds.map { TaqsimGenerator(it).generate(m, TaqsimLength.Medium) }
        fun mean(kind: SectionKind) = all.flatMap { t -> t.phrases.filter { it.section == kind }.flatMap { it.notes } }.map { it.velocity }.average()
        assertTrue(mean(SectionKind.Peak) > mean(SectionKind.Opening) + 0.15)
        val peak = all.flatMap { t -> t.phrases.filter { it.section == SectionKind.Peak } }.first()
        val qafla = all.first().phrases.last()
        assertTrue(peak.tempo < 1.0 && qafla.tempo > 1.1)
    }

    @Test fun aPhraseSwellsToItsTopAndEasesOff() {
        val m = Maqamat.all.first { it.name == "Раст" }
        var checked = 0
        seeds.forEach { s ->
            TaqsimGenerator(s).generate(m, TaqsimLength.Medium).phrases.filter { it.notes.size >= 7 }.forEach { p ->
                val top = p.notes.indices.maxBy { p.notes[it].degree }
                if (top in 2 until p.notes.lastIndex - 1) {
                    // Allowing for accents and the small random spread.
                    assertTrue(p.notes[top].velocity > p.notes.first().velocity - 0.12f)
                    assertTrue(p.notes[top].velocity > p.notes.last().velocity - 0.12f)
                    checked++
                }
            }
        }
        assertTrue(checked > 20)
    }

    @Test fun velocitiesStayInRange() {
        Maqamat.all.forEach { m ->
            TaqsimGenerator(1).generate(m, TaqsimLength.Long).notes.forEach { assertTrue(it.velocity in 0.3f..1f) }
        }
    }

    @Test fun notesGoOnTheHighestCourseAtOrBelowThem() {
        val tuning = Tunings.arabic.first() // C2 F2 A2 D3 G3 C4
        assertEquals(0, OudTone.course(72, tuning)) // C2 open
        assertEquals(3, OudTone.course(103, tuning)) // E½♭3 on the D3 course
        assertEquals(5, OudTone.course(124, tuning)) // D4 on the C4 course
        assertTrue(OudTone.brightness(0, tuning) < OudTone.brightness(5, tuning))
        assertTrue(OudTone.ring(0, tuning) > OudTone.ring(5, tuning))
    }

    private fun render(p: PluckSynth.Pluck) = PluckSynth(rate).render(listOf(p), rate)

    @Test fun vibratoSwingsRoundTheNoteAndStaysInTune() {
        val f = 220.0
        val samples = render(PluckSynth.Pluck(f, 0, rate, vibratoCents = 15.0, vibratoDelaySamples = 0))
        val detector = YinPitchDetector(rate, 2048, threshold = 0.3f)
        // Short windows across a few vibrato cycles: they disagree, but centre on the note.
        val cents = (0 until 12).mapNotNull { k ->
            val at = 14000 + k * 1100
            detector.detect(samples.copyOfRange(at, at + 2048))?.let { centsBetween(it.frequency.toDouble(), f) }
        }
        assertTrue("$cents", cents.size >= 8)
        assertTrue("$cents", cents.max() - cents.min() > 8)
        assertTrue("$cents", abs(cents.average()) < 6)
    }

    @Test fun aBassCourseSoundsDarker() {
        fun edge(samples: FloatArray): Double {
            // Share of energy in quick changes: a bright tone has more.
            val part = samples.copyOfRange(2000, 12000)
            val diff = part.toList().zipWithNext { a, b -> (b - a).toDouble() * (b - a) }.sum()
            return diff / part.sumOf { it.toDouble() * it }
        }
        val dark = render(PluckSynth.Pluck(196.0, 0, rate, brightness = 0.2f))
        val bright = render(PluckSynth.Pluck(196.0, 0, rate, brightness = 0.9f))
        assertTrue(edge(dark) < edge(bright))
    }
}
