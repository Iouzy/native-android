package com.pauta.app.domain

import com.pauta.app.data.entity.IntentionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The Revisão (insights) math, mirroring store.jsx one-to-one: weeklyReview /
 * monthReview, focusByHour / bestHourStats, habitFocusCorrelation and
 * narrativeStats — same windows, thresholds, rounding and tie-breaking, so the
 * native sheet shows the numbers the web would. // PT: matemática da Revisão,
 * igual à da store.jsx — mesmas janelas, limiares e arredondamentos.
 */
object InsightsMath {

    /** One block and its sessions — the web's `b.sessions` shape. */
    data class BlockSegs(val blockId: String, val segs: List<FocusMath.FocusSeg>)

    /** [HabitModel] carries no name; the insights copy needs one. */
    data class NamedHabit(val name: String, val model: HabitModel)

    data class WeekReview(
        val startKey: String,
        val endKey: String,
        val days: List<String>,
        val focusMs: Long,
        val activeDays: Int,
        val blockCount: Int,
        val topKey: String?,
        val topMs: Long,
        val intTotal: Int,
        val intDone: Int,
        val habitDone: Int,
        val habitObservedSlots: Int,
        val respiros: Int,
        val habitPct: Int?,
        val reflections: Int,
    )

    data class MonthReview(
        val year: Int,
        val month: Int, // 1..12
        val focusMs: Long,
        val activeDays: Int,
        val topKey: String?,
        val topMs: Long,
        val intTotal: Int,
        val intDone: Int,
        val reflections: Int,
        val habitPct: Int?,
        val tideDoneDays: Int,
    )

    data class BestHours(val hours: List<Long>, val total: Long, val peak: Int, val peakMs: Long)

    data class Correlation(
        val id: String,
        val name: String,
        val doneAvg: Long,
        val missAvg: Long,
        val deltaPct: Int,
        val doneN: Int,
        val missN: Int,
    )

    data class Narrative(
        val topHabitName: String?,
        val topHabitDays: Int,
        val peakHour: Int?,
        val highPrioPct: Int?,
    )

    /** dailyFocusMs over every block's sessions (web dailyFocusMs(blocks, k)). */
    fun dailyFocus(blocks: List<BlockSegs>, dayKey: String, now: Long): Long {
        var ms = 0L
        for (b in blocks) ms += FocusMath.dailyFocusMs(b.segs, dayKey, now)
        return ms
    }

    /** store.jsx weeklyReview(): the 7 days ending at [endKey], inclusive. */
    fun weeklyReview(
        blocks: List<BlockSegs>,
        habits: List<NamedHabit>,
        intentions: List<IntentionEntity>,
        reflectionByDay: Map<String, String>,
        endKey: String,
        now: Long,
    ): WeekReview {
        val days = (6 downTo 0).map { DateUtils.addDays(endKey, -it) }
        val startKey = days.first()

        var focusMs = 0L
        var activeDays = 0
        var topKey: String? = null
        var topMs = 0L
        for (k in days) {
            val f = dailyFocus(blocks, k, now)
            focusMs += f
            if (f > 0) activeDays++
            if (f > topMs) { topMs = f; topKey = k }
        }
        val blockCount = blocks.count { b ->
            b.segs.any { DateUtils.dayKeyOf(it.startedAt) in startKey..endKey }
        }

        val byDay = intentions.groupBy { it.dayKey }
        var intTotal = 0
        var intDone = 0
        for (k in days) {
            val items = byDay[k].orEmpty()
            intTotal += items.size
            intDone += items.count { it.done }
        }

        // Habits: cadence-aware — daily counts due days, weekly/monthly count
        // each period overlapping the window once (deduped by period start).
        var habitDone = 0
        var habitObservedSlots = 0
        var respiros = 0
        for (nh in habits) {
            val h = nh.model
            if (h.cadence == "daily") {
                for (k in days) {
                    if (!HabitCalculator.isActiveOn(h, k)) continue
                    if (!HabitCalculator.dailyDueOn(h, k)) continue
                    habitObservedSlots++
                    if (k in h.log) habitDone++
                    else if (k in h.respiros) respiros++
                }
            } else {
                val seen = mutableSetOf<String>()
                for (k in days) {
                    if (!HabitCalculator.isActiveOn(h, k)) continue
                    val start = HabitCalculator.periodRange(h, k).first
                    if (!seen.add(start)) continue
                    habitObservedSlots++
                    when (HabitCalculator.periodMark(h, k).first) {
                        MarkKind.DONE -> habitDone++
                        MarkKind.RESPIRO -> respiros++
                        MarkKind.NONE -> {}
                    }
                }
            }
        }
        val denom = habitObservedSlots - respiros
        val habitPct = if (denom > 0) Math.round(habitDone.toDouble() / denom * 100).toInt() else null

        val reflections = days.count { reflectionByDay[it]?.isNotBlank() == true }

        return WeekReview(
            startKey = startKey, endKey = endKey, days = days,
            focusMs = focusMs, activeDays = activeDays, blockCount = blockCount,
            topKey = topKey, topMs = topMs,
            intTotal = intTotal, intDone = intDone,
            habitDone = habitDone, habitObservedSlots = habitObservedSlots,
            respiros = respiros, habitPct = habitPct,
            reflections = reflections,
        )
    }

