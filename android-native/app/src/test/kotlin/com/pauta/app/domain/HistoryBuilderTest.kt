package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryBuilderTest {

    @Test
    fun pastDaysWithContentNewestFirst() {
        val days = listOf(
            DayEntity("2025-05-18", "ontem"),
            DayEntity("2025-05-17", ""),        // empty reflection, no intentions → dropped
            DayEntity("2025-05-20", "hoje"),    // today → excluded
        )
        val intentions = listOf(
            IntentionEntity(id = "a", dayKey = "2025-05-18", text = "x", done = true),
            IntentionEntity(id = "b", dayKey = "2025-05-18", text = "y", done = false),
            IntentionEntity(id = "c", dayKey = "2025-05-16", text = "z", done = true), // no DayEntity row
            IntentionEntity(id = "d", dayKey = "2025-05-20", text = "today", done = false), // today
        )

        val out = HistoryBuilder.build(days, intentions, todayKey = "2025-05-20")

        assertEquals(listOf("2025-05-18", "2025-05-16"), out.map { it.dayKey })
        val d18 = out[0]
        assertEquals(2, d18.totalCount)
        assertEquals(1, d18.doneCount)
        assertEquals("ontem", d18.reflection)
        val d16 = out[1]
        assertEquals(1, d16.totalCount)
        assertEquals(1, d16.doneCount)
        assertEquals("", d16.reflection)
    }

    @Test
    fun emptyWhenNoPastContent() {
        val out = HistoryBuilder.build(
            days = listOf(DayEntity("2025-05-20", "hoje")),
            intentions = listOf(IntentionEntity(id = "a", dayKey = "2025-05-20", text = "t")),
            todayKey = "2025-05-20",
        )
        assertEquals(emptyList<DayHistoryItem>(), out)
    }
}
