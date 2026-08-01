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
import com.pauta.app.i18n.tr
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
    val done by vm.booksDone.collectAsStateWithLifecycle()
    val book = remember(reading, tbr, done, bookId) {
        (reading + tbr + done).firstOrNull { it.id == bookId }
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
    // Re-attaching the same book lands on the same path (`books/<id>.pdf`), so the
    // path alone can't tell "there is a file now" from "there wasn't one a moment
    // ago". This counter can. // PT: reanexar dá o mesmo caminho — é este contador
    // que diz que o ficheiro mudou.
    var attachEpoch by remember { mutableIntStateOf(0) }
    // §5 of the Security model: the reader opens exactly one path — the one on the
    // book's row, and only if it really is inside `filesDir/books/`. A tampered
    // database row gets no file handle. (`:reader` checks again on its side.)
    // // PT: só abre o ficheiro do livro, e só se estiver mesmo em filesDir/books.
    val present = remember(path, attachEpoch) {
        path != null && BookFiles.isOurs(context, path) && File(path).isFile
    }
    // R3 reads PDFs. An attached EPUB keeps its manual progress editor until R4
    // teaches this shell the other branch. // PT: por agora o leitor é de PDFs.
    val readable = present && book.fileKind == "pdf"

    val session = remember(path, readable) { if (readable) PdfSession(context) else null }
    var info by remember(path, readable) { mutableStateOf<PdfInfo?>(null) }
    var failed by remember(path, readable) { mutableStateOf(false) }
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        val opened = s.open(path!!)
        if (opened == null) failed = true else info = opened
    }
    DisposableEffect(session) { onDispose { session?.close() } }

    val listState = rememberLazyListState()
    var restored by remember(path) { mutableStateOf(false) }
    // R5: where this sitting began — the page the bookmark opened on, 1-based like
    // every page the app shows. It is the session's starting line, and the reason a
    // ten-second peek can tell itself apart from reading. // PT: a página onde esta
    // sessão começou.
    var startPage by remember(path) { mutableIntStateOf(0) }
    // Restore before anything can scroll: the bookmark is a page index, and a
    // book with no bookmark starts at the beginning. // PT: repõe a página
    // guardada antes de qualquer scroll.
    LaunchedEffect(info) {
        val pages = info?.pageCount ?: return@LaunchedEffect
        val start = (book.readPosition.toIntOrNull() ?: 0).coerceIn(0, (pages - 1).coerceAtLeast(0))
        listState.scrollToItem(start)
        startPage = start + 1
        restored = true
    }
    // A page that stays put for a second is where the reader is. // PT: a página
    // que fica parada um segundo é a posição a guardar.
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { page ->
                delay(PositionDebounceMs)
                vm.setReadPosition(bookId, page.toString())
            }
    }
    // …and leaving is a settle too, however abrupt.
    //
    // R5: it is also the end of a reading session — the bookmark, the progress and
    // the block are one write, because they all describe the same act and two
    // read-modify-writes racing over the book's row would lose one of them. Nothing
    // is asked: the reader knows what page it was left on. // PT: sair guarda o
    // marcador, o progresso e a sessão de uma só vez — sem perguntar a página.
    DisposableEffect(bookId, path) {
        onDispose {
            if (restored) {
                val page = listState.firstVisibleItemIndex
                vm.endReading(bookId, startPage, page + 1, page.toString())
            }
        }
    }

    // Chrome starts visible (so the way back is never a secret), then gets out of
    // the way. With nothing to read it stays. // PT: a cromagem aparece, esconde-se
    // sozinha, e fica se não houver nada para ler.
    val showingPages = present && readable && !failed && info != null

    // R5: opening the reader is starting to read. A document that actually renders
    // starts the very session the Sessão tab starts — the same block, the same
    // project ("book:<id>"), so the timer, the focus notification and the history
    // need to know nothing about the reader. A session already running for this
    // book is joined, never doubled; a file that won't open starts nothing.
    // // PT: abrir o livro começa (ou entra n') a sessão de leitura — a mesma que
    // a tab Sessão cria. Um ficheiro que não abre não começa nada.
    LaunchedEffect(showingPages) {
        if (showingPages) vm.beginReading(bookId, book.title)
    }

    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(chrome, showingPages) {
        if (chrome && showingPages) {
            delay(ChromeLingerMs)
            chrome = false
        }
    }
    val chromeVisible = chrome || !showingPages

    var showDetail by remember { mutableStateOf(false) }
    val page = listState.firstVisibleItemIndex + 1
    val pages = info?.pageCount ?: 0

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
            failed || !readable -> ReaderNotice(tr("Não foi possível abrir este ficheiro."))
            info != null -> PdfPages(
                session = session!!,
                info = info!!,
                listState = listState,
                onTapMiddle = { chrome = !chrome },
                onReaderDied = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
            else -> ReaderNotice(tr("A abrir…"))
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
            visible = chromeVisible && showingPages && pages > 0,
            enter = if (animate) fadeIn(PautaMotion.tween()) else EnterTransition.None,
            exit = if (animate) fadeOut(PautaMotion.tween()) else ExitTransition.None,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(page = page, pages = pages)
        }
    }

    if (showDetail) {
        // No "Ler" from in here — you are already reading. // PT: sem "Ler" — já
        // se está a ler.
        BookDetailSheet(bookId = bookId, onDismiss = { showDetail = false })
    }
}

/**
 * R3: whether a book can be opened in the reader right now — it has an attached
 * document of a kind this build renders, that file is one of ours, and it is
 * still on disk. The shelf and the detail sheet both ask before offering to read.
 * R5 asks it of the session tab's "Continuar a ler" too, where there may be no
 * book selected at all — hence the nullable argument. // PT: se o livro pode mesmo
 * ser aberto — formato suportado e ficheiro nosso, ainda presente.
 */
@Composable
internal fun rememberCanRead(book: BookEntity?): Boolean {
    val context = LocalContext.current
    return remember(book?.filePath, book?.fileKind) {
        val path = book?.filePath
        book?.fileKind == "pdf" && path != null &&
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

/** Where you are, twice: the count and a hairline of it. // PT: a posição, em
 *  número e em traço. */
@Composable
private fun ReaderBottomBar(page: Int, pages: Int) {
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
                    .fillMaxWidth((page.toFloat() / pages.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
        Text(
            text = "$page / $pages",
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
