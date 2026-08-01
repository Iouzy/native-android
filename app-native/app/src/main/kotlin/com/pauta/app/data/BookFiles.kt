package com.pauta.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.domain.BookImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * native-only (R2): where an attached book lives on disk, and the only door it
 * can come in through. One file per book at `filesDir/books/<bookId>.<ext>`,
 * never anywhere else, never written into the `pauta.v4` export — a restored
 * backup brings back the *book*, not the file.
 *
 * The decisions about whether a file may be kept all live in [BookImport] (pure,
 * unit-tested); this object is the Android half: the SAF stream, the display
 * name and the path arithmetic. // PT: armazenamento privado dos ficheiros
 * anexados; as regras de aceitação estão em BookImport.
 */
object BookFiles {

    /** `filesDir/books`, created on demand. */
    fun dir(context: Context): File = File(context.filesDir, "books").apply { mkdirs() }

    fun fileFor(context: Context, bookId: String, ext: String): File = File(dir(context), "$bookId.$ext")

    /** True when [path] really is one of our own book files — cheap, and it closes
     *  off a tampered database row pointing the reader elsewhere. // PT: confirma
     *  que o caminho está dentro de filesDir/books. */
    fun isOurs(context: Context, path: String): Boolean = runCatching {
        File(path).canonicalPath.startsWith(dir(context).canonicalPath + File.separator)
    }.getOrDefault(false)

    /**
     * Copies a picked SAF uri into private storage, refusing anything the import
     * gate doesn't like. Returns the stored file, or null when the copy failed
     * for mundane reasons (the provider vanished, no stream). A refusal on
     * format or safety grounds throws [BookImport.RejectedException] so the UI
     * can say *why*; either way nothing is left on disk.
     * // PT: copia o ficheiro escolhido para o armazenamento privado; recusas
     * explicam-se com uma exceção, e nunca fica nada meio-escrito.
     */
    suspend fun importFrom(context: Context, uri: Uri, bookId: String): ImportedFile? =
        withContext(Dispatchers.IO) {
            val name = displayName(context, uri)
            // Fast pre-check on the header alone: refuse a 200 MB video before
            // copying a byte of it. // PT: rejeita cedo, antes de copiar.
            val guess = kindOf(context, uri) ?: throw BookImport.RejectedException(BookImport.Rejection.UNSUPPORTED)

            val target = fileFor(context, bookId, guess)
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                ?: return@withContext null
            val kind = stream.use { BookImport.storeGuarded(it, target) }

            // A book keeps exactly one file: swapping a PDF for an EPUB (or back)
            // clears the other extension. // PT: um livro, um ficheiro.
            for (ext in listOf("pdf", "epub")) {
                if (ext != kind) fileFor(context, bookId, ext).delete()
            }
            ImportedFile(path = target.absolutePath, kind = kind, name = name)
        }

    /** Removes a book's attached file, whatever its kind. */
    fun delete(context: Context, book: BookEntity) {
        book.filePath?.let { deleteAt(context, it) }
        // Belt and braces for a row whose path was cleared but whose file wasn't:
        // the naming scheme means we can always find it. // PT: apaga também pelo
        // nome previsível, caso a coluna já esteja vazia.
        for (ext in listOf("pdf", "epub")) fileFor(context, book.id, ext).delete()
    }

    /** Deletes one stored file, but only if it is genuinely ours. */
    fun deleteAt(context: Context, path: String) {
        if (isOurs(context, path)) File(path).delete()
    }

    /**
     * "pdf" / "epub" / null — the magic bytes decide, and a display-name
     * extension, when there is one, has to agree with them. A zip signature only
     * makes a file an EPUB candidate; the `mimetype` entry settles it during the
     * copy. // PT: tipo pelos bytes mágicos, confirmado pela extensão.
     */
    fun kindOf(context: Context, uri: Uri): String? {
        val header = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val buf = ByteArray(BookImport.MAGIC_BYTES)
                var off = 0
                while (off < buf.size) {
                    val n = s.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                buf.copyOf(off)
            }
        }.getOrNull() ?: return null
        val magic = BookImport.sniffMagic(header) ?: return null
        val ext = displayName(context, uri).substringAfterLast('.', "").lowercase()
        return if (ext.isEmpty() || ext == magic) magic else null
    }

    /** The picked document's display name, for the UI. Falls back to something
     *  printable rather than an opaque content:// segment. */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "livro"
    }
}

/** A file that made it through the gate and is now ours. */
data class ImportedFile(val path: String, val kind: String, val name: String)

/**
 * What came of an attach. [Rejected] carries the reason so the form can say
 * which rule the file broke — too big, malformed, or DRM — instead of one
 * shrug for all three. // PT: resultado do anexo; a recusa explica-se.
 */
sealed interface AttachResult {
    data class Ok(val kind: String, val pageCount: Int, val file: ImportedFile) : AttachResult
    data object UnsupportedType : AttachResult
    data object CopyFailed : AttachResult
    data class Rejected(val reason: BookImport.Rejection) : AttachResult
}
