package com.pauta.app.domain

import com.pauta.app.data.entity.IntentionEntity

/** A carry-over offer: the most recent past day that still has unfinished
 *  intentions, and those items. */
data class CarrySource(val dayKey: String, val items: List<IntentionEntity>)

/**
 * Pure Hoje-tab helpers, kept Room-free so they're unit-testable. // PT:
 * auxiliares puros da tab Hoje, testáveis sem Room.
 */
object HojeLogic {

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
}
