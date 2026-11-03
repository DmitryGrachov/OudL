package com.udl.oud.fretboard

import org.junit.Assert.assertEquals
import org.junit.Test

class MaqamTest {
    private fun maqam(name: String) = Maqamat.all.first { it.name == name }

    private fun spelled(name: String, tonic: Int? = null, style: NoteLabels = NoteLabels.Solfege): String {
        val m = maqam(name)
        return m.notes(tonic ?: m.tonic).joinToString(" ") { it.spelling.label(style) }
    }

    @Test fun everyMaqamSpansAnOctave() {
        // The Maqam constructor checks this and the ajnas; building the list must not throw.
        assertEquals(8, Maqamat.all.size)
    }

    @Test fun rastHasHalfFlatThirdAndSeventh() =
        assertEquals("До Ре Ми½♭ Фа Соль Ля Си½♭", spelled("Раст"))

    @Test fun bayatiStartsWithTwoThreeQuarterTones() =
        assertEquals("Ре Ми½♭ Фа Соль Ля Си♭ До", spelled("Баяти"))

    @Test fun hijazIsSpelledWithEFlatNotDSharp() =
        assertEquals("Ре Ми♭ Фа♯ Соль Ля Си½♭ До", spelled("Хиджаз"))

    @Test fun sabaHasGFlat() =
        assertEquals("Ре Ми½♭ Фа Соль♭ Ля Си♭ До", spelled("Саба"))

    @Test fun nahawandAscendsWithHijazOnTheFifth() =
        assertEquals("C D E♭ F G A♭ B", spelled("Нахаванд", style = NoteLabels.Latin))

    @Test fun kurd() = assertEquals("Ре Ми♭ Фа Соль Ля Си♭ До", spelled("Курд"))

    @Test fun ajamOnBFlatIsAMajorScale() = assertEquals("Си♭ До Ре Ми♭ Фа Соль Ля", spelled("Аджам"))

    @Test fun sikahStartsOnEHalfFlat() = assertEquals("Ми½♭ Фа Соль Ля Си½♭ До Ре", spelled("Сикях"))

    @Test fun transposedHijazOnG() =
        assertEquals("Соль Ля♭ Си До Ре Ми½♭ Фа", spelled("Хиджаз", tonic = 110))

    @Test fun transposedRastOnG() =
        assertEquals("Соль Ля Си½♭ До Ре Ми Фа½♯", spelled("Раст", tonic = 110))

    private fun descending(name: String): String {
        val m = maqam(name)
        return m.notes(m.tonic, descending = true).joinToString(" ") { it.spelling.label(NoteLabels.Solfege) }
    }

    @Test fun rastComesDownThroughBFlat() {
        assertEquals("До Ре Ми½♭ Фа Соль Ля Си♭", descending("Раст"))
        assertEquals("Нахаванд", maqam("Раст").upperJins(descending = true).name)
    }

    @Test fun hijazComesDownThroughBFlat() = assertEquals("Ре Ми♭ Фа♯ Соль Ля Си♭ До", descending("Хиджаз"))

    @Test fun nahawandComesDownWithKurdOnTheFifth() {
        assertEquals("До Ре Ми♭ Фа Соль Ля♭ Си♭", descending("Нахаванд"))
        assertEquals("Курд", maqam("Нахаванд").upperJins(descending = true).name)
    }

    @Test fun bayatiComesDownTheWayItGoesUp() = assertEquals(spelled("Баяти"), descending("Баяти"))

    @Test fun ghammazStartsTheUpperJins() {
        val bayati = maqam("Баяти").notes()
        assertEquals(listOf(false, false, false, true, true, true, true), bayati.map { it.upper })
    }

    @Test fun intervalsReadAsTones() {
        assertEquals("1 ¾ ¾ 1 1 ¾ ¾", maqam("Раст").intervals.joinToString(" ") { intervalLabel(it) })
        assertEquals("½ 1½ ½ 1 ¾ ¾ 1", maqam("Хиджаз").intervals.joinToString(" ") { intervalLabel(it) })
    }
}
