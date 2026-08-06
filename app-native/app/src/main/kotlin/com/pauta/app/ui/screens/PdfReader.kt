package com.pauta.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.pauta.app.service.PdfInfo
import com.pauta.app.service.PdfSession
import com.pauta.app.ui.theme.LocalPautaColors
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color

/** How far a page may be pinched, and how many rendered pages are kept around.
 *  Five is enough for the page you're on plus its neighbours in both directions;
 *  more is just heap. // PT: limites do zoom e da cache de páginas. */
private const val MaxZoom = 4f
private const val CachedPages = 5

/**
 * native-only (R3): the PDF half of the reader — a vertical column of pages, each
 * drawn in the `:reader` process and handed here as pixels. Nothing in this file
 * (or anywhere above it) names `PdfRenderer`; it asks a [PdfSession] for page *n*
 * at *w* pixels wide and gets a bitmap or a null.
 *
 * Pages render lazily as they scroll into view, one at a time (the session
 * serialises them), and the last few stay in an LRU so scrolling back is instant.
 * A page that hasn't arrived yet still occupies its full height — laid out from
 * the document's first-page ratio — so the scrollbar never jumps under the
 * finger. // PT: as páginas são desenhadas à medida que aparecem, uma de cada
 * vez; as últimas ficam em cache e o espaço é reservado antes de existirem.
 */
@Composable
internal fun PdfPages(
    session: PdfSession,
    info: PdfInfo,
    listState: LazyListState,
    // F5(b): the measured height of the reader's own bars. A page drawn under
    // them is a page with its top and bottom hidden, and the bars are drawn over
    // the content by design. // PT: a altura medida das barras do leitor.
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    // L5: the theme's paper, for the surround only. // PT: só o fundo à volta.
    surround: Color? = null,
    onTapMiddle: () -> Unit,
    onReaderDied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val cache = remember(session) { PageCache() }
    // The document's own page ratio, for pages not yet drawn. // PT: proporção
    // das páginas ainda por desenhar.
    val fallbackAspect = remember(info) {
        if (info.pageWidth > 0 && info.pageHeight > 0) {
            info.pageWidth.toFloat() / info.pageHeight
        } else DefaultPageAspect
    }

    // L5 · the PDF honours `readerTheme` only in what it *can*: the surface behind
    // the page. **A rendered PDF page is never inverted or recoloured** — a
    // scanned page inverted is unreadable and a diagram inverted is wrong — so
    // `night` dims the surround and leaves the page as the document drew it.
    // // PT: o tema só muda o fundo à volta; a página fica como o documento a
    // desenhou — inverter um digitalizado torna-o ilegível.
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(if (surround != null) Modifier.background(surround) else Modifier)
            .onSizeChanged { widthPx = it.width },
        contentPadding = PaddingValues(top = topInset + 4.dp, bottom = bottomInset + 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(info.pageCount) { index ->
            PdfPage(
                index = index,
                widthPx = widthPx,
                fallbackAspect = fallbackAspect,
                session = session,
                cache = cache,
                onTapMiddle = onTapMiddle,
                onReaderDied = onReaderDied,
            )
        }
    }
}

/**
 * One page. Renders itself when it comes into view, pinches to zoom (1×–4×, pan
 * clamped to its own bounds, double-tap back to 1×) and passes a tap in the
 * middle band up to the shell so the chrome can appear. // PT: uma página —
 * desenha-se quando aparece, faz zoom com dois dedos e devolve o toque ao meio.
 */
@Composable
private fun PdfPage(
    index: Int,
    widthPx: Int,
    fallbackAspect: Float,
    session: PdfSession,
    cache: PageCache,
    onTapMiddle: () -> Unit,
    onReaderDied: () -> Unit,
) {
    val colors = LocalPautaColors.current
    // A cached page is shown on the very first frame; only a miss renders.
    // // PT: uma página em cache aparece logo; só as outras são desenhadas.
    var image by remember(index, widthPx) { mutableStateOf(cache.get(index, widthPx)) }
    LaunchedEffect(index, widthPx) {
        if (image != null || widthPx <= 0) return@LaunchedEffect
        val rendered = session.render(index, widthPx)?.asImageBitmap()
        if (rendered == null) {
            // A page that won't draw is usually just that page — but if `:reader`
            // took a malformed PDF down with it, the reader says so once instead
            // of scrolling through a document of blanks. // PT: se o processo
            // morreu, o leitor diz-o uma vez em vez de mostrar páginas vazias.
            if (session.died) onReaderDied()
            return@LaunchedEffect
        }
        cache.put(index, widthPx, rendered)
        image = rendered
    }

    // Zoom lives with the page, so it resets by itself when the page scrolls away
    // and comes back. // PT: o zoom é da página e volta ao sítio sozinho.
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }

    val aspect = image?.let { it.width.toFloat() / it.height } ?: fallbackAspect
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { scale = 1f; offset = Offset.Zero },
                    onTap = { p ->
                        val w = size.width
                        if (w <= 0 || (p.x > w * 0.3f && p.x < w * 0.7f)) onTapMiddle()
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // One finger on an un-zoomed page belongs to the list, not
                        // to us. // PT: um dedo numa página sem zoom é scroll.
                        if (fingers > 1 || scale > 1f) {
                            val next = (scale * event.calculateZoom()).coerceIn(1f, MaxZoom)
                            val pan = event.calculatePan()
                            val maxX = size.width * (next - 1f) / 2f
                            val maxY = size.height * (next - 1f) / 2f
                            val nx = (offset.x + pan.x).coerceIn(-maxX, maxX)
                            val ny = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            val moved = next != scale || nx != offset.x || ny != offset.y
                            scale = next
                            offset = Offset(nx, ny)
                            // Once a zoomed page has nowhere left to go, the drag is
                            // the list's again — otherwise zooming would trap the
                            // reader on one page. // PT: no limite do deslocamento,
                            // o gesto volta a ser da lista.
                            if (fingers > 1 || moved) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .background(colors.paper),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}

/** A4-ish, for a document that wouldn't say. // PT: proporção por omissão. */
private const val DefaultPageAspect = 0.707f

/**
 * The last [CachedPages] rendered pages, keyed by page index at one width. A
 * width change (rotation, a text-scale change that resizes the surface) makes
 * every entry the wrong size, so the whole cache goes rather than being scaled.
 * // PT: cache das últimas páginas desenhadas; muda a largura, esvazia-se.
 */
private class PageCache {

    // Access-ordered, so the first entry is always the least recently used.
    private val pages = LinkedHashMap<Int, ImageBitmap>(0, 0.75f, true)
    private var width = 0

    fun get(page: Int, widthPx: Int): ImageBitmap? =
        if (widthPx != width) null else pages[page]

    fun put(page: Int, widthPx: Int, image: ImageBitmap) {
        if (widthPx != width) {
            pages.clear()
            width = widthPx
        }
        pages[page] = image
        while (pages.size > CachedPages) {
            val eldest = pages.keys.firstOrNull() ?: break
            pages.remove(eldest)
        }
    }
}
