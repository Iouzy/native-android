package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L8: a hundred-book library has to be searchable and sortable. All of it is
 * pure, which is the point — a shelf that stops working at scale is not a thing
 * to find out on a device. // PT: procurar e ordenar, tudo puro e testável.
 */
class BookShelfTest {

    private fun book(
        title: String,
        author: String = "",
        series: String = "",
        genre: String = "",
        status: String = "done",
        rating: Int? = null,
        finishedAt: Long? = null,
        createdAt: Long = 0L,
    ) = BookEntity(
        id = "bk_$title",
        title = title,
        author = author,
        series = series,
        genre = genre,
        status = status,
        rating = rating,
        finishedAt = finishedAt,
        createdAt = createdAt,
    )

    // ── search ────────────────────────────────────────────────

    @Test fun aBlankQueryMatchesNothing() {
        // The caller shows its ordinary four sections then, not a result list of
        // everything. // PT: sem consulta, a estante normal — não uma lista de tudo.
        val books = listOf(book("Ensaio sobre a Cegueira"))
        assertEquals(emptyList<BookEntity>(), BookShelf.search(books, ""))
        assertEquals(emptyList<BookEntity>(), BookShelf.search(books, "   "))
    }

    @Test fun searchIsAccentAndCaseInsensitive() {
        // A personal library is typed from memory, and memory does not carry
        // accents. // PT: escreve-se de memória, e a memória não leva acentos.
        val books = listOf(book("Ensaio sobre a Cegueira", author = "José Saramago"))
        assertEquals(1, BookShelf.search(books, "saramago").size)
        assertEquals(1, BookShelf.search(books, "SARAMAGO").size)
        assertEquals(1, BookShelf.search(books, "sarámago").size)
        assertEquals(1, BookShelf.search(books, "cegueira").size)
    }

    @Test fun allFourFieldsAreSearched() {
        val books = listOf(
            book("Um", author = "Calvino"),
            book("Dois", series = "As Cidades"),
            book("Três", genre = "ficção, ensaio"),
        )
        assertEquals(listOf("Um"), BookShelf.search(books, "calvino").map { it.title })
        assertEquals(listOf("Dois"), BookShelf.search(books, "cidades").map { it.title })
        assertEquals(listOf("Três"), BookShelf.search(books, "ensaio").map { it.title })
    }

    @Test fun titleMatchesOutrankAuthorMatchesOutrankTheRest() {
        // When you search "levine" you mean the book called that before the one
        // merely written by them. // PT: título antes de autor antes do resto.
        val byAuthor = book("Outro livro", author = "Levine")
        val byGenre = book("Terceiro", genre = "levine")
        val byTitle = book("Levine e o Apego")
        val found = BookShelf.search(listOf(byGenre, byAuthor, byTitle), "levine")
        assertEquals(listOf("Levine e o Apego", "Outro livro", "Terceiro"), found.map { it.title })
    }

    @Test fun aPrefixMatchBeatsAMatchInTheMiddle() {
        val middle = book("O Grande Gatsby")
        val prefix = book("Gatsby, o Grande")
        val found = BookShelf.search(listOf(middle, prefix), "gatsby")
        assertEquals("Gatsby, o Grande", found.first().title)
    }

    @Test fun tiesAreBrokenByTitleSoResultsNeverShuffle() {
        val b = book("Beta", author = "X")
        val a = book("Alfa", author = "X")
        assertEquals(listOf("Alfa", "Beta"), BookShelf.search(listOf(b, a), "x").map { it.title })
    }

    @Test fun aQueryThatMatchesNothingReturnsNothing() {
        assertTrue(BookShelf.search(listOf(book("Um")), "zzz").isEmpty())
    }

    @Test fun aHundredBooksAreStillSearchable() {
        // The scale this task is named for. // PT: a escala que dá nome à tarefa.
        val many = (1..100).map { book("Livro $it", author = "Autor ${it % 7}") }
        assertEquals(1, BookShelf.search(many, "Livro 42").size)
        assertEquals(15, BookShelf.search(many, "Autor 3").size)
    }

    // ── sort ──────────────────────────────────────────────────

    @Test fun titleAndAuthorSortIgnoreAccentsAndCase() {
        val books = listOf(book("Ébano"), book("abc"), book("Zulu"))
        assertEquals(
            listOf("abc", "Ébano", "Zulu"),
            BookShelf.sorted(books, BookShelf.Sort.Title).map { it.title },
        )
    }

    @Test fun recentMeansMostRecentlyFinished() {
        val old = book("Velho", finishedAt = 1_000L)
        val new = book("Novo", finishedAt = 9_000L)
        assertEquals(
            listOf("Novo", "Velho"),
            BookShelf.sorted(listOf(old, new), BookShelf.Sort.Recent).map { it.title },
        )
    }

    @Test fun recentFallsBackToWhenTheBookWasAdded() {
        // A book with no finish date is not undated — it was added at some point.
        val a = book("Sem data", createdAt = 5_000L)
        val b = book("Terminado", finishedAt = 9_000L)
        assertEquals(
            listOf("Terminado", "Sem data"),
            BookShelf.sorted(listOf(a, b), BookShelf.Sort.Recent).map { it.title },
        )
    }

    @Test fun anUnratedBookSortsLastRatherThanAsZero() {
        // Unrated is not the same as bad. // PT: sem classificação não é má.
        val unrated = book("Sem nota")
        val one = book("Uma estrela", rating = 1)
        val five = book("Cinco", rating = 5)
        assertEquals(
            listOf("Cinco", "Uma estrela", "Sem nota"),
            BookShelf.sorted(listOf(unrated, one, five), BookShelf.Sort.Rating).map { it.title },
        )
    }

    @Test fun authorSortBreaksTiesByTitle() {
        val b = book("Beta", author = "Calvino")
        val a = book("Alfa", author = "Calvino")
        assertEquals(
            listOf("Alfa", "Beta"),
            BookShelf.sorted(listOf(b, a), BookShelf.Sort.Author).map { it.title },
        )
    }

    @Test fun sortingNeverLosesOrAddsABook() {
        val books = (1..30).map { book("Livro $it", rating = it % 6) }
        for (order in BookShelf.Sort.ALL) {
            assertEquals(books.size, BookShelf.sorted(books, order).size)
            assertEquals(books.map { it.id }.toSet(), BookShelf.sorted(books, order).map { it.id }.toSet())
        }
    }

    @Test fun anUnknownSortIsTreatedAsRecentRatherThanCrashing() {
        val books = listOf(book("Um", finishedAt = 2L), book("Dois", finishedAt = 9L))
        assertEquals(
            BookShelf.sorted(books, BookShelf.Sort.Recent).map { it.title },
            BookShelf.sorted(books, "nonsense").map { it.title },
        )
    }
}
