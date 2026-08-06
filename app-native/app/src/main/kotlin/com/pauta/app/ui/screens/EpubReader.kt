package com.pauta.app.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.pauta.app.domain.Epub
import com.pauta.app.service.EpubSession
import com.pauta.app.ui.theme.LocalPautaColors
import java.io.ByteArrayInputStream
import androidx.compose.ui.unit.Dp
import com.pauta.app.ui.theme.ReaderThemes

/** Where a tap landed: the outer thirds turn a page, the middle calls the chrome
 *  back. // PT: os terços exteriores mudam de capítulo; o meio chama a cromagem. */
enum class ReaderTap { PREVIOUS, MIDDLE, NEXT }

/**
 * native-only (R4): the EPUB half of the reader — one chapter at a time in one
 * WebView, in Pauta's own colours and measure.
 *
 * **This is the app's only browser engine, and every setting below is a control,
 * not a preference.** §3 of the Security model in full: scripting off, no file or
 * content access, network blocked at the engine, no storage, no
 * `addJavascriptInterface` (here or anywhere in the tree). The document is loaded
 * with a **null base URL**, which gives it an opaque origin — it cannot read app
 * storage, cannot reach another origin, and has no same-origin privileges to
 * abuse. Nothing is ever loaded from a `file://` URL.
 *
 * Navigation is refused unconditionally, for every scheme: `shouldOverrideUrlLoading`
 * always returns true, so an `intent://`, a `market://` and an ordinary link all
 * do nothing at the engine. An internal chapter link is not an exception — it is
 * matched against the parsed spine *in Kotlin* and the reader jumps there itself.
 * Every subresource request is answered with an empty response, which is the belt
 * to `blockNetworkLoads`' braces.
 *
 * The chapter HTML arriving here has already been through the allow-list
 * sanitiser in the `:reader` process, and the wrapper adds a CSP as a second
 * layer. Neither is trusted to be the only one.
 *
 * // PT: o leitor de EPUB — um capítulo de cada vez num WebView fechado à chave:
 * sem JavaScript, sem rede, sem ficheiros, origem opaca e nenhuma navegação
 * permitida.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubChapterView(
    session: EpubSession,
    chapter: Int,
    // L5: the reader's own type and colour. Named `reader` and not `settings`
    // because the WebView's own `settings` is an implicit receiver a few lines
    // below, and two things called the same thing in one function is how a
    // lockdown line silently stops applying. // PT: chama-se `reader` porque o
    // WebView já tem um `settings` — dois nomes iguais aqui seria perigoso.
    reader: ReaderSettings,
    // F5(b): the measured height of the reader's bars, which the chapter is inset
    // by. WebView maps one CSS px to one dp, so these carry straight into the
    // stylesheet. // PT: a altura das barras, que o capítulo respeita.
    topInset: Dp,
    bottomInset: Dp,
    restoreScroll: Float,
    onScroll: (Float) -> Unit,
    onTap: (ReaderTap) -> Unit,
    onLink: (String) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPautaColors.current
    val webBg = (ReaderThemes.pair(reader.theme)?.first ?: colors.paper).toArgb()
    val css = rememberChapterCss(topInset, bottomInset, reader)

    // The chapter's markup, fetched from `:reader`. A null answer for a chapter
    // that should exist means the archive (or the process) is gone. // PT: o
    // capítulo vem do processo :reader; um nulo é o fim.
    var html by remember(session) { mutableStateOf<String?>(null) }
    var forChapter by remember(session) { mutableStateOf(-1) }
    // L5: `css` is a key. The stylesheet is baked into the document at fetch time,
    // so without it a settings change would write a new stylesheet nobody ever
    // loaded — the sheet would look like it did nothing. // PT: o CSS entra na
    // chave, senão mudar as definições não muda nada do que está carregado.
    LaunchedEffect(session, chapter, css) {
        html = null
        val body = session.chapter(chapter)
        if (body == null) {
            onFailed()
            return@LaunchedEffect
        }
        html = Epub.wrapChapter(body, css)
        forChapter = chapter
    }

    // What has actually been handed to the engine, so a recomposition doesn't
    // reload the chapter and throw the scroll away. // PT: o que já foi carregado,
    // para não recarregar a cada recomposição.
    val loaded = remember(session) { LoadState() }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    // The engine paints its own background before our CSS lands;
                    // paper it now or every chapter starts with a white flash.
                    // // PT: fundo já em papel, senão pisca branco.
                    // L5: the reader's paper, not the app's — a night theme must not
                    // flash the app's light paper before the CSS lands.
                    // // PT: o papel do leitor, senão pisca o da app.
                    setBackgroundColor(webBg)
                    overScrollMode = WebView.OVER_SCROLL_NEVER

                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.blockNetworkLoads = true
                    settings.setGeolocationEnabled(false)
                    settings.domStorageEnabled = false
                    @Suppress("DEPRECATION")
                    settings.databaseEnabled = false
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    // No JS dialogs, no fullscreen, no permission prompts.
                    webChromeClient = null

                    webViewClient = object : WebViewClient() {
                        /** Nothing in a book may navigate — not http, not file, not
                         *  `intent://`, not a custom scheme. An internal link is
                         *  handed up instead, and the reader scrolls itself.
                         *  // PT: nada navega; um link interno sobe para o leitor. */
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            runCatching { onLink(request.url.toString()) }
                            return true
                        }

                        /**
                         * Belt to `blockNetworkLoads`' braces: every subresource,
                         * whatever its scheme, is an empty response.
                         *
                         * The main frame is the one thing let through, and only
                         * because it is *ours*: the sole main-frame load this
                         * WebView ever performs is the `loadDataWithBaseURL` below.
                         * Emptying it too would leave a blank reader, and it buys
                         * nothing — a link cannot navigate (refused above), and a
                         * `<meta http-equiv="refresh">` cannot exist (the sanitiser
                         * drops `meta` entirely).
                         * // PT: só o documento principal passa, e é o nosso; tudo
                         * o resto vem vazio.
                         */
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            if (request.isForMainFrame) return null
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            // The document has a height now (or will in a moment);
                            // put the reader back where it was. // PT: repor o
                            // scroll assim que há altura.
                            restore(view, loaded.pendingScroll)
                        }
                    }

                    setOnScrollChangeListener { v, _, scrollY, _, _ ->
                        val web = v as WebView
                        val max = web.maxScroll()
                        onScroll(if (max <= 0) 0f else (scrollY.toFloat() / max).coerceIn(0f, 1f))
                    }

                    // A tap is read here rather than through a Compose overlay: an
                    // overlay would have to swallow the drag as well, and then the
                    // chapter wouldn't scroll. Returning false leaves the gesture
                    // entirely to the engine. // PT: o toque é lido aqui e não
                    // consumido — senão o capítulo deixava de deslizar.
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                loaded.downX = event.x
                                loaded.downY = event.y
                                loaded.downAt = event.eventTime
                            }
                            MotionEvent.ACTION_UP -> {
                                val moved = kotlin.math.abs(event.x - loaded.downX) > TapSlopPx ||
                                    kotlin.math.abs(event.y - loaded.downY) > TapSlopPx
                                val quick = event.eventTime - loaded.downAt < TapMaxMs
                                if (!moved && quick) {
                                    val w = v.width.toFloat().coerceAtLeast(1f)
                                    onTap(
                                        when {
                                            event.x < w * 0.3f -> ReaderTap.PREVIOUS
                                            event.x > w * 0.7f -> ReaderTap.NEXT
                                            else -> ReaderTap.MIDDLE
                                        },
                                    )
                                }
                            }
                        }
                        false
                    }
                }
            },
            update = { view ->
                val document = html
                if (document != null && loaded.html !== document) {
                    loaded.html = document
                    loaded.pendingScroll = if (forChapter == chapter) restoreScroll else 0f
                    // §3: a null base URL is the opaque origin. Never a file:// load.
                    // // PT: base nula = origem opaca.
                    view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                }
            },
            onRelease = { view ->
                view.setOnTouchListener(null)
                view.stopLoading()
                view.destroy()
            },
        )
    }
}

