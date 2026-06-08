package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity

/** One past day in the history view: its reflection + intentions done/total. */
data class DayHistoryItem(
    val dayKey: String,
    val reflection: String,
    val doneCount: Int,
    val totalCount: Int,
)

/**
 * Builds the history list from the archived days + all intentions. A day appears
 * if it is before today AND has at least one intention or a non-blank reflection.
 * Day keys are taken from BOTH tables (an intention can exist for a day that has
 * no reflection row, and vice-versa). Newest first. Pure + unit-tested.
 */
object HistoryBuilder {
    fun build(
        days: List<DayEntity>,
        intentions: List<IntentionEntity>,
        todayKey: String,
    ): List<DayHistoryItem> {
        val intentionsByDay = intentions.groupBy { it.dayKey }
        val reflectionByDay = days.associate { it.dayKey to it.reflection }
        val keys = reflectionByDay.keys + intentionsByDay.keys
        return keys
            .filter { it < todayKey }
            .map { key ->
                val ints = intentionsByDay[key].orEmpty()
                DayHistoryItem(
                    dayKey = key,
                    reflection = reflectionByDay[key] ?: "",
                    doneCount = ints.count { it.done },
                    totalCount = ints.size,
                )
            }
            .filter { it.totalCount > 0 || it.reflection.isNotBlank() }
            .sortedByDescending { it.dayKey }
    }
}
