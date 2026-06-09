package com.pauta.app.domain

import com.pauta.app.domain.FocusMath.FocusSeg
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusMathTest {

    @Test fun blockElapsedSumsSessionsAndCountsRunningOneToNow() {
        val sessions = listOf(
            FocusSeg(0, 1_000),        // 1s closed
            FocusSeg(2_000, null),     // running
        )
        assertEquals(1_000L + 3_000L, FocusMath.blockElapsedMs(sessions, now = 5_000))
    }

    @Test fun fmtTimer() {
        assertEquals("00:00", FocusMath.fmtTimer(0))
        assertEquals("01:05", FocusMath.fmtTimer(65_000))
        assertEquals("1:02:05", FocusMath.fmtTimer(3_725_000)) // 1h 2m 5s
    }

    @Test fun fmtDuration() {
        assertEquals("45 min", FocusMath.fmtDuration(45 * 60_000L))
        assertEquals("1h02", FocusMath.fmtDuration(3_725_000))   // 1h 2m
        assertEquals("0 min", FocusMath.fmtDuration(0))
    }

    @Test fun dailyFocusOnlyCountsSegmentsStartingThatDay() {
        val base = DateUtils.startOfDayMs("2024-06-15")
        val nextDay = DateUtils.startOfDayMs("2024-06-16")
        val segments = listOf(
            FocusSeg(base, base + 1_000),          // counts (06-15)
            FocusSeg(base + 5_000, base + 7_000),  // counts (06-15)
            FocusSeg(nextDay, nextDay + 9_000),    // different day → excluded
        )
        assertEquals(3_000L, FocusMath.dailyFocusMs(segments, "2024-06-15", now = nextDay + 9_000))
    }
}
