package com.pauta.app.domain

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

/** One chapter of the spine, in reading order. [words] is a real count, not an
 *  estimate — it is what weights the progress line. */
data class EpubChapter(val href: String, val title: String, val words: Int)

/** A parsed book: what the OPF says it is, and its spine in order. */
data class EpubBook(val title: String, val author: String, val chapters: List<EpubChapter>)

/**
 * native-only (R4): everything about an EPUB that can be decided without Android
 * — the container, the OPF, the spine, the word counts, and the sanitiser that
 * decides what of a chapter is allowed to reach a browser engine. No Android
 * imports, so every rule here is a JVM unit test rather than a device
 * observation.
 *
 * **An EPUB is untrusted input, and this file is where that is taken seriously.**
 * Three decisions carry most of the weight:
 *
 * - **There is no XML engine.** The container, the OPF and the chapters are read
 *   by a hand-written scanner that understands tags and nothing else. It cannot
 *   be told to fetch an external entity because it cannot fetch anything, and it
 *   cannot be made to expand a billion laughs because the only entities it knows
 *   are a fixed table it cannot be added to. §4 of the Security model asks for
 *   DTDs and entity expansion to be off; not having a parser that knows what a
 *   DTD is turns that from a setting into a property. A declaration is skipped
 *   like any other doctype, and a reference to what it declared stays the literal
 *   text it was written as.
 * - **Nothing is ever extracted.** Entries are read by name from the open
 *   archive, so there is no directory for a `../` to escape into. Hrefs are
 *   normalised as strings and re-validated with [BookImport.isSafeEntryName]
 *   before they are looked up.
 * - **The sanitiser is an allow-list.** The tags a book needs are few; the tags
 *   HTML will grow next year are not knowable. Anything unlisted is dropped, and
 *   a handful of elements (`script`, `style`, `iframe`, …) take their contents
 *   with them.
 *
 * // PT: tudo o que num EPUB se decide sem Android — o contentor, o OPF, a
 * espinha, as palavras e o que de um capítulo pode chegar a um motor de browser.
 * Sem parser de XML, sem extrair nada, e uma lista de permitidos em vez de uma
 * de proibidos.
 */
object Epub {

    /** A book that isn't one, or is one we can't read. Caught at the UI edge and
     *  shown as a single sentence. // PT: EPUB malformado. */
    class FormatException(message: String) : Exception(message)

    private const val CONTAINER = "META-INF/container.xml"

    /** A chapter body is read into memory; keep the ceiling BookImport already
     *  enforces at import time, in case the file changed underneath us. */
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** An inlined image, and all of a chapter's images together. Images travel as
     *  `data:` URIs (the only thing the CSP lets through), which costs a third
     *  more than the bytes themselves — so both are bounded. // PT: limites das
     *  imagens embutidas. */
    private const val MAX_IMAGE_BYTES = 1024 * 1024
    private const val MAX_CHAPTER_IMAGE_BYTES = 4 * 1024 * 1024

    /** A spine longer than this is not a book. */
    private const val MAX_SPINE = 2_000

    // ── the archive ───────────────────────────────────────────

    /**
     * Reads `META-INF/container.xml` → the OPF → the spine, in order, counting
     * each chapter's words on the way through. One pass over the archive; the
     * counting strips tags rather than running the full sanitiser, so opening a
     * book doesn't pay for inlining every image in it.
     * // PT: contentor → OPF → espinha, com a contagem de palavras à passagem.
     */
    fun parse(zip: ZipFile): EpubBook {
        val opfPath = rootfilePath(readText(zip, CONTAINER))
        val opf = readText(zip, opfPath)
        val doc = parseOpf(opf)
        val base = opfPath.substringBeforeLast('/', "")

        val chapters = ArrayList<EpubChapter>()
        for (idref in doc.spine) {
            if (chapters.size >= MAX_SPINE) break
            val item = doc.manifest[idref] ?: continue
            if (!isChapter(item)) continue
            val href = resolve(base, item.href) ?: continue
            val entry = zip.getEntry(href) ?: continue
            val words = runCatching {
                countWords(readText(zip, entry.name))
            }.getOrDefault(0)
            chapters += EpubChapter(href = href, title = item.title.ifBlank { "" }, words = words)
        }
        if (chapters.isEmpty()) throw FormatException("spine has no readable chapters")
        return EpubBook(title = doc.title, author = doc.author, chapters = chapters)
    }

