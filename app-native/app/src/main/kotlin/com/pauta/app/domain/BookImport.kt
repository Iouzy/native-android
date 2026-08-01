package com.pauta.app.domain

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * native-only (R2): the import gate for an attached book — the whole of §1 of
 * the reader's Security model, written as pure JVM code so every rule is
 * unit-testable without an emulator. `BookFiles` is only the Android skin over
 * this: it hands us an [InputStream] from the SAF picker and a target [File]
 * inside `filesDir/books/`, and we decide whether anything is allowed to stay
 * on disk.
 *
 * An attached book is untrusted input. Two ideas run through everything here:
 * the archive's own headers are a *fast reject*, never the guarantee (a crafted
 * zip lies about its sizes, so every ceiling is enforced again against a running
 * byte counter while the bytes are actually read); and a partial file is never
 * left behind — the instant a counter trips, the target is deleted.
 * // PT: o portão de importação — limites, nomes de entradas e bytes mágicos,
 * em código puro para ser testável; os cabeçalhos do zip são só uma rejeição
 * rápida, a contagem real é que manda.
 */
object BookImport {

    /** The ceilings from the Security model. Overridable so the tests can trip a
     *  counter without writing half a gigabyte. // PT: limites (parametrizáveis
     *  para os testes). */
    data class Limits(
        val maxFileBytes: Long = 200L * 1024 * 1024,        // refuse rather than fill storage
        val maxTotalUncompressed: Long = 500L * 1024 * 1024, // zip bomb
        val maxEntryBytes: Long = 50L * 1024 * 1024,        // one huge chapter
        val maxEntries: Int = 10_000,                        // zip bomb by entry count
        val maxRatio: Long = 100,                            // per entry and overall
        /** Ratios only mean anything above this size: a 200-byte XHTML file can
         *  legitimately deflate to almost nothing. // PT: rácios só contam acima
         *  deste tamanho. */
        val ratioFloorBytes: Long = 64L * 1024,
    ) {
        companion object { val DEFAULT = Limits() }
    }

    /** Why an import was refused — each maps to one user-facing sentence.
     *  // PT: motivo da recusa; cada um tem uma frase própria. */
    enum class Rejection { UNSUPPORTED, TOO_LARGE, CORRUPT, DRM }

    class RejectedException(val reason: Rejection) : IOException(reason.name)

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK"
    private const val EPUB_MIMETYPE = "application/epub+zip"
    private const val ENCRYPTION_ENTRY = "META-INF/encryption.xml"

    /** How many header bytes [sniffMagic] needs. */
    const val MAGIC_BYTES = 8

    /**
     * "pdf" / "epub" / null from the first bytes alone — extensions are a claim,
     * magic bytes are evidence. A zip signature only makes a file an EPUB
     * *candidate*; [storeGuarded] settles it with the `mimetype` entry.
     * // PT: cheira o tipo pelos bytes mágicos, não pela extensão.
     */
    fun sniffMagic(header: ByteArray): String? = when {
        header.startsWith(PDF_MAGIC) -> "pdf"
        header.startsWith(ZIP_MAGIC) -> "epub"
        else -> null
    }

    /**
     * An entry name is *validated, never resolved* — we never build a path from
     * it, so there is no directory for a traversal to escape into; this simply
     * refuses archives that were built to try. // PT: nomes validados, nunca
     * transformados em caminhos.
     */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() &&
            !name.startsWith("/") &&
            !name.contains("..") &&
            !name.contains('\\') &&
            !name.contains('\u0000') &&
            !name.matches(Regex("^[A-Za-z]:.*")) &&
            !File(name).isAbsolute

