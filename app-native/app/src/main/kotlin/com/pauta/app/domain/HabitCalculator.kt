package com.pauta.app.domain

/**
 * Cadence / streak / tier / Respiro math for Marés (tides), ported faithfully
 * from store.jsx so the native app's numbers match the web exactly.
 *
 * Pure and Room-free: it operates on a plain [HabitModel] (the habit's static
 * fields plus its sets of done/respiro day keys), which makes it unit-testable
 * and keeps the domain layer independent of persistence. Day-key strings sort
 * lexicographically = chronologically, so `<=`/`>=` comparisons are valid.
 *
 * Core rules preserved from the web:
 * - **Respiros keep the streak** (count as active) but are **removed from the
 *   completion-% denominator** — an honest rest is neither a success nor a miss.
 * - **Daily off-days** (a weekday schedule) are skipped entirely: never observed,
 *   never missed.
 * - **Weekly/monthly tides** are one mark per period; an unfinished current
 *   period never breaks the streak.
 * - Streaks are reported in **days** (weekly ×7, monthly ×30) so the day-based
 *   tier thresholds stay meaningful.
 * // PT: matemática das Marés portada da store.jsx — respiros mantêm a streak e
 * saem do denominador; off-days não contam; períodos contam uma marca.
 */

/** A habit reduced to what the math needs. Weekdays use the JS convention
 *  (0=Sun..6=Sat), matching storage. */
data class HabitModel(
    val id: String,
    val createdAt: Long,
    val cadence: String = "daily",        // "daily" | "weekly" | "monthly"
    val anchor: Int? = null,
    val weekdays: List<Int> = emptyList(),
    val recurrence: String = "forever",   // "forever" | "period" | "month"
    val endsAt: Long? = null,
    val log: Set<String> = emptySet(),     // day keys marked done
    val respiros: Set<String> = emptySet(),// day keys marked respiro
)

enum class MarkKind { DONE, RESPIRO, NONE }

data class StreakResult(val days: Int, val respiros: Int, val units: Int, val unit: String)
data class PeriodStats(val observed: Int, val done: Int, val respiros: Int)
data class Tier(val min: Int, val name: String, val subtitle: String)

object HabitCalculator {

    // Streak tiers (Onda → Tsunami), highest first. PT source strings; the UI
    // translates via tr() (which falls back to PT).
    val TIDE_TIERS = listOf(
        Tier(720, "Tsunami", "uma força que ninguém trava"),
        Tier(360, "Oceano", "já não é uma maré"),
        Tier(240, "Maré anual", "quase um ano de constância"),
        Tier(120, "Maré viva", "a mais forte do mês"),
        Tier(60, "Maré alta", "a corrente é forte"),
        Tier(30, "Maré média", "a corrente assentou"),
        Tier(7, "Maré baixa", "a água começou a subir"),
        Tier(1, "Onda", "primeira agitação"),
    )

    // Navigator levels (Aprendiz → Almirante), highest first.
    val NAVIGATOR_LEVELS = listOf(
        Tier(5000, "Almirante", "mestre dos mares"),
        Tier(2000, "Navegador", "com N maiúsculo"),
        Tier(1000, "Capitão", "comanda a sua embarcação"),
        Tier(600, "Piloto", "lê a água e o tempo"),
        Tier(300, "Timoneiro", "leme firme"),
        Tier(100, "Marujo", "já é da tripulação"),
        Tier(30, "Grumete", "aprendeu as cordas"),
        Tier(0, "Aprendiz", "pés ainda em terra firme"),
    )

    const val HABIT_MATURITY_DAYS = 7

    // ── basic accessors ───────────────────────────────────────
    fun createdKey(h: HabitModel): String = DateUtils.dayKeyOf(h.createdAt)

    /** Last active day for non-recurring habits; null = "forever". */
    fun endKey(h: HabitModel): String? = when (h.recurrence) {
        "month" -> DateUtils.monthEnd(createdKey(h))
        "period" -> h.endsAt?.let { DateUtils.dayKeyOf(it) }
        else -> null
    }

    /** Is the habit within its active window on [dayKey]? */
    fun isActiveOn(h: HabitModel, dayKey: String): Boolean {
        if (dayKey < createdKey(h)) return false
        val end = endKey(h)
        return end == null || dayKey <= end
    }

    private fun cadenceUnitShort(c: String) = when (c) {
        "weekly" -> "sem"; "monthly" -> "mês"; else -> "d"
    }

    /** Is the day inside a daily tide's weekday schedule? Empty schedule = every
     *  day. Only meaningful for daily cadence. */
    fun dailyDueOn(h: HabitModel, dayKey: String): Boolean {
        if (h.weekdays.isEmpty()) return true
        return DateUtils.weekdayJs(dayKey) in h.weekdays
    }

