package com.pauta.app.ui

import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.domain.HabitModel

/**
 * One habit's actionable state today — the web's habitDayStatus() shape.
 * Derived from the same HabitCalculator math as the Marés grid so every
 * surface (Hoje strip, conclude sheet, widget) agrees with the grid.
 * // PT: o estado acionável de hoje de um hábito, partilhado entre ecrãs.
 */
data class TideToday(
    val habit: HabitEntity,
    val state: DayState,
    val isCount: Boolean,
    val count: Int,
    val target: Int,
)

/**
 * The actionable slice of Marés for [today] — daily habits always; weekly/
 * monthly only on an eligible, still-open day (DONE/RESPIRO/EMPTY states;
 * everything else is hidden, exactly like the web's todayTides).
 */
fun computeTodayTides(
    habits: List<HabitEntity>,
    logs: List<HabitLogEntity>,
    respiros: List<HabitRespiroEntity>,
    counts: List<HabitCountEntity>,
    today: String,
): List<TideToday> {
    val logsByHabit = logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
    val respByHabit = respiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
    val countsByHabit = counts.groupBy { it.habitId }.mapValues { e -> e.value.associate { it.dayKey to it.count } }
    return habits.mapNotNull { h ->
        val model = HabitModel(
            id = h.id, createdAt = h.createdAt, cadence = h.cadence, anchor = h.anchor,
            weekdays = h.weekdays, recurrence = h.recurrence, endsAt = h.endsAt,
            log = logsByHabit[h.id].orEmpty(), respiros = respByHabit[h.id].orEmpty(),
        )
        val state = HabitCalculator.dayState(model, today, today)
        if (state != DayState.DONE && state != DayState.RESPIRO && state != DayState.EMPTY) return@mapNotNull null
        TideToday(
            habit = h,
            state = state,
            isCount = h.target != null && h.cadence == "daily",
            count = countsByHabit[h.id]?.get(today) ?: 0,
            target = h.target ?: 1,
        )
    }
}
