package com.udl.oud.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoTest {
    @Test fun oneTapIsNotATempo() = assertNull(tapTempo(listOf(1000L)))

    @Test fun evenTapsGiveTheirTempo() = assertEquals(120, tapTempo(listOf(0L, 500L, 1000L, 1500L)))

    @Test fun unevenTapsAverageOut() = assertEquals(100, tapTempo(listOf(0L, 550L, 1150L, 1800L)))
}
