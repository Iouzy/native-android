package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitCalculatorTest {

    private fun ms(key: String) = DateUtils.startOfDayMs(key)

    /** Inclusive set of day keys from [from] to [to]. */
    private fun range(from: String, to: String): Set<String> {
        val out = sortedSetOf<String>()
        var k = from
        while (k <= to) { out.add(k); k = DateUtils.addDays(k, 1) }
        return out
    }

    @Test fun dailyStreak_respiroKeepsIt_gapBreaksIt() {
        val h = HabitModel(
            id = "h1", createdAt = ms("2024-01-01"), cadence = "daily",
            log = setOf("2024-01-10", "2024-01-09", "2024-01-08", "2024-01-06"),
            respiros = setOf("2024-01-07"),
        )
        val s = HabitCalculator.currentStreak(h, "2024-01-10")
        assertEquals(5, s.days)      // 10,09,08,07(respiro),06 then 05 missing → stop
        assertEquals(1, s.respiros)
        assertEquals(5, s.units)
    }

    @Test fun dailyStreak_skipsOffDays() {
        // Mon/Wed/Fri schedule (JS 1,3,5). Every due day done; off-days ignored.
        val due = setOf("2024-01-01", "2024-01-03", "2024-01-05", "2024-01-08", "2024-01-10", "2024-01-12")
        val h = HabitModel(
            id = "h2", createdAt = ms("2024-01-01"), cadence = "daily",
            weekdays = listOf(1, 3, 5), log = due,
        )
        val s = HabitCalculator.currentStreak(h, "2024-01-12") // a Friday
        assertEquals(6, s.days)
    }

    @Test fun weeklyStreak_unfinishedCurrentPeriodDoesNotBreak() {
        val h = HabitModel(
            id = "h3", createdAt = ms("2024-01-01"), cadence = "weekly", anchor = null,
            log = setOf("2024-01-03", "2024-01-10"), // weeks Jan1-7 and Jan8-14
        )
        val s = HabitCalculator.currentStreak(h, "2024-01-17") // current week Jan15-21, not yet done
        assertEquals(2, s.units)
        assertEquals(14, s.days) // weekly ×7
    }

    @Test fun tierThresholds() {
        assertNull(HabitCalculator.tideTier(0))
        assertEquals("Onda", HabitCalculator.tideTier(1)?.name)
        assertEquals("Onda", HabitCalculator.tideTier(6)?.name)
        assertEquals("Maré baixa", HabitCalculator.tideTier(7)?.name)
        assertEquals("Maré média", HabitCalculator.tideTier(30)?.name)
        assertEquals("Tsunami", HabitCalculator.tideTier(720)?.name)
    }

    @Test fun navigatorLevels() {
        assertEquals("Aprendiz", HabitCalculator.navigatorLevel(0).name)
        assertEquals("Grumete", HabitCalculator.navigatorLevel(30).name)
        assertEquals("Marujo", HabitCalculator.navigatorLevel(100).name)
        assertEquals("Navegador", HabitCalculator.navigatorLevel(4999).name)
        assertEquals("Almirante", HabitCalculator.navigatorLevel(5000).name)
    }

    @Test fun monthlyPct_removesRespirosFromDenominator() {
        // March: 20 done, 5 respiro, 6 missed → 20 / (31-5) = 77%.
        val h = HabitModel(
            id = "h4", createdAt = ms("2024-03-01"), cadence = "daily",
            log = range("2024-03-01", "2024-03-20"),
            respiros = range("2024-03-21", "2024-03-25"),
        )
        assertEquals(77, HabitCalculator.pctInMonth(h, 2024, 3, "2024-03-31"))
    }

    @Test fun overallPct_onlyCountsMatureHabits() {
        val mature = HabitModel(
            id = "a", createdAt = ms("2024-03-01"), cadence = "daily",
            log = range("2024-03-01", "2024-03-15"), // 15/31 ≈ 48%
        )
        val immature = HabitModel(
            id = "b", createdAt = ms("2024-03-29"), cadence = "daily",
            log = range("2024-03-29", "2024-03-31"), // only 3 observed → excluded
        )
        val overall = HabitCalculator.overallPctInMonth(listOf(mature, immature), 2024, 3, "2024-03-31")
        assertEquals(48, overall)
    }

    @Test fun dayState_dailyStatesAndOffDays() {
        val h = HabitModel(
            id = "h", createdAt = ms("2024-06-01"), cadence = "daily",
            log = setOf("2024-06-05"), respiros = setOf("2024-06-06"),
        )
        assertEquals(HabitCalculator.DayState.PRE, HabitCalculator.dayState(h, "2024-05-31", "2024-06-10"))
        assertEquals(HabitCalculator.DayState.DONE, HabitCalculator.dayState(h, "2024-06-05", "2024-06-10"))
        assertEquals(HabitCalculator.DayState.RESPIRO, HabitCalculator.dayState(h, "2024-06-06", "2024-06-10"))
        assertEquals(HabitCalculator.DayState.EMPTY, HabitCalculator.dayState(h, "2024-06-07", "2024-06-10"))
        assertEquals(HabitCalculator.DayState.FUTURE, HabitCalculator.dayState(h, "2024-06-11", "2024-06-10"))
    }

    @Test fun dayState_dailyWeekdayScheduleOff() {
        // Mon/Wed/Fri only. 2024-06-04 is a Tuesday → OFF.
        val h = HabitModel(id = "h", createdAt = ms("2024-06-01"), cadence = "daily", weekdays = listOf(1, 3, 5))
        assertEquals(HabitCalculator.DayState.OFF, HabitCalculator.dayState(h, "2024-06-04", "2024-06-10"))
    }

    @Test fun dayState_weeklyDoneLocksRestOfPeriod() {
        val h = HabitModel(
            id = "h", createdAt = ms("2024-06-01"), cadence = "weekly", anchor = null,
            log = setOf("2024-06-12"), // week Jun10-16
        )
        assertEquals(HabitCalculator.DayState.DONE, HabitCalculator.dayState(h, "2024-06-12", "2024-06-20"))
        assertEquals(HabitCalculator.DayState.LOCKED, HabitCalculator.dayState(h, "2024-06-13", "2024-06-20"))
        assertEquals(HabitCalculator.DayState.EMPTY, HabitCalculator.dayState(h, "2024-06-18", "2024-06-20"))
    }

    @Test fun totalDoneDays_sumsLogSizes() {
        val a = HabitModel(id = "a", createdAt = ms("2024-01-01"), log = setOf("2024-01-01", "2024-01-02"))
        val b = HabitModel(id = "b", createdAt = ms("2024-01-01"), log = setOf("2024-01-01"))
        assertEquals(3, HabitCalculator.totalDoneDays(listOf(a, b)))
    }
}
