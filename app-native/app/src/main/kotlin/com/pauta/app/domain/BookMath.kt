package com.pauta.app.domain

import kotlin.math.ceil

/**
 * native-only (K-extra): pure reading-pace math for book mode — how fast is
 * this book going, and when will it end at that pace? Pages and minutes share
 * the same arithmetic (an audiobook's "page" is a listened minute), so both
 * formats flow through unchanged. Tested in `BookMathTest`. // PT: matemática
 * pura do ritmo de leitura — págs (ou minutos) por hora e a estimativa de fim.
 */
object BookMath {

    /** One reading session's contribution: pages (or minutes) gained over its
     *  wall-clock duration. // PT: o delta de páginas de uma sessão e a sua duração. */
    data class SessionSpan(val pagesDelta: Int, val durationMs: Long)

    /**
     * Pages (or minutes) read per hour across the given sessions — the overall
     * rate, not a per-session average, so long sessions weigh more. Null with
     * fewer than 2 usable data points (a single session is too noisy to call a
     * pace) or when nothing was actually read. // PT: ritmo global; null com
     * menos de 2 sessões úteis.
     */
    fun pagesPerHour(sessions: List<SessionSpan>): Float? {
        val valid = sessions.filter { it.durationMs > 0 && it.pagesDelta >= 0 }
        if (valid.size < 2) return null
        val pages = valid.sumOf { it.pagesDelta }
        val hours = valid.sumOf { it.durationMs } / 3_600_000.0
        if (pages <= 0 || hours <= 0.0) return null
        return (pages / hours).toFloat()
    }

    /**
     * Estimated days to finish [remaining] pages at [pagesPerHour], assuming
     * [dailyReadingMinutes] of reading per day. 0 when nothing remains; null
     * when the pace (or the daily budget) can't produce an estimate. Rounds up —
     * a partial day of reading still ends on that day. // PT: dias estimados até
     * ao fim ao ritmo actual; arredonda para cima.
     */
    fun etaDays(remaining: Int, pagesPerHour: Float, dailyReadingMinutes: Float = 60f): Int? {
        if (remaining <= 0) return 0
        if (pagesPerHour <= 0f || dailyReadingMinutes <= 0f) return null
        val pagesPerDay = pagesPerHour * (dailyReadingMinutes / 60f)
        return ceil(remaining / pagesPerDay).toInt()
    }
}
