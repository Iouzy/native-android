package com.pauta.app.service

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import com.pauta.app.data.BookFiles
import com.pauta.app.domain.Epub
import com.pauta.app.domain.EpubBook
import com.pauta.app.domain.EpubChapter
import com.pauta.app.domain.ReaderMath
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/**
 * native-only (R2 · extended by R3 and R4, §2 of the reader's Security model):
 * the process every attached book is parsed in, and **the only file in the tree
 * that names `PdfRenderer`**. It is native platform code and a malformed PDF can
 * corrupt memory or abort the process outright — a crash no Kotlin `try` can
 * catch. Running it here, on `:reader`, means such a crash kills this process and
 * nothing else: the user's planner data, their running focus block and their
 * unsaved state all survive, and they see a friendly error instead of the app
 * disappearing.
 *
 * To be honest about what this buys: a separate `android:process` shares the
 * app's UID and therefore its storage permissions. It is a **crash and
 * fault-containment boundary, not a privilege boundary** — worthwhile, but not
 * a sandbox.
 *
 * The service is `exported="false"`, receives an already-validated path from the
 * main process (never a `content://` uri), re-checks that the path is inside
 * `filesDir/books/` before opening it, writes no files and returns only the answer
 * asked for: a page count, one page's pixels, or one chapter's sanitised HTML,
 * down a pipe the caller supplied. A book's original markup never crosses back.
 * // PT: o processo :reader — onde o livro é aberto, para que um crash nativo não
 * leve a app com ele.
 *
 * ### The protocol
 *
 * `Messenger`-based, one request per [Message]. R2's one-shot page count keeps
 * its own open/close; R3 adds a session — [MSG_PDF_OPEN] keeps the document open
 * so [MSG_PDF_RENDER] can serve page after page without re-parsing the file, and
 * [MSG_PDF_CLOSE] lets it go. R4 mirrors that trio for EPUBs
 * ([MSG_EPUB_OPEN] / [MSG_EPUB_CHAPTER] / [MSG_EPUB_CLOSE]), with the parsing and
 * the allow-list sanitiser both on this side of the binder.
 *
 * Rendered pages are **not** replied over the binder: a page is megabytes of
 * pixels and a binder transaction is a megabyte in total. The caller sends the
 * write end of a pipe with the request; this service streams
 * `[int width][int height][ARGB_8888 pixels]` into it and closes it — and a
 * chapter's HTML travels the same way, for the same reason. Failure — a bad page
 * index, a renderer that threw, a process that died — is an empty stream, so the
 * caller's read is the single place the answer arrives.
 * // PT: as páginas (e os capítulos) vão por um "pipe", não pelo binder; o fim do
 * stream é a resposta.
 */
class DocumentParseService : Service() {

    companion object {
        /** Request: `data[KEY_PATH]` → reply `arg1` = page count, or -1 on failure. */
        const val MSG_PDF_PAGE_COUNT = 1

        /** R3 · open (and keep open) `data[KEY_PATH]` → reply `arg1` = page count,
         *  `data[KEY_WIDTH]`/`data[KEY_HEIGHT]` = the first page's size in points. */
        const val MSG_PDF_OPEN = 2

        /** R3 · render `data[KEY_PAGE]` at `data[KEY_WIDTH]` px wide into the pipe in
         *  `data[KEY_PIPE]`. No reply — the stream is the answer. */
        const val MSG_PDF_RENDER = 3

        /** R3 · close the open document. No reply. */
        const val MSG_PDF_CLOSE = 4

        /** R4 · open (and keep open) the EPUB at `data[KEY_PATH]` → reply `arg1` =
         *  chapter count (or [FAILED]), `data[KEY_WORDS]` = each chapter's word
         *  count and `data[KEY_HREFS]` its entry name, both in spine order. */
        const val MSG_EPUB_OPEN = 5

        /** R4 · sanitised HTML for chapter `data[KEY_PAGE]`, written as UTF-8 into
         *  the pipe in `data[KEY_PIPE]`. No reply — the stream is the answer. */
        const val MSG_EPUB_CHAPTER = 6

        /** R4 · close the open archive. No reply. */
        const val MSG_EPUB_CLOSE = 7

        const val KEY_PATH = "path"
        const val KEY_PAGE = "page"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_PIPE = "pipe"
        const val KEY_WORDS = "words"
        const val KEY_HREFS = "hrefs"

        /** Reply value for "this file would not open" — the caller fails closed. */
        const val FAILED = -1

        /** Longest edge of a rendered page, in pixels. Above this a page starts
         *  costing more memory than a phone screen can use. // PT: limite do lado
         *  maior de uma página desenhada. */
        const val MAX_EDGE = 2048
    }

    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger

