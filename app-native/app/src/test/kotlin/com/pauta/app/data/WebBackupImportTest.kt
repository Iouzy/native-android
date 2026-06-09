package com.pauta.app.data

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBackupImportTest {

    private fun sample() = WebBackup.Snapshot(
        todayKey = "2024-06-09",
        days = listOf(DayEntity("2024-06-09", "good day"), DayEntity("2024-06-08", "")),
        intentions = listOf(
            IntentionEntity("i1", "2024-06-09", "task", done = true, priority = 1, createdAt = 100, position = 0),
            IntentionEntity("i2", "2024-06-08", "old", createdAt = 50, position = 0),
        ),
        blocks = listOf(FocusBlockEntity("b1", "focus", status = "done", createdAt = 1, reflection = "")),
        sessions = listOf(FocusSessionEntity(blockId = "b1", startedAt = 1000, endedAt = 4000, position = 0)),
        habits = listOf(HabitEntity("h1", name = "Água", createdAt = 10, cadence = "daily")),
        logs = listOf(HabitLogEntity("h1", "2024-06-09")),
        respiros = listOf(HabitRespiroEntity("h1", "2024-06-07", reason = "doente", at = 99)),
        counts = emptyList(),
        goals = emptyList(), milestones = emptyList(),
        routines = emptyList(), routineItems = emptyList(),
        plans = emptyList(), prefs = PrefsEntity(lang = "en", accent = "#2563EB"),
    )

    @Test fun roundTripsNativeExport() {
        val out = WebBackup.import(WebBackup.export(sample()))
        assertEquals(setOf("2024-06-09", "2024-06-08"), out.days.map { it.dayKey }.toSet())
        val i1 = out.intentions.first { it.id == "i1" }
        assertEquals(1, i1.priority)
        assertTrue(i1.done)
        assertTrue(out.logs.any { it.habitId == "h1" && it.dayKey == "2024-06-09" })
        assertEquals("doente", out.respiros.first().reason)
        assertEquals(4000L, out.sessions.first().endedAt)
        assertEquals("en", out.prefs.lang)
        assertEquals("#2563EB", out.prefs.accent)
    }

    @Test fun webShapesLegacyPriorityWhenAndActivePause() {
        val webJson = """
            {"app":"pauta","version":4,"data":{
              "today":{"dayKey":"2024-06-09","reflection":"","intentions":[
                {"id":"i1","text":"x","done":false,"priority":"principal","when":"manha","createdAt":1}]},
              "days":{},
              "blocks":[{"id":"b1","title":"focus","status":"active","reflection":"","createdAt":1,
                "sessions":[{"startedAt":1000,"endedAt":null,"note":""}]}],
              "habits":[],"goals":[],"routines":[],"plans":{},"prefs":{}
            }}
        """.trimIndent()
        val s = WebBackup.import(webJson)
        val i1 = s.intentions.first()
        assertEquals(1, i1.priority)              // legacy "principal" → 1
        assertEquals("manha", i1.timeOfDay)        // when bucket preserved
        val b = s.blocks.first()
        assertEquals("paused", b.status)           // active auto-paused on import
        assertNotNull(s.sessions.first().endedAt)  // the running span is closed
    }
}
