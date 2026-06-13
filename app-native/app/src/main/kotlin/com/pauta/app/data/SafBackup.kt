package com.pauta.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * B1: writes auto-backup JSON into a user-chosen folder via the Storage Access
 * Framework, using raw [DocumentsContract] (no extra dependency — only the
 * `androidx.work` artifact the task allows). The folder is a persisted tree URI
 * the user picked with `ACTION_OPEN_DOCUMENT_TREE`; unlike the app-private
 * filesDir copies, files here survive an uninstall or a wiped device. Every call
 * is best-effort: if the folder was deleted or its permission revoked it returns
 * false rather than throwing, so a missing folder never breaks the backup (the
 * filesDir fallback still ran). // PT: escreve a cópia numa pasta escolhida pelo
 * utilizador (sobrevive à desinstalação); falha em silêncio se a pasta sumir.
 */
object SafBackup {

    private const val PREFIX = "pauta-auto-"
    private const val SUFFIX = ".json"
    private const val KEEP = 5

    /** Write [json] as [fileName] into the SAF [treeUriString], then prune the
     *  folder to the last [KEEP] auto-backups. Returns true on success. */
    fun write(context: Context, treeUriString: String, fileName: String, json: String): Boolean =
        runCatching {
            val resolver = context.contentResolver
            val tree = Uri.parse(treeUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)

            val file = DocumentsContract.createDocument(resolver, parent, "application/json", fileName)
                ?: return false
            resolver.openOutputStream(file)?.use { it.write(json.toByteArray()) } ?: return false

            prune(context, tree, treeDocId)
            true
        }.getOrDefault(false)

    /** Delete our older `pauta-auto-*.json` files in the folder, keeping the most
     *  recent [KEEP]. Mirrors the filesDir pruning so the folder doesn't grow
     *  without bound. Only touches files we wrote. */
    private fun prune(context: Context, tree: Uri, treeDocId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        val ours = mutableListOf<Pair<String, Long>>() // documentId to lastModified
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) continue
                ours += c.getString(0) to c.getLong(2)
            }
        }
        ours.sortedByDescending { it.second }
            .drop(KEEP)
            .forEach { (docId, _) ->
                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                    )
                }
            }
    }
}
