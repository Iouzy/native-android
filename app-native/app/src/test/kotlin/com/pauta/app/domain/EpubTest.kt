package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * native-only (R4): the EPUB parser and its sanitiser. Half of this file is
 * ordinary — a book parses, a spine is in order, words are counted — and half is
 * hostile fixtures, where the assertion is always that the payload was **rejected
 * or neutered**. "It didn't crash" is not a passing test.
 * // PT: o parser de EPUB e o higienizador; metade livros normais, metade
 * ficheiros hostis — e a asserção é sempre que o ataque não passou.
 */
class EpubTest {

    @get:Rule val tmp = TemporaryFolder()

    // ── fixtures ──────────────────────────────────────────────

    private fun zipOf(build: ZipOutputStream.() -> Unit): ZipFile {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.build() }
        val file = File(tmp.root, "book-${System.nanoTime()}.epub")
        file.writeBytes(bytes.toByteArray())
        return ZipFile(file)
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.entry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private val container = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf"
                     media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun opf(
        title: String = "Attached",
        author: String = "Amir Levine",
        items: String = """
            <item id="c1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="css" href="style.css" media-type="text/css"/>
        """,
        spine: String = """<itemref idref="c1"/><itemref idref="c2"/>""",
        prologue: String = "",
    ) = """
        <?xml version="1.0"?>
        $prologue
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$title</dc:title>
            <dc:creator>$author</dc:creator>
          </metadata>
          <manifest>$items</manifest>
          <spine>$spine</spine>
        </package>
    """.trimIndent()

    /** A well-formed two-chapter book. */
    private fun book(
        ch1: String = "<html><body><p>Uma frase com cinco palavras.</p></body></html>",
        ch2: String = "<html><body><p>Duas</p><p>três quatro</p></body></html>",
        opfXml: String = opf(),
        containerXml: String = container,
    ) = zipOf {
        entry("mimetype", "application/epub+zip")
        entry("META-INF/container.xml", containerXml)
        entry("OEBPS/content.opf", opfXml)
        entry("OEBPS/Text/ch1.xhtml", ch1)
        entry("OEBPS/Text/ch2.xhtml", ch2)
        entry("OEBPS/style.css", "body{color:red}")
    }

    // ── the ordinary book ─────────────────────────────────────

    @Test fun `a valid book parses its metadata and spine in order`() {
        val parsed = Epub.parse(book())
        assertEquals("Attached", parsed.title)
        assertEquals("Amir Levine", parsed.author)
        assertEquals(
            listOf("OEBPS/Text/ch1.xhtml", "OEBPS/Text/ch2.xhtml"),
            parsed.chapters.map { it.href },
        )
    }

    @Test fun `the stylesheet in the manifest is not a chapter`() {
        // It is in the manifest and not in the spine, and it isn't markup either.
        assertTrue(Epub.parse(book()).chapters.none { it.href.endsWith(".css") })
    }

    @Test fun `words are counted from the text, not the markup`() {
        val parsed = Epub.parse(book())
        assertEquals(5, parsed.chapters[0].words)
        assertEquals(3, parsed.chapters[1].words)
    }

    @Test fun `a script's contents are not counted as words`() {
        val words = Epub.countWords(
            "<p>duas palavras</p><script>var a = 1; alert('muitas palavras aqui')</script>",
        )
        assertEquals(2, words)
    }

    @Test fun `hrefs relative to the opf are resolved into archive paths`() {
        assertEquals("OEBPS/Text/ch1.xhtml", Epub.resolve("OEBPS", "Text/ch1.xhtml"))
        assertEquals("OEBPS/ch1.xhtml", Epub.resolve("OEBPS/Text", "../ch1.xhtml"))
        assertEquals("OEBPS/a b.xhtml", Epub.resolve("OEBPS", "a%20b.xhtml"))
    }

    @Test fun `a chapter renders as a fragment with its text intact`() {
        val html = Epub.chapterHtml(book(), "OEBPS/Text/ch1.xhtml")
        assertTrue(html, html.contains("<p>"))
        assertTrue(html, html.contains("Uma frase com cinco palavras."))
        // A fragment, not a document: the head is the wrapper's job.
        assertFalse(html, html.contains("<html", ignoreCase = true))
        assertFalse(html, html.contains("<body", ignoreCase = true))
    }

    // ── the book that isn't one ───────────────────────────────

