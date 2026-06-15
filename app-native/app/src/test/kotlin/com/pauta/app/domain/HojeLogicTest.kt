package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HojeLogicTest {

    private fun intent(day: String, text: String, done: Boolean = false, pos: Int = 0) =
        IntentionEntity(id = "i_$day$text", dayKey = day, text = text, done = done, createdAt = 0, position = pos)

    private fun day(key: String, reflection: String) = DayEntity(dayKey = key, reflection = reflection)

    private fun timed(text: String, w: String?) =
        IntentionEntity(id = "i_$text", dayKey = "2024-01-12", text = text, createdAt = 0, timeOfDay = w)

    @Test fun groupsByTimeOfDayInCanonicalOrderDroppingEmpties() {
        val sorted = listOf(
            timed("n1", "noite"),
            timed("m1", "manha"),
            timed("x1", null),
            timed("m2", "manha"),
        )
        val groups = HojeLogic.groupByTimeOfDay(sorted)
        // manhã, noite, sem-hora present (tarde dropped); order canonical.
        assertEquals(listOf("manha", "noite", null), groups.map { it.first })
        // within-group incoming order preserved (m1 before m2).
        assertEquals(listOf("m1", "m2"), groups.first().second.map { it.text })
    }

    @Test fun picksMostRecentPastDayWithUnfinished() {
        val all = listOf(
            intent("2024-01-08", "a", done = true),
            intent("2024-01-09", "b", done = false),  // unfinished
            intent("2024-01-09", "c", done = true),
            intent("2024-01-10", "d", done = true),   // more recent but all done → skipped
            intent("2024-01-12", "e", done = false),  // today → ignored
        )
        val src = HojeLogic.carrySource(all, "2024-01-12")
        assertEquals("2024-01-09", src?.dayKey)
        assertEquals(listOf("b"), src?.items?.map { it.text })
    }

    @Test fun nullWhenEverythingDone() {
        val all = listOf(intent("2024-01-10", "x", done = true))
        assertNull(HojeLogic.carrySource(all, "2024-01-12"))
    }

    @Test fun ignoresBlankText() {
        val all = listOf(intent("2024-01-10", "   ", done = false))
        assertNull(HojeLogic.carrySource(all, "2024-01-12"))
    }

    @Test fun ignoresTodayAndFutureDays() {
        val all = listOf(
            intent("2024-01-12", "today", done = false),
            intent("2024-01-15", "future", done = false),
        )
        assertNull(HojeLogic.carrySource(all, "2024-01-12"))
    }

    // ── E2 · Memórias ─────────────────────────────────────────

    @Test fun memoriesFindsSameMonthDayPriorYearsNewestFirst() {
        val days = listOf(
            day("2023-06-15", "two years ago"),
            day("2025-06-15", "one year ago"),
            day("2024-06-14", "wrong day"),     // different month-day → skipped
            day("2022-07-15", "wrong month"),   // different month-day → skipped
        )
        val mems = HojeLogic.memories(days, "2026-06-15")
        // Newest year (fewest yearsAgo) first.
        assertEquals(listOf("2025-06-15", "2023-06-15"), mems.map { it.dayKey })
        assertEquals(listOf(1, 3), mems.map { it.yearsAgo })
        assertEquals("one year ago", mems.first().reflection)
    }

    @Test fun memoriesSkipsBlankReflections() {
        val days = listOf(
            day("2025-06-15", ""),       // empty → skipped
            day("2024-06-15", "   "),    // whitespace → skipped
            day("2023-06-15", "real"),
        )
        assertEquals(listOf("2023-06-15"), HojeLogic.memories(days, "2026-06-15").map { it.dayKey })
    }

    @Test fun memoriesIgnoreTodayAndFuture() {
        val days = listOf(
            day("2026-06-15", "today's own reflection"),
            day("2027-06-15", "a year ahead"),
        )
        assertTrue(HojeLogic.memories(days, "2026-06-15").isEmpty())
    }

    @Test fun memoriesEmptyWhenNoHistory() {
        assertTrue(HojeLogic.memories(emptyList(), "2026-06-15").isEmpty())
    }
}