    /**
     * One chapter, sanitised and with its images inlined, ready to be wrapped and
     * handed to a WebView. Never a whole document — [wrapChapter] adds the head
     * this fragment is allowed to live in. // PT: um capítulo já limpo, com as
     * imagens embutidas.
     */
    fun chapterHtml(zip: ZipFile, href: String): String {
        val raw = readText(zip, href)
        val base = href.substringBeforeLast('/', "")
        var budget = MAX_CHAPTER_IMAGE_BYTES
        return sanitize(
            raw,
            inlineImage = inline@{ src ->
                val path = resolve(base, src) ?: return@inline null
                val data = readImage(zip, path, budget) ?: return@inline null
                budget -= data.second
                data.first
            },
            // F5(d): the archive is the authority on whether a link goes anywhere.
            // // PT: é o arquivo que decide se o link leva a algum lado.
            linkResolves = { link ->
                val path = resolve(base, link)
                path != null && zip.getEntry(path) != null
            },
        )
    }

    /**
     * Words in a chapter's *text*, which is what the progress line is weighted by
     * — a two-page chapter and a forty-page one are not the same fraction of a
     * book. Markup is stripped first, so a chapter is not credited for its tags.
     * // PT: as palavras do texto, sem a marcação.
     */
    fun countWords(html: String): Int {
        var count = 0
        var inWord = false
        forEachText(html) { text ->
            for (ch in text) {
                if (ch.isLetterOrDigit()) {
                    if (!inWord) { count++; inWord = true }
                } else if (!ch.isLetter() && ch != '\'' && ch != '’' && ch != '-') {
                    inWord = false
                }
            }
            inWord = false
        }
        return count
    }

    // ── the sanitiser ─────────────────────────────────────────

    /** Elements a book is made of. Anything not here is dropped, keeping whatever
     *  text was inside it. // PT: os elementos permitidos. */
    private val ALLOWED = setOf(
        "p", "br", "hr", "div", "span", "section", "article", "aside", "main",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "em", "i", "strong", "b", "u", "s", "del", "ins", "mark", "small", "sub", "sup",
        "blockquote", "q", "cite", "abbr", "code", "pre", "kbd", "samp", "var",
        "ul", "ol", "li", "dl", "dt", "dd",
        "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "colgroup", "col",
        "figure", "figcaption", "img", "a", "ruby", "rt", "rp", "bdi", "bdo", "wbr",
    )

    /** Elements dropped *with their contents* — what is inside them is not text a
     *  reader wants, and in the first four cases it is actively dangerous.
     *  // PT: elementos removidos com o conteúdo. */
    private val STRIPPED = setOf(
        "script", "style", "iframe", "object", "embed", "applet", "noscript",
        "template", "form", "input", "button", "select", "option", "textarea",
        "head", "title", "link", "meta", "base", "svg", "math", "audio", "video",
        "source", "track", "canvas", "map", "area", "frame", "frameset",
    )

    /** Attributes worth keeping, per element. `style` is deliberately absent:
     *  a book's own CSS fights the reader's type, and `url()` in a style
     *  attribute is a fetch we would rather not have to reason about.
     *  // PT: atributos mantidos; `style` fica de fora de propósito. */
    private val KEEP_ATTRS = mapOf(
        "a" to setOf("href"),
        "img" to setOf("src", "alt"),
        "td" to setOf("colspan", "rowspan"),
        "th" to setOf("colspan", "rowspan"),
        "ol" to setOf("start"),
    )

    /**
     * A chapter reduced to the tags on the allow-list, with every event handler,
     * every unknown attribute and every dangerous URL gone, and each `<img src>`
     * replaced by whatever [inlineImage] returns for it (a `data:` URI, or null to
     * drop the image). The result is a fragment: no `<html>`, no `<head>`, nothing
     * that could carry a `<base>` or a stylesheet link.
     * // PT: o capítulo reduzido à lista de permitidos, com as imagens embutidas.
     */
    /**
     * F5(d) · the class a link that goes nowhere is marked with, so the stylesheet
     * can paint it as ink rather than as a link. The reader refuses every
     * navigation by design (§G.3) and resolves the internal ones itself, so a link
     * out of the book is inert — and `www.panmacmillan.com`, which carries no
     * scheme and therefore survives [safeHref] as a "relative" href, was being
     * painted in the accent and doing nothing when tapped. Colour is a promise
     * here; this is what stops it being one the reader cannot keep.
     * // PT: a classe de um link que não vai a lado nenhum — pinta-se como texto,
     * porque um link que não navega não deve parecer um link.
     */
    const val DEAD_LINK_CLASS = "dead"

