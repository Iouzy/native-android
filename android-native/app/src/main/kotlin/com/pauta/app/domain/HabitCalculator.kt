package com.pauta.app.domain

import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** All pure calculations for habits. Matches store.jsx logic exactly. */
object HabitCalculator {

    // ── Cadence period helpers ─────────────────────────────────────────────

    /**
     * Returns the period key that contains [dayKey] for [habit].
     * - daily → the day itself
     * - weekly → Monday of that week ("YYYY-MM-DD")
     * - monthly → "YYYY-MM" of that month
     */
    fun periodKey(habit: HabitEntity, dayKey: String): String {
        return when (habit.cadence) {
            "weekly"  -> DateUtils.weekStart(dayKey)
            "monthly" -> dayKey.substring(0, 7)
            else      -> dayKey
        }
    }

    /** All dayKeys in the period containing [dayKey] for [habit]. */
    fun periodDays(habit: HabitEntity, dayKey: String): List<String> {
        return when (habit.cadence) {
            "weekly" -> {
                val start = DateUtils.weekStart(dayKey)
                (0..6).map { DateUtils.addDays(start, it) }
            }
            "monthly" -> {
                val (y, m) = DateUtils.yearMonth(dayKey)
                val start = DateUtils.monthKey(y, m)
                val n = DateUtils.daysInMonth(dayKey)
                (0 until n).map { DateUtils.addDays(start, it) }
            }
            else -> listOf(dayKey)
        }
    }

    // ── Day state ─────────────────────────────────────────────────────────

    enum class DayState { DONE, RESPIRO, EMPTY, LOCKED, OFF, PRE, FUTURE }

    data class DayStatus(
        val state: DayState,
        val count: Int = 0,
        val target: Int = 0
    )

    fun weekdaysOf(habit: HabitEntity): Set<Int> {
        return try {
            Json.parseToJsonElement(habit.weekdays)
                .jsonArray
                .mapNotNull { it.jsonPrimitive.intOrNull }
                .toSet()
        } catch (e: Exception) { emptySet() }
    }

    /**
     * Compute the display state for [habit] on [dayKey].
     *
     * [logSet]      — set of all dayKeys where this habit is done
     * [respiroMap]  — map dayKey → HabitRespiroEntity
     * [countMap]    — map dayKey → count (for countable habits)
     * [todayKey]    — today's dayKey (to detect future days)
     */
    fun dayStatus(
        habit: HabitEntity,
        logSet: Set<String>,
        respiroMap: Map<String, HabitRespiroEntity>,
        countMap: Map<String, Int>,
        dayKey: String,
        todayKey: String
    ): DayStatus {
        val habitCreated = DateUtils.keyFromMs(habit.createdAt)

        // Before the habit existed
        if (dayKey < habitCreated) return DayStatus(DayState.PRE)
        // Future
        if (dayKey > todayKey) return DayStatus(DayState.FUTURE)
        // Ended
        val endsAt = habit.endsAt
        if (endsAt != null && dayKey > DateUtils.keyFromMs(endsAt)) return DayStatus(DayState.PRE)

        // Daily schedule restriction
        if (habit.cadence == "daily") {
            val wds = weekdaysOf(habit)
            if (wds.isNotEmpty() && DateUtils.weekdayOf(dayKey) !in wds) {
                return DayStatus(DayState.OFF)
            }
        }

        // Countable habit
        if (habit.target > 0) {
            val count = countMap[dayKey] ?: 0
            val done = count >= habit.target
            return if (done) DayStatus(DayState.DONE, count, habit.target)
            else if (respiroMap.containsKey(dayKey)) DayStatus(DayState.RESPIRO, count, habit.target)
            else DayStatus(DayState.EMPTY, count, habit.target)
        }

        // Boolean habit
        return when {
            logSet.contains(dayKey) -> DayStatus(DayState.DONE)
            respiroMap.containsKey(dayKey) -> DayStatus(DayState.RESPIRO)
            habit.cadence != "daily" -> {
                // For weekly/monthly: check if ANY other day in the period is done
                val allPeriodDays = periodDays(habit, dayKey)
                val periodHasDone = allPeriodDays.any { d -> d != dayKey && logSet.contains(d) }
                if (periodHasDone) DayStatus(DayState.LOCKED) else DayStatus(DayState.EMPTY)
            }
            else -> DayStatus(DayState.EMPTY)
        }
    }

    // ── Streak ────────────────────────────────────────────────────────────

    data class StreakInfo(
        val streakDays: Int,
        val bestDays: Int,
        val tierName: String
    )

    /** Compute streak as equivalent "days" (weekly unit=7, monthly unit=30). */
    fun streakInfo(
        habit: HabitEntity,
        logSet: Set<String>,
        respiroSet: Set<String>,
        todayKey: String
    ): StreakInfo {
        val streak = computeStreak(habit, logSet, respiroSet, todayKey)
        val best = computeBestStreak(habit, logSet, respiroSet, todayKey)
        return StreakInfo(streak, best, tierName(streak))
    }

