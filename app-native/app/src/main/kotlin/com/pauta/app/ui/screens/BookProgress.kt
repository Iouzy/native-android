package com.pauta.app.ui.screens

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.i18n.tr
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
        // F7: an attached EPUB still counts in percent — that is settled — but a
        // reader who also owns the paper copy has a print length they typed in,
        // and a percentage of it is a real (if rough) answer to "where am I in the
        // book on the shelf?". It carries `≈` and says what it assumed, because a
        // derived figure presented as measured is exactly the defect this round
        // exists to remove (GUARDRAILS K.11). The publisher's *own* numbers, where
        // the book carries them, are drawn in the page itself by the marker rule
        // — those need no `≈`. // PT: a percentagem fica; a página é uma estimativa
        // e diz que o é.
        countsPercent(book) && book.totalPages > 0 -> {
            val pct = page.coerceIn(0, 100)
            val approx = (book.totalPages * pct / 100).coerceIn(0, book.totalPages)
            "$pct% · " + trf("≈ p. {x} de {y}", "x" to approx, "y" to book.totalPages)
        }
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

/**
 * F1 · this file decided how progress is *shown*; from here it also decides how
 * it is **asked for**.
 *
 * That split is what put a book at 100%. R4 taught every display that an attached
 * EPUB counts percentage points, and taught none of the inputs: the conclude
 * sheet went on asking *"Até que página chegaste?"*, the owner answered it with a
 * page number, and the app stored a percentage. A field whose meaning differs
 * from the line above it is not a small inconsistency — it is a number the user
 * did not mean, written to their data.
 *
 * // PT: as perguntas e as marcas dos campos de progresso, na unidade que o livro
 * conta — o mesmo sítio que já decidia como se mostra.
 */

/** The eyebrow above a progress input. // PT: a pergunta do campo. */
internal fun bookProgressQuestion(book: BookEntity): String = when {
    countsPercent(book) -> tr("Em que percentagem ficaste?")
    book.format == "audiobook" -> tr("Quantos minutos ouviste?")
    else -> tr("Até que página chegaste?")
}

/** The field's own noun, for a label that isn't a question. // PT: o nome da unidade. */
internal fun bookProgressUnit(book: BookEntity): String = when {
    countsPercent(book) -> tr("Percentagem")
    book.format == "audiobook" -> tr("Minutos")
    else -> tr("Página")
}

/** The mark that sits beside the field — `%`, `min.`, `p.` — so the unit is
 *  visible while typing, not only in the label. // PT: a marca ao lado do campo. */
internal fun bookProgressMark(book: BookEntity): String = when {
    countsPercent(book) -> "%"
    book.format == "audiobook" -> "min."
    else -> "p."
}

/** Clamp a typed value into what this book can mean. A percentage stops at 100,
 *  a book of known length at its length, and a book of unknown length is not
 *  second-guessed. // PT: limita o valor ao que o livro pode significar. */
internal fun clampBookProgress(book: BookEntity, value: Int): Int {
    val max = bookProgressMax(book)
    return if (max != null) value.coerceIn(0, max) else value.coerceAtLeast(0)
}