/** How far a finger may travel, and how long it may rest, and still be a tap. */
private const val TapSlopPx = 24f
private const val TapMaxMs = 250L

/** Mutable odds and ends the WebView needs to keep between callbacks — held in one
 *  remembered object rather than five. // PT: o estado que o WebView guarda entre
 *  chamadas. */
private class LoadState {
    var html: String? = null
    var pendingScroll: Float = 0f
    var downX: Float = 0f
    var downY: Float = 0f
    var downAt: Long = 0L
}

/** The scrollable height, in pixels: what the document measures, less the window
 *  showing it. Zero when a chapter fits on one screen. */
private fun WebView.maxScroll(): Int {
    val content = (contentHeight * scale).toInt()
    return (content - height).coerceAtLeast(0)
}

/**
 * Puts the reader back at [fraction] of the chapter. The engine reports a content
 * height a beat after the page finishes, so this asks again a few times before
 * giving up rather than landing at the top of a chapter you were halfway through.
 * // PT: repõe a posição; tenta algumas vezes porque a altura só chega depois.
 */
private fun restore(view: WebView, fraction: Float, attempt: Int = 0) {
    if (fraction <= 0f) return
    val max = view.maxScroll()
    if (max > 0) {
        view.scrollTo(0, (max * fraction.coerceIn(0f, 1f)).toInt())
        return
    }
    if (attempt >= 10) return
    view.postDelayed({ restore(view, fraction, attempt + 1) }, 60L)
}