    private fun computeStreak(
        habit: HabitEntity,
        logSet: Set<String>,
        respiroSet: Set<String>,
        todayKey: String
    ): Int {
        val unitDays = when (habit.cadence) { "weekly" -> 7; "monthly" -> 30; else -> 1 }
        var count = 0
        var cursor = todayKey
        var first = true

        // Walk backwards period-by-period
        repeat(4000) {
            val periodDays = periodDays(habit, cursor)
            val periodStart = periodDays.first()
            val periodEnd = periodDays.last()
            val habitCreated = DateUtils.keyFromMs(habit.createdAt)

            if (periodStart < habitCreated) return count * unitDays

            // Daily weekday-schedule off-days don't count and don't break the
            // streak (matches habitDailyDueOn in store.jsx).
            if (habit.cadence == "daily" && isDailyOffDay(habit, periodStart)) {
                cursor = DateUtils.addDays(periodStart, -1)
                return@repeat
            }

            val hasDone = periodDays.any { logSet.contains(it) }
            val hasRespiro = periodDays.any { respiroSet.contains(it) }

            when {
                hasDone || hasRespiro -> count++
                // The current, still-in-progress weekly/monthly period may be empty
                // without breaking the streak — only a missed *past* period stops it.
                // Matches store.jsx habitCurrentStreak; daily still breaks on an
                // empty today (no grace), as in the web build.
                habit.cadence != "daily" && first && periodEnd >= todayKey -> { /* grace */ }
                else -> return count * unitDays
            }

            first = false
            // Move to the period before
            cursor = DateUtils.addDays(periodStart, -1)
        }
        return count * unitDays
    }

    private fun computeBestStreak(
        habit: HabitEntity,
        logSet: Set<String>,
        respiroSet: Set<String>,
        todayKey: String
    ): Int {
        val unitDays = when (habit.cadence) { "weekly" -> 7; "monthly" -> 30; else -> 1 }
        val habitCreated = DateUtils.keyFromMs(habit.createdAt)
        var best = 0
        var current = 0
        var cursor = todayKey

        repeat(3650) {
            if (cursor < habitCreated) {
                best = maxOf(best, current)
                return best * unitDays
            }
            val periodDays = periodDays(habit, cursor)
            val periodStart = periodDays.first()

            // Daily off-days are skipped, not treated as a miss (best streak too).
            if (habit.cadence == "daily" && isDailyOffDay(habit, periodStart)) {
                cursor = DateUtils.addDays(periodStart, -1)
                return@repeat
            }

            val hasDone = periodDays.any { logSet.contains(it) }
            val hasRespiro = periodDays.any { respiroSet.contains(it) }

            if (hasDone || hasRespiro) {
                current++
                best = maxOf(best, current)
            } else {
                current = 0
            }
            cursor = DateUtils.addDays(periodStart, -1)
        }
        return best * unitDays
    }

    private fun isDailyOffDay(habit: HabitEntity, dayKey: String): Boolean {
        val wds = weekdaysOf(habit)
        return wds.isNotEmpty() && DateUtils.weekdayOf(dayKey) !in wds
    }

    private fun tierName(streakDays: Int): String = when {
        streakDays >= 720 -> "Tsunami"
        streakDays >= 360 -> "Oceano"
        streakDays >= 240 -> "Maré Anual"
        streakDays >= 120 -> "Maré Viva"
        streakDays >= 60  -> "Maré Alta"
        streakDays >= 30  -> "Maré Média"
        streakDays >= 7   -> "Maré Baixa"
        streakDays >= 1   -> "Onda"
        else              -> ""
    }

    // ── Monthly percentage ─────────────────────────────────────────────────

    data class MonthStats(
        val pct: Int?,          // null if immature (<7 observed units)
        val observed: Int,
        val done: Int,
        val respiros: Int
    )

    fun monthStats(
        habit: HabitEntity,
        logSet: Set<String>,
        respiroSet: Set<String>,
        countMap: Map<String, Int>,
        year: Int,
        month: Int,
        todayKey: String
    ): MonthStats {
        val monthKeyStr = DateUtils.monthKey(year, month)
        val daysInMonth = DateUtils.daysInMonth(monthKeyStr)
        val habitCreated = DateUtils.keyFromMs(habit.createdAt)
        val endsAt = habit.endsAt?.let { DateUtils.keyFromMs(it) }

        var observed = 0
        var done = 0
        var respiros = 0
        val seenPeriods = mutableSetOf<String>()

        for (d in 0 until daysInMonth) {
            val dk = DateUtils.addDays(monthKeyStr, d)
            if (dk > todayKey) break
            if (dk < habitCreated) continue
            if (endsAt != null && dk > endsAt) continue

            val pk = periodKey(habit, dk)
            if (seenPeriods.contains(pk)) continue
            seenPeriods.add(pk)

            // Check if the habit was "on" for this period
            val periodDays = periodDays(habit, dk)
            val isOff = habit.cadence == "daily" && weekdaysOf(habit).let { wds ->
                wds.isNotEmpty() && DateUtils.weekdayOf(dk) !in wds
            }
            if (isOff) continue

            observed++
            val hasDone = if (habit.target > 0) {
                (countMap[dk] ?: 0) >= habit.target
            } else {
                periodDays.any { logSet.contains(it) }
            }
            val hasRespiro = periodDays.any { respiroSet.contains(it) }
            when {
                hasDone -> done++
                hasRespiro -> respiros++
            }
        }

        val denominator = observed - respiros
        // Round (not truncate) to match store.jsx habitPctInMonth's Math.round.
        val pct = if (observed < 7 || denominator <= 0) null
                  else Math.round(done * 100.0 / denominator).toInt().coerceIn(0, 100)
        return MonthStats(pct, observed, done, respiros)
    }

    // ── Overall monthly score across all habits ────────────────────────────

    fun overallMonthPct(
        habits: List<HabitEntity>,
        logsByHabit: Map<String, Set<String>>,
        respirosByHabit: Map<String, Set<String>>,
        countsByHabit: Map<String, Map<String, Int>>,
        year: Int,
        month: Int,
        todayKey: String
    ): Int? {
        val scores = habits.mapNotNull { habit ->
            monthStats(
                habit,
                logsByHabit[habit.id] ?: emptySet(),
                respirosByHabit[habit.id] ?: emptySet(),
                countsByHabit[habit.id] ?: emptyMap(),
                year, month, todayKey
            ).pct
        }
        return if (scores.isEmpty()) null else scores.average().toInt()
    }
}
