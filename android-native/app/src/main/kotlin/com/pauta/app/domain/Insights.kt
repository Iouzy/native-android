package com.pauta.app.domain

import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity

/** A "navigator" rank from lifetime completed habit-days (Aprendiz → Almirante). */
data class NavigatorLevel(val name: String, val subtitle: String, val totalDays: Int)

/**
 * Small insights/summary computations, ported from store.jsx (navigatorLevel /
 * totalDoneDays + the overall monthly %). Pure + unit-tested.
 */
object Insights {

    // Ported from NAVIGATOR_LEVELS in store.jsx (Portuguese source names).
    private val LEVELS = listOf(
        5000 to ("Almirante" to "mestre dos mares"),
        2000 to ("Navegador" to "com N maiúsculo"),
        1000 to ("Capitão" to "comanda a sua embarcação"),
        600 to ("Piloto" to "lê a água e o tempo"),
        300 to ("Timoneiro" to "leme firme"),
        100 to ("Marujo" to "já é da tripulação"),
        30 to ("Grumete" to "aprendeu as cordas"),
        0 to ("Aprendiz" to "pés ainda em terra firme"),
    )

    fun navigatorLevel(totalDoneDays: Int): NavigatorLevel {
        val (_, names) = LEVELS.first { totalDoneDays >= it.first }
        return NavigatorLevel(names.first, names.second, totalDoneDays)
    }

    /** Lifetime completed habit-days = number of log entries across all habits. */
    fun totalDoneDays(logs: List<HabitLogEntity>): Int = logs.size

    data class MonthInsights(
        val level: NavigatorLevel,
        val overallPct: Int?,
        val activeHabits: Int,
    )

    fun monthInsights(
        habits: List<HabitEntity>,
        logs: List<HabitLogEntity>,
        respiros: List<HabitRespiroEntity>,
        counts: List<HabitCountEntity>,
        year: Int,
        month: Int,
        todayKey: String,
    ): MonthInsights {
        val logsByHabit = logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        val respirosByHabit = respiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        val countsByHabit = counts.groupBy { it.habitId }.mapValues { e -> e.value.associate { it.dayKey to it.count } }
        val pct = HabitCalculator.overallMonthPct(habits, logsByHabit, respirosByHabit, countsByHabit, year, month, todayKey)
        return MonthInsights(
            level = navigatorLevel(totalDoneDays(logs)),
            overallPct = pct,
            activeHabits = habits.size,
        )
    }
}
