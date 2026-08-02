package com.pauta.app.data

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.BookNoteEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/**
 * native-only (L2): the book library's own backup format — and the one place
 * that decides what counts as book data.
 *
 * Book mode is a device-local lens, so none of it belongs in the `pauta.v4`
 * export the web app shares. But "not in the backup" had come to mean "not
 * backed up at all": a reinstall lost the shelf, the ratings, the notes and the
 * reading history for good, while the *titles* of every book read leaked into
 * v4 anyway, because a reading session is a [FocusBlockEntity] whose title is
 * the book's. Both halves of that are this file's job — [plannerOnly] keeps the
 * v4 export honest, and `pauta.books.v1` gives the library a rescue path of its
 * own.
 *
 * **The attached documents are not in here.** They are the user's own files,
 * they run to hundreds of megabytes, and a restored book with no file is a
 * state the reader already handles ("O ficheiro já não está aqui." → "Anexar de
 * novo"). [BookEntity.filePath] and [BookEntity.fileKind] are this device's
 * paths and are neither written nor read; [BookEntity.fileName] is, because it
 * is what the user called the file and it makes that prompt mean something.
 *
 * // PT: o formato de backup da biblioteca — e o único sítio que decide o que é
 * dado de livro. Os ficheiros anexados ficam de fora (são do utilizador, e o
 * leitor já sabe lidar com um livro sem ficheiro).
 */
object BookBackup {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** The format id. Nothing but this app reads the file, so it is versioned and
     *  an unknown value is refused rather than guessed at. */
    const val FORMAT = "pauta.books.v1"

    /** A reading session is a focus block whose project is `book:<id>`. */
    const val PROJECT_PREFIX = "book:"

    /** Everything the library export carries. // PT: o conteúdo do ficheiro. */
    data class Library(
        val books: List<BookEntity>,
        val notes: List<BookNoteEntity>,
        val blocks: List<FocusBlockEntity>,
        val sessions: List<FocusSessionEntity>,
    ) {
        val isEmpty: Boolean get() = books.isEmpty() && notes.isEmpty() && blocks.isEmpty()
    }

    /** Thrown when the file is not a `pauta.books.v1` library. // PT: não é uma
     *  biblioteca Pauta. */
    class NotALibraryException : IllegalArgumentException("not a $FORMAT file")

    // ── the planner / book split ──────────────────────────────
    // One rule, one place. `AppViewModel` had it for the Pauta tab and the export
    // never learned it, which is how the book titles ended up in v4.
    // // PT: uma só regra, num só sítio.

    /** Is this block a reading session rather than planner focus? */
    fun isBookBlock(block: FocusBlockEntity): Boolean =
        block.project?.startsWith(PROJECT_PREFIX) == true

    /** The book id a reading-session block belongs to, or null for planner work. */
    fun bookIdOf(block: FocusBlockEntity): String? =
        if (isBookBlock(block)) block.project!!.removePrefix(PROJECT_PREFIX) else null

    /** The blocks (and only the sessions belonging to them) that the `pauta.v4`
     *  export may see. // PT: o que o export v4 pode ver. */
    fun plannerOnly(
        blocks: List<FocusBlockEntity>,
        sessions: List<FocusSessionEntity>,
    ): Pair<List<FocusBlockEntity>, List<FocusSessionEntity>> = split(blocks, sessions, book = false)

    /** The mirror image: the reading sessions, for the library export. */
    fun booksOnly(
        blocks: List<FocusBlockEntity>,
        sessions: List<FocusSessionEntity>,
    ): Pair<List<FocusBlockEntity>, List<FocusSessionEntity>> = split(blocks, sessions, book = true)

    private fun split(
        blocks: List<FocusBlockEntity>,
        sessions: List<FocusSessionEntity>,
        book: Boolean,
    ): Pair<List<FocusBlockEntity>, List<FocusSessionEntity>> {
        val kept = blocks.filter { isBookBlock(it) == book }
        val ids = kept.mapTo(HashSet()) { it.id }
        return kept to sessions.filter { it.blockId in ids }
    }

    // ── export ────────────────────────────────────────────────

