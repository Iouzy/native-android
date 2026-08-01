package com.pauta.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.pauta.app.data.BookFiles
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * native-only (R2, §2 of the reader's Security model): the process every
 * attached book is parsed in. `PdfRenderer` is native platform code and a
 * malformed PDF can corrupt memory or abort the process outright — a crash no
 * Kotlin `try` can catch. Running it here, on `:reader`, means such a crash
 * kills this process and nothing else: the user's planner data, their running
 * focus block and their unsaved state all survive, and they see a friendly
 * error instead of the app disappearing.
 *
 * To be honest about what this buys: a separate `android:process` shares the
 * app's UID and therefore its storage permissions. It is a **crash and
 * fault-containment boundary, not a privilege boundary** — worthwhile, but not
 * a sandbox.
 *
 * The service is `exported="false"`, receives an already-validated path from the
 * main process (never a `content://` uri), opens nothing outside
 * `filesDir/books/`, writes no files and returns only the answer asked for.
 * R2 needs one operation — a PDF's page count; R3 adds page rendering here.
 * // PT: o processo :reader — onde o PDF é aberto, para que um crash nativo não
 * leve a app com ele.
 */
class DocumentParseService : Service() {

    companion object {
        /** Request: `data[KEY_PATH]` → reply `arg1` = page count, or -1 on failure. */
        const val MSG_PDF_PAGE_COUNT = 1
        const val KEY_PATH = "path"

        /** Reply value for "this file would not open" — the caller fails closed. */
        const val FAILED = -1
    }

    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        // Parsing blocks; keep it off this process's main looper.
        thread = HandlerThread("reader-parse").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        val reply = when (msg.what) {
            MSG_PDF_PAGE_COUNT -> pageCount(msg.data?.getString(KEY_PATH))
            else -> FAILED
        }
        val to = msg.replyTo ?: return
        runCatching { to.send(Message.obtain(null, msg.what, reply, 0)) }
    }

    /** Opens the PDF just far enough to count its pages. Any failure — not a PDF,
     *  truncated, encrypted — comes back as [FAILED] and the import is refused.
     *  // PT: conta as páginas; qualquer falha recusa o ficheiro. */
    private fun pageCount(path: String?): Int {
        if (path == null || !BookFiles.isOurs(this, path)) return FAILED
        val file = File(path)
        if (!file.isFile) return FAILED
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                try { renderer.pageCount } finally { renderer.close() }
            }
        }.getOrDefault(FAILED)
    }
}

/**
 * The main process's side of the binder. Binds, asks one question, unbinds — and
 * treats a dead `:reader` as an answer of "no", never as something to retry in a
 * loop. // PT: cliente do processo :reader; a morte do processo é uma resposta,
 * não um motivo para insistir.
 */
object DocumentParse {

    private const val TIMEOUT_MS = 20_000L

    /** A PDF's page count, or [DocumentParseService.FAILED] if it would not open. */
    suspend fun pdfPageCount(context: Context, path: String): Int {
        val app = context.applicationContext
        var bound: ServiceConnection? = null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Int> { cont ->
                    fun finish(value: Int) { if (cont.isActive) cont.resume(value) }
                    val replyTo = Messenger(
                        Handler(Looper.getMainLooper()) { m -> finish(m.arg1); true },
                    )
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val msg = Message.obtain(null, DocumentParseService.MSG_PDF_PAGE_COUNT).apply {
                                data = Bundle().apply { putString(DocumentParseService.KEY_PATH, path) }
                                this.replyTo = replyTo
                            }
                            runCatching { Messenger(binder).send(msg) }
                                .onFailure { finish(DocumentParseService.FAILED) }
                        }
                        // The parse process died mid-answer (exactly the case this
                        // whole arrangement exists for). // PT: o processo morreu.
                        override fun onServiceDisconnected(name: ComponentName?) =
                            finish(DocumentParseService.FAILED)
                        override fun onBindingDied(name: ComponentName?) =
                            finish(DocumentParseService.FAILED)
                        override fun onNullBinding(name: ComponentName?) =
                            finish(DocumentParseService.FAILED)
                    }
                    bound = conn
                    val ok = runCatching {
                        app.bindService(
                            Intent(app, DocumentParseService::class.java),
                            conn,
                            Context.BIND_AUTO_CREATE,
                        )
                    }.getOrDefault(false)
                    if (!ok) finish(DocumentParseService.FAILED)
                }
            } ?: DocumentParseService.FAILED
        } finally {
            bound?.let { runCatching { app.unbindService(it) } }
        }
    }
}
