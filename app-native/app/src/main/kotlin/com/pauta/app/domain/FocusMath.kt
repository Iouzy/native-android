package com.pauta.app.domain

/**
 * Focus-time math for the Pauta tab, ported from store.jsx (blockFocusMs /
 * dailyFocusMs / fmtDuration). Pure and unit-testable; the repository feeds it
 * sessions from Room. A running session (endedAt == null) is measured up to
 * [now]. // PT: matemática do tempo de foco, portada da store.jsx.
 */
object FocusMath {

    /** One focus span. A null [endedAt] means it's still running. */
    data class FocusSeg(val startedAt: Long, val endedAt: Long?)

    /** Total focus across a block's sessions, counting a running session up to now. */
    fun blockElapsedMs(sessions: List<FocusSeg>, now: Long): Long =
        sessions.sumOf { (it.endedAt ?: now) - it.startedAt }

    /** Focus across many sessions whose start falls on [dayKey] (the web scopes
     *  by each segment's start day). */
    fun dailyFocusMs(segments: List<FocusSeg>, dayKey: String, now: Long): Long =
        segments
            .filter { DateUtils.dayKeyOf(it.startedAt) == dayKey }
            .sumOf { (it.endedAt ?: now) - it.startedAt }

    private fun pad(n: Long): String = n.toString().padStart(2, '0')

    /** Timer format: `m:ss` or `h:mm:ss` — fmtDuration({timer:true}). */
    fun fmtTimer(ms: Long): String {
        val secs = (ms / 1000).coerceAtLeast(0)
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return if (h > 0) "$h:${pad(m)}:${pad(s)}" else "${pad(m)}:${pad(s)}"
    }

    /** Compact duration: `45 min` or `1h02` — fmtDuration(). */
    fun fmtDuration(ms: Long): String {
        val secs = (ms / 1000).coerceAtLeast(0)
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) "${h}h${pad(m)}" else "$m min"
    }
}
