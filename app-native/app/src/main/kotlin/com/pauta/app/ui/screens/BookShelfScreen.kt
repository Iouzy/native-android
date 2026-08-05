package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.i18n.tr
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * native-only (K5): the book-mode face of the Hoje tab — a personal library
 * shelf with three sections (A ler agora · A seguir · Lidos) over live DB data,
 * plus an "Adicionar livro" action that opens [BookFormSheet]. Tapping a book
 * opens [BookDetailSheet] (K8) — progress, rating, status, notes and sessions.
 * // PT: a estante do modo livro — em curso, a seguir, lidos + adicionar.
 *
 * @param onOpenReader R3: opens the reader for a book with a readable file. A
 *   book in progress that carries a document goes straight there on a tap; its
 *   quiet lines still open the detail sheet. // PT: abre o leitor; as linhas
 *   discretas do cartão continuam a abrir o detalhe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookShelfScreen(onOpenReader: (String) -> Unit = {}) {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val paused by vm.booksPaused.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    // R1: quick capture moved off the floating chip and into the header. // PT: a
    // folha de captura rápida, agora aberta a partir do cabeçalho.
    var showCapture by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }

    // K8: any book tap opens the detail sheet, which owns progress/rating/
    // status/notes/sessions. // PT: o toque abre a folha de detalhe do livro.
    val onOpenBook: (String) -> Unit = { detailId = it }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // P5: shared ScreenTitle role — the book faces were 34sp, two down
                // from Marés. // PT: título no papel partilhado das tabs.
                Text(
                    text = tr("Estante"),
                    color = colors.ink,
                    style = PautaType.ScreenTitle,
                )
                // The weighted spacer is measured last, so the actions flow below
                // gets what the title leaves — and wraps inside it at a large text
                // scale instead of pushing the title off the row. // PT: o espaçador
                // com peso mede-se por último; as acções recebem o resto e quebram lá
                // dentro.
                Spacer(Modifier.weight(1f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // R1: quick capture lives here now — the floating chip that sat on
                    // the tab bar's hairline is gone. Quiet ink beside the accent
                    // "Adicionar livro". // PT: a captura rápida passou para o
                    // cabeçalho, discreta ao lado de "Adicionar livro".
                    HeaderAction(
                        label = "✎ " + tr("Nota") + " +",
                        color = colors.ink2,
                        description = tr("Nova nota"),
                    ) { showCapture = true }
                    Text(
                        text = "·",
                        color = colors.ink4,
                        style = PautaType.Meta,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    HeaderAction(
                        label = tr("Adicionar livro") + " +",
                        color = colors.accent,
                        description = tr("Adicionar livro"),
                    ) { showAdd = true }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Section 1 · A ler agora (status = "reading") ──
        item(key = "reading-section") {
            SectionEyebrow(tr("A ler agora"))
            Spacer(Modifier.height(12.dp))
            if (reading.isEmpty()) {
                // P10: the one empty state, shared with the planner faces.
                // // PT: o estado vazio único, partilhado com as tabs do planner.
                EmptyState(tr("Nenhum livro em curso"))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(reading, key = { "rd-${it.id}" }) { book ->
                        BookProgressCard(
                            book = book,
                            onOpenReader = { onOpenReader(book.id) },
                            onOpenDetail = { onOpenBook(book.id) },
                        )
                    }
                }
            }
        }

        // ── Section 2 · Em pausa (status = "paused") — L3 ──
        // Between the book you are in and the ones you have not opened, because
        // that is where a book you put down sits. Rendered as rows, not cards: a
        // paused book is a title you are choosing between, not one you are in the
        // middle of. Hidden when empty. // PT: a prateleira dos livros em pausa —
        // em linhas, entre "a ler agora" e "a seguir"; escondida se vazia.
        if (paused.isNotEmpty()) {
            item(key = "paused-header") {
                Spacer(Modifier.height(36.dp))
                SectionEyebrow(tr("Em pausa"))
                Spacer(Modifier.height(6.dp))
            }
            items(paused, key = { "ps-${it.id}" }) { book ->
                BookListRow(book) { onOpenBook(book.id) }
            }
        }

        // ── Section 3 · A seguir (status = "tbr") ──
        if (tbr.isNotEmpty()) {
            item(key = "tbr-header") {
                Spacer(Modifier.height(36.dp))
                SectionEyebrow(tr("A seguir"))
                // P6: one gap under every section eyebrow (the list rows carry their
                // own padding, so this one is shorter by design).
                Spacer(Modifier.height(6.dp))
            }
            items(tbr, key = { "tbr-${it.id}" }) { book ->
                BookListRow(book) { onOpenBook(book.id) }
            }
        }

        // ── Section 4 · Lidos (status = "done"/"dnf", finishedAt DESC) ──
        if (done.isNotEmpty()) {
            item(key = "done-section") {
                Spacer(Modifier.height(36.dp))
                SectionEyebrow(tr("Lidos"))
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(done, key = { "dn-${it.id}" }) { book ->
                        BookDoneCard(book) { onOpenBook(book.id) }
                    }
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
    }

    if (showAdd) {
        BookFormSheet(onClose = { showAdd = false })
    }
    if (showCapture) {
        QuoteCaptureSheet(onClose = { showCapture = false })
    }
    detailId?.let { id ->
        BookDetailSheet(
            bookId = id,
            onDismiss = { detailId = null },
            onOpenReader = { detailId = null; onOpenReader(id) },
        )
    }
}

/** A header action: quiet mono text with a touch target, announced to TalkBack
 *  by [description] because the glyphs ("✎", "+") don't read as words.
 *  // PT: acção do cabeçalho — texto mono discreto com descrição para o TalkBack. */
@Composable
private fun HeaderAction(
    label: String,
    color: Color,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = color,
        style = PautaType.Meta,
        letterSpacing = 0.44.sp,
        modifier = Modifier
            .clickableNoRipple(onClick)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description; role = Role.Button },
    )
}

