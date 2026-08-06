package com.pauta.app.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetActionGap
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.SheetFieldGap
import com.pauta.app.ui.SheetLabelGap
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * native-only (K9): quick quote/annotation capture — the highest-frequency book
 * action: jot a citação, anotação or pensamento mid-read without leaving the
 * current tab. Opens from the capture chip in the shell (book mode only) and
 * saves via addNote to the picked reading book; the page field hides for
 * audiobooks. // PT: captura rápida de citações e notas — tipo, texto, página
 * (excepto audiolivros) e livro; guarda sem interromper a leitura.
 *
 * **L6 · a targeted mode.** Capturing a quote is the highest-value thing a
 * reading app does, and this sheet opened from exactly one place — the shelf
 * header — offered only books with `status == "reading"`, and asked you to type
 * a page the reader already knew.
 *
 * [bookId] fixes the target: no picker, and **no shelf filter**, because the
 * caller has already chosen and a note on a book you just finished has to have
 * somewhere to go. [atPage] pre-fills the position and stays editable — the
 * reader's position is right far more often than not, and a quote spanning a page
 * break is real. Both null is the shelf header's original behaviour, unchanged.
 * // PT: com [bookId] não há selector nem filtro de prateleira (uma nota num livro
 * acabado tem de caber), e [atPage] pré-preenche sem trancar.
 */
@Composable
fun QuoteCaptureSheet(
    onClose: () -> Unit,
    bookId: String? = null,
    atPage: Int? = null,
) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    // L6: with a target, every shelf is in scope — the point of the parameter is
    // that the caller decided. // PT: com alvo, todas as prateleiras contam.
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val paused by vm.booksPaused.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    val targeted = remember(bookId, reading, tbr, paused, done) {
        bookId?.let { id -> (reading + tbr + paused + done).firstOrNull { it.id == id } }
    }

    var kind by remember { mutableStateOf("annotation") }
    var text by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(atPage?.takeIf { it > 0 }?.toString() ?: "") }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var triedSubmit by remember { mutableStateOf(false) }

    // Keep the pick pinned to the reading shelf: default to the first (often
    // only) book, re-pick if the chosen one leaves. // PT: escolhe o livro em curso.
    LaunchedEffect(reading) {
        if (pickedId == null || reading.none { it.id == pickedId }) {
            pickedId = reading.firstOrNull()?.id
        }
    }
    val book = targeted ?: reading.firstOrNull { it.id == pickedId }
    val isAudiobook = book?.format == "audiobook"

    fun submit() {
        val b = book ?: return
        if (text.isBlank()) { triedSubmit = true; return }
        // F1: a note's position is clamped like any other progress value — a
        // percentage cannot be 340. // PT: a posição da nota também se limita.
        val at = page.toIntOrNull()?.let { clampBookProgress(b, it) }
        vm.addNote(b.id, kind, text.trim(), at.takeIf { !isAudiobook })
        onClose()
    }

    PautaSheet(title = tr("Nova nota"), onClose = onClose) {
        // U1: inside the body, so the capture field waits for the sheet to settle
        // before raising the keyboard. // PT: espera que a folha assente.
        val textFocus = rememberAutoFocusRequester()
        // L6: with a target there is always somewhere for the note to land, so
        // the "no books being read" branch is the untargeted case only.
        // // PT: com alvo, a nota tem sempre onde cair.
        if (targeted == null && reading.isEmpty()) {
            // No book being read: the capture has nowhere to land, so say so.
            Text(
                text = tr("Sem livros em curso — adiciona um na Estante"),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(SheetActionGap))
            PautaButton(tr("Fechar"), Modifier.fillMaxWidth(), PautaButtonVariant.Ghost) { onClose() }
            return@PautaSheet
        }

        // ── Tipo ──
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectPill(tr("Citação"), kind == "quote", colors.accent, large = true) { kind = "quote" }
            SelectPill(tr("Anotação"), kind == "annotation", colors.accent, large = true) { kind = "annotation" }
            SelectPill(tr("Pensamento"), kind == "thought", colors.accent, large = true) { kind = "thought" }
        }

        // ── Texto ──
        Spacer(Modifier.height(SheetFieldGap))
        BoxedField(
            value = text,
            onChange = { text = it },
            placeholder = "",
            modifier = Modifier.focusRequester(textFocus),
            minHeight = 108.dp,
            isError = triedSubmit && text.isBlank(),
        )

        // ── Página (hidden for audiobooks) ──
        if (!isAudiobook) {
            Spacer(Modifier.height(SheetFieldGap))
            // F1: the note's position is stored in the same unit `currentPage`
            // is — a percentage point for an attached EPUB — so the label and the
            // mark have to say which, or the number means something the reader
            // didn't. // PT: a posição da nota usa a unidade do livro.
            // `reading` is non-empty here (the branch above returned), so the
            // fallback only covers the frame before the pick settles.
            // // PT: a lista não está vazia aqui; o recurso cobre só o 1.º frame.
            val unitBook = book ?: reading.first()
            SheetEyebrow(bookProgressUnit(unitBook) + " · " + tr("opcional"))
            Spacer(Modifier.height(SheetLabelGap))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bookProgressMark(unitBook), color = colors.ink3, style = PautaType.Meta)
                Box(Modifier.width(96.dp)) {
                    BoxedField(
                        value = page,
                        onChange = { raw -> page = raw.filter { it.isDigit() }.take(5) },
                        placeholder = "",
                        singleLine = true,
                        fontFamily = MonoFamily,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                }
            }
        }

        // ── Livro: a fixed label with one reading book, chips when several ──
        // L6: with a target there is nothing to pick — the caller chose, and a
        // picker offering one option is a question with one answer.
        // // PT: com alvo não há nada para escolher.
        Spacer(Modifier.height(SheetFieldGap))
        SheetEyebrow(tr("Livro"))
        Spacer(Modifier.height(SheetLabelGap))
        if (targeted != null) {
            Text(
                text = targeted.title,
                color = colors.ink2,
                style = PautaType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (reading.size == 1) {
            Text(
                text = reading.first().title,
                color = colors.ink2,
                style = PautaType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reading.forEach { b ->
                    val sel = b.id == pickedId
                    Text(
                        text = b.title,
                        color = if (sel) colors.ink else colors.ink3,
                        style = PautaType.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PautaRadius.Field))
                            .background(if (sel) colors.paper2 else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (sel) colors.accent else colors.rule, RoundedCornerShape(PautaRadius.Field))
                            .clickableNoRipple { pickedId = b.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(SheetActionGap))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Guardar"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}
