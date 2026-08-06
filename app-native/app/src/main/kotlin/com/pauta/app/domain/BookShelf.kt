package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import java.text.Normalizer

/**
 * native-only (L8): finding a book on a shelf that has grown.
 *
 * The shelf was built for a small library and quietly stopped working as it got
 * bigger: no search, no sort, no filter anywhere in book mode. "Lidos" was an
 * unbounded horizontal row, so a year's reading was a long sideways scroll with
 * no way to reach a title you remembered, and "A seguir" is a flat list in
 * `position` order, which after L3's moves is close to arbitrary. Finding a book
 * you read in March was not possible except by scrolling.
 *
 * All of it is pure and lives here so it is testable and so the shelf screen
 * stays a screen. // PT: procurar e ordenar a estante — puro e testável, fora do
 * ecrã.
 */
object BookShelf {

    private val CombiningMarks = Regex("\\p{Mn}+")

    /**
     * Accents off, case off. "Saramago" must match "saramago" and "sarámago" — a
     * personal library is typed from memory, and memory does not carry accents.
     * The same fold `SettingsScreen`'s search already uses. // PT: sem acentos nem
     * maiúsculas, como a procura das definições.
     */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(CombiningMarks, "").lowercase()

    /** How well a book answers a query — higher is better, 0 = not a match. A
     *  title hit outranks an author hit outranks the rest, which is the order
     *  someone searching actually means. // PT: título acima de autor acima do
     *  resto. */
    private fun score(book: BookEntity, folded: String): Int {
        val title = fold(book.title)
        val author = fold(book.author)
        val series = fold(book.series)
        // L7's split, reused rather than repeated — the reason it lives in
        // `BookMath` and not in the sheet that draws it.
        // // PT: a divisão do género vem de L7, não se repete aqui.
        val genres = BookMath.genreTags(book.genre).joinToString(" ") { fold(it) }
        return when {
            title.startsWith(folded) -> 5
            title.contains(folded) -> 4
            author.contains(folded) -> 3
            series.contains(folded) -> 2
            genres.contains(folded) -> 1
            else -> 0
        }
    }

    /**
     * Books matching [query], best first. A blank query matches nothing — the
     * caller shows its ordinary shelf then, rather than a "result list" of
     * everything.
     *
     * Substring matching over four fields, and **no fuzzy matching**: for a
     * personal library it is enough, and it is explicable — a result you cannot
     * explain is a result you cannot trust.
     * // PT: correspondência por substring em quatro campos, sem lógica difusa —
     * chega para uma biblioteca pessoal e é explicável.
     */
    fun search(books: List<BookEntity>, query: String): List<BookEntity> {
        val folded = fold(query.trim())
        if (folded.isEmpty()) return emptyList()
        return books
            .map { it to score(it, folded) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<BookEntity, Int>> { it.second }
                    .thenBy { fold(it.first.title) },
            )
            .map { it.first }
    }

    /** The orders the shelf offers. Stored as ids so a label change is not a
     *  behaviour change. // PT: as ordens disponíveis, por id. */
    object Sort {
        const val Recent = "recent"
        const val Title = "title"
        const val Author = "author"
        const val Rating = "rating"
        val ALL = listOf(Recent, Title, Author, Rating)
    }

    /**
     * [books] in the given order.
     *
     * `Recent` means the most recently *finished* first, falling back to when the
     * book was added — which is what "recent" means on a shelf of books you have
     * read, and what "Lidos" was already sorted by. An unrated book sorts last
     * under `Rating` rather than as a zero: unrated is not bad.
     * // PT: "recentes" = terminados há menos tempo (ou adicionados); sem
     * classificação fica no fim, que não é o mesmo que classificação baixa.
     */
    fun sorted(books: List<BookEntity>, sort: String): List<BookEntity> = when (sort) {
        Sort.Title -> books.sortedBy { fold(it.title) }
        Sort.Author -> books.sortedWith(compareBy({ fold(it.author) }, { fold(it.title) }))
        Sort.Rating -> books.sortedWith(
            compareByDescending<BookEntity> { it.rating ?: Int.MIN_VALUE }
                .thenBy { fold(it.title) },
        )
        else -> books.sortedByDescending { it.finishedAt ?: it.createdAt }
    }

    /**
     * Past this many finished books, "Lidos" stops being a horizontal row and
     * becomes rows that scroll the way the page already scrolls. Twelve is about
     * a year of ordinary reading — the point at which sideways stops being a
     * gesture and starts being a chore. // PT: acima disto, "Lidos" deixa de
     * rolar para o lado.
     */
    const val CAROUSEL_MAX = 12
}
