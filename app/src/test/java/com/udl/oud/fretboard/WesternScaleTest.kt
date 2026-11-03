package com.udl.oud.fretboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WesternScaleTest {
    private fun scale(name: String) = WesternScales.all.first { it.name == name }

    private fun spelled(name: String, tonic: Int? = null, descending: Boolean = false): String {
        val s = scale(name)
        return s.notes(tonic ?: s.tonic, descending).joinToString(" ") { it.spelling.label(NoteLabels.Solfege) }
    }

    @Test fun cMajor() = assertEquals("До Ре Ми Фа Соль Ля Си", spelled("Мажор"))

    @Test fun majorOnFHasBFlat() = assertEquals("Фа Соль Ля Си♭ До Ре Ми", spelled("Мажор", tonic = 106))

    @Test fun majorOnDHasFSharpAndCSharp() = assertEquals("Ре Ми Фа♯ Соль Ля Си До♯", spelled("Мажор", tonic = 100))

    @Test fun aNaturalMinor() = assertEquals("Ля Си До Ре Ми Фа Соль", spelled("Минор"))

    @Test fun harmonicMinorRaisesTheSeventh() = assertEquals("Ля Си До Ре Ми Фа Соль♯", spelled("Гармонический минор"))

    @Test fun melodicMinorGoesUpRaisedAndComesDownNatural() {
        assertEquals("Ля Си До Ре Ми Фа♯ Соль♯", spelled("Мелодический минор"))
        assertEquals("Ля Си До Ре Ми Фа Соль", spelled("Мелодический минор", descending = true))
        assertTrue(scale("Мелодический минор").hasDescent)
    }

    @Test fun modesOnTheirWhiteKeyTonics() {
        assertEquals("Ре Ми Фа Соль Ля Си До", spelled("Дорийский"))
        assertEquals("Ми Фа Соль Ля Си До Ре", spelled("Фригийский"))
        assertEquals("Фа Соль Ля Си До Ре Ми", spelled("Лидийский"))
        assertEquals("Соль Ля Си До Ре Ми Фа", spelled("Миксолидийский"))
        assertEquals("Си До Ре Ми Фа Соль Ля", spelled("Локрийский"))
    }

    @Test fun pentatonicsSkipLetters() {
        assertEquals("До Ре Ми Соль Ля", spelled("Пентатоника мажор"))
        assertEquals("Ля До Ре Ми Соль", spelled("Пентатоника минор"))
        assertEquals("Соль Ля Си Ре Ми", spelled("Пентатоника мажор", tonic = 110))
    }

    @Test fun westernScalesHaveNoQuarterTones() {
        WesternScales.all.forEach { s -> assertTrue(s.name, s.notes().all { it.offset % 2 == 0 }) }
    }
}