    /** `pauta-livros-<YYYY-MM-DD>.json`'s contents. */
    fun export(lib: Library): String {
        val segsByBlock = lib.sessions.groupBy { it.blockId }
        val root = buildJsonObject {
            put("format", FORMAT)
            put("exportedAt", Instant.now().toString())
            putJsonArray("books") {
                lib.books.forEach { b ->
                    addJsonObject {
                        put("id", b.id); put("title", b.title); put("author", b.author)
                        put("series", b.series)
                        b.seriesNumber?.let { put("seriesNumber", it) }
                        put("format", b.format)
                        put("totalPages", b.totalPages); put("currentPage", b.currentPage)
                        put("status", b.status)
                        b.startedAt?.let { put("startedAt", it) }
                        b.finishedAt?.let { put("finishedAt", it) }
                        b.rating?.let { put("rating", it) }
                        put("genre", b.genre); put("position", b.position); put("createdAt", b.createdAt)
                        // The name of the file, never the path to it. // PT: o nome,
                        // nunca o caminho.
                        put("fileName", b.fileName)
                        put("readPosition", b.readPosition)
                        put("wordCount", b.wordCount)
                    }
                }
            }
            putJsonArray("notes") {
                lib.notes.forEach { n ->
                    addJsonObject {
                        put("id", n.id); put("bookId", n.bookId); put("kind", n.kind); put("text", n.text)
                        n.page?.let { put("page", it) }
                        put("createdAt", n.createdAt)
                    }
                }
            }
            putJsonArray("sessions") {
                lib.blocks.forEach { blk ->
                    addJsonObject {
                        put("id", blk.id); put("title", blk.title); put("project", blk.project)
                        blk.targetMs?.let { put("targetMs", it) }
                        put("status", blk.status); put("reflection", blk.reflection)
                        put("createdAt", blk.createdAt)
                        // R5's measured page span — the whole reason a v4 file could
                        // never hold this. // PT: o intervalo medido pelo leitor.
                        blk.pagesDelta?.let { put("pagesDelta", it) }
                        putJsonArray("segments") {
                            segsByBlock[blk.id].orEmpty().sortedBy { it.position }.forEach { s ->
                                addJsonObject {
                                    put("startedAt", s.startedAt)
                                    s.endedAt?.let { put("endedAt", it) }
                                    put("note", s.note)
                                }
                            }
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    // ── import ────────────────────────────────────────────────
    // Treat the file as untrusted input, because it is: it is a JSON file the
    // user picked, and nothing stops it having been hand-edited. Every enum falls
    // back to its default rather than reaching the DB as an unreachable state,
    // and a path in the file is ignored outright (§5 of the Security model).
    // // PT: o ficheiro é entrada não fiável — enumerações com recuo seguro e
    // caminhos ignorados.

    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement?.str(): String? = prim()?.contentOrNull
    private fun JsonElement?.long(): Long? = prim()?.longOrNull ?: prim()?.contentOrNull?.toLongOrNull()
    private fun JsonElement?.int(): Int? = prim()?.intOrNull ?: prim()?.contentOrNull?.toIntOrNull()
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.array(): JsonArray? = this as? JsonArray

    private val STATUSES = setOf("tbr", "reading", "done", "dnf", "paused")
    private val FORMATS = setOf("physical", "ebook", "audiobook")
    private val NOTE_KINDS = setOf("quote", "annotation", "thought")

    /**
     * Parse a library file. Throws [NotALibraryException] when the envelope isn't
     * ours — refusing beats guessing, and a v4 planner backup picked by mistake
     * has to fail loudly rather than import as an empty shelf.
     */
    fun import(text: String): Library {
        val root = runCatching { json.parseToJsonElement(text).obj() }.getOrNull()
            ?: throw NotALibraryException()
        if (root["format"].str() != FORMAT) throw NotALibraryException()

        val books = mutableListOf<BookEntity>()
        root["books"].array()?.forEachIndexed { idx, be ->
            val b = be.obj() ?: return@forEachIndexed
            val id = b["id"].str()?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val total = (b["totalPages"].int() ?: 0).coerceAtLeast(0)
            val current = (b["currentPage"].int() ?: 0).coerceAtLeast(0)
            books.add(
                BookEntity(
                    id = id,
                    title = b["title"].str() ?: "",
                    author = b["author"].str() ?: "",
                    series = b["series"].str() ?: "",
                    seriesNumber = b["seriesNumber"].int(),
                    format = b["format"].str()?.takeIf { it in FORMATS } ?: "physical",
                    totalPages = total,
                    // Clamp only where a length is known: 0 means "unknown", and
                    // clamping to it would erase the progress. // PT: só se o total
                    // for conhecido; 0 é "não se sabe".
                    currentPage = if (total > 0) current.coerceAtMost(total) else current,
                    status = b["status"].str()?.takeIf { it in STATUSES } ?: "tbr",
                    startedAt = b["startedAt"].long(),
                    finishedAt = b["finishedAt"].long(),
                    rating = b["rating"].int()?.takeIf { it in 1..5 },
                    genre = b["genre"].str() ?: "",
                    position = b["position"].int() ?: idx,
                    createdAt = b["createdAt"].long() ?: System.currentTimeMillis(),
                    // Never from the file. A restored book has no document until the
                    // user attaches one again. // PT: nunca vêm do ficheiro.
                    filePath = null,
                    fileKind = null,
                    fileName = b["fileName"].str() ?: "",
                    readPosition = b["readPosition"].str() ?: "",
                    wordCount = (b["wordCount"].int() ?: 0).coerceAtLeast(0),
                ),
            )
        }

        val notes = mutableListOf<BookNoteEntity>()
        root["notes"].array()?.forEach { ne ->
            val n = ne.obj() ?: return@forEach
            val id = n["id"].str()?.takeIf { it.isNotBlank() } ?: return@forEach
            val bookId = n["bookId"].str()?.takeIf { it.isNotBlank() } ?: return@forEach
            notes.add(
                BookNoteEntity(
                    id = id,
                    bookId = bookId,
                    kind = n["kind"].str()?.takeIf { it in NOTE_KINDS } ?: "annotation",
                    text = n["text"].str() ?: "",
                    page = n["page"].int()?.takeIf { it >= 0 },
                    createdAt = n["createdAt"].long() ?: System.currentTimeMillis(),
                ),
            )
        }

        val blocks = mutableListOf<FocusBlockEntity>()
        val sessions = mutableListOf<FocusSessionEntity>()
        root["sessions"].array()?.forEach { se ->
            val s = se.obj() ?: return@forEach
            val id = s["id"].str()?.takeIf { it.isNotBlank() } ?: return@forEach
            // Only real reading sessions. A file claiming a planner block here
            // would smuggle one in through the back door. // PT: só sessões de
            // leitura entram por aqui.
            val project = s["project"].str()?.takeIf { it.startsWith(PROJECT_PREFIX) } ?: return@forEach
            // A block left "active" would resurrect a phantom timer on import, so
            // it is paused and its open span closed — the same rule WebBackup
            // applies. // PT: um bloco "activo" é posto em pausa, como no v4.
            val stored = s["status"].str() ?: "done"
            val wasActive = stored == "active"
            blocks.add(
                FocusBlockEntity(
                    id = id,
                    title = s["title"].str() ?: "",
                    project = project,
                    targetMs = s["targetMs"].long(),
                    status = if (wasActive) "paused" else stored,
                    reflection = s["reflection"].str() ?: "",
                    createdAt = s["createdAt"].long() ?: 0L,
                    pagesDelta = s["pagesDelta"].int(),
                ),
            )
            s["segments"].array()?.forEachIndexed { idx, ge ->
                val g = ge.obj() ?: return@forEachIndexed
                var ended = g["endedAt"].long()
                if (wasActive && ended == null) ended = System.currentTimeMillis()
                sessions.add(
                    FocusSessionEntity(
                        blockId = id,
                        startedAt = g["startedAt"].long() ?: 0L,
                        endedAt = ended,
                        note = g["note"].str() ?: "",
                        position = idx,
                    ),
                )
            }
        }

        return Library(books = books, notes = notes, blocks = blocks, sessions = sessions)
    }

    /** The export's filename for a given day key. // PT: o nome do ficheiro. */
    fun fileName(dayKey: String): String = "pauta-livros-$dayKey.json"
}
