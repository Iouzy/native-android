package com.pauta.app.domain

import com.pauta.app.data.entity.IntentionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HojeLogicTest {

    private fun intent(day: String, text: String, done: Boolean = false, pos: Int = 0) =
        IntentionEntity(id = "i_$day$text", dayKey = day, text = text, done = done, createdAt = 0, position = pos)

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
}
