package com.udl.oud.risha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RishaAnalysisTest {
    @Test fun evenTremoloIsFastAndEven() {
        val run = (0 until 20).map { RishaStroke(1000.0 + it * 125.0, 0.2) }
        val t = RishaAnalysis.tremolo(run)!!
        assertEquals(8.0, t.perSecond, 1e-9)
        assertEquals(100, t.evenness)
        assertEquals(19, t.intervals.size)
    }

    @Test fun onlyTheLastRunCounts() {
        val first = (0 until 10).map { RishaStroke(it * 100.0, 0.2) }
        val second = (0 until 8).map { RishaStroke(5000.0 + it * 200.0 + if (it % 2 == 0) 0.0 else 30.0, 0.2) }
        val t = RishaAnalysis.tremolo(first + second)!!
        assertEquals(8, t.strokes)
        assertTrue("${t.evenness}", t.evenness < 90)
        assertNull(RishaAnalysis.tremolo(first.take(4)))
    }

    @Test fun gridTellsDownFromUp() {
        val step = 250.0
        // Down strokes on time and loud, up strokes 20 ms late and quieter (half the level ≈ −6 dB).
        val strokes = (0 until 8).map { i -> RishaStroke(i * step + if (i % 2 == 1) 20.0 else 0.0, if (i % 2 == 1) 0.1 else 0.2) }
        val g = RishaAnalysis.grid(strokes, step, 0.0, 8 * step)
        assertEquals(8, g.expected)
        assertEquals(8, g.onTime)
        assertEquals(0, g.missed)
        assertEquals(20.0, g.upMs - g.downMs, 1e-9)
        assertEquals(-6.02, g.upLevelDb, 0.01)
    }

    @Test fun gridCountsMissedAndExtra() {
        val step = 250.0
        val strokes = listOf(0, 1, 3, 4, 5, 6, 7).map { RishaStroke(it * step, 0.2) } + RishaStroke(2 * step + 125.0, 0.2)
        val g = RishaAnalysis.grid(strokes, step, 0.0, 8 * step)
        assertEquals(1, g.missed)
        assertEquals(1, g.extra)
        assertNull(g.errors[2])
    }
}