    // The open document. PdfRenderer is not thread-safe and allows one open page
    // at a time; every request is handled on [thread], so the serialisation is
    // structural rather than a lock we could forget to take. // PT: um documento
    // aberto de cada vez, sempre na mesma thread.
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var openPath: String? = null

    // R4: the open EPUB. A zip is not `PdfRenderer` — nothing about it can abort
    // the process natively — but it is parsed here all the same, because §2 of the
    // Security model puts the *parsing* of an attached book on this side of the
    // binder and because a book that turns out to be a decompression bomb should
    // exhaust this process's heap, not the app's. // PT: o EPUB também é lido
    // aqui; se rebentar a memória, rebenta a deste processo.
    private var archive: ZipFile? = null
    private var book: EpubBook? = null
    private var openEpubPath: String? = null

    override fun onCreate() {
        super.onCreate()
        // Parsing blocks; keep it off this process's main looper.
        thread = HandlerThread("reader-parse").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // The last reader went away; don't sit on a file handle. // PT: fecha o
        // documento quando ninguém está ligado.
        Handler(thread.looper).post { closeDocument(); closeArchive() }
        return false
    }

    override fun onDestroy() {
        closeDocument()
        closeArchive()
        thread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        // A copy: the Message (and its Bundle) belongs to the looper and is
        // recycled the moment this returns. // PT: cópia — a mensagem é reciclada.
        val data = msg.data
        when (msg.what) {
            MSG_PDF_PAGE_COUNT -> reply(msg, pageCount(data?.getString(KEY_PATH)))
            MSG_PDF_OPEN -> {
                val opened = openDocument(data?.getString(KEY_PATH))
                reply(msg, opened?.count ?: FAILED, opened)
            }
            MSG_PDF_RENDER -> render(
                page = data?.getInt(KEY_PAGE, -1) ?: -1,
                targetWidth = data?.getInt(KEY_WIDTH, 0) ?: 0,
                pipe = data?.let { BundleCompat.getParcelable(it, KEY_PIPE, ParcelFileDescriptor::class.java) },
            )
            MSG_PDF_CLOSE -> closeDocument()
            MSG_EPUB_OPEN -> {
                val spine = openEpub(data?.getString(KEY_PATH))
                reply(
                    msg,
                    spine?.size ?: FAILED,
                    extras = spine?.let {
                        Bundle().apply {
                            putIntArray(KEY_WORDS, it.map { c -> c.words }.toIntArray())
                            putStringArray(KEY_HREFS, it.map { c -> c.href }.toTypedArray())
                        }
                    },
                )
            }
            MSG_EPUB_CHAPTER -> chapter(
                index = data?.getInt(KEY_PAGE, -1) ?: -1,
                pipe = data?.let { BundleCompat.getParcelable(it, KEY_PIPE, ParcelFileDescriptor::class.java) },
            )
            MSG_EPUB_CLOSE -> closeArchive()
        }
    }

    private fun reply(msg: Message, value: Int, opened: Opened? = null, extras: Bundle? = null) {
        val to = msg.replyTo ?: return
        val out = Message.obtain(null, msg.what, value, 0)
        if (opened != null) {
            out.data = Bundle().apply {
                putInt(KEY_WIDTH, opened.width)
                putInt(KEY_HEIGHT, opened.height)
            }
        } else if (extras != null) {
            out.data = extras
        }
        runCatching { to.send(out) }
    }

    /** What an [MSG_PDF_OPEN] found: how many pages, and the first page's size in
     *  points — enough for the reader to lay out pages it hasn't drawn yet. */
    private data class Opened(val count: Int, val width: Int, val height: Int)

