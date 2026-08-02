package com.pauta.app.data

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.BookNoteEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * native-only (L2): the library backup, and the planner/book split that keeps
 * book data out of `pauta.v4`.
 *
 * Two things are on trial here. One is the leak: a reading session is a focus
 * block whose *title is the book's*, so every v4 file the app had ever written
 * listed what its owner was reading. The other is the rescue path that did not
 * exist — the shelf, the ratings, the notes and the reading history had no
 * export at all. // PT: a fuga (livros no v4) e a salvação que não existia.
 */
class BookBackupTest {

    private fun book(
        id: String = "bk_1",
        status: String = "reading",
        format: String = "physical",
        total: Int = 300,
        current: Int = 120,
    ) = BookEntity(
        id = id, title = "A Jangada de Pedra", author = "Saramago",
        series = "", seriesNumber = null, format = format,
        totalPages = total, currentPage = current, status = status,
        startedAt = 1_000L, finishedAt = null, rating = 4, genre = "ficção, ensaio",
        position = 2, createdAt = 900L,
        filePath = "/data/user/0/com.pauta.app/files/books/bk_1.epub",
        fileKind = "epub", fileName = "jangada.epub",
        readPosition = "3:0.42", wordCount = 88_000,
    )

    private fun readingBlock(id: String = "b_r1", bookId: String = "bk_1") = FocusBlockEntity(
        id = id, title = "A Jangada de Pedra", project = "book:$bookId",
        targetMs = null, status = "done", reflection = "boa sessão",
        createdAt = 5_000L, pagesDelta = 17,
    )

    private fun plannerBlock(id: String = "b_p1", project: String? = null) = FocusBlockEntity(
        id = id, title = "Escrever o relatório", project = project,
        targetMs = 1_500_000L, status = "done", reflection = "", createdAt = 4_000L,
    )

    // ── the planner / book split ──────────────────────────────

    @Test fun plannerOnlyDropsReadingBlocksAndTheirSessions() {
        val blocks = listOf(plannerBlock(), readingBlock())
        val sessions = listOf(
            FocusSessionEntity(rowId = 1, blockId = "b_p1", startedAt = 10, endedAt = 20),
            FocusSessionEntity(rowId = 2, blockId = "b_r1", startedAt = 30, endedAt = 40),
        )
        val (keptBlocks, keptSessions) = BookBackup.plannerOnly(blocks, sessions)
        assertEquals(listOf("b_p1"), keptBlocks.map { it.id })
        // The sessions have to go with the block, or the export still leaks the
        // timings of a reading it claims not to know about.
        assertEquals(listOf(1L), keptSessions.map { it.rowId })
    }

    @Test fun booksOnlyIsTheMirrorImage() {
        val blocks = listOf(plannerBlock(), readingBlock())
        val sessions = listOf(
            FocusSessionEntity(rowId = 1, blockId = "b_p1", startedAt = 10, endedAt = 20),
            FocusSessionEntity(rowId = 2, blockId = "b_r1", startedAt = 30, endedAt = 40),
        )
        val (keptBlocks, keptSessions) = BookBackup.booksOnly(blocks, sessions)
        assertEquals(listOf("b_r1"), keptBlocks.map { it.id })
        assertEquals(listOf(2L), keptSessions.map { it.rowId })
    }

    @Test fun aPlannerProjectThatMerelyLooksLikeOneIsPlannerData() {
        // "project" is free text the user types. Someone reading Dune may well
        // have a planner project called "Book: Dune" — that is planner data, and
        // the match is case-sensitive on purpose. // PT: o teste é sensível a
        // maiúsculas de propósito.
        val blocks = listOf(plannerBlock(id = "b_p2", project = "Book: Dune"))
        assertEquals(1, BookBackup.plannerOnly(blocks, emptyList()).first.size)
        assertEquals(0, BookBackup.booksOnly(blocks, emptyList()).first.size)
    }

    @Test fun bookIdOfReadsTheProject() {
        assertEquals("bk_1", BookBackup.bookIdOf(readingBlock()))
        assertNull(BookBackup.bookIdOf(plannerBlock()))
    }