    /**
     * F7 · the class an EPUB3 page-break marker is rendered with.
     *
     * The owner's observation, and the best idea of the round: *"mesmo no nosso
     * epub temos as páginas algures lá"*. EPUB3 carries the **print edition's**
     * page numbers as `epub:type="pagebreak"` markers, precisely so a reader can
     * cross-reference a paper copy — and the sanitiser was throwing them away,
     * because `span` survives the allow-list and its attributes do not.
     *
     * Nothing here is invented. A book without markers is unchanged; a book with
     * them shows the numbers its publisher printed. That is what makes reading in
     * this app compatible with reading anywhere else.
     * // PT: os números de página da edição impressa, que o EPUB3 já traz — não se
     * inventa nenhum.
     */
    const val PAGEBREAK_CLASS = "pb"

    /** Where the marker's number is written, when the attributes carry it.
     *  // PT: onde fica o número, quando vem nos atributos. */
    const val PAGEBREAK_ATTR = "data-p"

    /**
     * F7 · whether this element is a page-break marker. EPUB3 spells it
     * `epub:type="pagebreak"`; the ARIA form (`role="doc-pagebreak"`) means the
     * same thing and appears in real books, so both are honoured.
     * // PT: reconhece as duas formas de marcar uma quebra de página.
     */
    private fun isPageBreak(attrs: List<Pair<String, String>>): Boolean = attrs.any { (name, value) ->
        val n = name.substringAfter(':').lowercase()
        (n == "type" && value.lowercase().contains("pagebreak")) ||
            (n == "role" && value.lowercase().contains("doc-pagebreak"))
    }

    /**
     * F7 · the printed page a marker names, taken from the attributes a publisher
     * actually uses — `title` first, then `aria-label`, then the digits inside an
     * `id` like `page123`. Null when none of them says anything, in which case the
     * marker keeps its own text content instead and the stylesheet draws that.
     *
     * The label is length-capped and reduced to what a page number can be —
     * digits, roman numerals, a hyphen — because it is text out of an untrusted
     * book being placed into the page by CSS. // PT: o número vem dos atributos que
     * os editores usam; limitado, porque é texto de um livro em que não se confia.
     */
    private fun pageBreakLabel(attrs: List<Pair<String, String>>): String? {
        fun attr(want: String): String? = attrs.firstOrNull {
            it.first.substringAfter(':').lowercase() == want
        }?.second?.trim()?.takeIf { it.isNotEmpty() }
        // Validated against what a page number can actually be, rather than
        // filtered down to it: filtering "&lt;script&gt;" would leave "lci", which
        // is a plausible-looking roman numeral and a lie. A value that isn't a page
        // number is not a page number. // PT: valida-se em vez de se limpar — um
        // valor que não é um número de página não vira um.
        fun pageish(v: String?): String? = v?.takeIf {
            it.length <= 12 &&
                (it.matches(ARABIC_PAGE) || it.matches(ROMAN_PAGE))
        }
        return pageish(attr("title"))
            ?: pageish(attr("aria-label"))
            ?: pageish(attr("id")?.let { id -> ARABIC_RUN.find(id)?.value })
    }

    private val ARABIC_PAGE = Regex("^\\d{1,6}$")
    private val ROMAN_PAGE = Regex("^[ivxlcdm]{1,12}$", RegexOption.IGNORE_CASE)
    private val ARABIC_RUN = Regex("\\d{1,6}")

