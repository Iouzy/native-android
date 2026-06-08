package com.pauta.app.domain

import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.domain.HabitCalculator.DayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [HabitCalculator] — the cadence / streak / percentage math ported
 * from store.jsx. Cases mirror tools/test-cadence.mjs (the web build's source of
 * truth), plus the day-state and monthly-% logic.
 */
class HabitCalculatorTest {

    private fun habit(
        cadence: String = "daily",
        createdKey: String = "2025-01-01",
        weekdays: String = "[]",
        target: Int = 0,
        endsAt: Long? = null,
    ) = HabitEntity(
        id = "h",
        name = "Test",
        cadence = cadence,
        weekdays = weekdays,
        target = target,
        endsAt = endsAt,
        createdAt = DateUtils.msFromKey(createdKey),
    )

    // ── periodKey / periodDays ───────────────────────────────────────────────
    @Test
    fun periodKey_perCadence() {
        assertEquals("2025-05-14", HabitCalculator.periodKey(habit("daily"), "2025-05-14"))
        assertEquals("2025-05-12", HabitCalculator.periodKey(habit("weekly"), "2025-05-14")) // Monday
        assertEquals("2025-05", HabitCalculator.periodKey(habit("monthly"), "2025-05-14"))
    }

    @Test
    fun periodDays_weekly_isMondayToSunday() {
        val days = HabitCalculator.periodDays(habit("weekly"), "2025-05-14")
        assertEquals(7, days.size)
        assertEquals("2025-05-12", days.first())
        assertEquals("2025-05-18", days.last())
    }

    @Test
    fun periodDays_monthly_coversWholeMonth() {
        val days = HabitCalculator.periodDays(habit("monthly"), "2025-05-14")
        assertEquals(31, days.size)
        assertEquals("2025-05-01", days.first())
        assertEquals("2025-05-31", days.last())
    }

    // ── dayStatus ────────────────────────────────────────────────────────────
    @Test
    fun dayStatus_daily_states() {
        val h = habit("daily", createdKey = "2025-05-01")
        val today = "2025-05-20"
        fun st(day: String, log: Set<String> = emptySet(), resp: Map<String, HabitRespiroEntity> = emptyMap()) =
            HabitCalculator.dayStatus(h, log, resp, emptyMap(), day, today).state

        assertEquals(DayState.DONE, st("2025-05-19", log = setOf("2025-05-19")))
        assertEquals(DayState.EMPTY, st("2025-05-18"))
        assertEquals(DayState.FUTURE, st("2025-05-21"))
        assertEquals(DayState.PRE, st("2025-04-30"))
        assertEquals(
            DayState.RESPIRO,
            st("2025-05-18", resp = mapOf("2025-05-18" to HabitRespiroEntity("h", "2025-05-18"))),
        )
    }

    @Test
    fun dayStatus_daily_offDayForWeekdaySchedule() {
        // weekdays use the native 0=Mon..6=Sun convention; [0,2,4] = Mon/Wed/Fri.
        val h = habit("daily", createdKey = "2025-05-01", weekdays = "[0,2,4]")
        // 2025-05-20 is a Tuesday (weekdayOf == 1) → not scheduled → OFF.
        assertEquals(
            DayState.OFF,
            HabitCalculator.dayStatus(h, emptySet(), emptyMap(), emptyMap(), "2025-05-20", "2025-05-20").state,
        )
    }

    @Test
    fun dayStatus_weekly_locksRestOfPeriodOnceDone() {
        val h = habit("weekly", createdKey = "2025-04-01")
        val log = setOf("2025-05-13") // Tuesday done
        val today = "2025-05-20"
        fun st(day: String) = HabitCalculator.dayStatus(h, log, emptyMap(), emptyMap(), day, today).state
        assertEquals(DayState.DONE, st("2025-05-13"))
        assertEquals(DayState.LOCKED, st("2025-05-18")) // same week, another day
        assertEquals(DayState.EMPTY, st("2025-05-07"))  // prior week, nothing done
    }

