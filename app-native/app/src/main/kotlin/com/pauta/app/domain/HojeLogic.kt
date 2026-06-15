package com.pauta.app.domain

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.IntentionEntity

/** A carry-over offer: the most recent past day that still has unfinished
 *  intentions, and those items. */
data class CarrySource(val dayKey: String, val items: List<IntentionEntity>)

/** E2 · a surfaced memory: a past day's reflection that fell on the same
 *  month-day as today, [yearsAgo] whole years back (≥1). */
data class Memory(val dayKey: String, val reflection: String, val yearsAgo: Int)

/**
 * Pure Hoje-tab helpers, kept Room-free so they're unit-testable. // PT:
 * auxiliares puros da tab Hoje, testáveis sem Room.
 */
object HojeLogic {

    /** Time-of-day buckets in canonical display order; null = "sem hora" (last),
     *  matching the web's grouping. */
    val WHEN_ORDER: List<String?> = listOf("manha", "tarde", "noite", null)

    /**
     * Bucket an already priority-sorted list into time-of-day groups, in
     * canonical order (manhã → tarde → noite → sem hora), dropping empty groups
     * and preserving each group's incoming order. // PT: agrupa por altura do dia
     * mantendo a ordem de prioridade dentro de cada grupo.
     */
    fun groupByTimeOfDay(sortedByPriority: List<IntentionEntity>): List<Pair<String?, List<IntentionEntity>>> =
        WHEN_ORDER
            .map { w -> w to sortedByPriority.filter { it.timeOfDay == w } }
            .filter { it.second.isNotEmpty() }

    /**
     * Mirrors the web's carry-over: scan archived days older than [todayKey],
     * newest first, and return the first day that has unfinished, non-blank
     * intentions — offered as a one-tap "bring forward". Null when there's
     * nothing to carry. // PT: dia arquivado mais recente com intenções por
     * concluir.
     */
    fun carrySource(all: List<IntentionEntity>, todayKey: String): CarrySource? {
        val pastKeys = all.asSequence()
            .map { it.dayKey }
            .filter { it < todayKey }
            .distinct()
            .sortedDescending()
        for (key in pastKeys) {
            val items = all.filter { it.dayKey == key && !it.done && it.text.isNotBlank() }
                .sortedBy { it.position }
            if (items.isNotEmpty()) return CarrySource(key, items)
        }
        return null
    }

    /**
     * Memórias (E2): past reflections that landed on the same calendar month-day
     * as [todayKey], one or more whole years ago — newest year first. A blank
     * reflection is never a memory; today itself and future days never match
     * (yearsAgo ≥ 1). Day keys are `YYYY-MM-DD`, so the month-day is the suffix
     * after the year and an exact-string compare also handles Feb 29 correctly (a
     * non-leap prior year simply has no such row). // PT: reflexões de anos
     * anteriores no mesmo dia do calendário, da mais recente para a mais antiga.
     */
    fun memories(days: List<DayEntity>, todayKey: String): List<Memory> {
        if (todayKey.length < 10) return emptyList()
        val todayYear = todayKey.substring(0, 4).toIntOrNull() ?: return emptyList()
        val monthDay = todayKey.substring(5) // "MM-DD"
        return days.asSequence()
            .filter { it.reflection.isNotBlank() }
            .filter { it.dayKey.length >= 10 && it.dayKey.substring(5) == monthDay }
            .mapNotNull { d ->
                val year = d.dayKey.substring(0, 4).toIntOrNull() ?: return@mapNotNull null
                val yearsAgo = todayYear - year
                if (yearsAgo >= 1) Memory(d.dayKey, d.reflection, yearsAgo) else null
            }
            .sortedByDescending { it.dayKey } // newest year (fewest yearsAgo) first
            .toList()
    }
}
