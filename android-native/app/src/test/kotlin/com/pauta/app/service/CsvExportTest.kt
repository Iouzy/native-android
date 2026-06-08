package com.pauta.app.service

import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the flat CSV export (ported from buildCSV in store.jsx). */
class CsvExportTest {

    @Test
    fun buildsHeaderRowsAndEscaping() {
        val created = DateUtils.msFromKey("2025-05-10")
        val data = BackupData(
            intentions = listOf(
                IntentionEntity(id = "i1", dayKey = "2025-05-10", text = "Estudar", done = true, createdAt = created),
                IntentionEntity(id = "i2", dayKey = "2025-05-10", text = "Ler, com vírgula", done = false, createdAt = created + 1),
            ),
            blocks = listOf(
                FocusBlockEntity(id = "b1", title = "Foco", project = "Escola", status = "done", reflection = "bom", createdAt = created),
            ),
            sessions = listOf(
                FocusSessionEntity(id = "s1", blockId = "b1", startedAt = created, endedAt = created + 25 * 60_000L),
            ),
            habits = listOf(HabitEntity(id = "h1", name = "Água")),
            logs = listOf(HabitLogEntity(habitId = "h1", dayKey = "2025-05-10")),
            respiros = listOf(HabitRespiroEntity(habitId = "h1", dayKey = "2025-05-11")),
        )

        val lines = CsvExport.build(data).split("\r\n")

        assertEquals("type,date,title,project,minutes,count,status,note", lines[0])
        assertTrue(lines.contains("intention,2025-05-10,Estudar,,,,done,"))
        // a comma in the text forces quoting
        assertTrue(lines.contains("intention,2025-05-10,\"Ler, com vírgula\",,,,open,"))
        // 25-minute session → 25 minutes
        assertTrue(lines.contains("block,2025-05-10,Foco,Escola,25,,done,bom"))
        assertTrue(lines.contains("habit,2025-05-10,Água,,,1,done,"))
        assertTrue(lines.contains("habit,2025-05-11,Água,,,,respiro,"))
    }
}
