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
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * native-only (K5): the add/edit book form, shared by the Estante's "Adicionar
 * livro" action and (later, K8) the detail sheet's "Editar". Pass a [book] to
 * edit it; null = a fresh add. Format drives whether the total field counts
 * pages or minutes (audiobooks), per the book-mode guardrails. // PT:
 * formulário de adicionar/editar livro; o formato decide páginas vs. minutos.
 */
@Composable
fun BookFormSheet(book: BookEntity? = null, onClose: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val editing = book != null

    var title by remember { mutableStateOf(book?.title ?: "") }
    var author by remember { mutableStateOf(book?.author ?: "") }
    var series by remember { mutableStateOf(book?.series ?: "") }
    var seriesNo by remember { mutableStateOf(book?.seriesNumber?.toString() ?: "") }
    var format by remember { mutableStateOf(book?.format ?: "physical") }
    var total by remember { mutableStateOf(book?.totalPages?.takeIf { it > 0 }?.toString() ?: "") }
    var genre by remember { mutableStateOf(book?.genre ?: "") }
    var status by remember { mutableStateOf(book?.status ?: "tbr") }
    var triedSubmit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // A6-style: título is focused on open and its IME Next chains down the form.
    val titleFocus = rememberAutoFocusRequester()
    val isAudiobook = format == "audiobook"

    fun submit() {
        if (title.isBlank()) { triedSubmit = true; return }
        val sn = seriesNo.toIntOrNull()
        val tp = total.toIntOrNull()?.coerceAtLeast(0) ?: 0
        if (editing) {
            vm.updateBook(
                book!!.copy(
                    title = title.trim(), author = author.trim(), series = series.trim(),
                    seriesNumber = sn, format = format, totalPages = tp, genre = genre.trim(),
                ),
            )
        } else {
            vm.addBook(title.trim(), author.trim(), series.trim(), sn, format, tp, genre.trim(), status)
        }
        onClose()
    }

    PautaSheet(title = if (editing) tr("Editar livro") else tr("Adicionar livro"), onClose = onClose) {
        SheetEyebrow(tr("Título"))
        Spacer(Modifier.height(8.dp))
        UnderlineField(
            title, { title = it }, tr("Título"),
            modifier = Modifier.focusRequester(titleFocus),
            isError = triedSubmit && title.isBlank(),
            imeAction = ImeAction.Next,
        )
        if (triedSubmit && title.isBlank()) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Dá um título ao livro."))
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("Autor"))
        Spacer(Modifier.height(8.dp))
        BoxedField(author, { author = it }, tr("Autor"), singleLine = true, imeAction = ImeAction.Next)

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(2f)) {
                SheetEyebrow(tr("Série"))
                Spacer(Modifier.height(8.dp))
                BoxedField(series, { series = it }, tr("Série"), singleLine = true, imeAction = ImeAction.Next)
            }
            Column(Modifier.width(88.dp)) {
                SheetEyebrow(tr("Nº na série"))
                Spacer(Modifier.height(8.dp))
                DigitField(seriesNo, max = 3) { seriesNo = it }
            }
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("Formato"))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectPill(tr("Físico"), format == "physical", colors.accent, large = true) { format = "physical" }
            SelectPill(tr("Ebook"), format == "ebook", colors.accent, large = true) { format = "ebook" }
            SelectPill(tr("Audiolivro"), format == "audiobook", colors.accent, large = true) { format = "audiobook" }
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(if (isAudiobook) tr("Total de minutos") else tr("Total de páginas"))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(120.dp)) {
            DigitField(total, max = 6, imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() })) { total = it }
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("Género"))
        Spacer(Modifier.height(8.dp))
        BoxedField(genre, { genre = it }, tr("Género"), singleLine = true, imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() }))

        // Estado is only set on add; an existing book changes status through the
        // detail sheet (K8). // PT: o estado só se escolhe ao adicionar.
        if (!editing) {
            Spacer(Modifier.height(18.dp))
            SheetEyebrow(tr("Estado"))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectPill(tr("A ler"), status == "reading", colors.accent, large = true) { status = "reading" }
                SelectPill(tr("A seguir"), status == "tbr", colors.accent, large = true) { status = "tbr" }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(if (editing) tr("Guardar") else tr("Adicionar"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }

        // Delete is edit-only and two-step: the first tap arms the confirm, the
        // second removes. // PT: eliminar só em edição, com confirmação em dois toques.
        if (editing) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (confirmDelete) tr("Tocar de novo para eliminar") else tr("Eliminar livro"),
                color = DangerRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(999.dp))
                    .clickableNoRipple {
                        if (confirmDelete) { vm.deleteBook(book!!.id); onClose() } else confirmDelete = true
                    }
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/** Digits-only boxed input ([max] = digit count). Mirrors the Marés form's
 *  number field but lives here so book mode stays self-contained. */
@Composable
private fun DigitField(
    value: String,
    max: Int,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onChange: (String) -> Unit,
) {
    BoxedField(
        value = value,
        onChange = { raw -> onChange(raw.filter { it.isDigit() }.take(max)) },
        placeholder = "",
        singleLine = true,
        fontFamily = com.pauta.app.ui.theme.MonoFamily,
        keyboardType = KeyboardType.Number,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
    )
}