    /** Opens the PDF just far enough to count its pages, leaving no handle behind.
     *  Any failure — not a PDF, truncated, encrypted — comes back as [FAILED] and
     *  the import is refused. // PT: conta as páginas; qualquer falha recusa o
     *  ficheiro. */
    private fun pageCount(path: String?): Int {
        val file = verified(path) ?: return FAILED
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                try { renderer.pageCount } finally { renderer.close() }
            }
        }.getOrDefault(FAILED)
    }

    /** Opens (or re-uses) the document at [path] and keeps it open for rendering. */
    private fun openDocument(path: String?): Opened? {
        val file = verified(path) ?: return null
        if (openPath == file.absolutePath) {
            renderer?.let { return describe(it) }
        }
        closeDocument()
        val opened = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = try {
                PdfRenderer(pfd)
            } catch (e: Throwable) {
                runCatching { pfd.close() }
                throw e
            }
            descriptor = pfd
            renderer = r
            openPath = file.absolutePath
            describe(r)
        }.getOrNull()
        // A file that opens but has no readable first page is not a book we can
        // show; don't sit on its handle. // PT: sem primeira página, fecha tudo.
        if (opened == null) closeDocument()
        return opened
    }

    private fun describe(r: PdfRenderer): Opened? = runCatching {
        if (r.pageCount <= 0) return@runCatching null
        val page = r.openPage(0)
        try {
            Opened(r.pageCount, page.width, page.height)
        } finally {
            page.close()
        }
    }.getOrNull()

    private fun closeDocument() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
        openPath = null
    }

    /**
     * Renders one page into the caller's pipe. The pipe is always closed, so a
     * refusal (no document, page out of range, a renderer that threw) reaches the
     * caller as an empty stream rather than a hang. // PT: desenha uma página para
     * o "pipe"; uma recusa é um stream vazio, nunca uma espera infinita.
     */
    private fun render(page: Int, targetWidth: Int, pipe: ParcelFileDescriptor?) {
        if (pipe == null) return
        ParcelFileDescriptor.AutoCloseOutputStream(pipe).use { out ->
            val r = renderer ?: return
            if (page < 0 || page >= r.pageCount || targetWidth <= 0) return
            runCatching {
                val p = r.openPage(page)
                val (w, h) = try {
                    ReaderMath.fitPage(p.width, p.height, targetWidth, MAX_EDGE)
                } catch (e: Throwable) {
                    p.close()
                    throw e
                }
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    // A PDF page is transparent where it has no ink; paper it first
                    // or the text lands on nothing. // PT: fundo branco por baixo.
                    bitmap.eraseColor(Color.WHITE)
                    p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    p.close()
                }
                val bytes = ByteArray(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
                bitmap.recycle()
                DataOutputStream(BufferedOutputStream(out, 1 shl 16)).run {
                    writeInt(w)
                    writeInt(h)
                    write(bytes)
                    flush()
                }
            }
        }
    }

    // ── R4 · EPUB ─────────────────────────────────────────────

    /**
     * Opens the EPUB at [path] and parses its spine, returning its chapters in
     * reading order — the whole answer the reader needs to lay a book out and
     * weight its progress line. Null when the file is not ours, not a book, or
     * malformed; the caller shows one sentence and doesn't retry.
     * // PT: abre o EPUB e devolve os capítulos, por ordem.
     */
    private fun openEpub(path: String?): List<EpubChapter>? {
        val file = verified(path) ?: return null
        if (openEpubPath == file.absolutePath) {
            book?.let { return it.chapters }
        }
        closeArchive()
        return runCatching {
            val zip = ZipFile(file)
            val parsed = try {
                Epub.parse(zip)
            } catch (e: Throwable) {
                runCatching { zip.close() }
                throw e
            }
            archive = zip
            book = parsed
            openEpubPath = file.absolutePath
            parsed.chapters
        }.getOrElse {
            // A malformed book, a stack overflow from something pathological, a
            // heap this process couldn't find: all the same answer. // PT: qualquer
            // falha é a mesma resposta.
            closeArchive()
            null
        }
    }

    /**
     * Writes one chapter's sanitised HTML into the caller's pipe as UTF-8. Like a
     * rendered page, it goes down a pipe rather than the binder — a chapter with
     * its images inlined is comfortably larger than a transaction — and, like a
     * page, a refusal is an empty stream rather than a hang. // PT: o capítulo vai
     * por um "pipe"; recusar é fechar o stream vazio.
     */
    private fun chapter(index: Int, pipe: ParcelFileDescriptor?) {
        if (pipe == null) return
        ParcelFileDescriptor.AutoCloseOutputStream(pipe).use { out ->
            val zip = archive ?: return
            val chapters = book?.chapters ?: return
            if (index < 0 || index >= chapters.size) return
            runCatching {
                val html = Epub.chapterHtml(zip, chapters[index].href)
                BufferedOutputStream(out, 1 shl 16).run {
                    write(html.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
        }
    }

    private fun closeArchive() {
        runCatching { archive?.close() }
        archive = null
        book = null
        openEpubPath = null
    }

    /** §5 of the Security model: the reader opens exactly one kind of path — one
     *  inside `filesDir/books/`. Re-checked here, in the process that does the
     *  opening, so a tampered database row can't point it at an arbitrary file
     *  even if the main process were talked into asking. // PT: só abre caminhos
     *  dentro de filesDir/books, verificado aqui também. */
    private fun verified(path: String?): File? {
        if (path == null || !BookFiles.isOurs(this, path)) return null
        return File(path).takeIf { it.isFile }
    }
}
