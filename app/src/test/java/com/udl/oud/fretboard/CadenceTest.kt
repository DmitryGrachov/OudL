package com.udl.oud.fretboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {
    private fun maqam(name: String) = Maqamat.all.first { it.name == name }

    /** Names as the app shows them: each note spelled as the maqam degree it is. */
    private fun spelled(maqamName: String, cadenceName: String): String {
        val m = maqam(maqamName)
        val byClass = m.notes().associateBy { (m.tonic + it.offset).mod(24) }
        val cadence = Cadences.of(m).first { it.name == cadenceName }
        return cadence.steps(m, m.tonic).joinToString(" ") { byClass.getValue(it.mod(24)).spelling.label(NoteLabels.Solfege) }
    }

    @Test fun everyMaqamHasCadences() = Maqamat.all.forEach { assertTrue(it.name, Cadences.of(it).isNotEmpty()) }

    @Test fun hijazkarComesDownThroughDFlat() = assertEquals("Фа Ми Ре♭ До", spelled("Хиджазкар", "Спуск через Ре♭"))

    @Test fun suznakComesDownThroughHijazOnG() =
        assertEquals("Си Ля♭ Соль Ми½♭ Ре До", spelled("Сузнак", "Через Хиджаз на Соль"))

    @Test fun iraqRestsOnBHalfFlat() = assertEquals("Ре До Си½♭", spelled("Ирак", "Сверху на тонику"))

    @Test fun everyCadenceEndsOnTheTonicAndStaysNearIt() {
        Cadences.all.forEach { (name, cadences) ->
            val m = maqam(name)
            cadences.forEach { c ->
                val steps = c.steps(m, m.tonic)
                assertEquals("${c.name} in $name", m.tonic, steps.last())
                assertTrue("${c.name} in $name", steps.all { it in m.tonic - 12..m.tonic + 24 })
            }
        }
    }

    @Test fun rastDescends() = assertEquals("Соль Фа Ми½♭ Ре До", spelled("Раст", "Спуск по Расту"))

    @Test fun rastLeansOnTheHalfFlatSeventhBelow() {
        val m = maqam("Раст")
        val steps = Cadences.of(m).first { it.name == "Через нижний Си½♭" }.steps(m, m.tonic)
        assertEquals(93, steps[4]) // B½♭2, under C3
    }

    @Test fun hijazTakesTheAugmentedSecond() = assertEquals("Соль Фа♯ Ми♭ Ре", spelled("Хиджаз", "Спуск через полтора тона"))

    @Test fun sabaGoesThroughGFlat() = assertEquals("Соль♭ Фа Ми½♭ Ре", spelled("Саба", "Через Соль♭"))

    @Test fun nahawandLeadingNoteIsBNatural() = assertEquals("Ми♭ Ре До Си До", spelled("Нахаванд", "С вводным Си"))

    @Test fun cadenceFollowsAMovedTonic() {
        val m = maqam("Баяти")
        val cadence = Cadences.of(m).first()
        val onD = cadence.steps(m, m.tonic)
        val onE = cadence.steps(m, m.tonic + 4)
        assertEquals(onD.map { it + 4 }, onE)
    }
}
