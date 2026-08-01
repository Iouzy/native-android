package com.pauta.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * native-only (R2 · R3): the main process's side of the `:reader` binder. This
 * file — and everything above it in the app — never names `PdfRenderer`; it asks
 * [DocumentParseService] for an answer and treats a dead `:reader` as an answer
 * of "no", never as something to retry in a loop. // PT: cliente do processo
 * :reader; a morte do processo é uma resposta, não um motivo para insistir.
 */
object DocumentParse {

    private const val TIMEOUT_MS = 20_000L

    /** A PDF's page count, or [DocumentParseService.FAILED] if it would not open.
     *  One-shot: binds, asks, unbinds — used by the import gate, which has no
     *  reader open. // PT: uma pergunta só, para o momento de anexar. */
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

/** What a PDF turned out to be: how many pages, and the first page's size in
 *  points — enough to lay out pages before they are drawn. */
data class PdfInfo(val pageCount: Int, val pageWidth: Int, val pageHeight: Int)

/**
 * native-only (R3): one reader's worth of `:reader`. Binds once, keeps the
 * document open across page renders, and hands back plain [Bitmap]s.
 *
 * Two properties matter and are deliberate:
 *
 * - **One request at a time.** `PdfRenderer` allows a single open page, and a
 *   render streams megabytes through a pipe; a [Mutex] keeps callers in a queue
 *   instead of letting a fast scroll open five at once.
 * - **Death is final, and quiet.** When `:reader` dies (the malformed-PDF case
 *   this whole arrangement exists for) the session unbinds and every later call
 *   returns null, so the UI shows its error once instead of respawning the
 *   process in a loop.
 *
 * // PT: uma sessão de leitura sobre o processo :reader — um pedido de cada vez,
 * e a morte do processo é definitiva (sem ciclos de reinício).
 */
class PdfSession(context: Context) {

    private val app = context.applicationContext
    private val mutex = Mutex()

    private var conn: ServiceConnection? = null
    private var pending: CompletableDeferred<Messenger?>? = null
    private var messenger: Messenger? = null

    /** True once `:reader` has died or refused to bind — see the class comment. */
    @Volatile
    var died: Boolean = false
        private set

    private var closed = false

    /** Opens [path] in `:reader`, or null when it would not open. */
    suspend fun open(path: String): PdfInfo? = mutex.withLock {
        val m = bind() ?: return null
        val reply = request(m, DocumentParseService.MSG_PDF_OPEN) {
            putString(DocumentParseService.KEY_PATH, path)
        } ?: return null
        if (reply.value <= 0) null else PdfInfo(reply.value, reply.width, reply.height)
    }

    /**
     * Renders one page [widthPx] pixels wide, or null when it could not be drawn.
     * The pixels come down a pipe rather than the binder (a page is far larger
     * than a transaction), and the end of that stream is the whole answer — a
     * refusal and a crashed process both read as EOF. // PT: os píxeis vêm por um
     * "pipe"; o fim do stream é a resposta, mesmo quando o processo morre.
     */
    suspend fun render(page: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (widthPx <= 0) return null
        val m = bind() ?: return null
        withContext(Dispatchers.IO) {
            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
                ?: return@withContext null
            val (read, write) = pipe[0] to pipe[1]
            val msg = Message.obtain(null, DocumentParseService.MSG_PDF_RENDER).apply {
                data = Bundle().apply {
                    putInt(DocumentParseService.KEY_PAGE, page)
                    putInt(DocumentParseService.KEY_WIDTH, widthPx)
                    putParcelable(DocumentParseService.KEY_PIPE, write)
                }
            }
            val sent = runCatching { m.send(msg) }.isSuccess
            // Our copy of the write end goes now, whatever happened: the binder
            // duplicated it during the send, and while we hold one the read below
            // would never see EOF. // PT: fechar já a ponta de escrita — senão o
            // fim do stream nunca chega.
            runCatching { write.close() }
            if (!sent) {
                runCatching { read.close() }
                return@withContext null
            }
            readPixels(read)
        }
    }

    /** Lets the document go and unbinds. Safe to call twice. */
    fun close() {
        closed = true
        messenger?.let {
            runCatching { it.send(Message.obtain(null, DocumentParseService.MSG_PDF_CLOSE)) }
        }
        // Release anyone waiting on the binding rather than leaving them to time
        // out on a session that is over. // PT: liberta quem esperava a ligação.
        pending?.complete(null)
        unbind()
    }

    // ── binding ───────────────────────────────────────────────

    private suspend fun bind(): Messenger? {
        if (died || closed) return null
        pending?.let { return withTimeoutOrNull(TIMEOUT_MS) { it.await() } }
        val deferred = CompletableDeferred<Messenger?>()
        pending = deferred
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val m = binder?.let { Messenger(it) }
                messenger = m
                deferred.complete(m)
            }
            override fun onServiceDisconnected(name: ComponentName?) = die()
            override fun onBindingDied(name: ComponentName?) = die()
            override fun onNullBinding(name: ComponentName?) = die()
        }
        conn = c
        val ok = runCatching {
            app.bindService(Intent(app, DocumentParseService::class.java), c, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!ok) {
            die()
            return null
        }
        return withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
    }

    /** `:reader` is gone. Record it, release anyone waiting, and stay unbound —
     *  rebinding here is what a retry loop looks like. // PT: o processo morreu;
     *  não voltar a ligar. */
    private fun die() {
        died = true
        pending?.complete(null)
        unbind()
    }

    private fun unbind() {
        conn?.let { runCatching { app.unbindService(it) } }
        conn = null
        pending = null
        messenger = null
    }

    // ── the wire ──────────────────────────────────────────────

    private data class Reply(val value: Int, val width: Int, val height: Int)

    private suspend fun request(
        m: Messenger,
        what: Int,
        fill: Bundle.() -> Unit,
    ): Reply? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine<Reply?> { cont ->
            val replyTo = Messenger(
                Handler(Looper.getMainLooper()) { msg ->
                    if (cont.isActive) {
                        cont.resume(
                            Reply(
                                value = msg.arg1,
                                width = msg.data?.getInt(DocumentParseService.KEY_WIDTH, 0) ?: 0,
                                height = msg.data?.getInt(DocumentParseService.KEY_HEIGHT, 0) ?: 0,
                            ),
                        )
                    }
                    true
                },
            )
            val msg = Message.obtain(null, what).apply {
                data = Bundle().apply(fill)
                this.replyTo = replyTo
            }
            runCatching { m.send(msg) }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    /** Reads `[int w][int h][ARGB_8888 pixels]` and rebuilds the page, or null on a
     *  short/empty stream — which is how `:reader` says no. // PT: lê a página do
     *  stream; um stream vazio é uma recusa. */
    private fun readPixels(read: ParcelFileDescriptor): Bitmap? = runCatching {
        ParcelFileDescriptor.AutoCloseInputStream(read).use { stream ->
            val input = DataInputStream(stream)
            val w = input.readInt()
            val h = input.readInt()
            // The far side is our own process, but the sizes still bound the
            // allocation — a garbled header must not become a 2 GB array. // PT: o
            // cabeçalho é limitado, para nunca alocar o que não faz sentido.
            if (w !in 1..DocumentParseService.MAX_EDGE || h !in 1..DocumentParseService.MAX_EDGE) {
                return@use null
            }
            val bytes = ByteArray(w * h * 4)
            input.readFully(bytes)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            }
        }
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
