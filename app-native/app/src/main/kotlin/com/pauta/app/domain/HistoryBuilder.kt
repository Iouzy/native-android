package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity

/** A past day in the history list: its intentions' done/total and the reflection. */
data class HistoryDay(
    val dayKey: String,
    val doneCount: Int,
    val totalCount: Int,
    val reflection: String,
)

/**
 * Builds the read-only Hoje history, mirroring the web's HistoryBuilder: every
 * archived day older than today that has at least one intention or a non-blank
 * reflection, newest first. // PT: histórico só-leitura dos dias passados com
 * conteúdo, do mais recente para o mais antigo.
 */
object HistoryBuilder {

    fun build(
        days: List<DayEntity>,
        intentions: List<IntentionEntity>,
        todayKey: String,
    ): List<HistoryDay> {
        val reflectionByDay = days.associate { it.dayKey to it.reflection }
        val intentionsByDay = intentions.groupBy { it.dayKey }

        // Every day key that has any content, restricted to the past.
        val keys = (reflectionByDay.keys + intentionsByDay.keys)
            .filter { it < todayKey }
            .distinct()

        return keys.mapNotNull { key ->
            val items = intentionsByDay[key].orEmpty()
            val reflection = reflectionByDay[key].orEmpty()
            val hasContent = items.isNotEmpty() || reflection.isNotBlank()
            if (!hasContent) null
            else HistoryDay(
                dayKey = key,
                doneCount = items.count { it.done },
                totalCount = items.size,
                reflection = reflection,
            )
        }.sortedByDescending { it.dayKey }
    }
}
