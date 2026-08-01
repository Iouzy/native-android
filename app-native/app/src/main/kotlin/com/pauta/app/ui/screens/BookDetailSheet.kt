package com.pauta.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.domain.BookMath
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.SheetActionGap
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.SheetFieldGap
import com.pauta.app.ui.SheetLabelGap
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.tick
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlin.math.roundToInt

/**
 * native-only (K8): the book detail sheet the shelf cards open — all book-level
 * state in one place: inline progress editing, the 1–5 star rating, status
 * moves (começar a ler / marcar como lido), edit + 2-step delete, the notes &
 * quotes list (long-press to delete) and this book's reading-session history.
 * Lives on the live flows, so every change lands immediately on the shelf
 * behind it. // PT: a folha de detalhe do livro — progresso, classificação,
 * estado, notas e sessões, tudo em cima dos flows vivos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookDetailSheet(bookId: String, onDismiss: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    // P10 · the haptic map's last entry: arming a two-step delete ticks, so the
    // "tap again" state announces itself without a glance. // PT: armar o
    // eliminar em dois passos dá um toque háptico.
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    val notes by remember(bookId) { vm.notesForBook(bookId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sessionBlocks by vm.bookSessionBlocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()

    // The book comes straight from the shelf flows; when it vanishes (deleted
    // here or elsewhere) the sheet closes itself. // PT: fecha se o livro sumir.
    val book = remember(reading, tbr, done, bookId) {
        (reading + tbr + done).firstOrNull { it.id == bookId }
    }
    LaunchedEffect(book == null) { if (book == null) onDismiss() }
    if (book == null) return

    val isAudiobook = book.format == "audiobook"
    var editingProgress by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var armedNoteId by remember { mutableStateOf<String?>(null) }

    // This book's concluded sessions, newest first, with per-block durations
    // summed from the session spans. // PT: sessões deste livro, mais recentes primeiro.
    val bookBlocks = remember(sessionBlocks, bookId) {
        sessionBlocks.filter { it.project == "book:$bookId" }.sortedByDescending { it.createdAt }
    }
    val segsByBlock = remember(allSessions) { allSessions.groupBy { it.blockId } }
    fun blockMs(id: String): Long = FocusMath.blockElapsedMs(
        segsByBlock[id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
        System.currentTimeMillis(),
    )

    PautaSheet(title = tr("Estante"), onClose = onDismiss) {
        // ── Header: title · author · series · format ──
        Text(
            text = book.title,
            color = colors.ink,
            style = PautaType.CardTitle,
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(book.author, color = colors.ink3, style = PautaType.Meta)
        }
        if (book.series.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = book.series + (book.seriesNumber?.let { " · " + trf("Nº {n}", "n" to it) } ?: ""),
                color = colors.ink4,
                style = PautaType.MetaSmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when (book.format) {
                "ebook" -> tr("Ebook")
                "audiobook" -> tr("Audiolivro")
                else -> tr("Físico")
            },
            color = colors.ink3,
            style = PautaType.MetaSmall,
            letterSpacing = 0.54.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        // ── R2 · the attached file ──
        // Just the name for now: reading it is R3/R4's job. // PT: por agora só o
        // nome — abrir o ficheiro é tarefa do leitor.
        if (book.filePath != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "📄 " + book.fileName,
                color = colors.ink3,
                style = PautaType.MetaSmall,
                fontFamily = MonoFamily,
            )
        }

        // ── Progress: tap the line to edit inline ──
        Spacer(Modifier.height(SheetFieldGap))
        if (editingProgress) {
            ProgressEditor(
                book = book,
                onConfirm = { n -> vm.updateProgress(book.id, n); editingProgress = false },
                onCancel = { editingProgress = false },
            )
        } else {
            Text(
                text = when {
                    book.totalPages > 0 && isAudiobook ->
                        trf("Min {x} de {y}", "x" to book.currentPage, "y" to book.totalPages)
                    book.totalPages > 0 ->
                        trf("Página {x} de {y}", "x" to book.currentPage, "y" to book.totalPages)
                    isAudiobook -> "min. ${book.currentPage}"
                    else -> "p. ${book.currentPage}"
                } + " ✎",
                color = colors.ink2,
                style = PautaType.Meta,
                modifier = Modifier
                    .clickableNoRipple { editingProgress = true }
                    .padding(vertical = 4.dp),
            )
        }
        if (book.totalPages > 0) {
            Spacer(Modifier.height(6.dp))
            ProgressBar(book.currentPage.toFloat() / book.totalPages.coerceAtLeast(1))
        }

        // ── K-extra: pace + ETA ──
        // Per-session page history isn't stored, so the spans take the last 5
        // concluded sessions' durations with the book's total progress
        // apportioned by duration — the overall rate, needing ≥ 2 sessions.
        // // PT: ritmo global das últimas sessões; estimativa só com 2+ sessões.
        val pace = remember(bookBlocks, segsByBlock, book.currentPage) {
            val durs = bookBlocks.take(5).map { blockMs(it.id) }.filter { it > 0 }
            val total = durs.sum()
            if (total <= 0) null else BookMath.pagesPerHour(
                durs.map { d -> BookMath.SessionSpan(((book.currentPage.toLong() * d) / total).toInt(), d) },
            )
        }
        if (pace != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = trf(
                    if (isAudiobook) "Ritmo: ~{n} min/hora" else "Ritmo: ~{n} págs/hora",
                    "n" to pace.roundToInt(),
                ),
                color = colors.ink3,
                style = PautaType.MetaSmall,
            )
            val eta = if (book.totalPages > 0) {
                BookMath.etaDays(book.totalPages - book.currentPage, pace)
            } else null
            if (eta != null && eta > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = trf("Conclusão estimada: em ~{n} dias", "n" to eta),
                    color = colors.ink3,
                    style = PautaType.MetaSmall,
                )
            }
        }

        // ── Rating: tap to set 1–5; tap the current star to clear ──
        Spacer(Modifier.height(SheetFieldGap))
        StarRow(
            rating = book.rating,
            onRate = { n -> vm.updateBook(book.copy(rating = n.takeIf { it != book.rating })) },
        )

        // ── Status actions ──
        Spacer(Modifier.height(SheetFieldGap))
        if (book.status == "reading") {
            PautaButton(tr("Marcar como lido"), Modifier.fillMaxWidth(), PautaButtonVariant.Primary) {
                showFinish = true
            }
            Spacer(Modifier.height(10.dp))
        } else if (book.status == "tbr") {
            PautaButton(tr("Começar a ler"), Modifier.fillMaxWidth(), PautaButtonVariant.Primary) {
                vm.updateBook(book.copy(status = "reading", startedAt = System.currentTimeMillis()))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Editar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { showEdit = true }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(999.dp))
                    .clickableNoRipple {
                        if (confirmDelete) {
                            vm.deleteBook(book.id)
                        } else {
                            confirmDelete = true
                            haptic.tick(prefs)
                        }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (confirmDelete) tr("Tocar de novo para eliminar") else tr("Eliminar livro"),
                    color = DangerRed,
                    fontSize = 13.sp,
                )
            }
        }

        // ── Notas & Citações ──
        Spacer(Modifier.height(SheetFieldGap))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Spacer(Modifier.height(SheetFieldGap))
        SheetEyebrow(tr("Notas & Citações"))
        Spacer(Modifier.height(SheetLabelGap))
        if (notes.isEmpty()) {
            // P10: the one empty state. // PT: o estado vazio único.
            EmptyState(tr("Sem notas ainda"))
        } else {
            notes.forEach { note ->
                val armed = armedNoteId == note.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        // Long-press arms the 2-step delete; a plain tap disarms it.
                        // // PT: pressão longa arma o eliminar; toque simples desarma.
                        .combinedClickable(
                            onClick = { if (armed) armedNoteId = null },
                            onLongClick = { armedNoteId = note.id; haptic.tick(prefs) },
                        )
                        .padding(vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionEyebrow(
                            label = when (note.kind) {
                                "quote" -> tr("CITAÇÃO")
                                "thought" -> tr("PENSAMENTO")
                                else -> tr("ANOTAÇÃO")
                            },
                            color = colors.accent,
                        )
                        // Audiobooks have no pages, so the tag is hidden for them.
                        if (!isAudiobook && note.page != null) {
                            Spacer(Modifier.width(8.dp))
                            Text("p. ${note.page}", color = colors.ink4, style = PautaType.MetaSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        if (armed) {
                            Text(
                                text = tr("Tocar de novo para eliminar"),
                                color = DangerRed,
                                style = PautaType.MetaSmall,
                                modifier = Modifier
                                    .clickableNoRipple { vm.deleteNote(note.id); armedNoteId = null }
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.text,
                        color = colors.ink,
                        style = PautaType.Body,
                    )
                }
            }
        }

        // ── Sessões ──
        Spacer(Modifier.height(SheetFieldGap))
        SheetEyebrow(tr("Sessões"))
        Spacer(Modifier.height(SheetLabelGap))
        if (bookBlocks.isEmpty()) {
            EmptyState(tr("Nenhuma sessão ainda"))
        } else {
            bookBlocks.forEach { b ->
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
                if (b.reflection.isNotBlank()) {
                    Text(
                        text = b.reflection,
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    if (showEdit) {
        BookFormSheet(book = book, onClose = { showEdit = false })
    }
    if (showFinish) {
        FinishBookSheet(
            book = book,
            onConfirm = { rating -> vm.finishBook(book.id, rating); showFinish = false },
            onClose = { showFinish = false },
        )
    }
}

/** Inline number input for the progress line: pages, or minutes for audiobooks. */
@Composable
private fun ProgressEditor(book: BookEntity, onConfirm: (Int) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString() ?: "") }
    val focus = rememberAutoFocusRequester()
    fun submit() = onConfirm(value.toIntOrNull() ?: book.currentPage)

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(110.dp)) {
            BoxedField(
                value = value,
                onChange = { raw -> value = raw.filter { it.isDigit() }.take(6) },
                placeholder = book.currentPage.toString(),
                modifier = Modifier.focusRequester(focus),
                singleLine = true,
                fontFamily = MonoFamily,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        PautaButton(tr("Guardar"), variant = PautaButtonVariant.Primary) { submit() }
        PautaButton(tr("Cancelar"), variant = PautaButtonVariant.Ghost) { onCancel() }
    }
}