    /** [start, end] day keys of the cadence period containing [dayKey]. */
    fun periodRange(h: HabitModel, dayKey: String): Pair<String, String> = when (h.cadence) {
        "weekly" -> DateUtils.weekStart(dayKey) to DateUtils.weekEnd(dayKey)
        "monthly" -> DateUtils.monthStart(dayKey) to DateUtils.monthEnd(dayKey)
        else -> dayKey to dayKey
    }

    /** Within the period containing [dayKey], the mark that period carries:
     *  DONE wins over RESPIRO; the carrying key is returned too. */
    fun periodMark(h: HabitModel, dayKey: String): Pair<MarkKind, String?> {
        val (start, end) = periodRange(h, dayKey)
        var k = start
        var respiroKey: String? = null
        while (k <= end) {
            if (k in h.log) return MarkKind.DONE to k
            if (respiroKey == null && k in h.respiros) respiroKey = k
            k = DateUtils.addDays(k, 1)
        }
        return if (respiroKey != null) MarkKind.RESPIRO to respiroKey else MarkKind.NONE to null
    }

    /** Is this the fixed anchor day of its period? Manual (anchor null) and daily
     *  tides treat every day as eligible. */
    fun isAnchorDay(h: HabitModel, dayKey: String): Boolean {
        if (h.cadence == "daily") return true
        val anchor = h.anchor ?: return true
        return when (h.cadence) {
            "weekly" -> DateUtils.weekdayJs(dayKey) == anchor
            else -> { // monthly: clamp anchor to the month length
                val parts = dayKey.split("-")
                val days = DateUtils.daysInMonth(parts[0].toInt(), parts[1].toInt())
                DateUtils.dayOfMonth(dayKey) == minOf(anchor, days)
            }
        }
    }

    private fun streakDaysFromUnits(c: String, units: Int) = when (c) {
        "weekly" -> units * 7; "monthly" -> units * 30; else -> units
    }

    // ── streaks ───────────────────────────────────────────────
    fun currentStreak(h: HabitModel, todayKey: String = DateUtils.todayKey()): StreakResult {
        val c = h.cadence
        val created = createdKey(h)
        val end = endKey(h)
        val stopKey = if (end != null && end < todayKey) end else todayKey
        val unit = cadenceUnitShort(c)

        if (c == "daily") {
            var k = stopKey
            var streak = 0
            var respiros = 0
            while (k >= created) {
                if (!dailyDueOn(h, k)) { k = DateUtils.addDays(k, -1); continue }
                if (k in h.log) streak++
                else if (k in h.respiros) { streak++; respiros++ }
                else break
                k = DateUtils.addDays(k, -1)
            }
            return StreakResult(streak, respiros, streak, unit)
        }

        // Periodic: walk period→period back from the current one.
        var cursor = stopKey
        var units = 0
        var respiros = 0
        var first = true
        while (cursor >= created) {
            val (start, periodEnd) = periodRange(h, cursor)
            val (kind, _) = periodMark(h, cursor)
            when (kind) {
                MarkKind.DONE -> units++
                MarkKind.RESPIRO -> { units++; respiros++ }
                MarkKind.NONE -> if (!(first && periodEnd >= todayKey)) break
            }
            first = false
            cursor = DateUtils.addDays(start, -1)
        }
        return StreakResult(streakDaysFromUnits(c, units), respiros, units, unit)
    }

    fun bestStreak(h: HabitModel, todayKey: String = DateUtils.todayKey()): Int {
        val c = h.cadence
        val created = createdKey(h)
        val end = endKey(h)
        val stopKey = if (end != null && end < todayKey) end else todayKey
        var best = 0
        var run = 0

        if (c == "daily") {
            var k = created
            while (k <= stopKey) {
                if (!dailyDueOn(h, k)) { k = DateUtils.addDays(k, 1); continue }
                if (k in h.log || k in h.respiros) { run++; if (run > best) best = run } else run = 0
                k = DateUtils.addDays(k, 1)
            }
            return best
        }
        var cursor = created
        while (cursor <= stopKey) {
            val (_, periodEnd) = periodRange(h, cursor)
            val (kind, _) = periodMark(h, cursor)
            if (kind != MarkKind.NONE) { run++; if (run > best) best = run } else run = 0
            cursor = DateUtils.addDays(periodEnd, 1)
        }
        return best
    }

    // ── monthly stats ─────────────────────────────────────────
    /** Count observation units over [startKey, endKey]. Daily counts days
     *  (off-days excluded); weekly/monthly count periods. */
    fun periodStats(h: HabitModel, startKey: String, endKey: String): PeriodStats {
        var observed = 0; var done = 0; var respiros = 0
        if (h.cadence == "daily") {
            var k = startKey
            while (k <= endKey) {
                if (dailyDueOn(h, k)) {
                    observed++
                    if (k in h.log) done++ else if (k in h.respiros) respiros++
                }
                k = DateUtils.addDays(k, 1)
            }
            return PeriodStats(observed, done, respiros)
        }
        var cursor = startKey
        while (cursor <= endKey) {
            val (_, pEnd) = periodRange(h, cursor)
            observed++
            when (periodMark(h, cursor).first) {
                MarkKind.DONE -> done++
                MarkKind.RESPIRO -> respiros++
                MarkKind.NONE -> {}
            }
            cursor = DateUtils.addDays(pEnd, 1)
        }
        return PeriodStats(observed, done, respiros)
    }

