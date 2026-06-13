package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pauta.app.PautaApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * C2: fires when a running block reaches its soft focus target, posting the
 * one-time "target reached" alert. Guards by re-reading the active block — the
 * alarm is cancelled on pause/conclude, so a stray fire after the block ended
 * stays silent. Runs on the process scope, no Activity needed. // PT: dispara ao
 * atingir o alvo de foco; só avisa se ainda houver um bloco a correr.
 */
class FocusTargetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FocusTargetScheduler.ACTION) return
        val app = context.applicationContext as? PautaApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                val block = app.repository.activeBlock().first() ?: return@launch
                FocusNotifications.postTargetReached(context, block.title)
            } finally {
                pending.finish()
            }
        }
    }
}
