package com.pauta.app.domain

import java.text.SimpleDateFormat
import java.util.*

// All date math uses the device's local calendar (matching how the web app uses
// UTC midnight in JS). "dayKey" is always "YYYY-MM-DD".

object DateUtils {

    private val sdf get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun todayKey(): String = sdf.format(Date())

    fun keyFromMs(ms: Long): String = sdf.format(Date(ms))

    fun msFromKey(key: String): Long {
        return try { sdf.parse(key)?.time ?: 0L } catch (e: Exception) { 0L }
    }

    /** Add (or subtract) days to a dayKey string. */
    fun addDays(key: String, n: Int): String {
        val cal = calFromKey(key)
        cal.add(Calendar.DAY_OF_YEAR, n)
        return sdf.format(cal.time)
    }

    /** Monday-based week start for the week containing `key`. */
    fun weekStart(key: String): String {
        val cal = calFromKey(key)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return sdf.format(cal.time)
    }

    /** First day of the month containing `key`. */
    fun monthStart(key: String): String {
        val cal = calFromKey(key)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return sdf.format(cal.time)
    }

    /** Day-of-week for `key`: 0=Monday … 6=Sunday (matching JS getDay()-adjusted). */
    fun weekdayOf(key: String): Int {
        val cal = calFromKey(key)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            Calendar.SUNDAY    -> 6
            else               -> 0
        }
    }

    /** Last day of the month containing `key`. */
    fun monthEnd(key: String): String {
        val cal = calFromKey(key)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        return sdf.format(cal.time)
    }

    /** Number of days in the month of `key`. */
    fun daysInMonth(key: String): Int {
        return calFromKey(key).getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Year and 1-based month for a dayKey. */
    fun yearMonth(key: String): Pair<Int, Int> {
        val cal = calFromKey(key)
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    /** 1-based day-of-month for a dayKey. */
    fun dayOfMonth(key: String): Int = calFromKey(key).get(Calendar.DAY_OF_MONTH)

    /** "YYYY-MM-DD" for day 1 of (year, 1-based month). */
    fun monthKey(year: Int, month: Int): String =
        "%04d-%02d-01".format(year, month)

    /** Preceding month as (year, month). */
    fun prevMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 1) Pair(year - 1, 12) else Pair(year, month - 1)

    /** Following month as (year, month). */
    fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 12) Pair(year + 1, 1) else Pair(year, month + 1)

    /** Current quarter as "YYYY-Qn". */
    fun currentQuarter(): String {
        val cal = Calendar.getInstance()
        val q = (cal.get(Calendar.MONTH) / 3) + 1
        return "${cal.get(Calendar.YEAR)}-Q$q"
    }

    /** Format elapsed milliseconds as "MM:SS" or "H:MM:SS". */
    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** Format minutes as "Xh Ym" or "Ym". */
    fun formatMinutes(min: Long): String {
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0          -> "${h}h"
            else           -> "${m}m"
        }
    }

    fun calFromKey(key: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val d = sdf.parse(key)
            if (d != null) cal.time = d
        } catch (e: Exception) { /* keep now */ }
        return cal
    }
}
