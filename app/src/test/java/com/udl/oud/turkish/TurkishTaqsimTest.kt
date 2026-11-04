package com.udl.oud.turkish

import com.udl.oud.audio.PluckSynth
import com.udl.oud.taqsim.OrnamentLevel
import com.udl.oud.taqsim.Ornamenter
import com.udl.oud.taqsim.SectionKind
import com.udl.oud.taqsim.TaqsimForm
import com.udl.oud.taqsim.TaqsimLength
import com.udl.oud.taqsim.TaqsimRenderer
import com.udl.oud.tuner.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishTaqsimTest {
    private val seeds = 0L until 30L
    private fun makam(name: String) = TurkishMakams.all.first { it.turkishName == name }

    @Test fun everyMakamMakesATaqsimThatComesHomeToItsDurak() {
        TurkishMakams.all.forEach { m ->
            TaqsimLength.entries.forEach { length ->
                seeds.forEach { s ->
                    val t = TurkishTaqsim.generate(m, length, s)
                    val last = t.notes.last()
                    assertEquals("${m.turkishName} $s", 0, Math.floorMod(last.step - m.tonic, Koma.PER_OCTAVE))
                    assertEquals(SectionKind.Qafla, t.phrases.last().section)
                    assertTrue("${m.turkishName}: on the neck", t.notes.all { it.hz in 60.0..460.0 })
                    t.phrases.forEach { p -> assertTrue(p.notes.all { it.degree in p.station.low..p.station.high }) }
                }
            }
        }
    }

    @Test fun notesAreNamedByPerdeAndWrittenWithKomas() {
        val rast = TurkishTaqsim.generate(makam("Rast"), TaqsimLength.Medium, 3)
        val names = rast.notes.map { it.name }.toSet()
        assertTrue(names.toString(), "Rast" in names && "Segâh" in names)
        val segah = rast.notes.first { it.name == "Segâh" }
        assertEquals(1, segah.staff.komas) // B♭¹: one koma flat
    }

    @Test fun rastComesDownThroughAcem() {
        val acem = seeds.count { s -> TurkishTaqsim.generate(makam("Rast"), TaqsimLength.Long, s).notes.any { it.name == "Acem" } }
        assertTrue("$acem", acem >= seeds.count() / 3)
    }

    @Test fun theSeyirDirectionShapesTheForm() {
        seeds.forEach { s ->
            assertEquals(TaqsimForm.FromAbove, TurkishTaqsim.generate(makam("Mâhur"), TaqsimLength.Medium, s).form) // inici
            assertTrue(TurkishTaqsim.generate(makam("Hicaz"), TaqsimLength.Medium, s).form.opening == TaqsimForm.Opening.Ghammaz) // inici-çıkıcı
            assertEquals(TaqsimForm.Opening.Low, TurkishTaqsim.generate(makam("Uşşak"), TaqsimLength.Medium, s).form.opening) // çıkıcı
        }
    }

    @Test fun geckisBecomeModulations() {
        val moved = seeds.count { s -> TurkishTaqsim.generate(makam("Rast"), TaqsimLength.Medium, s).phrases.any { it.section == SectionKind.Modulation } }
        assertTrue("$moved", moved >= seeds.count() / 2)
    }

    @Test fun theKaradDrawsOnTheYeden() {
        // Bûselik's yeden is Nim zirgüle, not the seventh degree an octave down.
        val heard = seeds.count { s -> TurkishTaqsim.generate(makam("Bûselik"), TaqsimLength.Long, s).notes.any { it.name == "Nim zirgüle" } }
        assertTrue("$heard", heard >= 3)
    }

    @Test fun itRendersInTuneWithTheAhenk() {
        val t = TurkishTaqsim.generate(makam("Uşşak"), TaqsimLength.Short, 1)
        val synth = PluckSynth(8000)
        val tuning = Tunings.turkish.first()
        val r = TaqsimRenderer.render(synth, t, 0..0, 150.0, 440.0, tuning, Ornamenter.decorate(t, OrnamentLevel.Light), 8000)
        assertTrue(r.pcm.isNotEmpty() && r.starts.size == t.phrases[0].notes.size)
    }
}
