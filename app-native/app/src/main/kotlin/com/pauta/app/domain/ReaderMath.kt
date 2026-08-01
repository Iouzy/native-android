package com.pauta.app.domain

import kotlin.math.roundToInt

/**
 * native-only (R3): the reader's arithmetic, kept out of the `:reader` process so
 * it can be tested on the JVM. Two things live here, and both are worth getting
 * right: how large to draw a page (R3), and what a reading session is worth once
 * the reader closes (R5).
 *
 * A page is drawn as wide as the surface showing it, except that neither edge may
 * pass a ceiling: a bitmap is four bytes a pixel and a long document renders many
 * of them, so an oversized page is the difference between a smooth reader and an
 * out-of-memory one. Tested in `ReaderMathTest`. // PT: o tamanho a que uma
 * página é desenhada e o que vale uma sessão de leitura ao fechar o leitor.
 */
object ReaderMath {

    /** Below this, a reading session with no page turned is a peek, not a session.
     *  // PT: abaixo disto, sem virar página, foi uma espreitadela. */
    const val MIN_SESSION_MS = 60_000L

    /**
     * What to do with a reading session the reader is closing. [save] false means
     * leave nothing behind — no block, no progress write. // PT: guardar ou
     * descartar a sessão; [pagesDelta] pode ser negativo (leu para trás).
     */
    data class SessionOutcome(val save: Boolean, val page: Int, val pagesDelta: Int)

    /**
     * Whether a session that ran [durationMs] and moved the reader from [startPage]
     * to [endPage] is worth recording. Opening a book to check a quote shouldn't
     * litter the history, so a session under [MIN_SESSION_MS] that turned no page
     * is discarded; everything else is saved at the page it was left on.
     *
     * The delta is signed on purpose: reading backwards is not reading, and
     * `BookMath.pagesPerHour` already drops negative spans rather than having them
     * silently counted as progress. // PT: uma sessão curta sem virar página é
     * descartada; as outras guardam a página onde ficaram.
     */
    fun sessionOutcome(durationMs: Long, startPage: Int, endPage: Int): SessionOutcome {
        val peek = durationMs < MIN_SESSION_MS && endPage == startPage
        return SessionOutcome(save = !peek, page = endPage, pagesDelta = endPage - startPage)
    }

    /**
     * A session's wall-clock time in whole minutes, for the receipt line. Rounded to
     * the nearest minute but never to zero — a session that was saved took *some*
     * time, and "0 min" reads as a bug. // PT: minutos da sessão, arredondados e
     * nunca zero.
     */
    fun sessionMinutes(durationMs: Long): Int =
        ((durationMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)

    /**
     * The 1-based page a stored bookmark points at, or null when the bookmark says
     * nothing about pages. A PDF's bookmark is the zero-based page index R3 writes;
     * an EPUB's is `spine:percent` (R4), which is a position and not a page, so it
     * comes back null and the caller keeps showing percent. // PT: a página (base 1)
     * a que o marcador aponta; null quando o marcador não fala de páginas.
     */
    fun bookmarkPage(readPosition: String, fileKind: String?): Int? {
        if (fileKind != "pdf") return null
        val index = readPosition.trim().toIntOrNull() ?: return null
        return (index + 1).coerceAtLeast(1)
    }

    /**
     * The pixel size of a [pageWidth]×[pageHeight] page drawn [targetWidth] pixels
     * wide, scaled down as needed so neither edge exceeds [maxEdge]. The page's
     * proportions are always kept; a page with no size at all comes back 1×1
     * rather than as a division by zero. // PT: tamanho em píxeis, mantendo a
     * proporção e respeitando o limite.
     */
    fun fitPage(pageWidth: Int, pageHeight: Int, targetWidth: Int, maxEdge: Int): Pair<Int, Int> {
        if (pageWidth <= 0 || pageHeight <= 0 || targetWidth <= 0 || maxEdge <= 0) return 1 to 1
        var scale = targetWidth.toFloat() / pageWidth
        if (pageWidth * scale > maxEdge) scale = maxEdge.toFloat() / pageWidth
        if (pageHeight * scale > maxEdge) scale = maxEdge.toFloat() / pageHeight
        val w = (pageWidth * scale).roundToInt().coerceIn(1, maxEdge)
        val h = (pageHeight * scale).roundToInt().coerceIn(1, maxEdge)
        return w to h
    }
}