    /** Effective observed range within a month, or null if the habit didn't
     *  exist then. [month] is 1..12. */
    fun observedRangeInMonth(
        h: HabitModel, year: Int, month: Int, todayKey: String = DateUtils.todayKey(),
    ): Pair<String, String>? {
        val monthStart = "%04d-%02d-01".format(year, month)
        val monthEnd = "%04d-%02d-%02d".format(year, month, DateUtils.daysInMonth(year, month))
        val created = createdKey(h)
        val end = endKey(h)
        val start = if (created > monthStart) created else monthStart
        var rangeEnd = if (monthEnd < todayKey) monthEnd else todayKey
        if (end != null && end < rangeEnd) rangeEnd = end
        if (start > rangeEnd) return null
        return start to rangeEnd
    }

    fun maturityUnits(h: HabitModel): Int = when (h.cadence) {
        "weekly" -> 4; "monthly" -> 2; else -> HABIT_MATURITY_DAYS
    }

    /** Monthly completion %, with respiros removed from the denominator. Null
     *  when there's nothing observed (or everything was a respiro). */
    fun pctInMonth(
        h: HabitModel, year: Int, month: Int, todayKey: String = DateUtils.todayKey(),
    ): Int? {
        val range = observedRangeInMonth(h, year, month, todayKey) ?: return null
        val s = periodStats(h, range.first, range.second)
        if (s.observed == 0) return null
        val denom = s.observed - s.respiros
        if (denom <= 0) return null
        return Math.round(s.done.toDouble() / denom * 100).toInt()
    }

    /** Overall % for a month: the average over mature habits (observed units,
     *  net of respiros, ≥ their maturity threshold). */
    fun overallPctInMonth(
        habits: List<HabitModel>, year: Int, month: Int, todayKey: String = DateUtils.todayKey(),
    ): Int? {
        val mature = habits.mapNotNull { h ->
            val range = observedRangeInMonth(h, year, month, todayKey)
            val pct = pctInMonth(h, year, month, todayKey)
            if (range == null || pct == null) return@mapNotNull null
            val s = periodStats(h, range.first, range.second)
            val obsNet = s.observed - s.respiros
            if (obsNet >= maturityUnits(h)) pct else null
        }
        if (mature.isEmpty()) return null
        return Math.round(mature.sum().toDouble() / mature.size).toInt()
    }

    // ── tiers / levels ────────────────────────────────────────
    fun tideTier(days: Int): Tier? {
        if (days <= 0) return null
        return TIDE_TIERS.firstOrNull { days >= it.min }
    }

    fun navigatorLevel(totalDoneDays: Int): Tier =
        NAVIGATOR_LEVELS.firstOrNull { totalDoneDays >= it.min } ?: NAVIGATOR_LEVELS.last()

    fun totalDoneDays(habits: List<HabitModel>): Int = habits.sumOf { it.log.size }

    /** The state of a single grid cell. */
    enum class DayState { DONE, RESPIRO, EMPTY, OFF, LOCKED, FUTURE, PRE }

    /**
     * State of a tide on a given day for the Marés grid, mirroring the web's
     * habitDayStatus: PRE before it existed, OFF outside its window / a daily
     * off-day, FUTURE for days ahead, LOCKED for a non-anchor or already-taken
     * period day, DONE/RESPIRO when marked, else EMPTY (actionable today/past).
     */
    fun dayState(h: HabitModel, dayKey: String, todayKey: String): DayState {
        if (dayKey < createdKey(h)) return DayState.PRE
        val end = endKey(h)
        if (end != null && dayKey > end) return DayState.OFF
        if (h.cadence != "daily") {
            val (kind, key) = periodMark(h, dayKey)
            if (kind == MarkKind.DONE) return if (key == dayKey) DayState.DONE else DayState.LOCKED
            if (kind == MarkKind.RESPIRO) return if (key == dayKey) DayState.RESPIRO else DayState.LOCKED
            if (!isAnchorDay(h, dayKey)) return DayState.LOCKED
            return if (dayKey > todayKey) DayState.FUTURE else DayState.EMPTY
        }
        if (!dailyDueOn(h, dayKey)) return DayState.OFF
        if (dayKey in h.log) return DayState.DONE
        if (dayKey in h.respiros) return DayState.RESPIRO
        return if (dayKey > todayKey) DayState.FUTURE else DayState.EMPTY
    }
}
