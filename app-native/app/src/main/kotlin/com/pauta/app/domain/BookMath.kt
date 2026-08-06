package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import kotlin.math.ceil

/**
 * native-only (K-extra): pure reading-pace math for book mode — how fast is
 * this book going, and when will it end at that pace? Pages and minutes share
 * the same arithmetic (an audiobook's "page" is a listened minute), so both
 * formats flow through unchanged. R6 adds the stat a reading app owes you on
 * top: words per minute. Tested in `BookMathTest`. // PT: matemática pura do
 * ritmo de leitura — págs (ou minutos) por hora, palavras por minuto e a
 * estimativa de fim.
 */
object BookMath {

    /**
     * native-only (R6): the words on a page nobody counted. A physical book and
     * a PDF have no extractable word count, so the app uses one constant for
     * both rather than pretending each edition is different — and says "≈"
     * wherever a figure derives from it. // PT: a estimativa de palavras por
     * página; tudo o que dela deriva leva "≈".
     */
    const val WORDS_PER_PAGE = 280

    /**
     * native-only (F1): the fastest anyone reads. A span implying more than this
     * is not a measurement of reading — it is navigating, and in an EPUB one tap
     * moves several percentage points, so it takes only a chapter jump to produce
     * "Ritmo: 9191 palavras/min" from arithmetic that is entirely correct on
     * dishonest data.
     *
     * 1000 is chosen to be comfortably above any real reader (a fast one manages
     * ~400, a trained skimmer ~700) and far below what a jump produces, so the
     * ceiling never censors a genuine session. The span keeps its **time** in the
     * history and loses only its **words** — the sitting happened; what it claims
     * about pace did not. // PT: o tecto de velocidade humana; acima disto foi
     * navegação e não leitura, e o tempo fica mas o ritmo não conta.
     */
    const val MAX_HUMAN_WPM = 1000f

    /** One reading session's contribution: pages (or minutes) gained over its
     *  wall-clock duration. // PT: o delta de páginas de uma sessão e a sua duração. */
    data class SessionSpan(val pagesDelta: Int, val durationMs: Long)

    /**
     * native-only (F1): the words per minute [span] implies, or null when the
     * question doesn't apply (no time, no unit). // PT: as palavras por minuto que
     * um intervalo implica.
     */
    fun impliedWpm(span: SessionSpan, wordsPerUnit: Float): Float? {
        if (span.durationMs <= 0L || wordsPerUnit <= 0f) return null
        val minutes = span.durationMs / 60_000.0
        if (minutes <= 0.0) return null
        return (span.pagesDelta * wordsPerUnit / minutes).toFloat()
    }

    /**
     * native-only (F1): [spans] with the impossible ones dropped. A null or
     * non-positive [wordsPerUnit] means we have no way to judge — an audiobook,
     * where a listened minute is already time — so nothing is dropped rather than
     * everything. // PT: os intervalos plausíveis; sem unidade não se julga nada.
     */
    fun readingSpans(spans: List<SessionSpan>, wordsPerUnit: Float?): List<SessionSpan> {
        if (wordsPerUnit == null || wordsPerUnit <= 0f) return spans
        return spans.filter { span ->
            val wpm = impliedWpm(span, wordsPerUnit) ?: return@filter true
            wpm <= MAX_HUMAN_WPM
        }
    }

    /**
     * Pages (or minutes) read per hour across the given sessions — the overall
     * rate, not a per-session average, so long sessions weigh more. Null with
     * fewer than 2 usable data points (a single session is too noisy to call a
     * pace) or when nothing was actually read. // PT: ritmo global; null com
     * menos de 2 sessões úteis.
     *
     * F1: pass [wordsPerUnit] and spans implying more than [MAX_HUMAN_WPM] are
     * dropped before the rate is taken, rather than averaged in. It defaults to
     * null — no ceiling — so a caller that has no book in hand still gets the old
     * arithmetic. // PT: com [wordsPerUnit], descarta os intervalos impossíveis.
     */
    fun pagesPerHour(sessions: List<SessionSpan>, wordsPerUnit: Float? = null): Float? {
        val valid = readingSpans(sessions, wordsPerUnit)
            .filter { it.durationMs > 0 && it.pagesDelta >= 0 }
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

    /**
     * native-only (R6): whether [book]'s word count was actually counted rather
     * than estimated — true only for an EPUB the reader has read through, which
     * is the one format that hands over its real text. Everything else derives
     * from [WORDS_PER_PAGE] and must be shown with a "≈". // PT: se a contagem
     * de palavras é real (só EPUB) ou estimada.
     */
    fun hasCountedWords(book: BookEntity): Boolean =
        book.fileKind == "epub" && book.wordCount > 0

    /**
     * native-only (R6): how many words one unit of this book's progress is worth.
     *
     * A "unit" is whatever `currentPage` counts, and that differs by format: a
     * page for a physical book, an ebook or a PDF; one percent of the book for an
     * attached EPUB, which shows percent and not pages everywhere. Null for an
     * audiobook — a listened minute is already time, and dividing time by time
     * would give a number that looks like a reading speed without being one.
     * // PT: palavras por unidade de progresso; null para audiolivros.
     */
    fun wordsPerUnit(book: BookEntity): Float? = when {
        book.format == "audiobook" -> null
        // An EPUB the reader counted measures progress in percentage points, so
        // one unit is a hundredth of the whole text.
        hasCountedWords(book) -> book.wordCount / 100f
        else -> WORDS_PER_PAGE.toFloat()
    }

    /**
     * native-only (R6): words read per minute across [spans], where each unit of
     * progress is worth [wordsPerUnit] words. Same data points and same overall
     * rate as [pagesPerHour] — deliberately, so the two figures can never
     * disagree about the same sessions — which also means null under the same
     * conditions: fewer than 2 usable spans, or nothing actually read — and, since
     * F1, after the same [MAX_HUMAN_WPM] ceiling has removed the same spans.
     * // PT: palavras por minuto; mesmas regras de validade que [pagesPerHour].
     */
    fun wordsPerMinute(spans: List<SessionSpan>, wordsPerUnit: Float): Float? {
        if (wordsPerUnit <= 0f) return null
        val unitsPerHour = pagesPerHour(spans, wordsPerUnit) ?: return null
        return unitsPerHour * wordsPerUnit / 60f
    }
}
