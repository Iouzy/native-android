package com.pauta.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Date / cadence math, mirroring the helpers in the web app's store.jsx so the
 * native app computes day keys, week/month boundaries and quarters identically.
 *
 * Conventions matched to the web:
 * - A **day key** is `YYYY-MM-DD` in the device's LOCAL time zone (never UTC).
 * - **Weekday numbers** use the JS convention 0=Sun … 6=Sat (the same values
 *   stored in `HabitEntity.weekdays` / `anchor`). Monday-first *display* is a
 *   separate concern handled by the UI.
 * - **Weeks are Monday-based** (a week runs Monday→Sunday), as on the web.
 *
 * Uses java.time (available natively from API 26 = our minSdk), which is
 * DST-safe — no UTC arithmetic hacks needed. // PT: matemática de datas igual à
 * da store.jsx; chaves de dia locais, semanas de segunda a domingo.
 */
object DateUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Today's local day key. */
    fun todayKey(): String = LocalDate.now(zone).toString()

    /** Local day key for an epoch-millis timestamp. */
    fun dayKeyOf(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

    /** Epoch millis at local midnight (start) of a day key. */
    fun startOfDayMs(key: String): Long =
        LocalDate.parse(key).atStartOfDay(zone).toInstant().toEpochMilli()

    /** A day key offset by [n] days (negative = past). DST-safe. */
    fun addDays(key: String, n: Int): String =
        LocalDate.parse(key).plusDays(n.toLong()).toString()

    /** Inclusive count of days from [fromKey] to [toKey] (same day = 1). */
    fun daysBetweenInclusive(fromKey: String, toKey: String): Int =
        (java.time.temporal.ChronoUnit.DAYS.between(
            LocalDate.parse(fromKey), LocalDate.parse(toKey),
        ).toInt()) + 1

    /** Monday of the week containing [key]. */
    fun weekStart(key: String): String =
        LocalDate.parse(key).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /** Sunday of the week containing [key]. */
    fun weekEnd(key: String): String =
        LocalDate.parse(key).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString()

    /** First day of the month containing [key]. */
    fun monthStart(key: String): String =
        LocalDate.parse(key).withDayOfMonth(1).toString()

    /** Last day of the month containing [key]. */
    fun monthEnd(key: String): String =
        LocalDate.parse(key).with(TemporalAdjusters.lastDayOfMonth()).toString()

    /** Number of days in a given month. [month] is 1..12. */
    fun daysInMonth(year: Int, month: Int): Int =
        YearMonth.of(year, month).lengthOfMonth()

    /** Weekday of a day key in the JS convention: 0=Sun, 1=Mon … 6=Sat. */
    fun weekdayJs(key: String): Int =
        LocalDate.parse(key).dayOfWeek.value % 7   // Mon(1)…Sun(7) → 1…6,0

    /** The `YYYY-MM` month key for a day key. */
    fun monthKeyOf(key: String): String = key.substring(0, 7)

    /** Quarter label `YYYY-Qn` for a day key. */
    fun quarterOf(key: String): String {
        val d = LocalDate.parse(key)
        val q = (d.monthValue - 1) / 3 + 1
        return "${d.year}-Q$q"
    }

    /** The current quarter (`YYYY-Qn`). */
    fun currentQuarter(): String = quarterOf(todayKey())

    private fun quarterParts(q: String): Pair<Int, Int> {
        val (y, qq) = q.split("-Q")
        return y.toInt() to qq.toInt()
    }

    fun nextQuarter(q: String): String {
        val (y, n) = quarterParts(q)
        return if (n >= 4) "${y + 1}-Q1" else "$y-Q${n + 1}"
    }

    fun prevQuarter(q: String): String {
        val (y, n) = quarterParts(q)
        return if (n <= 1) "${y - 1}-Q4" else "$y-Q${n - 1}"
    }

    /** Day-of-month (1..31) for a day key. */
    fun dayOfMonth(key: String): Int = LocalDate.parse(key).dayOfMonth
}
