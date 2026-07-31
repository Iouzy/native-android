package com.pauta.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.i18n.tr
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
 */
@Composable
fun BookShelfScreen() {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
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
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tr("Adicionar livro") + " +",
                    color = colors.accent,
                    style = PautaType.Meta,
                    letterSpacing = 0.44.sp,
                    modifier = Modifier
                        .clickableNoRipple { showAdd = true }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Section 1 · A ler agora (status = "reading") ──
        item(key = "reading-section") {
            SectionEyebrow(tr("A ler agora"))
            Spacer(Modifier.height(12.dp))
            if (reading.isEmpty()) {
                EmptyLine(tr("Nenhum livro em curso"))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(reading, key = { "rd-${it.id}" }) { book ->
                        BookProgressCard(book) { onOpenBook(book.id) }
                    }
                }
            }
        }

        // ── Section 2 · A seguir (status = "tbr") ──
        if (tbr.isNotEmpty()) {
            item(key = "tbr-header") {
                Spacer(Modifier.height(36.dp))
                SectionEyebrow(tr("A seguir"))
                Spacer(Modifier.height(4.dp))
            }
            items(tbr, key = { "tbr-${it.id}" }) { book ->
                BookListRow(book) { onOpenBook(book.id) }
            }
        }

        // ── Section 3 · Lidos (status = "done"/"dnf", finishedAt DESC) ──
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
    detailId?.let { id ->
        BookDetailSheet(bookId = id, onDismiss = { detailId = null })
    }
}

/** A quiet ink4 mono line standing in for an empty section. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        color = LocalPautaColors.current.ink4,
        style = PautaType.Meta,
    )
}

/** "Reading now" card: title, author, a tide-weight progress bar and the
 *  page/minute count — pages for physical/ebook, minutes for audiobooks. */
@Composable
private fun BookProgressCard(book: BookEntity, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    val unit = if (book.format == "audiobook") "min." else "p."
    PautaCard(
        Modifier.width(168.dp),
        padding = PaddingValues(14.dp),
        onClick = onClick,
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
            )
        }
        Spacer(Modifier.height(12.dp))
        if (book.totalPages > 0) {
            ProgressBar(book.currentPage.toFloat() / book.totalPages.coerceAtLeast(1))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${book.currentPage} / ${book.totalPages} $unit",
                color = colors.ink3,
                style = PautaType.MetaSmall,
            )
        } else {
            // Unknown length: no bar, just the position reached.
            Text(
                text = "$unit ${book.currentPage}",
                color = colors.ink3,
                style = PautaType.MetaSmall,
            )
        }
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
        Text(
            text = book.title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 15.sp,
            lineHeight = 19.sp,
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
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = book.author,
                color = colors.ink3,
                style = PautaType.MetaSmall,
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