    /** store.jsx monthReview(): the month up to today (future days excluded). */
    fun monthReview(
        blocks: List<BlockSegs>,
        habits: List<NamedHabit>,
        intentions: List<IntentionEntity>,
        reflectionByDay: Map<String, String>,
        year: Int,
        month: Int,
        todayKey: String,
        now: Long,
    ): MonthReview {
        val nd = DateUtils.daysInMonth(year, month)
        var focusMs = 0L
        var activeDays = 0
        var topKey: String? = null
        var topMs = 0L
        var intTotal = 0
        var intDone = 0
        var reflections = 0
        val byDay = intentions.groupBy { it.dayKey }
        for (d in 1..nd) {
            val k = "%04d-%02d-%02d".format(year, month, d)
            if (k > todayKey) break
            val f = dailyFocus(blocks, k, now)
            focusMs += f
            if (f > 0) activeDays++
            if (f > topMs) { topMs = f; topKey = k }
            val items = byDay[k].orEmpty()
            intTotal += items.size
            intDone += items.count { it.done }
            if (reflectionByDay[k]?.isNotBlank() == true) reflections++
        }
        val monthStart = "%04d-%02d-01".format(year, month)
        val monthEnd = "%04d-%02d-%02d".format(year, month, nd)
        var tideDoneDays = 0
        for (nh in habits) for (kk in nh.model.log) if (kk in monthStart..monthEnd) tideDoneDays++
        return MonthReview(
            year = year, month = month,
            focusMs = focusMs, activeDays = activeDays, topKey = topKey, topMs = topMs,
            intTotal = intTotal, intDone = intDone, reflections = reflections,
            habitPct = HabitCalculator.overallPctInMonth(habits.map { it.model }, year, month, todayKey),
            tideDoneDays = tideDoneDays,
        )
    }

    /** store.jsx focusByHour(): sessions split across hour boundaries, local time. */
    fun focusByHour(blocks: List<BlockSegs>, now: Long): List<Long> {
        val zone = ZoneId.systemDefault()
        val hours = LongArray(24)
        for (b in blocks) for (seg in b.segs) {
            var start = seg.startedAt
            val end = seg.endedAt ?: now
            while (start < end) {
                val zdt = Instant.ofEpochMilli(start).atZone(zone)
                val nextHour = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = minOf(end, nextHour)
                if (sliceEnd <= start) break // clock weirdness — never loop forever
                hours[zdt.hour] += sliceEnd - start
                start = sliceEnd
            }
        }
        return hours.toList()
    }

    /** store.jsx bestHourStats(): peak ties resolve to the earliest hour. */
    fun bestHourStats(blocks: List<BlockSegs>, now: Long): BestHours {
        val hours = focusByHour(blocks, now)
        val total = hours.sum()
        var peak = -1
        var peakMs = 0L
        hours.forEachIndexed { h, ms -> if (ms > peakMs) { peakMs = ms; peak = h } }
        return BestHours(hours, total, peak, peakMs)
    }

    /**
     * store.jsx habitFocusCorrelation(): over the last [days] days, average
     * daily focus on done vs. missed days per habit. Off-days and respiros are
     * excluded; needs ≥3 samples on each side; sorted by |Δ%| descending.
     */
    fun habitFocusCorrelation(
        habits: List<NamedHabit>,
        blocks: List<BlockSegs>,
        days: Int = 30,
        todayKey: String,
        now: Long,
    ): List<Correlation> {
        val focusCache = mutableMapOf<String, Long>()
        fun focusOn(k: String) = focusCache.getOrPut(k) { dailyFocus(blocks, k, now) }
        val out = mutableListOf<Correlation>()
        for (nh in habits) {
            val h = nh.model
            var doneSum = 0L
            var doneN = 0
            var missSum = 0L
            var missN = 0
            for (i in 0 until days) {
                val k = DateUtils.addDays(todayKey, -i)
                if (!HabitCalculator.isActiveOn(h, k)) continue
                if (h.cadence == "daily" && !HabitCalculator.dailyDueOn(h, k)) continue
                if (k in h.respiros) continue // respiros count neither way
                val f = focusOn(k)
                if (k in h.log) { doneSum += f; doneN++ } else { missSum += f; missN++ }
            }
            if (doneN >= 3 && missN >= 3) {
                val doneAvg = doneSum.toDouble() / doneN
                val missAvg = missSum.toDouble() / missN
                val deltaPct = when {
                    missAvg > 0 -> Math.round((doneAvg - missAvg) / missAvg * 100).toInt()
                    doneAvg > 0 -> 100
                    else -> 0
                }
                out += Correlation(h.id, nh.name, doneAvg.toLong(), missAvg.toLong(), deltaPct, doneN, missN)
            }
        }
        return out.sortedByDescending { kotlin.math.abs(it.deltaPct) }
    }

    /** store.jsx narrativeStats(): longest current streak, peak hour, and the
     *  all-time high-priority completion rate (needs ≥3 to show). */
    fun narrativeStats(
        habits: List<NamedHabit>,
        blocks: List<BlockSegs>,
        intentions: List<IntentionEntity>,
        todayKey: String,
        now: Long,
    ): Narrative {
        var topName: String? = null
        var topDays = 0
        for (nh in habits) {
            val st = HabitCalculator.currentStreak(nh.model, todayKey)
            if (st.days > 0 && st.days > topDays) { topDays = st.days; topName = nh.name }
        }
        val bh = bestHourStats(blocks, now)
        val peakHour = if (bh.total > 0) bh.peak else null
        var hpTotal = 0
        var hpDone = 0
        for (it in intentions) {
            if ((it.priority ?: 4) == 1) { hpTotal++; if (it.done) hpDone++ }
        }
        val highPrioPct = if (hpTotal >= 3) Math.round(hpDone.toDouble() / hpTotal * 100).toInt() else null
        return Narrative(topName, topDays, peakHour, highPrioPct)
    }
}
