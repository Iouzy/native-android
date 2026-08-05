package com.pauta.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.AttachResult
import com.pauta.app.data.BookFiles
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.domain.BookImport
import com.pauta.app.domain.Epub
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.service.EpubInfo
import com.pauta.app.service.EpubSession
import com.pauta.app.service.PdfInfo
import com.pauta.app.service.PdfSession
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.ScreenMode
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.PautaMotion
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.theme.rememberMotionEnabled
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

/** How long the chrome lingers before getting out of the way. */
private const val ChromeLingerMs = 2_000L

/** How long a page must be settled before the bookmark is written. */
private const val PositionDebounceMs = 1_000L

/**
 * native-only (R3): the reader — a full-surface destination, not a sheet, shared
 * by both formats (R4 adds the EPUB branch). It owns everything around the page:
 * the chrome that gets out of the way, the window while you read, the bookmark —
 * and, since R5, the reading session itself. Opening it starts one and closing it
 * concludes it, so the book's progress is something the app observes rather than
 * something the user reports.
 *
 * The reading surface itself is quiet on purpose. Chrome hides two seconds after
 * it appears and returns on a tap in the middle of the page; the system bars go
 * for as long as the reader is open (whatever the `immersive` preference says)
 * and come back exactly as the user had them on the way out. Position is written
 * a second after a page settles and again on the way out, so closing the app
 * mid-page loses nothing.
 *
 * // PT: o leitor — um destino de página inteira, com a cromagem a esconder-se
 * sozinha, as barras do sistema fora enquanto se lê, e a posição guardada sem
 * ninguém ter de a escrever.
 *
 * @param bookId the book to read; the reader closes itself if it disappears.
 * @param onClose back — to wherever the reader was opened from.
 */
