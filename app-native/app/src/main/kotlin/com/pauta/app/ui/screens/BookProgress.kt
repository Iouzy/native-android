package com.pauta.app.ui.screens

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.i18n.trf

/**
 * native-only (R4): how far through a book you are, said in the unit that book
 * actually counts in.
 *
 * `docs/BOOK_MODE.md` had two cases — a physical book or an ebook counts pages, an
 * audiobook counts minutes. An **attached EPUB is a third**, and it is not a
 * stylistic choice: an EPUB has no pages. Its text reflows with the type size, so
 * "page 80" would mean a different place at every text scale and a different place
 * again on a wider screen. What it has is a position, and the honest way to say a
 * position is a percentage.
 *
 * That is also exactly the unit R6 already reasons in: for a counted EPUB,
 * `BookMath.wordsPerUnit` is `wordCount / 100`, one unit being one percentage
 * point. So the reader records percent, the sheets show percent, and the reading
 * speed keeps working with no special case of its own.
 *
 * // PT: a progressão dita na unidade do livro — páginas, minutos, ou (num EPUB
 * anexado) percentagem, porque um EPUB não tem páginas.
 */

/** True when this book's progress is a percentage rather than a page count — an
 *  EPUB the app has a file for. // PT: verdade quando o livro conta em percentagem. */
internal fun countsPercent(book: BookEntity): Boolean = book.fileKind == "epub"

/**
 * The progress line: `"43%"`, `"Página 80 de 228"`, `"Min 30 de 480"`, or the bare
 * position when the length isn't known. [page] lets a caller show somewhere other
 * than the stored position (the session screen shows the bookmark).
 * // PT: a linha de progresso, na unidade certa.
 */
internal fun bookProgressLabel(book: BookEntity, page: Int = book.currentPage): String {
    val audiobook = book.format == "audiobook"
    return when {
        countsPercent(book) -> "${page.coerceIn(0, 100)}%"
        book.totalPages > 0 && audiobook ->
            trf("Min {x} de {y}", "x" to page, "y" to book.totalPages)
        book.totalPages > 0 -> trf("Página {x} de {y}", "x" to page, "y" to book.totalPages)
        audiobook -> "min. $page"
        else -> "p. $page"
    }
}

/** The same, in the shelf card's shorter voice: `"43%"` / `"80 / 228 págs"`. */
internal fun bookProgressShort(book: BookEntity, unit: String): String = when {
    countsPercent(book) -> "${book.currentPage.coerceIn(0, 100)}%"
    book.totalPages > 0 -> "${book.currentPage} / ${book.totalPages} $unit"
    else -> "$unit ${book.currentPage}"
}

/** How full the bar is, or null when there is nothing honest to draw — a book of
 *  unknown length gets no bar rather than a made-up one. // PT: o preenchimento da
 *  barra, ou nada quando não se sabe o tamanho. */
internal fun bookProgressFraction(book: BookEntity, page: Int = book.currentPage): Float? = when {
    countsPercent(book) -> (page / 100f).coerceIn(0f, 1f)
    book.totalPages > 0 -> (page.toFloat() / book.totalPages).coerceIn(0f, 1f)
    else -> null
}

/** The largest value the editor will accept: 100 for a percentage, the book's
 *  length when it has one, and otherwise nothing to clamp against. */
internal fun bookProgressMax(book: BookEntity): Int? = when {
    countsPercent(book) -> 100
    book.totalPages > 0 -> book.totalPages
    else -> null
}
