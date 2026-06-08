package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM tests for the date/cadence math. These run under
 *  `:app:testDebugUnitTest` and gate the APK build. */
class DateUtilsTest {

    @Test fun addDays_handlesLeapYearForward() {
        assertEquals("2024-02-29", DateUtils.addDays("2024-02-28", 1))
        assertEquals("2024-03-01", DateUtils.addDays("2024-02-29", 1))
    }

    @Test fun addDays_handlesLeapYearBackward() {
        assertEquals("2024-02-29", DateUtils.addDays("2024-03-01", -1))
    }

    @Test fun daysBetweenInclusive() {
        assertEquals(1, DateUtils.daysBetweenInclusive("2024-01-01", "2024-01-01"))
        assertEquals(3, DateUtils.daysBetweenInclusive("2024-01-01", "2024-01-03"))
    }

    @Test fun weekIsMondayToSunday() {
        // 2024-01-10 is a Wednesday.
        assertEquals("2024-01-08", DateUtils.weekStart("2024-01-10"))
        assertEquals("2024-01-14", DateUtils.weekEnd("2024-01-10"))
        // Monday stays its own week start; Sunday its own week end.
        assertEquals("2024-01-08", DateUtils.weekStart("2024-01-08"))
        assertEquals("2024-01-14", DateUtils.weekEnd("2024-01-14"))
    }

    @Test fun weekdayJs_usesSundayZeroConvention() {
        assertEquals(0, DateUtils.weekdayJs("2024-01-07")) // Sunday
        assertEquals(1, DateUtils.weekdayJs("2024-01-08")) // Monday
        assertEquals(6, DateUtils.weekdayJs("2024-01-13")) // Saturday
    }

    @Test fun monthBoundariesAndLength() {
        assertEquals("2024-07-01", DateUtils.monthStart("2024-07-15"))
        assertEquals("2024-07-31", DateUtils.monthEnd("2024-07-15"))
        assertEquals("2024-02-29", DateUtils.monthEnd("2024-02-10")) // leap February
        assertEquals(29, DateUtils.daysInMonth(2024, 2))
        assertEquals(28, DateUtils.daysInMonth(2023, 2))
        assertEquals(30, DateUtils.daysInMonth(2024, 4))
    }

    @Test fun quarters() {
        assertEquals("2024-Q1", DateUtils.quarterOf("2024-01-15"))
        assertEquals("2024-Q2", DateUtils.quarterOf("2024-04-01"))
        assertEquals("2024-Q3", DateUtils.quarterOf("2024-09-30"))
        assertEquals("2024-Q4", DateUtils.quarterOf("2024-12-31"))
    }

    @Test fun monthKeyAndDayOfMonth() {
        assertEquals("2024-06", DateUtils.monthKeyOf("2024-06-15"))
        assertEquals(9, DateUtils.dayOfMonth("2024-06-09"))
    }

    @Test fun dayKeyRoundTripsThroughLocalMidnight() {
        // dayKeyOf(startOfDayMs(k)) == k in the system zone, whatever it is.
        val k = "2024-06-15"
        assertEquals(k, DateUtils.dayKeyOf(DateUtils.startOfDayMs(k)))
    }
}
