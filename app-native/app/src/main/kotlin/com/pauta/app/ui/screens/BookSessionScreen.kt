package com.pauta.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.ReaderMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.tick
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaMotion
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.theme.rememberMotionEnabled
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/**
 * native-only (K6): the book-mode face of the Pauta tab — a reading-session
 * timer bound to a specific book. A reading session is just a FocusBlockEntity
 * with `project = "book:<id>"`, so the timer, pause/resume and history come for
 * free (see [AppViewModel.bookSessionBlocks]). On conclude we prompt for the new
 * page/minute and persist the session note in the block's reflection. The normal
 * Pauta tab is untouched when book mode is off. // PT: o cronómetro vira sessão
 * de leitura ligada a um livro; concluir pede a página/minuto e guarda a nota.
 *
 * R5: a book with a file no longer goes through any of that by hand. "Continuar a
 * ler" opens the reader at the page it was left on — and since opening the reader
 * *is* starting the session, that card is the primary action here; the manual
 * start card below it belongs to books the app cannot open. The conclude sheet
 * still exists for those, and stops asking the page for the ones it can.
 * // PT: com ficheiro, "Continuar a ler" abre o leitor (que já começa a sessão);
 * o cartão manual fica para os livros sem ficheiro.
 *
 * @param onOpenReader opens the reader for a book with a readable file.
 */
