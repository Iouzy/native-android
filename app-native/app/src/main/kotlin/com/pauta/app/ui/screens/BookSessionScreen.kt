package com.pauta.app.ui.screens

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
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
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
 */
@Composable
fun BookSessionScreen() {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val active by vm.activeBlock.collectAsStateWithLifecycle()
    val activeSessions by vm.activeSessions.collectAsStateWithLifecycle()
    val sessionBlocks by vm.bookSessionBlocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(22.dp))
            Text(
                text = tr("Sessão"),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 34.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(20.dp))
        }

        // ── Active session OR the start card ──
        item(key = "active-or-start") {
            if (activeBook != null) {
                ActiveReadingCard(
                    block = activeBook,
                    bookTitle = activeBookEntity?.title ?: activeBook.title,
                    elapsed = activeSessions.lastOrNull()?.let { now - it.startedAt } ?: 0L,
                    onPause = { vm.pauseActive("") },
                    onConclude = { activeBookEntity?.let { concludeFor = it } },
                )
            } else {
                StartReadingCard(
                    book = selectedBook,
                    canPick = reading.size > 1,
                    targetMin = targetMin,
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
                        }
                    },
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        // ── Paused reading sessions (resume) ──
        if (pausedBlocks.isNotEmpty()) {
            item(key = "paused-header") {
                MonoSectionLabel(tr("Em pausa"))
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
            MonoSectionLabel(tr("Sessões de leitura"))
            Spacer(Modifier.height(14.dp))
        }
        if (grouped.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = tr("Nenhuma sessão ainda"),
                    color = colors.ink4,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                )
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
                                fontFamily = MonoFamily,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = FocusMath.fmtDuration(blockMs(b.id)),
                                color = colors.ink3,
                                fontFamily = MonoFamily,
                                fontSize = 10.sp,
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
            onConfirm = { newPage, note ->
                vm.updateProgress(book.id, newPage)
                vm.concludeActive(note)
                concludeFor = null
            },
            onClose = { concludeFor = null },
        )
    }
}

/** The dark running-session card: book title above a live mono timer, with
 *  Pausar / Concluir actions. A close equivalent of the planner's active card. */
@Composable
private fun ActiveReadingCard(
    block: FocusBlockEntity,
    bookTitle: String,
    elapsed: Long,
    onPause: () -> Unit,
    onConclude: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceDark)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
    ) {
        Text(
            text = tr("em curso").uppercase(),
            color = colors.onDark2,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 2.sp, // 0.2em of 10sp
        )
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
            Text(
                text = FocusMath.fmtTimer(elapsed),
                color = colors.onDark,
                fontFamily = MonoFamily,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.84).sp, // -0.02em of 42sp
                lineHeight = 40.sp,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkOutlinePill(tr("Pausar"), icon = { PauseBars(colors.onDark, 10.dp) }, onClick = onPause)
                Text(
                    text = tr("Concluir").uppercase(),
                    color = colors.onDark,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp, // 0.1em of 11sp
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent)
                        .clickableNoRipple(onConclude)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** A translucent outlined mono pill on the dark card (mirrors Pausar/Trocar). */
@Composable
private fun DarkOutlinePill(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x38F5F1EA), RoundedCornerShape(999.dp)) // rgba(245,241,234,0.22)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(
            text = label.uppercase(),
            color = colors.onDark,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp, // 0.08em of 10sp
        )
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
    onChangeTarget: (Int) -> Unit,
    onPick: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.rule, RoundedCornerShape(16.dp))
            .background(colors.paper2)
            .padding(20.dp),
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
            return@Column
        }

        // Book row — tap to change when more than one book is being read.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
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
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
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
        ChipFlow {
            listOf(0 to tr("Sem limite"), 25 to "25 min", 50 to "50 min", 90 to "90 min").forEach { (m, label) ->
                SelectPill(label = label, selected = targetMin == m, accent = colors.accent, large = true) { onChangeTarget(m) }
            }
        }

        Spacer(Modifier.height(20.dp))
        PautaButton(tr("Começar"), Modifier.fillMaxWidth(), PautaButtonVariant.Primary) { onStart() }
    }
}

/** A paused reading session row with a Retomar pill — the book-mode echo of the
 *  planner's PausedBlockCard, trimmed to title + accumulated time + resume. */
@Composable
private fun PausedReadingRow(title: String, totalMs: Long, onResume: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.paper2)
            .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = FocusMath.fmtDuration(totalMs),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent)
                .clickableNoRipple(onResume)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PlayTriangle(colors.onDark, 10.dp)
            Text(
                text = tr("Retomar").uppercase(),
                color = colors.onDark,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) colors.paper2 else Color.Transparent)
                        .border(1.dp, if (sel) colors.accent else colors.rule, RoundedCornerShape(10.dp))
                        .clickableNoRipple { onPick(b.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title, color = colors.ink, fontFamily = SerifFamily, fontSize = 16.sp, lineHeight = 20.sp)
                        if (b.author.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(b.author, color = colors.ink3, fontFamily = MonoFamily, fontSize = 11.sp)
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
 *  session note. Confirm persists progress + the note (in the block reflection). */
@Composable
private fun BookConcludeSheet(
    book: BookEntity,
    onConfirm: (newPage: Int, note: String) -> Unit,
    onClose: () -> Unit,
) {
    val isAudiobook = book.format == "audiobook"
    var page by remember { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf("") }
    val pageFocus = rememberAutoFocusRequester()

    fun submit() = onConfirm(page.toIntOrNull() ?: book.currentPage, note.trim())

    PautaSheet(title = tr("Concluir bloco"), onClose = onClose) {
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