/**
 * The stylesheet a chapter is read through: Pauta's paper, Pauta's ink, Pauta's
 * measure, and the reader's own text size.
 *
 * One honest compromise. The CSP that keeps a book from fetching anything permits
 * `img-src data:` and nothing else — no `font-src` — so the app's own Instrument
 * Serif cannot be handed to the engine without widening it (and without inlining
 * a few hundred kilobytes of base64 into every chapter). The body is therefore
 * set in the platform's `serif`, which is a better long-form reading face than a
 * display serif anyway. Everything else — the colours, the measure, the leading,
 * the size — is the app's.
 * // PT: o CSS do capítulo; a fonte é a `serif` do sistema, porque a CSP não
 * deixa passar tipos de letra e a Instrument Serif é de títulos, não de corpo.
 */
@Composable
private fun rememberChapterCss(topInset: Dp, bottomInset: Dp, reader: ReaderSettings): String {
    val appColors = LocalPautaColors.current
    // L5: the reader's own scale **replaces** the density's font scale here. The
    // app-wide `textScale` accessibility preference still governs the reader's
    // *chrome* — the bars, the sheets, every label — as it does everywhere else;
    // it simply stops being the only thing that sets the body size of a book.
    // // PT: a escala do leitor substitui a global no corpo do livro; a global
    // continua a valer para a cromagem.
    val scale = reader.textScale
    // `app` follows LocalPautaColors, sepia wash and all; the three fixed themes
    // come from the token table beside the others. // PT: "app" segue as cores da
    // app; os outros vêm da tabela de tokens.
    val fixed = ReaderThemes.pair(reader.theme)
    val paper = fixed?.first ?: appColors.paper
    val ink = fixed?.second ?: appColors.ink
    val rule = ReaderThemes.rule(reader.theme) ?: appColors.rule
    val ink3 = if (fixed != null) ink.copy(alpha = 0.55f) else appColors.ink3
    val accent = appColors.accent
    // Clamped in the setter, so these arrive already sane; the local names keep
    // the CSS readable. // PT: já vêm limitados do setter.
    val lineHeight = reader.lineHeight
    val margin = reader.margin
    // The CSP still forbids `font-src`, so the body face stays the platform
    // `serif`. A face picker here would be a picker with one entry — said out loud
    // so the next reader doesn't rediscover it. // PT: a CSP não deixa carregar
    // tipos de letra; um selector de tipo teria uma entrada só.
    // F5(b): the bars' own height, plus the breathing room the page had before.
    // A WebView's CSS px is a dp, so the value crosses unchanged. The floor keeps
    // a sane margin in the frame before the bars have been measured.
    // // PT: a altura das barras em px de CSS (= dp), com um mínimo para o
    // primeiro frame.
    val topPad = topInset.value.toInt().coerceAtLeast(8) + 8
    val bottomPad = bottomInset.value.toInt().coerceAtLeast(24) + 16
    // The key must carry every value the string interpolates, or a changed
    // setting leaves the chapter as it was — and getting it wrong the other way
    // reloads the chapter on every recomposition and throws the scroll away (see
    // the LoadState comment above). // PT: a chave leva tudo o que a string usa.
    return remember(paper, ink, ink3, accent, rule, scale, lineHeight, margin, topPad, bottomPad) {
        val body = (18f * scale).toInt().coerceIn(12, 40)
        """
        html,body{margin:0;padding:0;background:${hex(paper)};}
        body{
          color:${hex(ink)};
          font-family:serif;
          font-size:${body}px;
          line-height:$lineHeight;
          padding:${topPad}px ${margin}px ${bottomPad}px ${margin}px;
          text-align:left;
          word-wrap:break-word;
          overflow-wrap:break-word;
          -webkit-text-size-adjust:100%;
        }
        p{margin:0 0 0.9em 0;text-indent:0;}
        h1,h2,h3,h4,h5,h6{
          font-weight:normal;
          line-height:1.25;
          margin:1.6em 0 0.7em 0;
          color:${hex(ink)};
        }
        h1{font-size:1.5em;} h2{font-size:1.3em;} h3{font-size:1.15em;}
        a{color:${hex(accent)};text-decoration:none;}
        /* F5(d): a link that resolves to nothing in this book is not painted as a
           link. The reader refuses every navigation, so an external URL is inert —
           and an inert thing in the accent is a promise the app cannot keep.
           // PT: um link que não leva a lado nenhum fica em tinta, não em acento. */
        a.${Epub.DEAD_LINK_CLASS}{color:${hex(ink)};text-decoration:none;}
        /* F7: the print edition's page break — a hairline across the measure with
           the publisher's own number small at the end of it. This is the
           orientation pagination would have given, using numbers nobody invented:
           a book without markers is unchanged. The number comes either from the
           marker's attributes (drawn by ::after) or, when it wasn't there, from
           the element's own text. // PT: a separação da página impressa — um traço
           e o número que o editor imprimiu. */
        .${Epub.PAGEBREAK_CLASS}{
          display:block;
          border-top:1px solid ${hex(rule)};
          margin:1.7em 0 1.5em 0;
          padding-top:0.3em;
          text-align:right;
          font-family:monospace;
          font-size:0.72em;
          letter-spacing:0.06em;
          color:${hex(ink3)};
        }
        .${Epub.PAGEBREAK_CLASS}::after{content:attr(${Epub.PAGEBREAK_ATTR});}
        blockquote{
          margin:1.2em 0;padding-left:1em;
          border-left:2px solid ${hex(rule)};
          color:${hex(ink)};
        }
        hr{border:0;border-top:1px solid ${hex(rule)};margin:1.8em 0;}
        img{max-width:100%;height:auto;display:block;margin:1.2em auto;}
        ul,ol{padding-left:1.4em;margin:0 0 0.9em 0;}
        li{margin:0 0 0.35em 0;}
        table{border-collapse:collapse;width:100%;margin:1.2em 0;}
        td,th{border:1px solid ${hex(rule)};padding:6px 8px;text-align:left;}
        pre,code{font-family:monospace;font-size:0.9em;white-space:pre-wrap;}
        """.trimIndent()
    }
}