@Composable
fun ReaderScreen(bookId: String, onClose: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val context = LocalContext.current
    val colors = LocalPautaColors.current
    val animate = rememberMotionEnabled()

    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val paused by vm.booksPaused.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    // L3: all four shelves — a paused book still opens, and a reader that closed
    // itself on one would be the worst kind of surprise. // PT: as quatro
    // prateleiras; um livro em pausa também abre.
    val book = remember(reading, tbr, paused, done, bookId) {
        (reading + tbr + paused + done).firstOrNull { it.id == bookId }
    }
    LaunchedEffect(book == null) { if (book == null) onClose() }
    if (book == null) return

    // The reader claims the window for as long as it is on screen, and gives it
    // straight back. // PT: o leitor toma a janela enquanto está aberto.
    DisposableEffect(Unit) {
        ScreenMode.immersive = true
        ScreenMode.keepAwake = true
        onDispose {
            ScreenMode.immersive = false
            ScreenMode.keepAwake = false
        }
    }

    val path = book.filePath
    // Re-attaching the same book lands on the same path (`books/<id>.<ext>`), so
    // the path alone can't tell "there is a file now" from "there wasn't one a
    // moment ago". This counter can. // PT: reanexar dá o mesmo caminho — é este
    // contador que diz que o ficheiro mudou.
    var attachEpoch by remember { mutableIntStateOf(0) }
    // §5 of the Security model: the reader opens exactly one path — the one on the
    // book's row, and only if it really is inside `filesDir/books/`. A tampered
    // database row gets no file handle. (`:reader` checks again on its side.)
    // // PT: só abre o ficheiro do livro, e só se estiver mesmo em filesDir/books.
    val present = remember(path, attachEpoch) {
        path != null && BookFiles.isOurs(context, path) && File(path).isFile
    }
    val kind = if (present) book.fileKind else null

    // R4: both formats keep the same shell, and the shell knows about neither.
    // Each half writes where the reader is into this one place — a page for a
    // PDF, a percentage for an EPUB — and the chrome, the bookmark and the
    // session read it without asking which kind of book they are looking at.
    // // PT: os dois formatos escrevem a posição no mesmo sítio; a casca não
    // precisa de saber qual é qual.
    val state = remember(path, kind) { ReaderState() }

    // R5: opening the reader is starting to read. A document that actually renders
    // starts the very session the Sessão tab starts — the same block, the same
    // project ("book:<id>"), so the timer, the focus notification and the history
    // need to know nothing about the reader. A session already running for this
    // book is joined, never doubled; a file that won't open starts nothing.
    // // PT: abrir o livro começa (ou entra n') a sessão de leitura — a mesma que
    // a tab Sessão cria. Um ficheiro que não abre não começa nada.
    LaunchedEffect(state.ready) {
        if (state.ready) vm.beginReading(bookId, book.title)
    }

    // A position that stays put for a second is where the reader is. // PT: a
    // posição que fica parada um segundo é a que se guarda.
    LaunchedEffect(state.ready) {
        if (!state.ready) return@LaunchedEffect
        snapshotFlow { state.position }
            .distinctUntilChanged()
            .collectLatest { position ->
                delay(PositionDebounceMs)
                if (position.isNotEmpty()) vm.setReadPosition(bookId, position)
            }
    }

    // …and leaving is a settle too, however abrupt.
    //
    // R5: it is also the end of a reading session — the bookmark, the progress and
    // the block are one write, because they all describe the same act and two
    // read-modify-writes racing over the book's row would lose one of them. Nothing
    // is asked: the reader knows where it was left. // PT: sair guarda o marcador,
    // o progresso e a sessão de uma só vez — sem perguntar nada.
    DisposableEffect(bookId, path, kind) {
        onDispose {
            if (state.ready) vm.endReading(bookId, state.startUnit, state.unit, state.position)
        }
    }

    // Chrome starts visible (so the way back is never a secret), then gets out of
    // the way. With nothing to read it stays. // PT: a cromagem aparece, esconde-se
    // sozinha, e fica se não houver nada para ler.
    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(chrome, state.ready) {
        if (chrome && state.ready) {
            delay(ChromeLingerMs)
            chrome = false
        }
    }
    val chromeVisible = chrome || !state.ready

    var showDetail by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.paper),
    ) {
        when {
            // The file went away, or was never ours: offer it back rather than
            // explaining. // PT: sem ficheiro, oferece anexar de novo.
            !present -> MissingFileNotice(bookId = bookId, onAttached = { attachEpoch++ })
            // It wouldn't open, or `:reader` died trying — said once, and not
            // retried. // PT: não abriu (ou o processo morreu) — dito uma vez.
            state.failed -> ReaderNotice(
                if (kind == "epub") tr("Não foi possível abrir este EPUB.")
                else tr("Não foi possível abrir este ficheiro."),
            )
            kind == "pdf" -> PdfReaderHost(
                path = path!!,
                book = book,
                state = state,
                onTapMiddle = { chrome = !chrome },
            )
            kind == "epub" -> EpubReaderHost(
                path = path!!,
                book = book,
                state = state,
                onTapMiddle = { chrome = !chrome },
                onWordCount = { words -> vm.setWordCount(bookId, words) },
            )
            else -> ReaderNotice(tr("Não foi possível abrir este ficheiro."))
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = if (animate) fadeIn(PautaMotion.tween()) else EnterTransition.None,
            exit = if (animate) fadeOut(PautaMotion.tween()) else ExitTransition.None,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = book.title,
                onBack = onClose,
                onDetails = { showDetail = true },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible && state.ready && state.label.isNotEmpty(),
            enter = if (animate) fadeIn(PautaMotion.tween()) else EnterTransition.None,
            exit = if (animate) fadeOut(PautaMotion.tween()) else ExitTransition.None,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(label = state.label, fraction = state.fraction)
        }
    }

    if (showDetail) {
        // No "Ler" from in here — you are already reading. // PT: sem "Ler" — já
        // se está a ler.
        BookDetailSheet(bookId = bookId, onDismiss = { showDetail = false })
    }
}

/**
 * Where the reader is, in the one shape the shell understands. [unit] is what a
 * reading session is measured in — a page for a PDF, a percentage point for an
 * EPUB — which is exactly what `BookMath` already means by a unit of progress,
 * so the session, the pace and the speed all keep working across both formats
 * without knowing which they are looking at. // PT: a posição em unidades: página
 * num PDF, ponto percentual num EPUB.
 */