@Composable
fun BookSessionScreen(onOpenReader: (String) -> Unit = {}) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val active by vm.activeBlock.collectAsStateWithLifecycle()
    val activeSessions by vm.activeSessions.collectAsStateWithLifecycle()
    val sessionBlocks by vm.bookSessionBlocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val motion = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current

    // 1s clock tick driving the live timer (same as the planner's Pauta).
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val segsByBlock = remember(allSessions) { allSessions.groupBy { it.blockId } }
    fun blockMs(id: String): Long =
        FocusMath.blockElapsedMs(segsByBlock[id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)

    // The running block is a reading session only when its project is "book:<id>".
    val activeBook = active?.takeIf { it.project?.startsWith("book:") == true }
    val activeBookId = activeBook?.project?.removePrefix("book:")
    val activeBookEntity = reading.firstOrNull { it.id == activeBookId }

    // Today's paused reading sessions — resume works exactly as in the planner.
    val pausedBlocks = sessionBlocks.filter {
        it.status == "paused" && DateUtils.dayKeyOf(it.createdAt) == today
    }

    // Concluded sessions, most recent first, grouped by book title for history.
    val doneBlocks = sessionBlocks.filter { it.status == "done" }.sortedByDescending { it.createdAt }
    val grouped = remember(doneBlocks) {
        val map = LinkedHashMap<String, MutableList<FocusBlockEntity>>()
        doneBlocks.forEach { map.getOrPut(it.title) { mutableListOf() }.add(it) }
        map
    }

    // Start-card selection: default to the only / first reading book; re-pick if
    // the chosen book leaves the reading shelf. // PT: pré-selecciona o livro.
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reading) {
        if (selectedBookId == null || reading.none { it.id == selectedBookId }) {
            selectedBookId = reading.firstOrNull()?.id
        }
    }
    val selectedBook = reading.firstOrNull { it.id == selectedBookId }
    var targetMin by remember { mutableStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    var concludeFor by remember { mutableStateOf<BookEntity?>(null) }

    // R5: the book the reader would open — the one being read, or the one picked
    // for the next session. It only earns the card if the file is really there.
    // // PT: o livro que o leitor abriria, e só se o ficheiro lá estiver mesmo.
    val continueBook = activeBookEntity ?: selectedBook
    val canContinue = rememberCanRead(continueBook)

    // R5: concluding by hand stops asking the page for a book the reader can open —
    // the bookmark already knows it. Without a readable file the prompt is exactly
    // as it was. // PT: com ficheiro legível, concluir deixa de perguntar a página.
    val concludeCanRead = rememberCanRead(concludeFor)
    val concludeDerived = concludeFor?.takeIf { concludeCanRead }
        ?.let { ReaderMath.bookmarkPage(it.readPosition, it.fileKind) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(22.dp))
            // P5: shared ScreenTitle role, same as Estante — the book faces keep
            // one headline line. // PT: título no papel partilhado das tabs.
            Text(
                text = tr("Sessão"),
                color = colors.ink,
                style = PautaType.ScreenTitle,
            )
            Spacer(Modifier.height(20.dp))
        }

        // ── R5 · back into the book, at the page it was left on ──
        // Above the timer card on purpose: with a document attached, opening the
        // reader is how a session starts, so the manual card underneath is the
        // fallback and not the headline. // PT: acima do cartão do cronómetro —
        // com ficheiro, é assim que uma sessão começa.
        if (continueBook != null && canContinue) {
            item(key = "continue-${continueBook.id}") {
                ContinueReadingCard(continueBook) { onOpenReader(continueBook.id) }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Active session OR the start card ──
        // P7: the same crossfade as the planner's hero slot — the book face
        // deliberately echoes those layouts, so it echoes the motion too.
        // // PT: a mesma fusão do cartão herói da Pauta.
        item(key = "active-or-start") {
            AnimatedContent(
                targetState = activeBook,
                contentKey = { it?.id },
                transitionSpec = {
                    if (!motion) {
                        (EnterTransition.None togetherWith ExitTransition.None)
                            .using(SizeTransform { _, _ -> snap() })
                    } else {
                        (
                            fadeIn(PautaMotion.tween(PautaMotion.Base)) togetherWith
                                fadeOut(PautaMotion.tween(PautaMotion.Fast))
                            ).using(SizeTransform { _, _ -> PautaMotion.tween(PautaMotion.Base) })
                    }
                },
                label = "book-session-hero",
            ) { a ->
                if (a != null) {
                    ActiveReadingCard(
                        bookTitle = activeBookEntity?.title ?: a.title,
                        elapsed = activeSessions.lastOrNull()?.let { now - it.startedAt } ?: 0L,
                        totalMs = blockMs(a.id),
                        targetMs = a.targetMs ?: 0L,
                        onPause = { vm.pauseActive("") },
                        onConclude = { activeBookEntity?.let { concludeFor = it } },
                    )
                } else {
                    StartReadingCard(
                        book = selectedBook,
                        canPick = reading.size > 1,
                        targetMin = targetMin,
                        // U2: a reading session leans on the simpler set unless the
                        // user actually asked for Pomodoro in Settings — reading
                        // isn't Pomodoro work. // PT: a leitura usa o conjunto
                        // simples, salvo escolha explícita de Pomodoro.
                        presets = TimerPresets.of(prefs.timerPresets, reading = true),
                        onChangeTarget = { targetMin = it },
                        onPick = { showPicker = true },
                        onStart = {
                            selectedBook?.let { b ->
                                vm.startBlock(
                                    title = b.title,
                                    linkedToId = null,
                                    project = "book:${b.id}",
                                    targetMin = targetMin.takeIf { it > 0 },
                                )
                                // P10: the same start tick as the planner's Pauta —
                                // the book face is a lens over the same gesture.
                                // // PT: o mesmo toque háptico da Pauta.
                                haptic.tick(prefs)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // ── Paused reading sessions (resume) ──
        if (pausedBlocks.isNotEmpty()) {
            item(key = "paused-header") {
                SectionEyebrow(tr("Em pausa"))
                Spacer(Modifier.height(10.dp))
            }
            items(pausedBlocks, key = { "paused-${it.id}" }) { b ->
                PausedReadingRow(
                    title = b.title,
                    totalMs = blockMs(b.id),
                    onResume = { vm.resumeBlock(b.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            item(key = "paused-footer") { Spacer(Modifier.height(14.dp)) }
        }

        // ── Session history, grouped by book ──
        item(key = "history-header") {
            SectionEyebrow(tr("Sessões de leitura"))
            Spacer(Modifier.height(14.dp))
        }
        if (grouped.isEmpty()) {
            item(key = "history-empty") {
                // P10: the one empty state. // PT: o estado vazio único.
                EmptyState(tr("Nenhuma sessão ainda"))
            }
        } else {
            grouped.forEach { (title, blocks) ->
                item(key = "grp-$title") {
                    Text(
                        text = title,
                        color = colors.ink,
                        fontFamily = SerifFamily,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    blocks.forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = DateUtils.dayKeyOf(b.createdAt),
                                color = colors.ink3,
                                style = PautaType.MetaSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = FocusMath.fmtDuration(blockMs(b.id)),
                                color = colors.ink3,
                                style = PautaType.MetaSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
    }

    if (showPicker) {
        BookPickerSheet(
            books = reading,
            selectedId = selectedBookId,
            onPick = { selectedBookId = it; showPicker = false },
            onClose = { showPicker = false },
        )
    }
    concludeFor?.let { book ->
        BookConcludeSheet(
            book = book,
            derivedPage = concludeDerived,
            onConfirm = { newPage, note ->
                vm.updateProgress(book.id, newPage)
                vm.concludeActive(note)
                concludeFor = null
                haptic.tick(prefs)
            },
            onClose = { concludeFor = null },
        )
    }
}

/**
 * R5: the way back into a book the app can open — the reader, at the page the
 * bookmark points to. The whole card is the target; TalkBack hears the action and
 * the title, since the eyebrow and the chevron say nothing on their own.
 * // PT: o cartão que volta ao livro na página onde ficou.
 */
@Composable
private fun ContinueReadingCard(book: BookEntity, onOpen: () -> Unit) {
    val colors = LocalPautaColors.current
    // Where the reader will actually land, which is the bookmark — not the
    // progress, which a hand-typed page can have moved elsewhere. // PT: a página
    // do marcador, que é onde o leitor abre.
    val page = ReaderMath.bookmarkPage(book.readPosition, book.fileKind) ?: book.currentPage
    PautaCard(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = tr("Continuar a ler") + ": " + book.title; role = Role.Button },
        padding = PaddingValues(20.dp),
        onClick = onOpen,
    ) {
        SectionEyebrow(tr("Continuar a ler"), color = colors.accent)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = colors.ink,
                    fontFamily = SerifFamily,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bookProgressLabel(book, page),
                    color = colors.ink3,
                    style = PautaType.Meta,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("›", color = colors.accent, fontSize = 20.sp)
        }
        bookProgressFraction(book, page)?.let { fraction ->
            Spacer(Modifier.height(14.dp))
            ProgressBar(fraction)
        }
    }
}

/** The dark running-session card: book title above a live mono timer, with
 *  Pausar / Concluir actions. A close equivalent of the planner's active card —
 *  P7 gave it that card's tabular timer digits, target hairline and shared pills.
 *  // PT: o cartão escuro da sessão, com os mesmos dígitos, fio de meta e pílulas. */
@Composable
private fun ActiveReadingCard(
    bookTitle: String,
    elapsed: Long,
    totalMs: Long,
    targetMs: Long,
    onPause: () -> Unit,
    onConclude: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PautaRadius.Card))
            .background(colors.surfaceDark)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
    ) {
        SectionEyebrow(tr("em curso"), color = colors.onDark2)
        Spacer(Modifier.height(10.dp))
        Text(
            text = bookTitle,
            color = colors.onDark,
            fontFamily = SerifFamily,
            fontSize = 24.sp,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = FocusMath.fmtTimer(elapsed),
                    color = colors.onDark,
                    style = PautaType.Timer,
                )
                // A reading session can carry a duration too (the start card's
                // duration picker), so it gets the planner's target hairline.
                // // PT: a sessão também tem meta — logo, o mesmo fio.
                if (targetMs > 0) {
                    TargetUnderline(
                        progress = totalMs.toFloat() / targetMs,
                        reached = totalMs >= targetMs,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkPillButton(tr("Pausar"), icon = { PauseBars(colors.onDark, 10.dp) }, onClick = onPause)
                DarkAccentPill(tr("Concluir"), onClick = onConclude)
            }
        }
    }
}

/** The "Iniciar sessão de leitura" card: book selector + optional duration +
 *  Começar. With no reading book the start is disabled and a hint points to the
 *  Estante. // PT: cartão para começar — selector de livro, duração e botão. */
@Composable
private fun StartReadingCard(
    book: BookEntity?,
    canPick: Boolean,
    targetMin: Int,
    presets: List<Int>,
    onChangeTarget: (Int) -> Unit,
    onPick: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalPautaColors.current
    PautaCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(20.dp),
    ) {
        SheetEyebrow(tr("Iniciar sessão de leitura"))
        Spacer(Modifier.height(14.dp))

        if (book == null) {
            Text(
                text = tr("Adiciona um livro na Estante primeiro"),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            PautaButton(tr("Começar"), Modifier.fillMaxWidth(), PautaButtonVariant.Primary, enabled = false) {}
            return@PautaCard
        }

        // Book row — tap to change when more than one book is being read.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PautaRadius.Field))
                .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Field))
                .then(if (canPick) Modifier.clickableNoRipple(onPick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = colors.ink,
                    fontFamily = SerifFamily,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        color = colors.ink3,
                        style = PautaType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (canPick) Text("›", color = colors.ink3, fontSize = 16.sp)
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("duração (opcional)"))
        Spacer(Modifier.height(10.dp))
        DurationPicker(minutes = targetMin, presets = presets, onChange = onChangeTarget)

        Spacer(Modifier.height(20.dp))
        PautaButton(
            tr("Começar"),
            Modifier.fillMaxWidth(),
            PautaButtonVariant.Primary,
            // U2: an out-of-range custom duration can't start a session either.
            // // PT: duração inválida também não inicia a sessão.
            enabled = targetMin != DurationInvalid,
        ) { onStart() }
    }
}

/** A paused reading session row with a Retomar pill — the book-mode echo of the
 *  planner's PausedBlockCard, trimmed to title + accumulated time + resume. */
@Composable
private fun PausedReadingRow(title: String, totalMs: Long, onResume: () -> Unit) {
    val colors = LocalPautaColors.current
    PautaCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.ink,
                    style = PautaType.Label,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = FocusMath.fmtDuration(totalMs),
                    color = colors.ink3,
                    style = PautaType.MetaSmall,
                )
            }
            ResumePill(onResume)
        }
    }
}

/** Picks which reading book the next session is for. */
@Composable
private fun BookPickerSheet(
    books: List<BookEntity>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    PautaSheet(title = tr("Iniciar sessão de leitura"), onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            books.forEach { b ->
                val sel = b.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PautaRadius.Field))
                        .background(if (sel) colors.paper2 else Color.Transparent)
                        .border(1.dp, if (sel) colors.accent else colors.rule, RoundedCornerShape(PautaRadius.Field))
                        .clickableNoRipple { onPick(b.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title, color = colors.ink, fontFamily = SerifFamily, fontSize = 16.sp, lineHeight = 20.sp)
                        if (b.author.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(b.author, color = colors.ink3, style = PautaType.Meta)
                        }
                    }
                    if (sel) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(colors.accent),
                        )
                    }
                }
            }
        }
    }
}

