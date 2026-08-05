package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * native-only (L3): the shelf's cover of the status set is total — that is the
 * whole point of the file under test. // PT: nenhum estado fica sem prateleira.
 */
class BookStatusTest {

    @Test
    fun `every status lands on a shelf`() {
        BookStatus.ALL.forEach { status ->
            assertNotNull("no shelf shows '$status'", BookStatus.shelfOf(status))
        }
    }

    @Test
    fun `every shelf shows something`() {
        // The other direction: a section nothing can reach is dead UI.
        val used = BookStatus.ALL.mapNotNull { BookStatus.shelfOf(it) }.toSet()
        assertEquals(BookStatus.Shelf.entries.toSet(), used)
    }

    @Test
    fun `done and dnf share the finished shelf`() {
        assertEquals(BookStatus.Shelf.Finished, BookStatus.shelfOf(BookStatus.DONE))
        assertEquals(BookStatus.Shelf.Finished, BookStatus.shelfOf(BookStatus.DNF))
    }

    @Test
    fun `an unknown status has no shelf and sanitises to tbr`() {
        assertNull(BookStatus.shelfOf("abandonado"))
        assertEquals(BookStatus.TBR, BookStatus.sanitize("abandonado"))
        assertEquals(BookStatus.TBR, BookStatus.sanitize(null))
        assertEquals(BookStatus.TBR, BookStatus.sanitize(""))
        assertEquals(BookStatus.PAUSED, BookStatus.sanitize("paused"))
    }

    @Test
    fun `the entity default is one of the five`() {
        val fresh = BookEntity(id = "bk_1", title = "Livro", createdAt = 0L)
        assertEquals(BookStatus.TBR, fresh.status)
    }
}
