package com.pauta.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.pauta.app.data.entity.FocusBlockEntity
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
fun BookDetailSheet(
    bookId: String,
    onDismiss: () -> Unit,
    // R3: how this sheet opens the reader — null when the caller has nowhere to
    // send it (the reader's own ⋯ opens this sheet, and offering "Ler" there
    // would be offering what you are already doing). // PT: null quando não há
    // para onde abrir o leitor.
    onOpenReader: (() -> Unit)? = null,
) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    // P10 · the haptic map's last entry: arming a two-step delete ticks, so the
    // "tap again" state announces itself without a glance. // PT: armar o
    // eliminar em dois passos dá um toque háptico.
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val paused by vm.booksPaused.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    val notes by remember(bookId) { vm.notesForBook(bookId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sessionBlocks by vm.bookSessionBlocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()

    // The book comes straight from the shelf flows; when it vanishes (deleted
    // here or elsewhere) the sheet closes itself. // PT: fecha se o livro sumir.
    // L3: four shelves now, and all four are searched — a paused book that no
    // flow carried would close this sheet the moment it opened. // PT: as quatro
    // prateleiras, para o livro em pausa não desaparecer daqui.
    val book = remember(reading, tbr, paused, done, bookId) {
        (reading + tbr + paused + done).firstOrNull { it.id == bookId }
    }
    LaunchedEffect(book == null) { if (book == null) onDismiss() }
    if (book == null) return

    val isAudiobook = book.format == "audiobook"
    val canRead = rememberCanRead(book)
    var editingProgress by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    // F2: the reading session opened from this sheet's Sessões list.
    var editBlock by remember { mutableStateOf<FocusBlockEntity?>(null) }
    // L6: capture against this book, whatever shelf it is on.
    var showCapture by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmAbandon by remember { mutableStateOf(false) }
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
        // The name; R3's "Ler" is further down, with the status actions. // PT: o
        // nome do ficheiro; o botão "Ler" está com as acções de estado.
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
                // R4: an attached EPUB says 43%, because it has no pages to count.
                // // PT: um EPUB anexado diz percentagem — não tem páginas.
                text = bookProgressLabel(book) + " ✎",
                color = colors.ink2,
                style = PautaType.Meta,
                modifier = Modifier
                    .clickableNoRipple { editingProgress = true }
                    .padding(vertical = 4.dp),
            )
        }
        bookProgressFraction(book)?.let { fraction ->
            Spacer(Modifier.height(6.dp))
            ProgressBar(fraction)
        }

        // ── K-extra: pace + ETA · R6: reading speed ──
        // A session the reader concluded knows exactly how many pages it turned
        // (R5's `pagesDelta`); one concluded by hand doesn't. So take the measured
        // spans when there are any and use *only* those — mixing them with the
        // book's total progress apportioned by duration would count the same pages
        // twice. With nothing measured, K-extra's apportioning still stands: the
        // last 5 sessions' durations carrying the total between them.
        // // PT: preferir os deltas medidos pelo leitor; sem eles, repartir o total
        // pela duração. Precisa sempre de 2+ sessões.
        val spans = remember(bookBlocks, segsByBlock, book.currentPage) {
            val measured = bookBlocks.mapNotNull { b ->
                val d = blockMs(b.id)
                if (b.pagesDelta != null && d > 0) BookMath.SessionSpan(b.pagesDelta, d) else null
            }
            if (measured.isNotEmpty()) return@remember measured.take(5)
            val durs = bookBlocks.take(5).map { blockMs(it.id) }.filter { it > 0 }
            val total = durs.sum()
            if (total <= 0) emptyList() else durs.map { d ->
                BookMath.SessionSpan(((book.currentPage.toLong() * d) / total).toInt(), d)
            }
        }
        // F1: the ceiling. A span implying more than MAX_HUMAN_WPM was navigating,
        // not reading — in an EPUB one tap moves several percentage points — and
        // averaging it in is what produced "Ritmo: 9191 palavras/min" from
        // arithmetic that was entirely correct. // PT: descarta os intervalos que
        // implicam uma velocidade impossível.
        val perUnit = remember(book) { BookMath.wordsPerUnit(book) }
        val pace = remember(spans, perUnit) { BookMath.pagesPerHour(spans, perUnit) }
        // R6: anything with words says its pace in words per minute. An audiobook
        // keeps min/hora — its progress is already time, and there is no honest
        // word figure to fudge out of it. // PT: WPM para tudo o que tem palavras;
        // o audiolivro fica-se pelos min/hora.
        val wpm = remember(spans, perUnit) {
            perUnit?.let { BookMath.wordsPerMinute(spans, it) }
        }
        if (pace != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    wpm == null -> trf(
                        if (isAudiobook) "Ritmo: ~{n} min/hora" else "Ritmo: ~{n} págs/hora",
                        "n" to pace.roundToInt(),
                    )
                    // Only an EPUB the reader counted gets to drop the "≈".
                    BookMath.hasCountedWords(book) ->
                        trf("Ritmo: {n} palavras/min", "n" to wpm.roundToInt())
                    else -> trf("Ritmo: ≈ {n} palavras/min", "n" to wpm.roundToInt())
                },
                color = colors.ink3,
                style = PautaType.MetaSmall,
            )
            // R4: an EPUB has no total pages, but it does have a total — a hundred
            // per cent of itself — and the pace is already measured in the same
            // unit. // PT: um EPUB não tem páginas, mas tem 100%, e o ritmo já vem
            // nessa unidade.
            val remaining = when {
                countsPercent(book) -> 100 - book.currentPage.coerceIn(0, 100)
                book.totalPages > 0 -> book.totalPages - book.currentPage
                else -> null
            }
            val eta = remaining?.let { BookMath.etaDays(it, pace) }
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
        // R3: with a readable file attached, reading it is the thing to do here —
        // above marking it read. The file has to still be there: a restored backup
        // brings back the book and not the document, and then this reverts to the
        // manual progress line above. // PT: com ficheiro (ainda) presente, "Ler" é
        // a acção principal; sem ele, fica o progresso manual.
        if (canRead && onOpenReader != null) {
            PautaButton(tr("Ler"), Modifier.fillMaxWidth(), PautaButtonVariant.Primary) { onOpenReader() }
            Spacer(Modifier.height(10.dp))
        }
        // L3: all five states, reachable and reversible. The primary is the move
        // you'd expect from where the book is; the quiet row under it holds the
        // rest, including the ways *back* — a book finished by a mis-tap used to
        // be finished for good. Every move goes through the repository, which
        // owns startedAt/finishedAt and the shelf position. // PT: o botão
        // principal é o movimento esperado; a linha discreta tem os restantes,
        // incluindo os caminhos de volta.
        val move: (String) -> Unit = { s -> confirmAbandon = false; vm.setBookStatus(book.id, s) }
        val primary: Pair<String, () -> Unit>? = when (book.status) {
            "reading" -> Pair(tr("Marcar como lido"), { showFinish = true })
            "tbr" -> Pair(tr("Começar a ler"), { move("reading") })
            "paused" -> Pair(tr("Retomar"), { move("reading") })
            "dnf" -> Pair(tr("Recomeçar"), { move("reading") })
            else -> null // "done" has no forward move left
        }
        primary?.let { (label, action) ->
            PautaButton(label, Modifier.fillMaxWidth(), PautaButtonVariant.Primary) { action() }
            Spacer(Modifier.height(10.dp))
        }
        // "Abandonar" is the one move that is a judgement rather than a
        // correction, so it arms in two steps like the delete below — it loses
        // nothing (the notes, the sessions and the progress all stay), but it is
        // not something to do by mis-tap. // PT: abandonar arma em dois passos.
        val abandonLabel = if (confirmAbandon) tr("Tocar de novo para abandonar") else tr("Abandonar")
        val onAbandon: () -> Unit = {
            if (confirmAbandon) move("dnf") else { confirmAbandon = true; haptic.tick(prefs) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (book.status) {
                "tbr" -> QuietAction(abandonLabel, onAbandon)
                "reading" -> {
                    QuietAction(tr("Pausar leitura")) { move("paused") }
                    QuietAction(abandonLabel, onAbandon)
                }
                "paused" -> {
                    QuietAction(tr("Marcar como lido")) { showFinish = true }
                    QuietAction(abandonLabel, onAbandon)
                }
                // Two names for one transition, deliberately: coming back to a
                // book you finished and undoing a mis-tap are the same move, and
                // neither touches the rating or the notes — a re-read is the same
                // book. // PT: dois nomes para o mesmo movimento; a intenção é que
                // difere, não o efeito.
                "done" -> {
                    QuietAction(tr("Voltar a ler")) { move("reading") }
                    QuietAction(tr("Marcar como não lido")) { move("reading") }
                }
                "dnf" -> QuietAction(tr("Marcar como lido")) { showFinish = true }
            }
        }
        Spacer(Modifier.height(10.dp))
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
        // L6: an add action on the eyebrow row. This is what makes a note on a
        // *finished* book possible at all — the shelf-header capture only ever
        // offered books being read. // PT: é isto que permite anotar um livro já
        // terminado.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SheetEyebrow(tr("Notas & Citações"), modifier = Modifier.weight(1f))
            Text(
                text = tr("+ Nota"),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier.clickableNoRipple { showCapture = true },
            )
        }
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
                        // L6: and the position reads in the book's own unit — an
                        // EPUB note is "43%", not "p. 43". Calling a percentage a
                        // page would be the second time the app had to learn this
                        // lesson (R4 was the first). // PT: a posição na unidade do
                        // livro; num EPUB é percentagem, não página.
                        if (!isAudiobook && note.page != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (countsPercent(book)) {
                                    "${note.page.coerceIn(0, 100)}%"
                                } else {
                                    "${bookProgressMark(book)} ${note.page}"
                                },
                                color = colors.ink4,
                                style = PautaType.MetaSmall,
                            )
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
                // F2: the entry point. A reading session was text here and text in
                // the Sessão tab, filtered out of the planner on purpose, and
                // therefore reachable from nowhere — twelve junk sessions from one
                // evening's testing were permanent. Tapping opens the same sheet a
                // planner block opens. // PT: a linha passa a abrir a folha de
                // edição — sem isto, nada do resto desta tarefa é alcançável.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { editBlock = b }
                        .padding(vertical = 3.dp),
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

    editBlock?.let { b ->
        EditBlockSheet(
            block = sessionBlocks.firstOrNull { it.id == b.id } ?: b,
            sessions = segsByBlock[b.id].orEmpty(),
            now = System.currentTimeMillis(),
            book = book,
            onSave = { edit ->
                vm.updateBlock(b.id, edit.title, edit.project, edit.targetMs)
                vm.setBlockReflection(b.id, edit.reflection)
                edit.notes.forEach { (rowId, text) -> vm.setSessionNote(rowId, text) }
                edit.times.forEach { vm.setSessionTimes(it.rowId, it.startedAt, it.endedAt) }
                if (edit.pagesDeltaChanged) vm.setBlockPagesDelta(b.id, edit.pagesDelta)
                editBlock = null
            },
            onDeleteSession = { rowId -> vm.deleteSession(rowId) },
            onDelete = { vm.deleteBlock(b.id); editBlock = null },
            onClose = { editBlock = null },
        )
    }
    if (showCapture) {
        QuoteCaptureSheet(onClose = { showCapture = false }, bookId = book.id)
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

/** L3: one of the quiet status moves — a ghost pill sharing the row evenly with
 *  its siblings, so the secondary line never reads as a row of primaries.
 *  // PT: uma acção discreta de estado, a dividir a linha em partes iguais. */
@Composable
private fun RowScope.QuietAction(label: String, onClick: () -> Unit) {
    PautaButton(label, Modifier.weight(1f), PautaButtonVariant.Ghost, onClick = onClick)
}

/** Inline number input for the progress line: pages, minutes for audiobooks, or
 *  (R4) a percentage for an attached EPUB. // PT: páginas, minutos ou percentagem. */
@Composable
private fun ProgressEditor(book: BookEntity, onConfirm: (Int) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString() ?: "") }
    val focus = rememberAutoFocusRequester()
    val colors = LocalPautaColors.current
    fun submit() = onConfirm(clampBookProgress(book, value.toIntOrNull() ?: book.currentPage))

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
        // F1: this editor always clamped correctly and never said what it was
        // clamping to. The mark is the difference between "100 pages" and
        // "finished". // PT: a marca diz a unidade; sem ela, 100 é ambíguo.
        Text(bookProgressMark(book), color = colors.ink3, style = PautaType.Meta)
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