    @Test fun `a book with no container is refused`() {
        val zip = zipOf {
            entry("mimetype", "application/epub+zip")
            entry("OEBPS/content.opf", opf())
        }
        assertThrowsFormat { Epub.parse(zip) }
    }

    @Test fun `a container with no rootfile is refused`() {
        assertThrowsFormat { Epub.parse(book(containerXml = "<container><rootfiles/></container>")) }
    }

    @Test fun `an opf with no spine is refused`() {
        assertThrowsFormat { Epub.parse(book(opfXml = opf(spine = ""))) }
    }

    @Test fun `a spine pointing at nothing readable is refused`() {
        assertThrowsFormat {
            Epub.parse(book(opfXml = opf(spine = """<itemref idref="missing"/>""")))
        }
    }

    // ── hostile: scripting ────────────────────────────────────

    @Test fun `a script element leaves with its contents`() {
        val html = Epub.sanitize(
            """<p>antes</p><script>document.title='pwned'</script><p>depois</p>""",
        )
        assertFalse(html, html.contains("script", ignoreCase = true))
        assertFalse(html, html.contains("pwned"))
        assertTrue(html, html.contains("antes") && html.contains("depois"))
    }

    @Test fun `event handler attributes do not survive`() {
        val html = Epub.sanitize("""<p onclick="steal()" onload="x()">texto</p>""")
        assertFalse(html, html.contains("onclick", ignoreCase = true))
        assertFalse(html, html.contains("onload", ignoreCase = true))
        assertFalse(html, html.contains("steal"))
        assertTrue(html, html.contains("texto"))
    }

    @Test fun `a javascript url is not a link`() {
        val html = Epub.sanitize("""<a href="javascript:alert(1)">toca</a>""")
        assertFalse(html, html.contains("javascript", ignoreCase = true))
        assertTrue("the text survives, only the link goes", html.contains("toca"))
    }

    @Test fun `a data text html url is not a link`() {
        val html = Epub.sanitize("""<a href="data:text/html;base64,PHNjcmlwdD4=">toca</a>""")
        assertFalse(html, html.contains("data:text/html", ignoreCase = true))
        assertFalse(html, html.contains("base64"))
    }

    @Test fun `an intent url launches nothing because it is not kept`() {
        val html = Epub.sanitize("""<a href="intent://evil#Intent;scheme=x;end">toca</a>""")
        assertFalse(html, html.contains("intent:", ignoreCase = true))
        val market = Epub.sanitize("""<a href="market://details?id=x">toca</a>""")
        assertFalse(market, market.contains("market:", ignoreCase = true))
    }

