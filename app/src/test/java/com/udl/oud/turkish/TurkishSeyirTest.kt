package com.udl.oud.turkish

import com.udl.oud.tuner.Ahenk
import com.udl.oud.tuner.Note
import com.udl.oud.tuner.OudType
import com.udl.oud.tuner.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishSeyirTest {
    private val a4 = 440.0
    private fun makam(name: String) = TurkishMakams.named(name) ?: error(name)
    private fun perde(tone: TurkishTone) = Perdes.of(tone.written) ?: tone.spelling.label()
    private fun perdes(tones: List<TurkishTone>) = tones.joinToString(" ", transform = ::perde)

    @Test fun everyMakamHasASeyirWithKnownGeckis() {
        TurkishMakams.all.forEach { m ->
            val seyir = TurkishSeyirs.of(m)
            seyir.gecki.forEach { assertNotNull("${m.turkishName} → ${it.makam}", TurkishMakams.named(it.makam)) }
            assertTrue(m.turkishName, TurkishSeyirs.kararlar(m).isNotEmpty())
        }
    }

    @Test fun pillarsOfTheCoreMakams() {
        assertEquals("Nevâ", perde(TurkishScale.guclu(makam("Rast"))))
        assertEquals("Irak", perde(TurkishScale.yeden(makam("Rast"))))
        assertEquals("Rast", perde(TurkishScale.yeden(makam("Uşşak"))))
        assertEquals("Hüseynî", perde(TurkishScale.guclu(makam("Bûselik"))))
        assertEquals("Nim zirgüle", perde(TurkishScale.yeden(makam("Bûselik"))))
        assertEquals("Çârgâh", perde(TurkishScale.guclu(makam("Saba"))))
        assertEquals("Dügâh", perde(TurkishScale.yeden(makam("Segâh"))))
        assertEquals("Bûselik", perde(TurkishScale.yeden(makam("Çârgâh"))))
    }

    @Test fun yedenIsWrittenOnTheLetterBelow() {
        assertEquals("Фа♯⁴", TurkishScale.yeden(makam("Rast")).spelling.label()) // Irak
        assertEquals("Соль♯⁴", TurkishScale.yeden(makam("Bûselik")).spelling.label()) // Nim zirgüle
    }

    @Test fun rastGoesUpThroughEvicAndDownThroughAcem() {
        val rast = makam("Rast")
        assertEquals("Rast Dügâh Segâh Çârgâh Nevâ Hüseynî Eviç Gerdâniye", perdes(TurkishScale.ascending(rast)))
        assertEquals("Gerdâniye Acem Hüseynî Nevâ Çârgâh Segâh Dügâh Rast", perdes(TurkishScale.descending(rast)))
        assertEquals("Фа", TurkishScale.tone(rast, 6, down = true).spelling.label())
    }

    @Test fun huseyniAndHicazDescendThroughAcem() {
        assertEquals("Acem", perde(TurkishScale.tone(makam("Hüseynî"), 5, down = true)))
        assertEquals("Acem", perde(TurkishScale.tone(makam("Hicaz"), 5, down = true)))
    }

    @Test fun kararTakesTheIniciFormOnlyOnTheWayDown() {
        val nihavend = makam("Nihâvend")
        val karar = TurkishSeyirs.kararlar(nihavend).first { it.name == "Спуск через Acem" }
        assertEquals("Gerdâniye Acem Hisar Nevâ Kürdî Dügâh Rast", perdes(TurkishScale.phrase(nihavend, karar)))
        val rast = makam("Rast")
        val viaIrak = TurkishSeyirs.kararlar(rast).first { it.name == "Через вводный Irak" }
        assertEquals("Segâh Dügâh Rast Irak Rast", perdes(TurkishScale.phrase(rast, viaIrak)))
    }

    @Test fun hicazkarIsZirguleliHicazOnRastWithItsKeySignature() {
        val hicazkar = makam("Hicazkâr")
        assertEquals(hicazkar, TurkishMakams.namedSed(makam("Zirgüleli Hicaz"), hicazkar.tonic))
        // Segâh koma flat, Zirgüle and Hisar bakiye flats, Eviç bakiye sharp.
        assertEquals("Rast Zirgüle Segâh Çârgâh Nevâ Hisar Eviç", perdes(TurkishScale.degreeTones(hicazkar, false)))
        assertEquals("Соль Ля♭⁴ Си♭¹ До Ре Ми♭⁴ Фа♯⁴", hicazkar.degrees().joinToString(" ") { it.spelling.label() })
        assertEquals("Irak", perde(TurkishScale.yeden(hicazkar)))
    }

    @Test fun sedarabanTakesBakiyeFlatsOnBAndEAndSharpsOnFAndC() =
        assertEquals("Ре Ми♭⁴ Фа♯⁴ Соль Ля Си♭⁴ До♯⁴", makam("Şedaraban").degrees().joinToString(" ") { it.spelling.label() })

    @Test fun zirguleliHicazIsNamedAfterItsYeden() = assertEquals("Nim zirgüle", perde(TurkishScale.yeden(makam("Zirgüleli Hicaz"))))

    @Test fun descendingSedPillars() {
        assertEquals("Gerdâniye", perde(TurkishScale.guclu(makam("Kürdîlihicazkâr"))))
        assertEquals("Acem aşîran", perde(TurkishScale.yeden(makam("Kürdîlihicazkâr"))))
        assertEquals("Acem", perde(TurkishScale.guclu(makam("Acemaşîran"))))
        assertEquals("Hüseynî aşîran", perde(TurkishScale.yeden(makam("Acemaşîran"))))
    }

    @Test fun anyMakamMovesToAnotherDurakKeepingItsSteps() {
        val ussak = makam("Uşşak")
        val onRast = ussak.onDurak(TurkishMakams.duraks.first { Perdes.of(it) == "Rast" })
        assertEquals(ussak.intervals, onRast.intervals)
        assertEquals("Соль Ля♭¹ Си♭⁵ До Ре Ми♭⁵ Фа", onRast.degrees().joinToString(" ") { it.spelling.label() })
        // The seyir moves with it: the yeden is still a whole tone under the durak.
        assertEquals("Фа", TurkishScale.yeden(onRast).spelling.label())
    }

    @Test fun ahenkLowersTheSoundButNotTheNotation() {
        val rast = TurkishScale.tone(makam("Rast"), 0)
        assertEquals(Note(48).frequency(a4), rast.frequency(a4, Ahenk.Supurde), 1e-6) // C3, a tone under D3
        assertEquals(Note(43).frequency(a4), rast.frequency(a4, Ahenk.Mansur), 1e-6) // G2, a fifth under D3
        assertEquals("До", rast.spelling.sounding(rast.written, Ahenk.Supurde).label())
        assertEquals("Ля", rast.spelling.sounding(rast.written, Ahenk.Kiz).label())
        assertEquals("Соль", rast.spelling.label())
    }

    @Test fun segahSoundsAKomaUnderEInSupurde() {
        val segah = TurkishScale.tone(makam("Segâh"), 0)
        assertEquals("Ми♭¹", segah.spelling.sounding(segah.written, Ahenk.Supurde).label())
    }

    @Test fun ahenkRetunesTheTurkishOudOnly() {
        assertEquals(
            "C2 E2 A2 D3 G3 C4",
            Tunings.forOud(OudType.Turkish, Ahenk.Supurde)[1].courses.joinToString(" ") { "${it.latin}${it.octave}" },
        )
        assertEquals(Tunings.arabic, Tunings.forOud(OudType.Arabic, Ahenk.Mansur))
    }

    @Test fun inAnyAhenkTheUssakDurakIsStillTheOpenFourthCourse() {
        val ussak = makam("Uşşak")
        val durak = TurkishScale.tone(ussak, 0)
        Ahenk.entries.forEach { ahenk ->
            val tuning = Tunings.forOud(OudType.Turkish, ahenk).first()
            val marks = TurkishBoard.marks(TurkishScale.degreeTones(ussak, false), tuning, a4, ahenk, 700)
            assertTrue(ahenk.name, marks.any { it.course == 3 && it.cents == 0.0 && it.tone == durak })
        }
    }
}
