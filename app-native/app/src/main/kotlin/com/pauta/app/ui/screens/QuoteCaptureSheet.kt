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
 */
@Composable
fun QuoteCaptureSheet(onClose: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()

    var kind by remember { mutableStateOf("annotation") }
    var text by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var bookId by remember { mutableStateOf<String?>(null) }
    var triedSubmit by remember { mutableStateOf(false) }
    val textFocus = rememberAutoFocusRequester()

    // Keep the pick pinned to the reading shelf: default to the first (often
    // only) book, re-pick if the chosen one leaves. // PT: escolhe o livro em curso.
    LaunchedEffect(reading) {
        if (bookId == null || reading.none { it.id == bookId }) {
            bookId = reading.firstOrNull()?.id
        }
    }
    val book = reading.firstOrNull { it.id == bookId }
    val isAudiobook = book?.format == "audiobook"

    fun submit() {
        val b = book ?: return
        if (text.isBlank()) { triedSubmit = true; return }
        vm.addNote(b.id, kind, text.trim(), page.toIntOrNull().takeIf { !isAudiobook })
        onClose()
    }

    PautaSheet(title = tr("Nova nota"), onClose = onClose) {
        if (reading.isEmpty()) {
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
            SheetEyebrow(tr("Página (opcional)"))
            Spacer(Modifier.height(SheetLabelGap))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("p.", color = colors.ink3, style = PautaType.Meta)
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
        Spacer(Modifier.height(SheetFieldGap))
        SheetEyebrow(tr("Livro"))
        Spacer(Modifier.height(SheetLabelGap))
        if (reading.size == 1) {
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
                    val sel = b.id == bookId
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
                            .clickableNoRipple { bookId = b.id }
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