    fun sanitize(
        html: String,
        inlineImage: (String) -> String? = { null },
        // True when this href names a document that exists in *this* book. False
        // for anything else, including a bare fragment: the reader has no
        // same-document jump, so `#p3` is as inert as an external URL and painting
        // it differently would be a second lie. // PT: verdade só quando o href
        // aponta para um documento deste livro.
        linkResolves: (String) -> Boolean = { false },
    ): String {
        val out = StringBuilder(html.length.coerceAtMost(1 shl 20))
        // How deep we are inside an element whose contents go with it. // PT:
        // profundidade dentro de um elemento a descartar por inteiro.
        var dropping = 0
        var droppingTag = ""
        scan(html, object : Sink {
            override fun text(s: String) {
                if (dropping == 0) out.append(escape(decode(s)))
            }

            override fun tag(name: String, attrs: List<Pair<String, String>>, closing: Boolean, selfClosing: Boolean) {
                val tag = name.substringAfter(':').lowercase()
                if (dropping > 0) {
                    if (tag == droppingTag) {
                        if (closing) dropping-- else if (!selfClosing) dropping++
                    }
                    return
                }
                if (tag in STRIPPED) {
                    if (!closing && !selfClosing) { dropping = 1; droppingTag = tag }
                    return
                }
                if (tag !in ALLOWED) return
                if (closing) {
                    if (tag !in VOID) out.append("</").append(tag).append('>')
                    return
                }
                // F7: a page-break marker is emitted as our own element rather than
                // passed through, because the number lives in attributes the
                // allow-list drops. When the attributes name the page, the marker
                // is written whole and the source element's own content is
                // discarded — publishers write the number in both places, and
                // printing it twice would put a stray "123" mid-paragraph. When
                // they don't, the element stays open and its text becomes the
                // label the stylesheet draws. // PT: o marcador é escrito por nós;
                // com número nos atributos, descarta-se o conteúdo (senão sai duas
                // vezes); sem ele, o texto do elemento é que serve de número.
                if (isPageBreak(attrs)) {
                    val label = pageBreakLabel(attrs)
                    val empty = selfClosing || tag in VOID
                    // The marker keeps the book's own tag name so the closing tag
                    // the scanner will hand us still matches; the class is what the
                    // stylesheet selects on. // PT: mantém-se o nome da etiqueta do
                    // livro, para o fecho continuar a bater certo.
                    out.append('<').append(tag)
                        .append(" class=\"").append(PAGEBREAK_CLASS).append('"')
                    if (label != null) {
                        out.append(' ').append(PAGEBREAK_ATTR).append("=\"")
                            .append(escape(label)).append('"')
                    }
                    out.append('>')
                    if (label != null) {
                        // The complete marker is written here, and the source
                        // element's own content is discarded down to its matching
                        // close — publishers write the number in the attributes
                        // *and* as text, and printing both puts a stray "123"
                        // mid-paragraph.
                        out.append("</").append(tag).append('>')
                        if (!empty) { dropping = 1; droppingTag = tag }
                    } else if (empty) {
                        out.append("</").append(tag).append('>')
                    }
                    // With no label in the attributes the element stays open and
                    // its own text becomes what the stylesheet draws.
                    return
                }
                val keep = KEEP_ATTRS[tag].orEmpty()
                val kept = ArrayList<Pair<String, String>>(2)
                for ((rawName, rawValue) in attrs) {
                    val attr = rawName.substringAfter(':').lowercase()
                    if (attr !in keep) continue
                    val value = decode(rawValue)
                    val clean = when {
                        tag == "img" && attr == "src" -> inlineImage(stripFragment(value))
                        attr == "href" -> safeHref(value)
                        else -> value.takeIf { it.length <= 64 }
                    } ?: continue
                    kept += attr to clean
                }
                // An image whose source didn't survive is not an empty `<img>`:
                // it is nothing. // PT: uma imagem sem origem não fica como caixa
                // vazia — desaparece.
                if (tag == "img" && kept.none { it.first == "src" }) return
                out.append('<').append(tag)
                for ((attr, value) in kept) {
                    out.append(' ').append(attr).append("=\"").append(escape(value)).append('"')
                }
                // F5(d): an anchor that resolves to nothing in this book — or that
                // has no surviving href at all, which is what a bare `<a id=…>`
                // target becomes — is marked so the stylesheet can leave it ink.
                // The class is written here rather than kept from the book: a
                // book's own `class` attributes are dropped, so nothing can
                // collide with this one. // PT: marca-se aqui; as classes do livro
                // são descartadas, por isso não há colisão possível.
                if (tag == "a") {
                    val href = kept.firstOrNull { it.first == "href" }?.second
                    if (href == null || !linkResolves(href)) {
                        out.append(' ').append("class=\"").append(DEAD_LINK_CLASS).append('"')
                    }
                }
                if (tag in VOID || selfClosing) out.append("/>") else out.append('>')
            }
        })
        return out.toString()
    }

    /** Void elements never get a closing tag. */
    private val VOID = setOf("br", "hr", "img", "wbr", "col")

