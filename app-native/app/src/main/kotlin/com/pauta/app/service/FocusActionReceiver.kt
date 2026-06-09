package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pauta.app.PautaApplication
import kotlinx.coroutines.launch

/**
 * Handles the focus notification's Pause / Conclude buttons. Runs the data
 * action on the process-wide app scope (works with no Activity), then stops the
 * service — so the controls behave correctly even when the app is backgrounded
 * or its UI is gone. // PT: trata os botões Pausar/Concluir da notificação,
 * mesmo com a app em segundo plano.
 */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PautaApplication ?: return
        val repo = app.repository
        when (intent.action) {
            ACTION_PAUSE -> app.appScope.launch { repo.pauseActive() }
            ACTION_CONCLUDE -> app.appScope.launch { repo.concludeActive("") }
            else -> return
        }
        // Either action leaves no running block, so tear the notification down.
        FocusServiceController.stop(context)
    }

    companion object {
        const val ACTION_PAUSE = "com.pauta.app.FOCUS_PAUSE"
        const val ACTION_CONCLUDE = "com.pauta.app.FOCUS_CONCLUDE"
    }
}
