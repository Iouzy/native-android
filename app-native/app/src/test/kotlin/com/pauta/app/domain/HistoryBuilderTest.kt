package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryBuilderTest {

    private fun intent(day: String, done: Boolean) =
        IntentionEntity(id = "i_$day${done}${Math.random()}", dayKey = day, text = "x", done = done, createdAt = 0)

    @Test fun pastDaysWithContentNewestFirst() {
        val days = listOf(DayEntity("2024-01-09", "good day"))
        val intentions = listOf(
            intent("2024-01-08", true),
            intent("2024-01-08", false),
            intent("2024-01-10", true), // future-of-08 but still < today
        )
        val out = HistoryBuilder.build(days, intentions, todayKey = "2024-01-12")
        assertEquals(listOf("2024-01-10", "2024-01-09", "2024-01-08"), out.map { it.dayKey })
        val d08 = out.first { it.dayKey == "2024-01-08" }
        assertEquals(1, d08.doneCount)
        assertEquals(2, d08.totalCount)
        val d09 = out.first { it.dayKey == "2024-01-09" }
        assertEquals("good day", d09.reflection) // reflection-only day still listed
    }

    @Test fun excludesTodayFutureAndEmptyDays() {
        val days = listOf(DayEntity("2024-01-11", "   ")) // blank reflection, no intentions → excluded
        val intentions = listOf(
            intent("2024-01-12", false), // today
            intent("2024-01-20", false), // future
        )
        assertEquals(emptyList<String>(), HistoryBuilder.build(days, intentions, "2024-01-12").map { it.dayKey })
    }
}
