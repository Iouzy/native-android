package com.pauta.app.domain

import com.pauta.app.domain.BookMath.SessionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookMathTest {

    private val hour = 3_600_000L

    // ── pagesPerHour ──────────────────────────────────────────

    @Test fun paceNullWithNoSessions() {
        assertNull(BookMath.pagesPerHour(emptyList()))
    }

    @Test fun paceNullWithOneSession() {
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(30, hour))))
    }

    @Test fun paceIsOverallRateNotPerSessionAverage() {
        // 30 pages in 1h + 30 pages in 3h = 60 pages / 4h = 15/h (a naive
        // per-session average would say 20/h). // PT: ritmo global, não média.
        val pace = BookMath.pagesPerHour(listOf(SessionSpan(30, hour), SessionSpan(30, 3 * hour)))!!
        assertEquals(15f, pace, 0.001f)
    }

    @Test fun paceMixedDurationsAndDeltas() {
        // 10 in 30min + 25 in 90min = 35 pages / 2h = 17.5/h.
        val pace = BookMath.pagesPerHour(listOf(SessionSpan(10, hour / 2), SessionSpan(25, hour * 3 / 2)))!!
        assertEquals(17.5f, pace, 0.001f)
    }

    @Test fun paceIgnoresZeroDurationSessionsWhenCounting() {
        // The zero-duration span isn't a usable data point, so only one remains.
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(30, hour), SessionSpan(10, 0))))
    }

    @Test fun paceNullWhenNothingWasRead() {
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(0, hour), SessionSpan(0, hour))))
    }

    // ── etaDays ───────────────────────────────────────────────

    @Test fun etaZeroWhenNothingRemains() {
        assertEquals(0, BookMath.etaDays(remaining = 0, pagesPerHour = 20f))
        assertEquals(0, BookMath.etaDays(remaining = -5, pagesPerHour = 20f))
    }

    @Test fun etaNullWithoutAPace() {
        assertNull(BookMath.etaDays(remaining = 100, pagesPerHour = 0f))
        assertNull(BookMath.etaDays(remaining = 100, pagesPerHour = -1f))
    }

    @Test fun etaRoundsUpToWholeDays() {
        // 100 pages at 20/h × 1h/day = 5 days exactly; 101 pages tips to 6.
        assertEquals(5, BookMath.etaDays(remaining = 100, pagesPerHour = 20f))
        assertEquals(6, BookMath.etaDays(remaining = 101, pagesPerHour = 20f))
    }

    @Test fun etaScalesWithDailyReadingBudget() {
        // Half an hour a day halves the daily pages, doubling the days.
        assertEquals(10, BookMath.etaDays(remaining = 100, pagesPerHour = 20f, dailyReadingMinutes = 30f))
    }
}
