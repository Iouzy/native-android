package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.domain.ReadingStats.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsTest {

    private fun session(day: String, minutes: Int = 30, pages: Int? = null, words: Float? = null) =
        Session(dayKey = day, minutes = minutes, pages = pages, words = words)

    private fun book(status: String = "done", finishedAt: Long?) = BookEntity(
        id = "bk_${finishedAt}_$status",
        title = "Livro",
        status = status,
        finishedAt = finishedAt,
        createdAt = 0L,
    )

    /** Local midday of a day key — the timestamps the shelf actually carries,
     *  built through [DateUtils] so the test agrees with it about the zone. */
    private fun middayOf(dayKey: String): Long = DateUtils.startOfDayMs(dayKey) + 12 * 3_600_000L

    // ── daysRead ──────────────────────────────────────────────

    @Test fun daysReadIsEmptyWithoutSessions() {
        assertTrue(ReadingStats.daysRead(emptyList()).isEmpty())
    }

    @Test fun daysReadCollapsesSeveralSessionsOnOneDay() {
        val days = ReadingStats.daysRead(
            listOf(session("2026-08-01"), session("2026-08-01"), session("2026-08-03")),
        )
        assertEquals(setOf("2026-08-01", "2026-08-03"), days)
    }

    // ── streaks ───────────────────────────────────────────────

    @Test fun streaksAreZeroWithoutDays() {
        assertEquals(0 to 0, ReadingStats.streaks(emptySet(), "2026-08-01"))
    }

    @Test fun currentStreakCountsBackFromToday() {
        val days = setOf("2026-07-30", "2026-07-31", "2026-08-01")
        assertEquals(3 to 3, ReadingStats.streaks(days, "2026-08-01"))
    }

    @Test fun todayWithoutReadingKeepsYesterdaysStreakAlive() {
        // The day isn't over — unlike a tide, an unread today is not yet a miss.
        val days = setOf("2026-07-30", "2026-07-31")
        assertEquals(2 to 2, ReadingStats.streaks(days, "2026-08-01"))
    }

    @Test fun oneMissedDayEndsTheCurrentStreakButNotTheBest() {
        // 5 days, a gap, then 2 — and today (the 10th) not read yet, so the
        // current streak is the two days ending yesterday.
        val days = setOf(
            "2026-08-01", "2026-08-02", "2026-08-03", "2026-08-04", "2026-08-05",
            // 6th missed
            "2026-08-07", "2026-08-08", "2026-08-09",
        )
        assertEquals(3 to 5, ReadingStats.streaks(days, "2026-08-10"))
    }

    @Test fun theCurrentStreakDiesTwoDaysAfterTheLastReading() {
        val days = setOf("2026-07-30", "2026-07-31")
        assertEquals(0 to 2, ReadingStats.streaks(days, "2026-08-02"))
    }

    @Test fun streaksCrossAMonthBoundary() {
        val days = setOf("2026-07-30", "2026-07-31", "2026-08-01", "2026-08-02")
        assertEquals(4 to 4, ReadingStats.streaks(days, "2026-08-02"))
    }

    // ── minutesByDay ──────────────────────────────────────────

    @Test fun minutesByDayIsEmptyWithoutSessions() {
        assertTrue(ReadingStats.minutesByDay(emptyList(), 2026, 8).isEmpty())
    }

    @Test fun minutesByDaySumsWithinTheMonthAndIgnoresTheRest() {
        val sessions = listOf(
            session("2026-07-31", minutes = 90),
            session("2026-08-01", minutes = 20),
            session("2026-08-01", minutes = 25),
            session("2026-08-14", minutes = 40),
            session("2026-09-01", minutes = 60),
        )
        val byDay = ReadingStats.minutesByDay(sessions, 2026, 8)
        assertEquals(mapOf("2026-08-01" to 45, "2026-08-14" to 40), byDay)
    }

    @Test fun aZeroMinuteSessionLeavesNoDay() {
        assertTrue(ReadingStats.minutesByDay(listOf(session("2026-08-01", minutes = 0)), 2026, 8).isEmpty())
    }

    // ── finishedByMonth ───────────────────────────────────────

    @Test fun finishedByMonthIsTwelveZerosWithoutBooks() {
        assertEquals(List(12) { 0 }, ReadingStats.finishedByMonth(emptyList(), 2026))
    }

    @Test fun finishedByMonthCountsOnlyFinishedBooksOfThatYear() {
        val books = listOf(
            book(finishedAt = middayOf("2026-01-14")),
            book(finishedAt = middayOf("2026-08-02")),
            book(finishedAt = middayOf("2026-08-27")),
            book(finishedAt = middayOf("2025-08-27")),          // another year
            book(status = "reading", finishedAt = null),        // still going
            book(status = "dnf", finishedAt = middayOf("2026-08-05")), // abandoned ≠ finished
        )
        val months = ReadingStats.finishedByMonth(books, 2026)
        assertEquals(1, months[0])
        assertEquals(2, months[7])
        assertEquals(3, months.sum())
    }

    // ── minutesLastDays ───────────────────────────────────────

    @Test fun minutesLastDaysKeepsTheEmptyDaysAndEndsOnToday() {
        val sessions = listOf(session("2026-08-08", minutes = 15), session("2026-08-10", minutes = 40))
        val values = ReadingStats.minutesLastDays(sessions, "2026-08-10", 5)
        // 06, 07, 08, 09, 10
        assertEquals(listOf(0, 0, 15, 0, 40), values)
    }

    @Test fun minutesLastDaysIgnoresAnythingOlderThanTheWindow() {
        val sessions = listOf(session("2026-07-01", minutes = 300), session("2026-08-10", minutes = 10))
        assertEquals(listOf(0, 0, 10), ReadingStats.minutesLastDays(sessions, "2026-08-10", 3))
    }

    @Test fun minutesLastDaysOfNoDaysIsEmpty() {
        assertTrue(ReadingStats.minutesLastDays(listOf(session("2026-08-10")), "2026-08-10", 0).isEmpty())
    }

    // ── pagesByWeek ───────────────────────────────────────────

    @Test fun pagesByWeekSumsMondayToSunday() {
        // 2026-08-10 is a Monday; the week before runs 03→09.
        val sessions = listOf(
            session("2026-08-03", pages = 10),
            session("2026-08-09", pages = 5),
            session("2026-08-10", pages = 20),
        )
        assertEquals(listOf(15, 20), ReadingStats.pagesByWeek(sessions, "2026-08-12", 2))
    }

    @Test fun anUncountedSessionAddsNothingToItsWeek() {
        val sessions = listOf(session("2026-08-10", pages = null), session("2026-08-11", pages = 7))
        assertEquals(listOf(7), ReadingStats.pagesByWeek(sessions, "2026-08-12", 1))
    }

    // ── speedPoints ───────────────────────────────────────────

    @Test fun speedPointsAreWordsOverMinutesOldestFirst() {
        val sessions = listOf(
            session("2026-08-10", minutes = 20, words = 5000f),
            session("2026-08-08", minutes = 10, words = 2000f),
        )
        assertEquals(listOf(200f, 250f), ReadingStats.speedPoints(sessions))
    }

    @Test fun aSessionWithNoWordFigureHasNoPoint() {
        val sessions = listOf(
            session("2026-08-08", minutes = 30, words = null),   // audiobook / by hand
            session("2026-08-09", minutes = 0, words = 900f),    // no time on record
            session("2026-08-10", minutes = 10, words = 0f),     // read nothing
        )
        assertTrue(ReadingStats.speedPoints(sessions).isEmpty())
    }
}