/** A Compose colour as CSS. // PT: cor em CSS. */
private fun hex(color: Color): String =
    String.format("#%06X", 0xFFFFFF and color.toArgb())

/**
 * Whether this device has a browser engine we are willing to render a book in.
 * WebView updates through Google Play system updates rather than our release
 * cycle, so it can be absent (some AOSP builds) or years old — and rendering
 * untrusted HTML in an engine whose security posture is unknown is exactly what
 * §6 of the Security model says not to do.
 *
 * The version is read once and, when it cannot be parsed at all, the book is
 * allowed: a vendor's odd version string is not evidence of an old engine, and
 * refusing to open a working reader over one is the worse failure.
 * // PT: só renderiza num WebView que exista e não seja antigo; se a versão for
 * ilegível, deixa passar — recusar um leitor que funciona era pior.
 */
fun webViewUsable(): Boolean {
    val pkg = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull() ?: return false
    val major = pkg.versionName
        ?.takeWhile { it.isDigit() }
        ?.toIntOrNull()
        ?: return true
    return major >= MinWebViewMajor
}

/** Chromium 80 shipped in 2020; below that an engine is old enough that its
 *  posture is genuinely unknown. // PT: abaixo disto, motor demasiado antigo. */
private const val MinWebViewMajor = 80
