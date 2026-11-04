package com.udl.oud.taqsim

import com.udl.oud.fretboard.Maqamat
import org.junit.Assert.assertTrue
import org.junit.Test

class AirAndCellTest {
    private val seeds = 0L until 60L
    private fun all(name: String, length: TaqsimLength = TaqsimLength.Medium) =
        seeds.map { TaqsimGenerator(it).generate(Maqamat.all.first { m -> m.name == name }, length) }

    @Test fun longPhrasesSometimesBreatheOnARestingDegree() {
        val phrases = all("Баяти", TaqsimLength.Long).flatMap { it.phrases }
        val breathing = phrases.filter { p -> p.notes.any { it.pauseAfter > 0 } }
        assertTrue("${breathing.size}", breathing.size >= 20)
        breathing.forEach { p ->
            val i = p.notes.indexOfFirst { it.pauseAfter > 0 }
            val rests = JinsGrammars.of(p.station.jins).rests.map { it + p.station.jinsRoot }
            assertTrue(p.notes[i].degree in rests)
            assertTrue("in the middle", i in 1 until p.notes.lastIndex)
            assertTrue(p.how.contains("дыхани"))
        }
    }

    @Test fun someQuestionsBreakOffAndHang() {
        val questions = all("Раст").flatMap { t -> t.phrases.filter { it.role == PhraseRole.Question } }
        val broken = questions.filter { it.how.contains("обрывается") }
        assertTrue("${broken.size} of ${questions.size}", broken.size in questions.size / 6..questions.size / 2)
        broken.forEach { p ->
            assertTrue(p.notes.last().beats < 1.7)
            assertTrue(p.restBeats >= 2.6)
        }
    }

    @Test fun aBreathIsHeldBeforeThePeak() {
        all("Хиджаз").forEach { t ->
            val k = t.phrases.indexOfFirst { it.section == SectionKind.Peak }
            if (k > 0) {
                val before = t.phrases[k - 1]
                assertTrue(before.how.contains("кульминацией"))
                assertTrue(before.restBeats >= 2.5)
            }
        }
    }

    @Test fun neighboursPassACellAlong() {
        val taqsims = all("Раст", TaqsimLength.Long)
        val calls = taqsims.flatMap { it.phrases }.count { it.how.contains("перекличка") }
        assertTrue("$calls", calls >= seeds.count() / 2)
        // Where the cell is passed on a step lower, the new phrase really holds that shape.
        taqsims.forEach { t ->
            t.phrases.forEachIndexed { k, b ->
                if (!b.how.contains("ступенью ниже")) return@forEachIndexed
                // The cell comes from the phrase that started the exchange, maybe two back.
                val source = (k - 1 downTo 0).map { t.phrases[it] }.first { !it.how.contains("перекличка") }
                val cell = source.notes.subList(1, 4).map { it.degree - 1 }
                val degrees = b.notes.map { it.degree }
                assertTrue("$cell in $degrees", degrees.windowed(3).any { it == cell })
            }
        }
    }

    @Test fun pausesAreCountedInTheLength() {
        val t = all("Баяти", TaqsimLength.Long).first { tq -> tq.notes.any { it.pauseAfter > 0 } }
        val p = t.phrases.first { ph -> ph.notes.any { it.pauseAfter > 0 } }
        assertTrue(p.beats > p.notes.sumOf { it.beats } + p.restBeats)
    }
}