    @Test fun `an internal link is the one thing a chapter may keep`() {
        val html = Epub.sanitize("""<a href="ch7.xhtml#p3">nota</a>""")
        assertTrue(html, html.contains("""href="ch7.xhtml#p3""""))
    }

    // ── F5(d) · a dead link is not painted as a link ──────────

    @Test fun `a schemeless url survives the sanitiser and is marked dead`() {
        // www.panmacmillan.com has no colon before its first slash, so safeHref
        // reads it as a relative href and keeps it. Nothing navigates — the reader
        // refuses every navigation — so painting it in the accent was a promise
        // the app could not keep. // PT: o caso encontrado no livro real.
        val html = Epub.sanitize("""<a href="www.panmacmillan.com">Pan Macmillan</a>""")
        assertTrue(html, html.contains("class=\"${Epub.DEAD_LINK_CLASS}\""))
    }

    @Test fun `a link the book actually contains keeps the accent`() {
        val html = Epub.sanitize(
            """<a href="ch7.xhtml#p3">nota</a>""",
            linkResolves = { it == "ch7.xhtml#p3" },
        )
        assertFalse(html, html.contains(Epub.DEAD_LINK_CLASS))
    }

    @Test fun `an anchor with no href at all is dead too`() {
        // `<a id="p3">` is a jump target, not a link; its id doesn't survive the
        // allow-list, so what is left must not look tappable.
        // // PT: uma âncora sem href também não deve parecer um link.
        val html = Epub.sanitize("""<a id="p3">texto</a>""")
        assertTrue(html, html.contains("class=\"${Epub.DEAD_LINK_CLASS}\""))
    }

    @Test fun `an external-looking url is dead when nothing resolves it`() {
        val html = Epub.sanitize("""<a href="other.xhtml">outro</a>""")
        assertTrue(html, html.contains(Epub.DEAD_LINK_CLASS))
    }

    @Test fun `a base element cannot redirect what relative urls mean`() {
        val html = Epub.sanitize("""<base href="https://evil.example/"><p>texto</p>""")
        assertFalse(html, html.contains("base", ignoreCase = true))
        assertFalse(html, html.contains("evil"))
    }

    @Test fun `iframes objects and embeds are gone with their contents`() {
        val html = Epub.sanitize(
            """<iframe src="https://evil.example"></iframe>
               <object data="x.swf"><param name="p"/></object>
               <embed src="y.swf"/><p>fica</p>""",
        )
        for (tag in listOf("iframe", "object", "embed", "param")) {
            assertFalse(html, html.contains(tag, ignoreCase = true))
        }
        assertTrue(html, html.contains("fica"))
    }

    // ── hostile: exfiltration ─────────────────────────────────

    @Test fun `a remote image is dropped rather than fetched`() {
        val html = Epub.chapterHtml(
            book(ch1 = """<p>a</p><img src="https://evil.example/beacon.png" alt="x"/>"""),
            "OEBPS/Text/ch1.xhtml",
        )
        assertFalse(html, html.contains("evil.example"))
        assertFalse(html, html.contains("https"))
        assertFalse("an image with no source is not an image", html.contains("<img"))
    }

    @Test fun `a stylesheet and its url() never reach the engine`() {
        val html = Epub.sanitize(
            """<style>body{background:url(https://evil.example/b.png)}</style>
               <link rel="stylesheet" href="https://evil.example/s.css"/><p>texto</p>""",
        )
        assertFalse(html, html.contains("url(", ignoreCase = true))
        assertFalse(html, html.contains("evil.example"))
        assertFalse(html, html.contains("<link", ignoreCase = true))
    }

    @Test fun `a style attribute is not an attribute a book may set`() {
        val html = Epub.sanitize("""<p style="background:url(https://evil.example/b.png)">t</p>""")
        assertFalse(html, html.contains("style", ignoreCase = true))
        assertFalse(html, html.contains("evil.example"))
    }

    @Test fun `an image inside the book is inlined as a data uri`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val zip = zipOf {
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", container)
            entry("OEBPS/content.opf", opf())
            entry("OEBPS/Text/ch1.xhtml", """<p>a</p><img src="../img/x.png"/>""")
            entry("OEBPS/Text/ch2.xhtml", "<p>b</p>")
            entry("OEBPS/img/x.png", png)
        }
        val html = Epub.chapterHtml(zip, "OEBPS/Text/ch1.xhtml")
        assertTrue(html, html.contains("<img src=\"data:image/png;base64,"))
    }

    // ── hostile: XML ──────────────────────────────────────────

    @Test fun `an external entity in the opf reads no file`() {
        // The classic XXE: a declared entity pointing at a local file, referenced
        // from the title. It must not be expanded — the title cannot contain what
        // the file contains, because nothing ever opens it.
        val hostile = opf(
            title = "&xxe;",
            prologue = """<!DOCTYPE package [<!ENTITY xxe SYSTEM "file:///etc/hosts">]>""",
        )
        val parsed = Epub.parse(book(opfXml = hostile))
        assertFalse(parsed.title, parsed.title.contains("localhost"))
        assertFalse(parsed.title, parsed.title.contains("127.0.0.1"))
        // Whatever it is, it is the literal reference and not a file's contents.
        assertEquals("&xxe;", parsed.title)
    }

    @Test fun `a billion laughs expands to nothing and returns`() {
        val laughs = buildString {
            append("""<!DOCTYPE package [<!ENTITY lol "lol">""")
            for (i in 1..9) {
                append("""<!ENTITY lol$i "&lol${if (i == 1) "" else "${i - 1}"};""")
                repeat(9) { append("&lol${if (i == 1) "" else "${i - 1}"};") }
                append("\">")
            }
            append("]>")
        }
        val parsed = Epub.parse(book(opfXml = opf(title = "&lol9;", prologue = laughs)))
        // Nine levels of tenfold expansion would be a billion characters. It is 6.
        assertEquals("&lol9;", parsed.title)
        assertTrue(parsed.title.length < 32)
    }

    @Test fun `a doctype with an internal subset does not swallow the document`() {
        val parsed = Epub.parse(
            book(opfXml = opf(prologue = """<!DOCTYPE package [<!ENTITY nbsp "&#160;">]>""")),
        )
        assertEquals("Attached", parsed.title)
        assertEquals(2, parsed.chapters.size)
    }

    @Test fun `ten thousand nested divs do not overflow the stack`() {
        val deep = "<div>".repeat(10_000) + "fundo" + "</div>".repeat(10_000)
        val html = Epub.sanitize(deep)
        assertTrue(html, html.contains("fundo"))
        assertEquals(10_000, Regex("<div>").findAll(html).count())
    }

    @Test fun `a chapter that is not xml at all still yields its text`() {
        val html = Epub.sanitize("texto solto < 3 e mais texto")
        assertTrue(html, html.contains("texto solto"))
        assertTrue(html, html.contains("e mais texto"))
    }

    // ── hostile: paths ────────────────────────────────────────

    @Test fun `an href climbing out of the archive resolves to nothing`() {
        assertNull(Epub.resolve("OEBPS", "../../../databases/pauta.db"))
        assertNull(Epub.resolve("", "../secret"))
        assertNull(Epub.resolve("OEBPS/Text", "../../../../etc/passwd"))
    }

    @Test fun `an absolute or schemed href resolves to nothing`() {
        assertNull(Epub.resolve("OEBPS", "/etc/passwd"))
        assertNull(Epub.resolve("OEBPS", "file:///etc/passwd"))
        assertNull(Epub.resolve("OEBPS", "https://evil.example/x.png"))
        assertNull(Epub.resolve("OEBPS", "//evil.example/x.png"))
    }

    @Test fun `a spine href pointing outside the archive is skipped, not followed`() {
        val hostile = opf(
            items = """<item id="c1" href="../../../etc/passwd" media-type="application/xhtml+xml"/>
                       <item id="c2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>""",
        )
        val parsed = Epub.parse(book(opfXml = hostile))
        assertEquals(listOf("OEBPS/Text/ch2.xhtml"), parsed.chapters.map { it.href })
    }

    // ── the wrapper ───────────────────────────────────────────

    @Test fun `every chapter is wrapped with a content security policy`() {
        val doc = Epub.wrapChapter("<p>olá</p>", "body{color:#000}")
        assertTrue(doc, doc.contains("Content-Security-Policy"))
        assertTrue(doc, doc.contains("default-src 'none'"))
        assertTrue(doc, doc.contains("img-src data:"))
        assertTrue(doc, doc.contains("sandbox"))
        assertTrue(doc, doc.contains("<p>olá</p>"))
    }

    // ── position & progress ───────────────────────────────────

    @Test fun `a bookmark round-trips`() {
        val mark = Epub.parsePosition(Epub.formatPosition(12, 0.43f))
        assertNotNull(mark)
        assertEquals(12, mark!!.chapter)
        assertEquals(0.43f, mark.scroll, 0.001f)
    }

    @Test fun `a pdf bookmark is not read as an epub one`() {
        // R3 writes a bare page index; reading "79" as chapter 79 would open the
        // wrong book entirely. // PT: "79" é uma página, não um capítulo.
        assertNull(Epub.parsePosition("79"))
        assertNull(Epub.parsePosition(""))
        assertNull(Epub.parsePosition("a:b"))
    }

    @Test fun `progress is weighted by words, not by chapters`() {
        // Chapter 1 is nine tenths of the book; finishing it is 90%, not 50%.
        val words = listOf(900, 100)
        assertEquals(0, Epub.percent(words, 0, 0f))
        assertEquals(45, Epub.percent(words, 0, 0.5f))
        assertEquals(90, Epub.percent(words, 1, 0f))
        assertEquals(100, Epub.percent(words, 1, 1f))
    }

    @Test fun `progress falls back to chapters when nothing has words`() {
        val words = listOf(0, 0, 0, 0)
        assertEquals(0, Epub.percent(words, 0, 0f))
        assertEquals(50, Epub.percent(words, 2, 0f))
    }

    @Test fun `progress never leaves 0 to 100, whatever it is handed`() {
        val words = listOf(10, 10)
        for (chapter in -5..5) {
            for (scroll in listOf(-1f, 0f, 0.5f, 1f, 9f)) {
                val p = Epub.percent(words, chapter, scroll)
                assertTrue("$chapter/$scroll gave $p", p in 0..100)
            }
        }
        assertEquals(0, Epub.percent(emptyList(), 3, 0.5f))
    }

    // ── helper ────────────────────────────────────────────────

    private fun assertThrowsFormat(block: () -> Unit) {
        try {
            block()
        } catch (e: Epub.FormatException) {
            return
        }
        throw AssertionError("expected the book to be refused, but it parsed")
    }
}
