package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity

/**
 * native-only (R7): the reading rhythm — everything the book-mode Hábitos tab
 * draws, derived from data that already exists (concluded reading sessions and
 * the shelf) with no new table behind it.
 *
 * A reading session is a `FocusBlockEntity` with `project = "book:<id>"`, but
 * its duration lives in the session spans and the words it moved depend on the
 * book it belongs to — three tables to answer one question. So the screen
 * flattens a session into [Session] first and the math here stays pure and
 * JVM-testable, exactly like [BookMath] beside it. Covered by `ReadingStatsTest`.
 * // PT: o ritmo de leitura — dias lidos, sequências, minutos e livros
 * terminados, tudo derivado das sessões já existentes.
 */
object ReadingStats {

    /**
     * One concluded reading session, reduced to what the rhythm needs: the local
     * day it ended on, how long it ran, and — when anyone counted — how much of
     * the book it moved.
     *
     * [pages] and [words] are nullable because "nobody counted" is not zero: a
     * session concluded by hand has no page delta (R5), an audiobook's progress
     * is minutes rather than pages, and an EPUB the reader has counted measures
     * its progress in percentage points, which don't add up with pages. Null
     * keeps such a session out of a total instead of dragging it down.
     * // PT: uma sessão achatada; null = ninguém contou, que não é zero.
     */
    data class Session(
        val dayKey: String,
        val minutes: Int,
        val pages: Int? = null,
        val words: Float? = null,
    )

    /** Local day keys on which any reading session was concluded. */
    fun daysRead(sessions: List<Session>): Set<String> =
        sessions.mapTo(LinkedHashSet()) { it.dayKey }

    /**
     * Current and best consecutive-day reading streaks, as `current to best`.
     *
     * The current streak walks back from [today] — or from yesterday when today
     * has no reading yet, which is where this parts company with the tides'
     * [HabitCalculator.currentStreak]. A tide is a self-report you can make at
     * any hour, so an unmarked today honestly means "not done". Reading is proven
     * by a session, and a day that hasn't ended yet is not yet a day without
     * reading — so the streak stands until midnight takes it.
     * // PT: a sequência actual conta a partir de hoje, ou de ontem se hoje ainda
     * não teve leitura — o dia ainda não acabou.
     */
    fun streaks(daysRead: Set<String>, today: String): Pair<Int, Int> {
        if (daysRead.isEmpty()) return 0 to 0

        var cursor = if (today in daysRead) today else DateUtils.addDays(today, -1)
        var current = 0
        while (cursor in daysRead) {
            current++
            cursor = DateUtils.addDays(cursor, -1)
        }

        var best = 0
        var streak = 0
        var prev: String? = null
        daysRead.sorted().forEach { day ->
            val previous = prev
            streak = if (previous != null && DateUtils.addDays(previous, 1) == day) streak + 1 else 1
            if (streak > best) best = streak
            prev = day
        }
        return current to best
    }

    /** Minutes read per day in one month, for the day grid and the bar chart.
     *  Only days with reading appear. // PT: minutos por dia de um mês. */
    fun minutesByDay(sessions: List<Session>, year: Int, month: Int): Map<String, Int> {
        val prefix = "%04d-%02d".format(year, month)
        val out = LinkedHashMap<String, Int>()
        sessions.forEach { s ->
            if (s.dayKey.startsWith(prefix) && s.minutes > 0) {
                out[s.dayKey] = (out[s.dayKey] ?: 0) + s.minutes
            }
        }
        return out
    }

    /** Books finished per month across [year] — twelve counts, January first.
     *  Only books actually marked read count; a `finishedAt` on any other status
     *  is leftover state. // PT: livros terminados por mês. */
    fun finishedByMonth(books: List<BookEntity>, year: Int): List<Int> {
        val out = MutableList(12) { 0 }
        books.forEach { b ->
            if (b.status != "done") return@forEach
            val key = DateUtils.dayKeyOf(b.finishedAt ?: return@forEach)
            if (key.take(4).toIntOrNull() == year) {
                val m = key.substring(5, 7).toInt()
                out[m - 1] = out[m - 1] + 1
            }
        }
        return out
    }

    /** Minutes read on each of the [days] days ending on [today], oldest first —
     *  the shape a bar chart wants, gaps included. // PT: minutos por dia, com
     *  os dias vazios lá dentro. */
    fun minutesLastDays(sessions: List<Session>, today: String, days: Int): List<Int> {
        if (days <= 0) return emptyList()
        val byDay = sessions.groupBy { it.dayKey }
        return (days - 1 downTo 0).map { back ->
            byDay[DateUtils.addDays(today, -back)].orEmpty().sumOf { it.minutes }
        }
    }

    /** Pages read in each of the [weeks] Monday-based weeks ending with [today]'s,
     *  oldest first. Sessions nobody counted contribute nothing rather than zero.
     *  // PT: páginas por semana, semanas de segunda a domingo. */
    fun pagesByWeek(sessions: List<Session>, today: String, weeks: Int): List<Int> {
        if (weeks <= 0) return emptyList()
        val thisWeek = DateUtils.weekStart(today)
        val byWeek = sessions.groupBy { DateUtils.weekStart(it.dayKey) }
        return (weeks - 1 downTo 0).map { back ->
            byWeek[DateUtils.addDays(thisWeek, -7 * back)].orEmpty().sumOf { it.pages ?: 0 }
        }
    }

    /** Words per minute for every session that measured both, oldest first — one
     *  point per session for the speed line. A session with no word figure (an
     *  audiobook, a conclusion typed by hand) has no speed to plot and is left
     *  out entirely. // PT: um ponto por sessão, só onde há palavras e tempo. */
    fun speedPoints(sessions: List<Session>): List<Float> =
        sessions
            .sortedBy { it.dayKey }
            .mapNotNull { s ->
                val words = s.words ?: return@mapNotNull null
                if (s.minutes > 0 && words > 0f) words / s.minutes else null
            }
}