/** The 1–5 star strip. Tapping star n rates n; tapping the current one clears. */
@Composable
private fun StarRow(rating: Int?, onRate: (Int) -> Unit) {
    val colors = LocalPautaColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { n ->
            Text(
                text = if (rating != null && n <= rating) "★" else "☆",
                color = if (rating != null && n <= rating) colors.accent else colors.ink4,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickableNoRipple { onRate(n) }
                    .padding(horizontal = 3.dp, vertical = 2.dp),
            )
        }
    }
}

/** Confirm sheet for "Marcar como lido": pick an (optional) rating, then save. */
@Composable
private fun FinishBookSheet(book: BookEntity, onConfirm: (Int?) -> Unit, onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    var rating by remember { mutableStateOf(book.rating) }
    PautaSheet(title = tr("Marcar como lido"), onClose = onClose) {
        Text(
            text = book.title,
            color = colors.ink,
            style = PautaType.CardTitle,
        )
        Spacer(Modifier.height(SheetFieldGap))
        StarRow(rating = rating, onRate = { n -> rating = n.takeIf { it != rating } })
        Spacer(Modifier.height(SheetActionGap))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Marcar como lido"), Modifier.weight(2f), PautaButtonVariant.Primary) { onConfirm(rating) }
        }
    }
}