private class ReaderState {
    /** True once the document is open, restored and showing. */
    var ready by mutableStateOf(false)
    /** True once it will never open — said once, never retried. */
    var failed by mutableStateOf(false)
    /** Where this sitting started, so a peek can tell itself apart from reading. */
    var startUnit by mutableIntStateOf(0)
    /** Where the reader is now. */
    var unit by mutableIntStateOf(0)
    /** The bookmark, in the format its own half of the reader parses back. */
    var position by mutableStateOf("")
    /** What the chrome shows: "80 / 228" or "43%". */
    var label by mutableStateOf("")
    /** How full the hairline is, 0–1. */
    var fraction by mutableFloatStateOf(0f)
}

/** The one line the EPUB chrome shows: how far through the book, and which
 *  chapter of how many — a percentage alone tells you nothing about where to stop.
 *  // PT: a percentagem e o capítulo; só a percentagem não diz onde parar. */
private fun chapterLabel(percent: Int, chapter: Int, chapters: Int): String =
    "$percent% · " + trf("Capítulo {n} de {total}", "n" to chapter + 1, "total" to chapters)

/**
 * R3: the PDF half — a column of pages drawn in `:reader`, with the bookmark as a
 * plain zero-based page index. // PT: o PDF — páginas desenhadas no processo
 * :reader; o marcador é o índice da página.
 */
