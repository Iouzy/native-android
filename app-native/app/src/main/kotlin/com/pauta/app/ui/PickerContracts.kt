package com.pauta.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri

/**
 * N-leftover · the document picker opens somewhere with files in it.
 *
 * `OpenDocument` lands on "Recent", which on a clean device is **empty** — so the
 * first thing a new user sees after tapping "Anexar ficheiro" is a blank screen,
 * and the book they just downloaded is two taps further on. `EXTRA_INITIAL_URI`
 * asks the picker to start in Downloads instead.
 *
 * It is a *hint*: the system picker may ignore it, and does on some OEM builds,
 * which is why nothing depends on it. Everything else about the contract — the
 * MIME filter, the persistable grant — is `OpenDocument`'s own.
 *
 * // PT: o selector abria em "Recentes", vazio num telemóvel novo; passa a abrir
 * nas transferências. É uma sugestão — o sistema pode ignorá-la, e nada depende
 * disso.
 */
class OpenDocumentInDownloads : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            downloadsUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        return intent
    }

    /** The public Downloads folder as the external-storage documents provider
     *  addresses it. A literal because there is no API that returns it, and it is
     *  only ever a hint. // PT: a pasta de transferências como o provider a
     *  endereça; é literal porque não há API que a devolva. */
    private fun downloadsUri(): Uri? =
        runCatching { DOWNLOADS.toUri() }.getOrNull()

    private companion object {
        const val DOWNLOADS = "content://com.android.externalstorage.documents/document/primary%3ADownload"
    }
}