    // ── streak ───────────────────────────────────────────────────────────────
    @Test
    fun streak_daily_threeConsecutive() {
        val h = habit("daily", createdKey = "2025-05-01")
        val log = setOf("2025-05-18", "2025-05-19", "2025-05-20")
        assertEquals(3, HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-20").streakDays)
    }

    @Test
    fun streak_weekly_threeConsecutiveIncludingCurrent() {
        val h = habit("weekly", createdKey = "2025-04-01")
        val log = setOf("2025-05-21", "2025-05-14", "2025-05-07")
        assertEquals(21, HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-22").streakDays) // 3 × 7
    }

    @Test
    fun streak_weekly_currentPeriodEmptyDoesNotBreak() {
        // Regression: an in-progress current week with no completion yet must NOT
        // reset the streak (matches store.jsx habitCurrentStreak).
        val h = habit("weekly", createdKey = "2025-04-01")
        val log = setOf("2025-05-14", "2025-05-07") // current week (5/19-25) empty
        assertEquals(14, HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-22").streakDays)
    }

    @Test
    fun streak_weekly_missedPastPeriodBreaks() {
        val h = habit("weekly", createdKey = "2025-04-01")
        val log = setOf("2025-05-14") // week 5/5-11 missed
        assertEquals(7, HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-22").streakDays)
    }

    @Test
    fun streak_tierName() {
        val h = habit("daily", createdKey = "2025-01-01")
        val log = (1..8).map { DateUtils.addDays("2025-05-20", -(it - 1)) }.toSet() // 8-day run to 5/20
        val info = HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-20")
        assertEquals(8, info.streakDays)
        assertEquals("Maré Baixa", info.tierName) // tier threshold >= 7
    }

    @Test
    fun streak_daily_skipsScheduleOffDays() {
        // Mon/Wed/Fri schedule (native weekdays 0=Mon..6=Sun → [0,2,4]).
        val h = habit("daily", createdKey = "2025-05-19", weekdays = "[0,2,4]")
        // 5/19 Mon, 5/21 Wed, 5/23 Fri done; 5/20 Tue & 5/22 Thu are off-days.
        val log = setOf("2025-05-19", "2025-05-21", "2025-05-23")
        val info = HabitCalculator.streakInfo(h, log, emptySet(), "2025-05-23")
        assertEquals(3, info.streakDays) // off-days skipped, don't break the streak
        assertEquals(3, info.bestDays)
    }

    // ── monthly % ────────────────────────────────────────────────────────────
    @Test
    fun monthStats_daily_pctRoundsAndExcludesRespiros() {
        val h = habit("daily", createdKey = "2025-05-01")
        val log = (1..10).map { "2025-05-" + "%02d".format(it) }.toSet()   // 10 done
        val resp = setOf("2025-05-11", "2025-05-12", "2025-05-13")         // 3 respiros
        val s = HabitCalculator.monthStats(h, log, resp, emptyMap(), 2025, 5, "2025-05-20")
        assertEquals(20, s.observed)
        assertEquals(10, s.done)
        assertEquals(3, s.respiros)
        assertEquals(59, s.pct) // round(10*100 / (20-3)) = round(58.8); truncation would give 58
    }

    @Test
    fun monthStats_immatureMonthHasNullPct() {
        val h = habit("daily", createdKey = "2025-05-18")
        val s = HabitCalculator.monthStats(h, emptySet(), emptySet(), emptyMap(), 2025, 5, "2025-05-20")
        assertEquals(3, s.observed) // 5/18, 5/19, 5/20
        assertNull(s.pct)
    }

    @Test
    fun overallMonthPct_averagesMatureHabits() {
        val a = habit("daily", createdKey = "2025-05-01").copy(id = "a")
        val b = habit("daily", createdKey = "2025-05-01").copy(id = "b")
        val logA = (1..10).map { "2025-05-" + "%02d".format(it) }.toSet()  // 10/20 = 50%
        val logB = (1..20).map { "2025-05-" + "%02d".format(it) }.toSet()  // 20/20 = 100%
        val overall = HabitCalculator.overallMonthPct(
            listOf(a, b),
            mapOf("a" to logA, "b" to logB),
            mapOf("a" to emptySet<String>(), "b" to emptySet<String>()),
            emptyMap(),
            2025, 5, "2025-05-20",
        )
        assertEquals(75, overall)
    }
}
