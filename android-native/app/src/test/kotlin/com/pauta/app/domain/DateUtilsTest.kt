package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure date-math tests for [DateUtils]. Deterministic across time zones: every
 * key is parsed/formatted in the device's local calendar consistently, and the
 * arithmetic stays within local dates.
 */
class DateUtilsTest {

    @Test
    fun addDays_acrossMonthAndYearBoundaries() {
        assertEquals("2025-02-01", DateUtils.addDays("2025-01-31", 1))
        assertEquals("2025-01-01", DateUtils.addDays("2024-12-31", 1))
        assertEquals("2024-12-31", DateUtils.addDays("2025-01-01", -1))
    }

    @Test
    fun weekStart_isMondayBased() {
        // May 2025: 12/19/26 are Mondays. Wed 5/14, Sun 5/18 and Mon 5/12 → 5/12.
        assertEquals("2025-05-12", DateUtils.weekStart("2025-05-14")) // Wednesday
        assertEquals("2025-05-12", DateUtils.weekStart("2025-05-18")) // Sunday (end of week)
        assertEquals("2025-05-12", DateUtils.weekStart("2025-05-12")) // Monday itself
    }

    @Test
    fun weekdayOf_isZeroMondayToSixSunday() {
        assertEquals(0, DateUtils.weekdayOf("2025-05-12")) // Monday
        assertEquals(2, DateUtils.weekdayOf("2025-05-14")) // Wednesday
        assertEquals(6, DateUtils.weekdayOf("2025-05-18")) // Sunday
    }

    @Test
    fun monthBoundaries_handleLeapFebruary() {
        assertEquals("2024-02-01", DateUtils.monthStart("2024-02-15"))
        assertEquals("2024-02-29", DateUtils.monthEnd("2024-02-15")) // leap year
        assertEquals(29, DateUtils.daysInMonth("2024-02-10"))
        assertEquals(28, DateUtils.daysInMonth("2025-02-10"))
    }

    @Test
    fun yearMonth_dayOfMonth_monthKey() {
        assertEquals(2025 to 5, DateUtils.yearMonth("2025-05-14"))
        assertEquals(14, DateUtils.dayOfMonth("2025-05-14"))
        assertEquals("2025-05-01", DateUtils.monthKey(2025, 5))
    }

    @Test
    fun prevMonth_nextMonth_wrapYear() {
        assertEquals(2024 to 12, DateUtils.prevMonth(2025, 1))
        assertEquals(2025 to 1, DateUtils.nextMonth(2024, 12))
        assertEquals(2025 to 4, DateUtils.prevMonth(2025, 5))
    }

    @Test
    fun currentQuarter_format() {
        assertTrue(DateUtils.currentQuarter().matches(Regex("""\d{4}-Q[1-4]""")))
    }

    @Test
    fun formatDuration() {
        assertEquals("01:30", DateUtils.formatDuration(90_000))
        assertEquals("1:01:01", DateUtils.formatDuration(3_661_000))
    }

    @Test
    fun formatMinutes() {
        assertEquals("45m", DateUtils.formatMinutes(45))
        assertEquals("1h", DateUtils.formatMinutes(60))
        assertEquals("1h 30m", DateUtils.formatMinutes(90))
    }

    @Test
    fun keyRoundTrip() {
        assertEquals("2025-05-14", DateUtils.keyFromMs(DateUtils.msFromKey("2025-05-14")))
    }
}