    @Test fun theSameRuleFiltersAV4FileOnTheWayBackIn() {
        // Every v4 file written before L2 carries book blocks. Letting them back
        // in would resurrect sessions for books that may not exist and — v4 has
        // no field for it — overwrite a live session's pagesDelta with null.
        // // PT: os ficheiros v4 antigos ainda trazem blocos de livro.
        val fromAnOldFile = WebBackup.import(
            WebBackup.export(
                WebBackup.Snapshot(
                    todayKey = "2026-08-02", days = emptyList(), intentions = emptyList(),
                    blocks = listOf(plannerBlock(), readingBlock()),
                    sessions = listOf(
                        FocusSessionEntity(rowId = 1, blockId = "b_p1", startedAt = 10, endedAt = 20),
                        FocusSessionEntity(rowId = 2, blockId = "b_r1", startedAt = 30, endedAt = 40),
                    ),
                    habits = emptyList(), logs = emptyList(), respiros = emptyList(), counts = emptyList(),
                    goals = emptyList(), milestones = emptyList(),
                    routines = emptyList(), routineItems = emptyList(),
                    plans = emptyList(), prefs = com.pauta.app.data.entity.PrefsEntity(),
                ),
            ),
        )
        val (blocks, sessions) = BookBackup.plannerOnly(fromAnOldFile.blocks, fromAnOldFile.sessions)
        assertEquals(listOf("b_p1"), blocks.map { it.id })
        assertEquals(listOf("b_p1"), sessions.map { it.blockId })
    }

    // ── export ────────────────────────────────────────────────

    private fun sampleLibrary() = BookBackup.Library(
        books = listOf(book()),
        notes = listOf(
            BookNoteEntity(
                id = "bn_1", bookId = "bk_1", kind = "quote",
                text = "A península move-se.", page = 43, createdAt = 2_000L,
            ),
        ),
        blocks = listOf(readingBlock()),
        sessions = listOf(
            FocusSessionEntity(rowId = 9, blockId = "b_r1", startedAt = 30, endedAt = 40, note = "x", position = 0),
        ),
    )

    @Test fun exportsItsOwnEnvelope() {
        val root = BookBackup.json.parseToJsonElement(BookBackup.export(sampleLibrary())).jsonObject
        assertEquals("pauta.books.v1", root["format"]!!.jsonPrimitive.content)
    }

    @Test fun theDevicesPathsAreNotInTheFile() {
        val text = BookBackup.export(sampleLibrary())
        // The name the user gave the file survives (it makes "Anexar de novo"
        // mean something); the path to it does not.
        assertTrue(text.contains("jangada.epub"))
        assertTrue("filePath leaked into the export", !text.contains("filePath"))
        assertTrue("fileKind leaked into the export", !text.contains("fileKind"))
        assertTrue("a device path leaked into the export", !text.contains("/data/user/"))
    }

    @Test fun pagesDeltaSurvivesTheRoundTrip() {
        // R5's measured span is the field pauta.v4 has nowhere to put — which is
        // half the reason this format exists.
        val back = BookBackup.import(BookBackup.export(sampleLibrary()))
        assertEquals(17, back.blocks.single().pagesDelta!!)
        assertEquals(1, back.sessions.size)
        assertEquals(30L, back.sessions.single().startedAt)
    }

    @Test fun roundTripsTheShelf() {
        val back = BookBackup.import(BookBackup.export(sampleLibrary()))
        val b = back.books.single()
        assertEquals("A Jangada de Pedra", b.title)
        assertEquals("Saramago", b.author)
        assertEquals(4, b.rating!!)
        assertEquals(120, b.currentPage)
        assertEquals("ficção, ensaio", b.genre)
        assertEquals("3:0.42", b.readPosition)
        assertEquals(88_000, b.wordCount)
        // A restored book has no document — the state R3 already handles.
        assertNull(b.filePath)
        assertNull(b.fileKind)
        assertEquals("jangada.epub", b.fileName)
        val n = back.notes.single()
        assertEquals("quote", n.kind)
        assertEquals(43, n.page!!)
    }

    // ── import: the file is untrusted ─────────────────────────

