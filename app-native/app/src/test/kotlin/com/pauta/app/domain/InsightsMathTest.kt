package com.pauta.app.domain

import com.pauta.app.data.entity.IntentionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsMathTest {

    private var counter = 0

    /** Local-zone timestamp for a day key + wall-clock time, like the app's data. */
    private fun ts(day: String, h: Int, m: Int = 0): Long =
        LocalDateTime.parse("${day}T%02d:%02d:00".format(h, m))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun seg(day: String, h: Int, m: Int, durMin: Int) =
        FocusMath.FocusSeg(ts(day, h, m), ts(day, h, m) + durMin * 60_000L)

    private fun intent(day: String, done: Boolean, priority: Int? = null) = IntentionEntity(
        id = "i${counter++}", dayKey = day, text = "x", done = done, priority = priority, createdAt = 0L,
    )

    @Test
    fun focusByHour_splitsAcrossHourBoundaries() {
        // 08:45 → 09:30 credits 15 min to hour 8 and 30 min to hour 9, like the web.
        val blocks = listOf(
            InsightsMath.BlockSegs("b1", listOf(FocusMath.FocusSeg(ts("2024-06-03", 8, 45), ts("2024-06-03", 9, 30)))),
        )
        val hours = InsightsMath.focusByHour(blocks, ts("2024-06-03", 12, 0))
        assertEquals(15 * 60_000L, hours[8])
        assertEquals(30 * 60_000L, hours[9])
        assertEquals(45 * 60_000L, hours.sum())
    }

    @Test
    fun bestHour_tieGoesToEarliestHour() {
        val blocks = listOf(InsightsMath.BlockSegs("b", listOf(seg("2024-06-03", 8, 0, 30), seg("2024-06-03", 10, 0, 30))))
        val best = InsightsMath.bestHourStats(blocks, ts("2024-06-03", 12, 0))
        assertEquals(8, best.peak)
        assertEquals(60 * 60_000L, best.total)
    }

    @Test
    fun weeklyReview_countsFocusIntentionsHabitsReflections() {
        val end = "2024-06-09" // window 06-03 … 06-09
        val blocks = listOf(
            InsightsMath.BlockSegs("b1", listOf(seg("2024-06-08", 9, 0, 60))),
            InsightsMath.BlockSegs("b2", listOf(seg("2024-06-09", 10, 0, 30))),
            InsightsMath.BlockSegs("b3", listOf(seg("2024-05-01", 10, 0, 30))), // outside the window
        )
        val habit = InsightsMath.NamedHabit(
            "Ler",
            HabitModel(
                id = "h1", createdAt = ts("2024-06-01", 0, 0), cadence = "daily",
                log = setOf("2024-06-08", "2024-06-07"), respiros = setOf("2024-06-06"),
            ),
        )
        val intents = listOf(intent("2024-06-08", true), intent("2024-06-09", false), intent("2024-05-01", true))
        val r = InsightsMath.weeklyReview(
            blocks, listOf(habit), intents,
            mapOf("2024-06-08" to "valeu", "2024-06-09" to "  "),
            end, ts("2024-06-09", 23, 0),
        )
        assertEquals(90 * 60_000L, r.focusMs)
        assertEquals(2, r.activeDays)
        assertEquals(2, r.blockCount)
        assertEquals("2024-06-08", r.topKey)
        assertEquals(2, r.intTotal)
        assertEquals(1, r.intDone)
        // Active all 7 window days, 2 done, 1 respiro → 2/(7-1) = 33%.
        assertEquals(7, r.habitObservedSlots)
        assertEquals(2, r.habitDone)
        assertEquals(1, r.respiros)
        assertEquals(33, r.habitPct)
        // A whitespace-only reflection doesn't count.
        assertEquals(1, r.reflections)
    }

    @Test
    fun correlation_computesDeltaOverDoneVsMissedDays() {
        val todayKey = "2024-06-29"
        val now = ts("2024-06-29", 22, 0)
        val segs = mutableListOf<FocusMath.FocusSeg>()
        listOf("2024-06-29", "2024-06-28", "2024-06-27").forEach { segs += seg(it, 9, 0, 60) } // done days
        listOf("2024-06-26", "2024-06-25", "2024-06-24").forEach { segs += seg(it, 9, 0, 30) } // missed days
        val blocks = listOf(InsightsMath.BlockSegs("b", segs))
        val h = InsightsMath.NamedHabit(
            "Treino",
            HabitModel(
                id = "h", createdAt = ts("2024-06-24", 0, 0), cadence = "daily",
                log = setOf("2024-06-29", "2024-06-28", "2024-06-27"),
            ),
        )
        val rows = InsightsMath.habitFocusCorrelation(listOf(h), blocks, 30, todayKey, now)
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].doneN)
        assertEquals(3, rows[0].missN)
        assertEquals(100, rows[0].deltaPct) // 60m vs 30m
    }

    @Test
    fun correlation_needsThreeSamplesOnEachSide() {
        val todayKey = "2024-06-29"
        val now = ts("2024-06-29", 22, 0)
        val blocks = listOf(InsightsMath.BlockSegs("b", listOf(seg("2024-06-29", 9, 0, 60))))
        val h = InsightsMath.NamedHabit(
            "Treino",
            HabitModel(
                id = "h", createdAt = ts("2024-06-24", 0, 0), cadence = "daily",
                log = setOf("2024-06-29", "2024-06-28"), // only 2 done days
            ),
        )
        assertTrue(InsightsMath.habitFocusCorrelation(listOf(h), blocks, 30, todayKey, now).isEmpty())
    }

    @Test
    fun narrative_highPrioNeedsThreeSamples() {
        val now = ts("2024-06-09", 12, 0)
        val two = listOf(intent("2024-06-01", true, 1), intent("2024-06-02", false, 1))
        assertNull(InsightsMath.narrativeStats(emptyList(), emptyList(), two, "2024-06-09", now).highPrioPct)
        val three = two + intent("2024-06-03", true, 1)
        assertEquals(67, InsightsMath.narrativeStats(emptyList(), emptyList(), three, "2024-06-09", now).highPrioPct)
    }

    @Test
    fun monthReview_stopsAtToday() {
        val blocks = listOf(
            InsightsMath.BlockSegs("b", listOf(seg("2024-06-10", 9, 0, 60), seg("2024-06-20", 9, 0, 60))),
        )
        val r = InsightsMath.monthReview(
            blocks, emptyList(), emptyList(), emptyMap(),
            2024, 6, "2024-06-15", ts("2024-06-15", 12, 0),
        )
        assertEquals(60 * 60_000L, r.focusMs) // the 06-20 block is after "today"
        assertEquals(1, r.activeDays)
        assertEquals("2024-06-10", r.topKey)
    }
}
