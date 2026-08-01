package com.pauta.app.domain

import kotlin.math.roundToInt

/**
 * native-only (R3): the reader's arithmetic, kept out of the `:reader` process so
 * it can be tested on the JVM. There is exactly one sum here and it is the one
 * worth getting right — how large to draw a page.
 *
 * A page is drawn as wide as the surface showing it, except that neither edge may
 * pass a ceiling: a bitmap is four bytes a pixel and a long document renders many
 * of them, so an oversized page is the difference between a smooth reader and an
 * out-of-memory one. Tested in `ReaderMathTest`. // PT: o tamanho a que uma
 * página é desenhada — a largura do ecrã, com um tecto em cada lado.
 */
object ReaderMath {

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
