package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pauta.app.PautaApplication
import kotlinx.coroutines.launch

/**
 * Fires when a daily reminder alarm goes off: re-arms for the next day (exact
 * alarms are one-shot), then posts the kind's notification. Planner/reflection
 * post synchronously; the habits reminder first reads today's pending tides from
 * Room (off the main thread, holding the broadcast open) so it can list them with
 * quick "done" actions. // PT: dispara o lembrete — re-arma para amanhã e mostra a
 * notificação (as marés leem o estado de hoje antes de mostrar).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching {
            ReminderScheduler.Kind.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: "")
        }.getOrNull() ?: return

        // Re-arm the next occurrence first — independent of what we post below.
        ReminderScheduler.reschedule(context)

        when (kind) {
            ReminderScheduler.Kind.PLANNER -> ReminderNotifications.postPlanner(context)
            ReminderScheduler.Kind.REFLECTION -> ReminderNotifications.postReflection(context, savedText = null)
            ReminderScheduler.Kind.HABITS -> {
                val app = context.applicationContext as? PautaApplication ?: return
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        ReminderNotifications.postHabits(context, ReminderNotifications.pendingTides(app.repository))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