/** "Reading now" card: title, author, a tide-weight progress bar and the
 *  page/minute count — pages for physical/ebook, minutes for audiobooks.
 *
 *  R3: when the book carries a document, the card itself opens the reader — the
 *  shortest path between "I am reading this" and the page. The quiet lines under
 *  the title (the author, and the progress meta, which is always there) stay a
 *  way into the detail sheet, so nothing becomes unreachable on a card with no
 *  author. // PT: com ficheiro, o cartão abre o leitor; as linhas discretas
 *  continuam a abrir o detalhe (mesmo sem autor). */
@Composable
private fun BookProgressCard(book: BookEntity, onOpenReader: () -> Unit, onOpenDetail: () -> Unit) {
    val colors = LocalPautaColors.current
    val unit = if (book.format == "audiobook") "min." else "p."
    val canRead = rememberCanRead(book)
    PautaCard(
        Modifier.width(168.dp),
        padding = PaddingValues(14.dp),
        onClick = if (canRead) onOpenReader else onOpenDetail,
    ) {
        Text(
            text = book.title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = book.author,
                color = colors.ink3,
                style = PautaType.Meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (canRead) Modifier.clickableNoRipple(onOpenDetail) else Modifier,
            )
        }
        Spacer(Modifier.height(12.dp))
        bookProgressFraction(book)?.let { fraction ->
            ProgressBar(fraction)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            // Unknown length: no bar, just the position reached. R4: an attached
            // EPUB is a percentage either way. // PT: sem tamanho não há barra; um
            // EPUB é sempre percentagem.
            text = bookProgressShort(book, unit),
            color = colors.ink3,
            style = PautaType.MetaSmall,
            modifier = if (canRead) Modifier.clickableNoRipple(onOpenDetail) else Modifier,
        )
    }
}

/** "Up next" row: title + author, full width, tappable. */
@Composable
private fun BookListRow(book: BookEntity, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 11.dp),
    ) {
        Text(
            text = book.title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author,
                color = colors.ink3,
                style = PautaType.Meta,
            )
        }
    }
}

/** "Finished" card: title + a star rating (filled/empty) when the book is rated. */
@Composable
private fun BookDoneCard(book: BookEntity, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    PautaCard(
        Modifier.width(150.dp),
        padding = PaddingValues(14.dp),
        onClick = onClick,
    ) {
        // P6: one shelf title treatment — the "Lidos" cards were a step smaller
        // (15/19) than the two other sections. // PT: um só tratamento de título.
        Text(
            text = book.title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        book.rating?.takeIf { it in 1..5 }?.let { r ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "★".repeat(r) + "☆".repeat(5 - r),
                color = colors.accent,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }
        // L3: "Lidos" holds both endings, and now that one of them is reachable
        // the card has to say which. A quiet word, not a badge. // PT: a
        // prateleira junta lidos e abandonados; a palavra diz qual.
        if (book.status == "dnf") {
            Spacer(Modifier.height(6.dp))
            Text(text = tr("Abandonado"), color = colors.ink4, style = PautaType.MetaSmall)
        }
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = book.author,
                color = colors.ink3,
                style = PautaType.Meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A slim accent-on-rule progress bar, the same visual weight as the tide-fill
 *  cells — a track with an accent fill clipped to [fraction]. Internal so K7's
 *  annual-goal card and K8's detail sheet can reuse it. */
@Composable
internal fun ProgressBar(fraction: Float) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.rule),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
    }
}
