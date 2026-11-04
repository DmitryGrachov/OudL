package com.udl.oud.fretboard

import com.udl.oud.turkish.TurkishMakams
import com.udl.oud.turkish.TurkishScale
import org.junit.Assert.assertEquals
import org.junit.Test

class StaffTest {
    @Test fun rastC3IsWrittenAsMiddleCWithOneLedgerLine() {
        val note = StaffNote.arabic(96) // C3, the Rast of a C–C oud
        assertEquals(StaffNote(0, 4, Accidental.None), note)
        assertEquals(-2, note.position)
        assertEquals(listOf(-2), note.ledgerLines)
    }

    @Test fun sikahSitsOnTheBottomLineWithAHalfFlat() =
        assertEquals(StaffNote(2, 4, Accidental.HalfFlat), StaffNote.arabic(103)) // E½♭3

    @Test fun usualAccidentalsAndAMaqamSpelling() {
        assertEquals(Accidental.Sharp, StaffNote.arabic(108).accidental) // F♯3
        assertEquals(Accidental.Flat, StaffNote.arabic(116).accidental) // B♭3
        // In a maqam the same pitch can be spelled from the other side: G♭ instead of F♯.
        assertEquals(StaffNote(4, 4, Accidental.Flat), StaffNote.arabic(108, Spelling(4, -2)))
    }

    @Test fun lowCourseNeedsLedgerLinesBelow() {
        val note = StaffNote.arabic(72) // C2 sounding, C3 written
        assertEquals(-9, note.position)
        assertEquals(listOf(-2, -4, -6, -8), note.ledgerLines)
    }

    @Test fun highNotesGetLedgerLinesAbove() =
        assertEquals(listOf(10, 12, 14), StaffNote(2, 6, Accidental.None).ledgerLines) // E6 sits on the third

    @Test fun turkishNotationKeepsWrittenPitchAndKomaCount() {
        val rast = TurkishMakams.all.first { it.turkishName == "Rast" }
        assertEquals(StaffNote(4, 4, Accidental.None), TurkishScale.tone(rast, 0).staffNote()) // G4 on the G line
        assertEquals(StaffNote(6, 4, Accidental.Flat, komas = 1), TurkishScale.tone(rast, 2).staffNote()) // Segâh B♭¹
        assertEquals(StaffNote(3, 5, Accidental.Sharp, komas = 4), TurkishScale.tone(rast, 6).staffNote()) // Eviç F♯⁴
    }
}
