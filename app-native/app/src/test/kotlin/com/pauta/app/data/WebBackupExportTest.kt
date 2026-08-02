package com.pauta.app.data

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class WebBackupExportTest {

    private fun sampleSnapshot() = WebBackup.Snapshot(
        todayKey = "2024-06-09",
        days = listOf(DayEntity("2024-06-09", "good day"), DayEntity("2024-06-08", "")),
        intentions = listOf(
            IntentionEntity("i1", "2024-06-09", "task", done = true, priority = 1, createdAt = 100, position = 0),
            IntentionEntity("i2", "2024-06-08", "old", createdAt = 50, position = 0),
        ),
        blocks = emptyList(), sessions = emptyList(),
        habits = listOf(HabitEntity("h1", name = "Água", createdAt = 10, cadence = "daily")),
        logs = listOf(HabitLogEntity("h1", "2024-06-09")),
        respiros = listOf(HabitRespiroEntity("h1", "2024-06-07", reason = "doente", at = 99)),
        counts = emptyList(),
        goals = emptyList(), milestones = emptyList(),
        routines = emptyList(), routineItems = emptyList(),
        plans = emptyList(), prefs = PrefsEntity(),
    )

    @Test fun exportsV4Envelope() {
        val root = WebBackup.json.parseToJsonElement(WebBackup.export(sampleSnapshot())).jsonObject
        assertEquals("pauta", root["app"]!!.jsonPrimitive.content)
        assertEquals(4, root["version"]!!.jsonPrimitive.int)
    }

    @Test fun todayAndDaysSeparated() {
        val data = WebBackup.json.parseToJsonElement(WebBackup.export(sampleSnapshot())).jsonObject["data"]!!.jsonObject
        val today = data["today"]!!.jsonObject
        assertEquals("2024-06-09", today["dayKey"]!!.jsonPrimitive.content)
        assertEquals("good day", today["reflection"]!!.jsonPrimitive.content)
        val ti = today["intentions"]!!.jsonArray
        assertEquals("i1", ti[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(1, ti[0].jsonObject["priority"]!!.jsonPrimitive.int)
        // Yesterday is under days, not today.
        val days = data["days"]!!.jsonObject
        val d8 = days["2024-06-08"]!!.jsonObject
        assertEquals("i2", d8["intentions"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test fun habitMarksAreSparseMaps() {
        val data = WebBackup.json.parseToJsonElement(WebBackup.export(sampleSnapshot())).jsonObject["data"]!!.jsonObject
        val habit = data["habits"]!!.jsonArray[0].jsonObject
        assertEquals(1, habit["log"]!!.jsonObject["2024-06-09"]!!.jsonPrimitive.int)
        val resp = habit["respiros"]!!.jsonObject["2024-06-07"]!!.jsonObject
        assertEquals("doente", resp["reason"]!!.jsonPrimitive.content)
    }

    /**
     * L2: `WebBackup` is the *format*, and the format is unchanged — hand it a
     * reading-session block and it still round-trips one. Deciding that it never
     * should be handed one is the repository's job, and lives with the rule
     * itself in [BookBackup] (see `BookBackupTest.plannerOnly…`).
     * // PT: o formato não mudou; quem decide o que entra é o repositório.
     */
    @Test fun theFormatItselfStillRoundTripsABookBlock() {
        val snap = sampleSnapshot().copy(
            blocks = listOf(
                FocusBlockEntity(
                    id = "b_r1", title = "A Jangada de Pedra", project = "book:bk_1",
                    status = "done", createdAt = 5_000L,
                ),
            ),
        )
        val data = WebBackup.json.parseToJsonElement(WebBackup.export(snap)).jsonObject["data"]!!.jsonObject
        val block = data["blocks"]!!.jsonArray[0].jsonObject
        assertEquals("book:bk_1", block["project"]!!.jsonPrimitive.content)
        assertEquals("b_r1", WebBackup.import(WebBackup.export(snap)).blocks[0].id)
    }
}