@Composable
private fun PdfReaderHost(
    path: String,
    book: BookEntity,
    state: ReaderState,
    onTapMiddle: () -> Unit,
) {
    val context = LocalContext.current
    val session = remember(path) { PdfSession(context) }
    var info by remember(path) { mutableStateOf<PdfInfo?>(null) }
    LaunchedEffect(session) {
        val opened = session.open(path)
        if (opened == null) state.failed = true else info = opened
    }
    DisposableEffect(session) { onDispose { session.close() } }

    val listState = rememberLazyListState()
    // Restore before anything can scroll: the bookmark is a page index, and a
    // book with no bookmark starts at the beginning. // PT: repõe a página
    // guardada antes de qualquer scroll.
    LaunchedEffect(info) {
        val pages = info?.pageCount ?: return@LaunchedEffect
        val start = (book.readPosition.toIntOrNull() ?: 0).coerceIn(0, (pages - 1).coerceAtLeast(0))
        listState.scrollToItem(start)
        state.startUnit = start + 1
        state.unit = start + 1
        state.position = start.toString()
        state.ready = true
    }
    // The visible page is the position, the label and the hairline at once.
    LaunchedEffect(listState, info) {
        val pages = info?.pageCount ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                state.unit = index + 1
                state.position = index.toString()
                state.label = "${index + 1} / $pages"
                state.fraction = ((index + 1).toFloat() / pages.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
    }

    val opened = info
    if (opened != null) {
        PdfPages(
            session = session,
            info = opened,
            listState = listState,
            onTapMiddle = onTapMiddle,
            onReaderDied = { state.failed = true },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        ReaderNotice(tr("A abrir…"))
    }
}

/**
 * R4: the EPUB half — one chapter at a time in one WebView, with the bookmark as
 * `chapter:scroll` and progress weighted by words rather than by chapters, so a
 * forty-page chapter moves the line further than a two-page one.
 *
 * An EPUB has no pages: text reflows with the type size, and a "page" would mean
 * something different at every text scale. So this half counts in **percent**, and
 * that is what the chrome shows, what the bookmark restores and what the session
 * records. // PT: o EPUB conta em percentagem — não tem páginas, o texto reflui.
 */
@Composable
private fun EpubReaderHost(
    path: String,
    book: BookEntity,
    state: ReaderState,
    onTapMiddle: () -> Unit,
    onWordCount: (Int) -> Unit,
) {
    val context = LocalContext.current
    // §6: an engine we don't know is an engine we don't render untrusted HTML in.
    // // PT: sem um WebView atual, não se abre o livro.
    val engineOk = remember { webViewUsable() }
    if (!engineOk) {
        ReaderNotice(tr("Atualiza o Android System WebView para ler EPUBs."))
        return
    }

    val session = remember(path) { EpubSession(context) }
    var info by remember(path) { mutableStateOf<EpubInfo?>(null) }
    LaunchedEffect(session) {
        val opened = session.open(path)
        if (opened == null) state.failed = true else info = opened
    }
    DisposableEffect(session) { onDispose { session.close() } }

    var chapter by remember(path) { mutableIntStateOf(0) }
    var scroll by remember(path) { mutableFloatStateOf(0f) }
    // The scroll to restore *once*, on the chapter the bookmark named. Cleared as
    // soon as it has been used, so turning a chapter later starts at its top.
    // // PT: o scroll a repor uma vez, no capítulo do marcador.
    var restoreScroll by remember(path) { mutableFloatStateOf(0f) }

    val opened = info

    // Every move — a scroll or a chapter turn — is the same four facts, written
    // where the shell reads them. Done here rather than in an effect keyed on the
    // scroll: the engine reports a scroll many times a second, and restarting a
    // coroutine for each one would be a lot of machinery for four assignments.
    // // PT: cada movimento escreve os mesmos quatro factos; sem efeito por
    // evento de scroll.
    fun mark(atChapter: Int, atScroll: Float) {
        val book0 = opened ?: return
        val percent = Epub.percent(book0.chapterWords, atChapter, atScroll)
        state.unit = percent
        state.position = Epub.formatPosition(atChapter, atScroll)
        state.label = chapterLabel(percent, atChapter, book0.chapterCount)
        state.fraction = percent / 100f
    }

    LaunchedEffect(opened) {
        val book0 = opened ?: return@LaunchedEffect
        // The word count is the book's, not a session's: store it once so the
        // detail sheet can stop estimating this book's reading speed at 280 words
        // a page. // PT: guarda a contagem de palavras — o ritmo deixa de ser
        // estimativa.
        val words = book0.chapterWords.sum()
        if (words > 0 && words != book.wordCount) onWordCount(words)

        val mark0 = Epub.parsePosition(book.readPosition)
        val start = (mark0?.chapter ?: 0).coerceIn(0, (book0.chapterCount - 1).coerceAtLeast(0))
        chapter = start
        scroll = mark0?.scroll ?: 0f
        restoreScroll = mark0?.scroll ?: 0f
        mark(start, scroll)
        state.startUnit = state.unit
        state.ready = true
    }

    // Nothing is drawn until the bookmark has been read: composing the first
    // chapter before it would fetch chapter 0 only to throw it away. // PT: só
    // desenha depois do marcador — senão buscava o capítulo 0 para o deitar fora.
    if (opened == null || !state.ready) {
        ReaderNotice(tr("A abrir…"))
        return
    }

    fun turn(to: Int) {
        val next = to.coerceIn(0, opened.chapterCount - 1)
        if (next == chapter) return
        chapter = next
        scroll = 0f
        restoreScroll = 0f
        mark(next, 0f)
    }

    EpubChapterView(
        session = session,
        chapter = chapter,
        restoreScroll = restoreScroll,
        onScroll = { value ->
            scroll = value
            mark(chapter, value)
            // The bookmark has been honoured; from here the reader is following
            // the finger. // PT: o marcador já foi reposto.
            if (restoreScroll != 0f) restoreScroll = 0f
        },
        onTap = { tap ->
            when (tap) {
                ReaderTap.PREVIOUS -> turn(chapter - 1)
                ReaderTap.NEXT -> turn(chapter + 1)
                ReaderTap.MIDDLE -> onTapMiddle()
            }
        },
        // A link inside a book never navigates the engine (that is refused
        // unconditionally); one that points at another chapter of this book is
        // resolved here and turned to. Anything else does nothing, which is the
        // whole point. // PT: um link interno é resolvido aqui; o resto não faz
        // nada.
        onLink = { url -> opened.chapterFor(url)?.let { turn(it) } },
        onFailed = { state.failed = true },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * R3 · R4: whether a book can be opened in the reader right now — it has an
 * attached document of a kind this build renders (both, since R4), that file is
 * one of ours, and it is still on disk. An EPUB on a device with no usable
 * browser engine still says yes: the reader explains why it cannot render it,
 * which is more use than a "Ler" button that quietly isn't there. The shelf and the detail sheet both ask before offering to read.
 * R5 asks it of the session tab's "Continuar a ler" too, where there may be no
 * book selected at all — hence the nullable argument. // PT: se o livro pode mesmo
 * ser aberto — formato suportado e ficheiro nosso, ainda presente.
 */
@Composable
internal fun rememberCanRead(book: BookEntity?): Boolean {
    val context = LocalContext.current
    return remember(book?.filePath, book?.fileKind) {
        val path = book?.filePath
        (book?.fileKind == "pdf" || book?.fileKind == "epub") && path != null &&
            BookFiles.isOurs(context, path) && File(path).isFile
    }
}

/** Back · title · the book's own sheet. Sits on a paper band so it stays legible
 *  over a white page. // PT: recuar, título e a folha do livro. */
@Composable
private fun ReaderTopBar(title: String, onBack: () -> Unit, onDetails: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "←",
            color = colors.accent,
            fontSize = 22.sp,
            modifier = Modifier
                .clickableNoRipple(onBack)
                .semantics { contentDescription = tr("Fechar"); role = Role.Button },
        )
        Text(
            text = title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "⋯",
            color = colors.ink2,
            fontSize = 20.sp,
            modifier = Modifier
                .clickableNoRipple(onDetails)
                .semantics { contentDescription = tr("Detalhes do livro"); role = Role.Button },
        )
    }
}

/** Where you are, twice: the count and a hairline of it. The count is a page in a
 *  PDF and a percentage in an EPUB — the shell is handed the sentence rather than
 *  the numbers. // PT: a posição, em texto e em traço. */
@Composable
private fun ReaderBottomBar(label: String, fraction: Float) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.paper),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.rule),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
        Text(
            text = label,
            color = colors.ink3,
            style = PautaType.Meta,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/** A quiet line where a page would be. // PT: uma linha calada no lugar da página. */
@Composable
private fun ReaderNotice(line: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line,
            color = colors.ink3,
            style = PautaType.Body,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/**
 * The file is gone — a restored backup brings back the book, not the document,
 * and storage can be cleared from under us. Offer to point the book at it again
 * rather than pretending; the reader opens the moment one arrives. // PT: o
 * ficheiro desapareceu (cópia restaurada, armazenamento limpo) — oferece anexar
 * de novo, e o leitor abre assim que houver ficheiro.
 */
@Composable
private fun MissingFileNotice(bookId: String, onAttached: () -> Unit) {
    val vm: AppViewModel = viewModel()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            error = null
            vm.attachFile(bookId, uri) { result ->
                busy = false
                error = when (result) {
                    is AttachResult.Ok -> { onAttached(); null }
                    AttachResult.UnsupportedType -> tr("Só PDF e EPUB por agora.")
                    AttachResult.CopyFailed -> tr("Não foi possível copiar o ficheiro.")
                    is AttachResult.Rejected -> when (result.reason) {
                        BookImport.Rejection.TOO_LARGE -> tr("Este ficheiro é demasiado grande.")
                        BookImport.Rejection.DRM -> tr("Este EPUB está protegido por DRM.")
                        else -> tr("Este ficheiro parece danificado.")
                    }
                }
            }
        }
    }
    ReaderNotice(line = error ?: tr("O ficheiro já não está aqui.")) {
        PautaButton(
            label = if (busy) tr("A copiar…") else tr("Anexar de novo"),
            variant = PautaButtonVariant.Primary,
            enabled = !busy,
        ) {
            picker.launch(arrayOf("application/pdf", "application/epub+zip"))
        }
    }
}
