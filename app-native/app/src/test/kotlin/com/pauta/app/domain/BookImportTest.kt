package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * native-only (R2): the import gate's security tests. Every case here is a file
 * built to be hostile, and every assertion is that it was **rejected** — "it
 * didn't crash" is not a passing test. // PT: testes do portão de importação;
 * cada ficheiro é hostil e cada asserção é que foi recusado.
 */
class BookImportTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun target(name: String = "bk_test.bin") = File(tmp.root, name)

    /** Runs the gate and returns the rejection reason, failing if it let the file through. */
    private fun rejectionOf(bytes: ByteArray, into: File): BookImport.Rejection = try {
        BookImport.storeGuarded(ByteArrayInputStream(bytes), into)
        fail("expected the import to be rejected, but it was accepted")
        error("unreachable")
    } catch (e: BookImport.RejectedException) {
        e.reason
    }

    // ── EPUB fixtures ─────────────────────────────────────────
    /** A minimal, well-formed EPUB: `mimetype` first, then whatever [extra] adds. */
    private fun epub(extra: ZipOutputStream.() -> Unit = {}): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container><rootfiles/></container>".toByteArray())
            zip.closeEntry()
            zip.extra()
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    @Test fun `a well-formed epub is accepted`() {
        val f = target("bk_ok.epub")
        val kind = BookImport.storeGuarded(
            ByteArrayInputStream(epub { entry("ch1.xhtml", "<p>olá</p>".toByteArray()) }),
            f,
        )
        assertEquals("epub", kind)
        assertTrue("the accepted file stays on disk", f.exists())
    }

    @Test fun `a zip bomb is rejected and leaves no partial file`() {
        // 60 MB of zeros deflates to a few dozen KB: over the 50 MB single-entry
        // ceiling and far over the 100:1 ratio.
        val bomb = epub { entry("bomb.xhtml", ByteArray(60 * 1024 * 1024)) }
        assertTrue("the bomb is small on disk", bomb.size < 200 * 1024)
        val f = target("bk_bomb.epub")
        assertEquals(BookImport.Rejection.TOO_LARGE, rejectionOf(bomb, f))
        assertFalse("no partial file is left behind", f.exists())
        assertEquals("nothing else was written either", 0, tmp.root.listFiles()!!.size)
    }

    @Test fun `an entry whose central directory understates its size is still rejected`() {
        // The declared sizes all look innocent; only the running byte counter
        // sees the 2 MB that actually inflate out of a ~2 KB entry.
        val honest = epub { entry("lie.xhtml", ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() }) }
        val lying = understateSize(honest, "lie.xhtml", declared = 1000L)

        // The lie really is in the central directory Java reads.
        val probe = target("probe.epub").apply { writeBytes(lying) }
        java.util.zip.ZipFile(probe).use { zip ->
            assertEquals(1000L, zip.getEntry("lie.xhtml").size)
        }
        probe.delete()

        val f = target("bk_lie.epub")
        assertEquals(BookImport.Rejection.TOO_LARGE, rejectionOf(lying, f))
        assertFalse(f.exists())
    }

    @Test fun `a path-traversal entry name is rejected`() {
        val evil = epub { entry("../../../databases/pauta.db", "gotcha".toByteArray()) }
        val f = target("bk_evil.epub")
        assertEquals(BookImport.Rejection.CORRUPT, rejectionOf(evil, f))
        assertFalse(f.exists())
        // And the rule itself, spelled out.
        assertFalse(BookImport.isSafeEntryName("../../../databases/pauta.db"))
        assertFalse(BookImport.isSafeEntryName("/etc/hosts"))
        assertFalse(BookImport.isSafeEntryName("OEBPS\\..\\x.xhtml"))
        assertFalse(BookImport.isSafeEntryName("C:\\Windows\\x"))
        assertTrue(BookImport.isSafeEntryName("OEBPS/ch1.xhtml"))
    }

    @Test fun `a DRM-protected epub is rejected as DRM`() {
        val drm = epub {
            entry("META-INF/encryption.xml", "<encryption/>".toByteArray())
            entry("ch1.xhtml", "<p>x</p>".toByteArray())
        }
        val f = target("bk_drm.epub")
        assertEquals(BookImport.Rejection.DRM, rejectionOf(drm, f))
        assertFalse(f.exists())
    }

    @Test fun `a plain zip that is not an epub is rejected as unsupported`() {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { it.entry("readme.txt", "not a book".toByteArray()) }
        val f = target("bk_zip.epub")
        assertEquals(BookImport.Rejection.UNSUPPORTED, rejectionOf(out.toByteArray(), f))
        assertFalse(f.exists())
    }

    @Test fun `an entry-count bomb is rejected`() {
        val many = epub { repeat(10_001) { i -> entry("c$i.xhtml", byteArrayOf(0x78)) } }
        val f = target("bk_many.epub")
        assertEquals(BookImport.Rejection.TOO_LARGE, rejectionOf(many, f))
        assertFalse(f.exists())
    }

    // ── PDF fixtures ──────────────────────────────────────────
    @Test fun `a PDF-magic file that is not a PDF is rejected`() {
        val decoy = "%PDF-1.7\nthis is really just a text file\n".toByteArray()
        val f = target("bk_decoy.pdf")
        assertEquals(BookImport.Rejection.CORRUPT, rejectionOf(decoy, f))
        assertFalse("a decoy is never stored as a usable book", f.exists())
    }

    @Test fun `a structurally plausible PDF is accepted`() {
        val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\nstartxref\n9\n%%EOF\n".toByteArray()
        val f = target("bk_ok.pdf")
        assertEquals("pdf", BookImport.storeGuarded(ByteArrayInputStream(pdf), f))
        assertTrue(f.exists())
    }

    @Test fun `a file with neither magic is rejected as unsupported`() {
        val f = target("bk_txt.bin")
        assertEquals(BookImport.Rejection.UNSUPPORTED, rejectionOf("hello".toByteArray(), f))
        assertFalse(f.exists())
    }

    @Test fun `a file over the size ceiling is rejected mid-copy`() {
        // A 1 MB ceiling and 3 MB of input: the copy must stop and clean up
        // rather than run to the end. // PT: o contador trava a meio da cópia.
        val limits = BookImport.Limits(maxFileBytes = 1024 * 1024)
        val big = ByteArray(3 * 1024 * 1024).also {
            "%PDF-1.7".toByteArray().copyInto(it)
        }
        val f = target("bk_big.pdf")
        try {
            BookImport.storeGuarded(ByteArrayInputStream(big), f, limits)
            fail("expected a TOO_LARGE rejection")
        } catch (e: BookImport.RejectedException) {
            assertEquals(BookImport.Rejection.TOO_LARGE, e.reason)
        }
        assertFalse(f.exists())
    }

    /**
     * Rewrites the uncompressed-size field of [name]'s central-directory record —
     * the number `ZipFile` reports — so the archive lies about how much it holds.
     * // PT: adultera o tamanho declarado no directório central.
     */
    private fun understateSize(zip: ByteArray, name: String, declared: Long): ByteArray {
        val patched = zip.copyOf()
        // Find the End Of Central Directory record (PK\x05\x06; no comment, so it
        // is the last 22 bytes) and start from the directory it points at, rather
        // than scanning for a signature that could also occur inside compressed
        // data. // PT: parte do EOCD, para não confundir dados comprimidos com
        // uma assinatura.
        val eocd = patched.size - 22
        require(patched[eocd] == 0x50.toByte() && patched[eocd + 1] == 0x4B.toByte() &&
            patched[eocd + 2] == 0x05.toByte() && patched[eocd + 3] == 0x06.toByte()) { "no EOCD" }
        var i = u32(patched, eocd + 16)
        while (i < eocd) {
            val nameLen = u16(patched, i + 28)
            val extraLen = u16(patched, i + 30)
            val commentLen = u16(patched, i + 32)
            if (String(patched, i + 46, nameLen, Charsets.UTF_8) == name) {
                // uncompressed size: 4 bytes little-endian at offset 24
                for (b in 0..3) patched[i + 24 + b] = ((declared shr (8 * b)) and 0xFF).toByte()
                return patched
            }
            i += 46 + nameLen + extraLen + commentLen
        }
        fail("central directory record for $name not found")
        return patched
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Int =
        u16(b, at) or (u16(b, at + 2) shl 16)
}