    /**
     * A link a chapter may keep. Only same-book, relative links survive — and even
     * those never navigate: the reader refuses every navigation and resolves
     * internal ones itself. Keeping the href is what lets it do that; every
     * scheme (`http`, `javascript`, `data`, `intent`, `market`, `file`…) is
     * dropped here anyway, so the WebView is never asked twice.
     * // PT: só links relativos ficam; qualquer esquema é removido.
     */
    private fun safeHref(value: String): String? {
        val v = value.trim()
        if (v.isEmpty() || v.length > 512) return null
        // A colon before the first slash is a scheme, whatever it spells.
        val colon = v.indexOf(':')
        val slash = v.indexOf('/')
        if (colon >= 0 && (slash < 0 || colon < slash)) return null
        if (v.startsWith("//")) return null
        return v
    }

    // ── the wrapper ───────────────────────────────────────────

    /**
     * The document a chapter is loaded as. The CSP is defence in depth — the
     * WebView's own settings are the control and must stand alone — but it costs
     * one line and closes anything an engine quirk might leave open:
     * `default-src 'none'` blocks what isn't re-allowed, `img-src data:` permits
     * only the images the parser itself inlined, `style-src 'unsafe-inline'` is
     * what the injected stylesheet needs (and is safe with scripting off), and the
     * bare `sandbox` applies the maximum restrictions with no `allow-*` tokens.
     * // PT: o documento em que o capítulo é carregado, com a CSP como segunda
     * camada.
     */
    fun wrapChapter(body: String, css: String): String = buildString {
        append("<!DOCTYPE html><html><head>")
        append("<meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
        append("default-src 'none'; img-src data:; style-src 'unsafe-inline'; sandbox")
        append("\">")
        append("<style>").append(css).append("</style>")
        append("</head><body>").append(body).append("</body></html>")
    }

    // ── position & progress ───────────────────────────────────

    /** Where the reader is: which chapter, and how far down it. */
    data class Position(val chapter: Int, val scroll: Float)

    /** `"<spineIndex>:<scrollPercent>"`, e.g. `"12:0.43"`. */
    fun formatPosition(chapter: Int, scroll: Float): String =
        "${chapter.coerceAtLeast(0)}:${"%.3f".format(java.util.Locale.US, scroll.coerceIn(0f, 1f))}"

    /** The stored bookmark, or null when it says nothing about an EPUB — a PDF's
     *  bookmark is a bare page index and must not be read as chapter 79.
     *  // PT: o marcador guardado; null quando não é de EPUB. */
    fun parsePosition(value: String): Position? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val scroll = parts[1].toFloatOrNull() ?: return null
        return Position(chapter.coerceAtLeast(0), scroll.coerceIn(0f, 1f))
    }

