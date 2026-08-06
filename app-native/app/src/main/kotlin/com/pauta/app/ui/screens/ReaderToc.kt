package com.pauta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.domain.Epub
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.service.EpubInfo
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetActionGap
import com.pauta.app.ui.SheetLabelGap
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily

/**
 * L4 · a way to see what the chapters are, and to jump to one.
 *
 * The reader could only move one step at a time: an EPUB turns one chapter per
 * edge-tap, so chapter 20 was nineteen taps and there was no way to see what the
 * chapters even were. And the names **already existed** — `Epub.EpubChapter`
 * parses a title from the OPF and the `:reader` boundary dropped it, which is the
 * whole reason the chrome said "Capítulo 7 de 31" rather than "Capítulo 7 · As
 * Cidades e os Mortos".
 *
 * A PDF has no table of contents to parse (`PdfRenderer` cannot read outlines),
 * so its half of this is the other thing a long document needs: a go-to-page.
 *
 * // PT: o índice de um EPUB (nomes que já eram lidos e se perdiam na fronteira
 * do processo) e, num PDF, o salto para uma página — que é o que um PDF pode ter.
 */

/**
 * The chapter list. Tapping turns to a chapter and closes; the jump goes through
 * the caller's own `turn`, so the bookmark, the label and the session follow it.
 * // PT: a lista de capítulos; o salto passa pelo caminho normal.
 */
@Composable
internal fun ReaderContentsSheet(
    info: EpubInfo,
    current: Int,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val listState = rememberLazyListState()
    // On a sixty-chapter book, opening at the top is opening in the wrong place.
    // // PT: abre no capítulo onde se está, não no primeiro.
    LaunchedEffect(current) {
        listState.scrollToItem(current.coerceAtLeast(0))
    }
    PautaSheet(title = tr("Índice"), onClose = onClose) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
        ) {
            items(info.chapterCount, key = { "ch-$it" }) { index ->
                run {
                    val here = index == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { onJump(index); onClose() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            // A title is untrusted text from the book. It renders
                            // as a Compose Text and never as HTML, and two lines is
                            // as far as one chapter may push the list apart.
                            // // PT: o título vem do livro; nunca como HTML, e no
                            // máximo em duas linhas.
                            text = info.titleOf(index) ?: trf("Capítulo {n}", "n" to index + 1),
                            color = if (here) colors.accent else colors.ink,
                            fontFamily = SerifFamily,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${Epub.percent(info.chapterWords, index, 0f)}%",
                            color = if (here) colors.accent else colors.ink4,
                            style = PautaType.MetaSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The PDF half: one numeric field, clamped to the document, `Go` on the keyboard.
 * There is no outline to offer, and inventing one would be inventing a number.
 * // PT: um campo para a página, limitado ao documento.
 */
@Composable
internal fun ReaderGoToPageSheet(
    pageCount: Int,
    current: Int,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var value by remember { mutableStateOf("") }
    val typed = value.toIntOrNull()
    val ok = typed != null && typed in 1..pageCount

    fun go() {
        val page = value.toIntOrNull() ?: return
        if (page !in 1..pageCount) return
        onJump(page)
        onClose()
    }

    PautaSheet(title = tr("Ir para a página"), onClose = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(110.dp)) {
                BoxedField(
                    value = value,
                    onChange = { raw ->
                        value = raw.filter { it.isDigit() }.take(pageCount.toString().length)
                    },
                    // The current page as the placeholder: it says where you are
                    // and what shape the answer takes. // PT: a página atual serve
                    // de exemplo e de referência.
                    placeholder = current.coerceAtLeast(1).toString(),
                    singleLine = true,
                    fontFamily = MonoFamily,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
            }
            Text(
                text = trf("de {n}", "n" to pageCount),
                color = colors.ink3,
                style = PautaType.Meta,
            )
        }
        if (value.isNotEmpty() && !ok) {
            Spacer(Modifier.height(SheetLabelGap))
            Text(
                text = trf("Entre 1 e {n}.", "n" to pageCount),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(SheetActionGap))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Ir"), Modifier.weight(2f), enabled = ok) { go() }
        }
    }
}
