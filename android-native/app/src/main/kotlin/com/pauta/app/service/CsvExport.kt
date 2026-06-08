package com.pauta.app.service

import com.pauta.app.domain.DateUtils

/**
 * Flat, spreadsheet-friendly CSV of every record — the anti-lock-in export,
 * ported from buildCSV() in src/store.jsx. One row per record across the three
 * data types, discriminated by a `type` column. Pure and unit-tested; the file
 * write lives in [BackupManager.exportCsvToDownloads].
 */
object CsvExport {

    private val HEADER = listOf("type", "date", "title", "project", "minutes", "count", "status", "note")

    fun build(data: BackupData): String {
        val rows = mutableListOf(HEADER)

        // Intentions — ordered by day, then creation order within the day.
        data.intentions
            .sortedWith(compareBy({ it.dayKey }, { it.createdAt }))
            .forEach { intn ->
                rows.add(listOf("intention", intn.dayKey, intn.text, "", "", "", if (intn.done) "done" else "open", ""))
            }

        // Focus blocks — total focused minutes + the final reflection.
        val sessionsByBlock = data.sessions.groupBy { it.blockId }
        data.blocks.forEach { b ->
            val ms = sessionsByBlock[b.id].orEmpty().sumOf { s -> (s.endedAt ?: s.startedAt) - s.startedAt }
            val minutes = Math.round(ms / 60000.0).toInt()
            rows.add(listOf("block", DateUtils.keyFromMs(b.createdAt), b.title, b.project ?: "", minutes.toString(), "", b.status, b.reflection))
        }

        // Habits — one row per completed day, one per respiro.
        val nameById = data.habits.associate { it.id to it.name }
        data.logs.sortedWith(compareBy({ it.habitId }, { it.dayKey })).forEach { log ->
            rows.add(listOf("habit", log.dayKey, nameById[log.habitId] ?: "", "", "", "1", "done", ""))
        }
        data.respiros.sortedWith(compareBy({ it.habitId }, { it.dayKey })).forEach { r ->
            rows.add(listOf("habit", r.dayKey, nameById[r.habitId] ?: "", "", "", "", "respiro", ""))
        }

        return rows.joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } }
    }

    // Minimal RFC-4180 escaping: quote when the value holds a comma, quote or newline.
    private fun cell(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }
}
