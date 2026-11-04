package com.udl.oud.rhythm

import com.udl.oud.audio.Drum
import com.udl.oud.audio.IqaGroup
import com.udl.oud.audio.Iqaat
import com.udl.oud.audio.OnsetDetector
import com.udl.oud.audio.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmCheckTest {
    private val maqsum = Iqaat.all.first { it.latin == "Maqsum" }
    private val bpm = 100 // eighth = 300 ms

    /** Hits on every dum and tak of cycle [c], each [shift] ms off. */
    private fun perfect(c: Int, shift: Double = 0.0) = maqsum.strokes.withIndex()
        .filter { it.value == Stroke.Dum || it.value == Stroke.Tak }
        .map { (i, s) -> Hit((c * maqsum.strokes.size + i) * maqsum.stepMs(bpm) + shift, s) }

    @Test fun libraryHasSixteenRhythmsInTwoGroups() {
        assertEquals(16, Iqaat.all.size)
        assertEquals(10, Iqaat.all.count { it.group == IqaGroup.Main })
        assertEquals(Iqaat.all.size, Iqaat.all.map { it.latin }.distinct().size)
        // Every cycle starts on a stroke, and the odd meters spell their groups.
        Iqaat.all.forEach { assertTrue(it.name, it.strokes.first() != Stroke.Rest) }
        assertEquals(listOf(0, 2, 4, 6), Iqaat.all.first { it.latin == "Aksak" }.groupStarts.sorted())
    }

    @Test fun meterMatchesTheCycle() {
        Iqaat.all.forEach { iqa ->
            val (top, bottom) = iqa.meter.split("/").map(String::toInt)
            // Beats are quarters (♩) or eighths (♪): the meter's numerator counted in that unit.
            val beats = if (iqa.beatUnit == "♪") top * 8 / bottom else top * 4 / bottom
            assertEquals(iqa.name, beats, iqa.beats)
        }
    }

    @Test fun perfectCycleIsAllGood() {
        val r = RhythmCheck.evaluate(maqsum, bpm, 2, perfect(2, 10.0))
        assertEquals(5, r.strokes.size)
        assertTrue(r.strokes.all { it.kind == HitKind.Good })
        assertEquals(0, r.extra)
    }

    @Test fun lateMissingAndWrong() {
        val hits = perfect(1).toMutableList()
        hits[0] = hits[0].copy(atMs = hits[0].atMs + 70) // late dum
        hits.removeAt(2) // second tek missing
        hits[3] = hits[3].copy(stroke = Stroke.Dum) // last tek played as a dum
        hits += Hit(1 * 8 * 300.0 + 5 * 300.0, Stroke.Tak) // a stroke on a rest
        val r = RhythmCheck.evaluate(maqsum, bpm, 1, hits)
        assertEquals(HitKind.Ok, r.strokes[0].kind)
        assertEquals(70.0, r.strokes[0].errorMs!!, 1e-9)
        assertEquals(HitKind.Missed, r.strokes[2].kind)
        assertEquals(HitKind.WrongStroke, r.strokes[4].kind)
        assertEquals(1, r.extra)
    }

    @Test fun earlyFirstStrokeBelongsToItsCycle() {
        val hits = perfect(3, -40.0)
        assertEquals(5, RhythmCheck.evaluate(maqsum, bpm, 3, hits).onTime)
        assertEquals(0, RhythmCheck.evaluate(maqsum, bpm, 2, hits).extra)
    }

    @Test fun lightTaksMayBePlayedOrNot() {
        val ayoub = Iqaat.all.first { it.latin == "Ayoub" }
        val step = ayoub.stepMs(bpm)
        val main = listOf(0, 4, 6).map { Hit(it * step) }
        assertEquals(0, RhythmCheck.evaluate(ayoub, bpm, 0, main).extra)
        assertEquals(0, RhythmCheck.evaluate(ayoub, bpm, 0, main + Hit(3 * step)).extra)
        assertEquals(3, RhythmCheck.evaluate(ayoub, bpm, 0, main).onTime)
    }

    @Test fun statsAddUp() {
        val a = RhythmCheck.evaluate(maqsum, bpm, 1, perfect(1, 20.0))
        val b = RhythmCheck.evaluate(maqsum, bpm, 2, perfect(2, -20.0).drop(1))
        val s = RhythmStats() + a + b
        assertEquals(10, s.strokes)
        assertEquals(9, s.onTime)
        assertEquals(1, s.missed)
        assertEquals((5 * 20.0 - 4 * 20.0) / 9, s.meanError, 1e-9)
    }

    @Test fun calibrationIsTheMedianLag() {
        val clicks = (0..13).map { it * 666.7 }
        val hits = clicks.drop(2).mapIndexed { i, c -> c + 60 + (i % 3 - 1) * 10 }
        assertEquals(60.0, RhythmCheck.latency(clicks, hits)!!, 10.0)
        assertEquals(null, RhythmCheck.latency(clicks, hits.take(2)))
    }

    @Test fun loopHoldsFourCyclesAndTheDrumOnlyWhereAsked() {
        val pcm = Drum.render(maqsum, 120, 4, drumIn = { it == 0 }, sampleRate = 8000)
        val cycle = pcm.size / 4
        fun loud(range: IntRange) = range.maxOf { kotlin.math.abs(pcm[it].toInt()) }
        assertTrue(loud(0 until cycle / 8) > 1000)
        assertTrue(loud(cycle * 2 until cycle * 3) == 0)
    }

    @Test fun onsetsAreStrokesNotTheirRinging() {
        val d = OnsetDetector()
        val frames = List(30) { 0.001 } + 0.3 + List(10) { 0.25 - it * 0.02 } + List(5) { 0.02 } + 0.4 + List(5) { 0.3 }
        assertEquals(2, frames.count { d.feed(it) })
    }
}