    /**
     * Copies [input] into [target] and validates what landed there. Returns the
     * settled kind ("pdf" / "epub"). On any refusal the target is deleted before
     * the exception leaves — a rejected import never leaves bytes behind.
     * // PT: copia e valida; em caso de recusa apaga o ficheiro parcial.
     */
    fun storeGuarded(input: InputStream, target: File, limits: Limits = Limits.DEFAULT): String {
        try {
            val header = ByteArray(MAGIC_BYTES)
            val headerLen = input.readFully(header)
            val kind = sniffMagic(header.copyOf(headerLen)) ?: throw RejectedException(Rejection.UNSUPPORTED)

            // The running counter is the guarantee: nothing about the source's
            // declared length is trusted. // PT: o contador é que manda.
            var written = 0L
            target.outputStream().buffered().use { out ->
                out.write(header, 0, headerLen)
                written += headerLen
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    written += n
                    if (written > limits.maxFileBytes) throw RejectedException(Rejection.TOO_LARGE)
                    out.write(buf, 0, n)
                }
            }
            if (written == 0L) throw RejectedException(Rejection.CORRUPT)

            when (kind) {
                "pdf" -> if (!looksLikePdf(target)) throw RejectedException(Rejection.CORRUPT)
                else -> ZipFile(target).use { validateEpub(it, written, limits) }
            }
            return kind
        } catch (e: Throwable) {
            target.delete()
            // A zip that java.util.zip itself can't open is malformed, not hostile
            // in any interesting way — same friendly message either way.
            // // PT: se nem o zip abre, é ficheiro danificado.
            throw when (e) {
                is RejectedException -> e
                is StackOverflowError, is OutOfMemoryError -> RejectedException(Rejection.CORRUPT)
                is IOException -> RejectedException(Rejection.CORRUPT)
                else -> e
            }
        }
    }

    /**
     * A PDF must open with `%PDF-` *and* close with `%%EOF` in its tail. This
     * catches the obvious "text file renamed .pdf" and the `%PDF-`-prefixed
     * decoy; the real gate is that the file must also open in the `:reader`
     * process before it is kept. // PT: cabeçalho e fim de ficheiro; o teste
     * verdadeiro é abrir no processo :reader.
     */
    fun looksLikePdf(file: File): Boolean {
        if (file.length() < PDF_MAGIC.size + 5) return false
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(PDF_MAGIC.size)
            raf.readFully(head)
            if (!head.startsWith(PDF_MAGIC)) return false
            // Trailing junk after %%EOF is common enough that the marker is looked
            // for in the last few KB rather than exactly at the end.
            val tailLen = minOf(4096L, raf.length()).toInt()
            raf.seek(raf.length() - tailLen)
            val tail = ByteArray(tailLen)
            raf.readFully(tail)
            return tail.indexOfSequence("%%EOF".toByteArray(Charsets.US_ASCII)) >= 0
        }
    }

    /**
     * Every EPUB rule in one pass over the archive: entry count, entry names, the
     * `mimetype` proof, DRM, and then the sizes — first as declared in the
     * central directory (cheap), then against the bytes that actually inflate
     * (the guarantee). Nothing is extracted to disk. // PT: validação completa do
     * EPUB; nada é extraído para disco.
     */
    fun validateEpub(zip: ZipFile, fileBytes: Long, limits: Limits = Limits.DEFAULT) {
        val entries = mutableListOf<ZipEntry>()
        val listing = zip.entries()
        while (listing.hasMoreElements()) {
            val e = listing.nextElement()
            if (entries.size >= limits.maxEntries) throw RejectedException(Rejection.TOO_LARGE)
            if (!isSafeEntryName(e.name)) throw RejectedException(Rejection.CORRUPT)
            entries += e
        }

        // Declared sizes: a fast reject before we spend time inflating anything.
        // // PT: tamanhos declarados — rejeição rápida.
        var declaredTotal = 0L
        for (e in entries) {
            val size = e.size
            if (size < 0) continue // unknown; the running counter covers it
            if (size > limits.maxEntryBytes) throw RejectedException(Rejection.TOO_LARGE)
            if (overRatio(size, e.compressedSize, limits)) throw RejectedException(Rejection.TOO_LARGE)
            declaredTotal += size
            if (declaredTotal > limits.maxTotalUncompressed) throw RejectedException(Rejection.TOO_LARGE)
        }

        // Proof it is an EPUB and not just a zip: the mimetype entry, exactly.
        val mimetype = zip.getEntry("mimetype") ?: throw RejectedException(Rejection.UNSUPPORTED)
        // Read a bounded slice — `readNBytes` is Java 9 and the app runs from
        // API 26, so the loop is the portable one. // PT: leitura limitada, sem
        // APIs recentes de Java.
        val declared = zip.getInputStream(mimetype).use { s ->
            val room = ByteArray(EPUB_MIMETYPE.length + 8)
            val n = s.readFully(room)
            String(room, 0, n, Charsets.US_ASCII)
        }.trim()
        if (declared != EPUB_MIMETYPE) throw RejectedException(Rejection.UNSUPPORTED)

        // DRM: refuse rather than render mojibake later.
        if (zip.getEntry(ENCRYPTION_ENTRY) != null) throw RejectedException(Rejection.DRM)

        // The real sizes. Read every entry through a discard buffer, counting as
        // we go — a crafted archive can understate both its per-entry and its
        // total uncompressed size, and only this pass sees through that.
        // // PT: contagem real ao inflar — é aqui que os cabeçalhos mentirosos caem.
        var total = 0L
        val buf = ByteArray(64 * 1024)
        for (e in entries) {
            if (e.isDirectory) continue
            var entryBytes = 0L
            zip.getInputStream(e).use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    entryBytes += n
                    total += n
                    if (entryBytes > limits.maxEntryBytes) throw RejectedException(Rejection.TOO_LARGE)
                    if (total > limits.maxTotalUncompressed) throw RejectedException(Rejection.TOO_LARGE)
                    if (overRatio(entryBytes, e.compressedSize, limits)) throw RejectedException(Rejection.TOO_LARGE)
                }
            }
        }
        if (overRatio(total, fileBytes, limits)) throw RejectedException(Rejection.TOO_LARGE)
    }

    /** True when [uncompressed] is more than [Limits.maxRatio] times [compressed]
     *  and big enough for the ratio to mean anything. */
    private fun overRatio(uncompressed: Long, compressed: Long, limits: Limits): Boolean =
        uncompressed > limits.ratioFloorBytes && compressed > 0 &&
            uncompressed / compressed > limits.maxRatio

    // ── small byte helpers ────────────────────────────────────
    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.indexOfSequence(needle: ByteArray): Int {
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Reads up to [into].size bytes, looping over short reads; returns the count. */
    private fun InputStream.readFully(into: ByteArray): Int {
        var off = 0
        while (off < into.size) {
            val n = read(into, off, into.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}