    @Test fun refusesAFileThatIsNotOurs() {
        // A v4 planner backup picked by mistake has to fail loudly, not import as
        // an empty shelf.
        listOf(
            """{"app":"pauta","version":4,"data":{}}""",
            """{"format":"pauta.books.v2","books":[]}""",
            """{"books":[]}""",
            "not json at all",
            "[]",
        ).forEach { text ->
            try {
                BookBackup.import(text)
                fail("expected a refusal for: $text")
            } catch (_: BookBackup.NotALibraryException) {
                // as it should
            }
        }
    }

    @Test fun unknownEnumsFallBackInsteadOfReachingTheDb() {
        val text = """
            {"format":"pauta.books.v1","books":[
              {"id":"bk_x","title":"T","status":"devoured","format":"papyrus","totalPages":100,"currentPage":10}
            ],"notes":[
              {"id":"bn_x","bookId":"bk_x","kind":"telepathy","text":"t"}
            ]}
        """.trimIndent()
        val lib = BookBackup.import(text)
        assertEquals("tbr", lib.books.single().status)
        assertEquals("physical", lib.books.single().format)
        assertEquals("annotation", lib.notes.single().kind)
    }

    @Test fun progressClampsOnlyWhenTheLengthIsKnown() {
        val known = """{"format":"pauta.books.v1","books":[
            {"id":"a","title":"T","totalPages":200,"currentPage":9999}]}"""
        assertEquals(200, BookBackup.import(known).books.single().currentPage)

        // totalPages 0 means "unknown", and clamping to it would erase the
        // progress instead of protecting it.
        val unknown = """{"format":"pauta.books.v1","books":[
            {"id":"a","title":"T","totalPages":0,"currentPage":140}]}"""
        assertEquals(140, BookBackup.import(unknown).books.single().currentPage)

        val negative = """{"format":"pauta.books.v1","books":[
            {"id":"a","title":"T","totalPages":200,"currentPage":-5}]}"""
        assertEquals(0, BookBackup.import(negative).books.single().currentPage)
    }

    @Test fun aHandEditedPathIsIgnored() {
        // §5 of the Security model: a path out of a JSON file is exactly the
        // tampered-row case. Reading it back would point the reader at any file
        // on the device the app can open.
        val text = """{"format":"pauta.books.v1","books":[
            {"id":"a","title":"T","filePath":"/data/data/com.other.app/databases/x.db","fileKind":"pdf"}]}"""
        val b = BookBackup.import(text).books.single()
        assertNull(b.filePath)
        assertNull(b.fileKind)
    }

    @Test fun aPlannerBlockCannotBeSmuggledInThroughTheSessionsList() {
        val text = """{"format":"pauta.books.v1","sessions":[
            {"id":"b_evil","title":"Escrever","project":null,"status":"done"},
            {"id":"b_ok","title":"Livro","project":"book:bk_1","status":"done"}]}"""
        assertEquals(listOf("b_ok"), BookBackup.import(text).blocks.map { it.id })
    }

    @Test fun anActiveSessionIsPausedOnImport() {
        // Otherwise the import resurrects a phantom timer — the same reason
        // WebBackup.import auto-pauses.
        val text = """{"format":"pauta.books.v1","sessions":[
            {"id":"b1","title":"L","project":"book:bk_1","status":"active",
             "segments":[{"startedAt":100}]}]}"""
        val lib = BookBackup.import(text)
        assertEquals("paused", lib.blocks.single().status)
        // and its open span is closed rather than left running.
        assertTrue(lib.sessions.single().endedAt != null)
    }

    @Test fun aRowWithNoIdIsSkippedRatherThanInvented() {
        val text = """{"format":"pauta.books.v1","books":[
            {"title":"no id"},{"id":"","title":"blank id"},{"id":"ok","title":"kept"}]}"""
        assertEquals(listOf("ok"), BookBackup.import(text).books.map { it.id })
    }

    @Test fun missingSectionsAreAnEmptyLibraryNotACrash() {
        val lib = BookBackup.import("""{"format":"pauta.books.v1"}""")
        assertTrue(lib.isEmpty)
    }

    @Test fun theFileNameCarriesTheDay() {
        assertEquals("pauta-livros-2026-08-02.json", BookBackup.fileName("2026-08-02"))
    }
}