    /**
     * How far through the book, 0–100, weighting chapters by their word count —
     * so a long chapter moves the line more than a short one, which is the whole
     * reason the words were counted. A book with no words anywhere falls back to
     * counting chapters, because a progress line that never moves is worse than
     * one that moves crudely. // PT: a percentagem, pesada por palavras.
     */
    fun percent(words: List<Int>, chapter: Int, scroll: Float): Int {
        if (words.isEmpty()) return 0
        val index = chapter.coerceIn(0, words.size - 1)
        val within = scroll.coerceIn(0f, 1f)
        val total = words.sumOf { it.toLong() }
        if (total <= 0L) {
            return (((index + within) / words.size) * 100).toInt().coerceIn(0, 100)
        }
        var before = 0L
        for (i in 0 until index) before += words[i]
        val read = before + (words[index] * within).toLong()
        return ((read.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    }

    // ── the OPF ───────────────────────────────────────────────

    private data class Item(val href: String, val type: String, val title: String = "")

    private data class Opf(
        val title: String,
        val author: String,
        val manifest: Map<String, Item>,
        val spine: List<String>,
    )

    /** The OPF's path, from the container's first `<rootfile full-path=…>`. */
    private fun rootfilePath(container: String): String {
        var found: String? = null
        scan(container, object : Sink {
            override fun tag(name: String, attrs: List<Pair<String, String>>, closing: Boolean, selfClosing: Boolean) {
                if (found != null || closing) return
                if (name.substringAfter(':').lowercase() != "rootfile") return
                val path = attrs.firstOrNull { it.first.substringAfter(':').lowercase() == "full-path" }?.second
                found = path?.let { resolve("", decode(it)) }
            }
        })
        return found ?: throw FormatException("no rootfile in $CONTAINER")
    }

    private fun parseOpf(opf: String): Opf {
        val manifest = LinkedHashMap<String, Item>()
        val spine = ArrayList<String>()
        var title = ""
        var author = ""
        // Which metadata element we are inside, so its text lands in the right
        // field. // PT: em que elemento de metadados estamos.
        var meta = ""
        scan(opf, object : Sink {
            override fun text(s: String) {
                val value = decode(s).trim()
                if (value.isEmpty()) return
                when (meta) {
                    "title" -> if (title.isEmpty()) title = value
                    "creator" -> if (author.isEmpty()) author = value
                }
            }

            override fun tag(name: String, attrs: List<Pair<String, String>>, closing: Boolean, selfClosing: Boolean) {
                val tag = name.substringAfter(':').lowercase()
                fun attr(want: String) =
                    attrs.firstOrNull { it.first.substringAfter(':').lowercase() == want }?.second?.let { decode(it) }
                when (tag) {
                    "title", "creator" -> meta = if (closing || selfClosing) "" else tag
                    "item" -> if (!closing) {
                        val id = attr("id") ?: return
                        val href = attr("href") ?: return
                        manifest[id] = Item(href, attr("media-type").orEmpty(), attr("title").orEmpty())
                    }
                    "itemref" -> if (!closing) attr("idref")?.let { if (spine.size < MAX_SPINE) spine += it }
                    else -> if (!closing && tag !in setOf("metadata", "manifest", "spine", "package")) meta = ""
                }
            }
        })
        if (spine.isEmpty()) throw FormatException("no spine in the OPF")
        return Opf(title, author, manifest, spine)
    }

    /** A spine item worth rendering: markup, not a stylesheet or a cover image. */
    private fun isChapter(item: Item): Boolean {
        val type = item.type.lowercase()
        if (type.isNotEmpty()) {
            return type.contains("xhtml") || type.contains("text/html") || type == "application/html"
        }
        val href = item.href.substringBefore('#').lowercase()
        return href.endsWith(".xhtml") || href.endsWith(".html") || href.endsWith(".htm")
    }

    // ── paths ─────────────────────────────────────────────────

    /**
     * An href resolved against the directory its document lives in — as *strings*,
     * inside the archive's own namespace. `..` is popped here rather than handed
     * to a filesystem, and the result must still pass the entry-name rules before
     * anyone looks it up. A path that climbs above the archive root comes back
     * null. // PT: resolve o href como texto, dentro do arquivo; nunca no disco.
     */
    fun resolve(baseDir: String, href: String): String? {
        val raw = stripFragment(href).trim()
        if (raw.isEmpty()) return null
        // A scheme is not a path in this archive. `https://…`, `file:///…` and
        // `intent://…` all leave here as null, so nothing downstream ever has to
        // decide what to do with one. // PT: um esquema não é um caminho daqui.
        val colon = raw.indexOf(':')
        val slash = raw.indexOf('/')
        if (colon >= 0 && (slash < 0 || colon < slash)) return null
        if (raw.startsWith("//")) return null
        val decoded = percentDecode(raw)
        // A leading slash is not a book's to write. Silently rebasing it onto the
        // archive root would turn `/etc/passwd` into a lookup for `etc/passwd`,
        // which finds nothing but is a different path than the one asked for —
        // and answering a different question is how a rule stops being one.
        // // PT: um href absoluto é recusado, não reescrito.
        if (decoded.startsWith("/")) return null
        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) {
            parts.addAll(baseDir.split('/').filter { it.isNotEmpty() })
        }
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.size - 1)
                else -> parts += segment
            }
        }
        if (parts.isEmpty()) return null
        val path = parts.joinToString("/")
        return if (BookImport.isSafeEntryName(path)) path else null
    }

    private fun stripFragment(href: String): String =
        href.substringBefore('#').substringBefore('?')

    /** `%20` and friends. Anything malformed is left as written rather than
     *  guessed at. // PT: descodifica %XX; o que estiver malformado fica igual. */
    private fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        val bytes = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                    continue
                }
            }
            for (b in c.toString().toByteArray(Charsets.UTF_8)) bytes.write(b.toInt())
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    // ── reading entries ───────────────────────────────────────

    private fun readText(zip: ZipFile, name: String): String {
        val entry = zip.getEntry(name) ?: throw FormatException("missing entry: $name")
        val bytes = readBytes(zip.getInputStream(entry), MAX_ENTRY_BYTES.toInt())
            ?: throw FormatException("entry too large: $name")
        return decodeText(bytes)
    }

    /** UTF-8 unless a BOM says otherwise; a stray invalid byte becomes U+FFFD
     *  rather than an exception, because half a chapter is better than none.
     *  // PT: UTF-8 (ou o que o BOM disser); bytes inválidos não param a leitura. */
    private fun decodeText(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> String(bytes, Charsets.UTF_8)
    }

    /** An image as a `data:` URI plus the bytes it cost, or null when it is too
     *  large, over the chapter's budget, or not an image. */
    private fun readImage(zip: ZipFile, path: String, budget: Int): Pair<String, Int>? {
        if (budget <= 0) return null
        val entry = zip.getEntry(path) ?: return null
        val mime = imageMime(path) ?: return null
        val cap = minOf(MAX_IMAGE_BYTES, budget)
        val bytes = readBytes(zip.getInputStream(entry), cap) ?: return null
        if (bytes.isEmpty()) return null
        val encoded = base64(bytes)
        return "data:$mime;base64,$encoded" to bytes.size
    }

    private fun imageMime(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }

    /** Reads at most [cap] bytes; null the moment the entry proves longer, so a
     *  lying central directory can't be read into memory. // PT: leitura limitada. */
    private fun readBytes(stream: InputStream, cap: Int): ByteArray? = stream.use { s ->
        val out = ByteArrayOutputStream(minOf(cap, 64 * 1024))
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            total += n
            if (total > cap) return@use null
            out.write(buf, 0, n)
        }
        out.toByteArray()
    }

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Base64 without `java.util.Base64` (API 26 has it, but this file is JVM-pure
     *  and this is six lines). // PT: base64 à mão, para o ficheiro ficar puro. */
    private fun base64(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(B64[n ushr 18 and 63]).append(B64[n ushr 12 and 63])
                .append(B64[n ushr 6 and 63]).append(B64[n and 63])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                sb.append(B64[n ushr 18 and 63]).append(B64[n ushr 12 and 63]).append("==")
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                sb.append(B64[n ushr 18 and 63]).append(B64[n ushr 12 and 63])
                    .append(B64[n ushr 6 and 63]).append('=')
            }
        }
        return sb.toString()
    }

    // ── the scanner ───────────────────────────────────────────

    /** What [scan] reports. Both callbacks are optional to implement. */
    private interface Sink {
        fun text(s: String) {}
        fun tag(name: String, attrs: List<Pair<String, String>>, closing: Boolean, selfClosing: Boolean) {}
    }

    /**
     * The whole XML/HTML understanding in this file: find `<`, read a tag name,
     * read attributes until `>`, report everything between tags as text. Comments,
     * CDATA, doctypes and processing instructions are skipped whole.
     *
     * It is a loop, not a grammar, and that is the point — it recurses nowhere, so
     * ten thousand nested `<div>`s cost ten thousand iterations and no stack; it
     * resolves nothing, so an external entity has nothing to resolve with; and it
     * has no notion of a DTD to be tricked by one. // PT: um ciclo, não uma
     * gramática — sem recursão, sem resolver nada, sem DTDs.
     */
    private fun scan(input: String, sink: Sink) {
        var i = 0
        val n = input.length
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                sink.text(text.toString())
                text.setLength(0)
            }
        }
        while (i < n) {
            val c = input[i]
            if (c != '<') {
                text.append(c)
                i++
                continue
            }
            flush()
            // <!-- comment -->, <![CDATA[…]]>, <!DOCTYPE …>, <? … ?>
            if (input.startsWith("<!--", i)) {
                val end = input.indexOf("-->", i + 4)
                i = if (end < 0) n else end + 3
                continue
            }
            if (input.startsWith("<![CDATA[", i)) {
                val end = input.indexOf("]]>", i + 9)
                val stop = if (end < 0) n else end
                sink.text(input.substring(i + 9, stop))
                i = if (end < 0) n else end + 3
                continue
            }
            if (input.startsWith("<!", i) || input.startsWith("<?", i)) {
                // A doctype may carry a bracketed internal subset; skip to the
                // matching '>' after it. // PT: salta o doctype inteiro.
                var j = i + 2
                var depth = 0
                while (j < n) {
                    val ch = input[j]
                    if (ch == '[') depth++
                    else if (ch == ']') depth--
                    else if (ch == '>' && depth <= 0) break
                    j++
                }
                i = if (j >= n) n else j + 1
                continue
            }
            // A '<' that isn't a tag is just text (unescaped '<' happens).
            if (i + 1 >= n || !(input[i + 1].isLetter() || input[i + 1] == '/')) {
                text.append(c)
                i++
                continue
            }
            var j = i + 1
            val closing = input[j] == '/'
            if (closing) j++
            val nameStart = j
            while (j < n && (input[j].isLetterOrDigit() || input[j] == ':' || input[j] == '-' || input[j] == '_')) j++
            val name = input.substring(nameStart, j)
            val attrs = ArrayList<Pair<String, String>>()
            var selfClosing = false
            while (j < n) {
                while (j < n && input[j].isWhitespace()) j++
                if (j >= n) break
                if (input[j] == '/') { selfClosing = true; j++; continue }
                if (input[j] == '>') { j++; break }
                val attrStart = j
                while (j < n && input[j] != '=' && input[j] != '>' && !input[j].isWhitespace() && input[j] != '/') j++
                val attrName = input.substring(attrStart, j)
                var value = ""
                while (j < n && input[j].isWhitespace()) j++
                if (j < n && input[j] == '=') {
                    j++
                    while (j < n && input[j].isWhitespace()) j++
                    if (j < n && (input[j] == '"' || input[j] == '\'')) {
                        val quote = input[j]
                        j++
                        val valueStart = j
                        while (j < n && input[j] != quote) j++
                        value = input.substring(valueStart, minOf(j, n))
                        if (j < n) j++
                    } else {
                        val valueStart = j
                        while (j < n && !input[j].isWhitespace() && input[j] != '>') j++
                        value = input.substring(valueStart, j)
                    }
                }
                if (attrName.isNotEmpty()) attrs += attrName to value
            }
            if (name.isNotEmpty()) sink.tag(name, attrs, closing, selfClosing)
            i = j
        }
        flush()
    }

    /** Every run of text outside a tag, with `script`/`style` contents skipped —
     *  used by [countWords], which must not count a stylesheet as prose.
     *  // PT: o texto fora das etiquetas, sem o conteúdo de script/style. */
    private fun forEachText(html: String, onText: (String) -> Unit) {
        var dropping = 0
        var droppingTag = ""
        scan(html, object : Sink {
            override fun text(s: String) {
                if (dropping == 0) onText(decode(s))
            }

            override fun tag(name: String, attrs: List<Pair<String, String>>, closing: Boolean, selfClosing: Boolean) {
                val tag = name.substringAfter(':').lowercase()
                if (dropping > 0) {
                    if (tag == droppingTag) {
                        if (closing) dropping-- else if (!selfClosing) dropping++
                    }
                    return
                }
                if (tag in STRIPPED && !closing && !selfClosing) {
                    dropping = 1
                    droppingTag = tag
                }
            }
        })
    }

    // ── entities ──────────────────────────────────────────────

    /**
     * The five predefined entities and numeric references, and **nothing else** —
     * an undeclared `&whatever;` stays the literal text it was written as. There
     * is no table to grow and no declaration to honour, which is what makes an
     * external-entity payload inert rather than dangerous.
     * // PT: só as cinco entidades e as numéricas; o resto fica texto literal.
     */
    fun decode(s: String): String {
        if (!s.contains('&')) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '&') {
                out.append(c)
                i++
                continue
            }
            val end = s.indexOf(';', i + 1)
            if (end < 0 || end - i > 12) {
                out.append(c)
                i++
                continue
            }
            val name = s.substring(i + 1, end)
            val replacement = when {
                name.startsWith("#x") || name.startsWith("#X") ->
                    name.drop(2).toIntOrNull(16)?.let { codePoint(it) }
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { codePoint(it) }
                else -> NAMED[name]
            }
            if (replacement == null) {
                out.append(c)
                i++
            } else {
                out.append(replacement)
                i = end + 1
            }
        }
        return out.toString()
    }

    /**
     * The five predefined entities, plus the handful of typographic ones books
     * actually use. It is a **fixed table**: a document cannot add to it, which is
     * what makes a declared entity — `<!ENTITY xxe SYSTEM "file:///etc/hosts">` and
     * its billion-laughs cousins alike — inert here. An undeclared reference stays
     * the literal text it was written as. // PT: tabela fixa; um documento não lhe
     * pode acrescentar nada, e é isso que torna uma entidade declarada inofensiva.
     */
    private val NAMED = mapOf(
        "lt" to "<", "gt" to ">", "amp" to "&", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "laquo" to "«", "raquo" to "»", "eacute" to "é", "shy" to "",
    )

    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF) String(Character.toChars(value)) else null

    /** Text and attribute values go back out escaped — whatever a chapter meant
     *  by `<`, it is not markup of ours. // PT: o texto sai escapado. */
    private fun escape(s: String): String {
        val out = StringBuilder(s.length + 16)
        for (c in s) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}