/** On conclude: ask the page reached (minutes for audiobooks) and an optional
 *  session note. Confirm persists progress + the note (in the block reflection).
 *
 *  R5: [derivedPage] is the page the reader's bookmark points at, non-null only for
 *  a book the reader can open. When it is there the sheet states the page instead
 *  of asking for it — the app knows, and asking anyway is the habit this whole
 *  phase is undoing. // PT: com marcador, a folha diz a página em vez de a
 *  perguntar. */
@Composable
private fun BookConcludeSheet(
    book: BookEntity,
    derivedPage: Int? = null,
    onConfirm: (newPage: Int, note: String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val isAudiobook = book.format == "audiobook"
    var page by remember { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf("") }

    fun submit() = onConfirm(derivedPage ?: page.toIntOrNull() ?: book.currentPage, note.trim())

    PautaSheet(title = tr("Concluir bloco"), onClose = onClose) {
        if (derivedPage != null) {
            // The same unlabelled progress line the detail sheet uses — a
            // statement, not a question. // PT: a linha de progresso, sem pergunta.
            Text(
                text = if (book.totalPages > 0) {
                    trf("Página {x} de {y}", "x" to derivedPage, "y" to book.totalPages)
                } else {
                    "p. $derivedPage"
                },
                color = colors.ink2,
                style = PautaType.Meta,
            )
        } else {
            // U1: inside the body, so the page field waits for the sheet to settle.
            // // PT: espera que a folha assente antes de focar.
            val pageFocus = rememberAutoFocusRequester()
            SheetEyebrow(if (isAudiobook) tr("Quantos minutos ouviste?") else tr("Até que página chegaste?"))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(120.dp)) {
                BoxedField(
                    value = page,
                    onChange = { raw -> page = raw.filter { it.isDigit() }.take(6) },
                    placeholder = book.currentPage.toString(),
                    modifier = Modifier.focusRequester(pageFocus),
                    singleLine = true,
                    fontFamily = MonoFamily,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("Nota da sessão"))
        Spacer(Modifier.height(8.dp))
        BoxedField(
            value = note,
            onChange = { note = it },
            placeholder = "",
            minHeight = 84.dp,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Concluir"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}
