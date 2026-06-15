package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pauta.app.PautaApplication
import kotlinx.coroutines.launch

/**
 * D2: fires when a per-habit reminder alarm goes off. Re-arms the whole per-habit
 * set for the next day (exact alarms are one-shot), then — off the main thread,
 * holding the broadcast open — posts the tide's reminder ONLY if it's still
 * actionable today: due on its schedule and not already done or respiro'd. A tide
 * that's off-schedule, finished, or no longer active stays quiet, and any lingering
 * reminder from a previous day is cleared. // PT: dispara o lembrete de uma maré —
 * re-arma para amanhã e só notifica se a maré ainda está por fazer hoje.
 */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(HabitReminderScheduler.EXTRA_HABIT_ID) ?: return
        // Re-arm the next occurrence first — independent of whether we post below.
        HabitReminderScheduler.reschedule(context)

        val app = context.applicationContext as? PautaApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                // The same "still open today" slice the Hoje strip and the global
                // digest derive (DayState.EMPTY): if this tide is in it, it's due and
                // unmarked → nudge; otherwise stay silent and clear any stale reminder.
                // // PT: só notifica se a maré está na fatia "por fazer hoje".
                val tide = ReminderNotifications.pendingTides(app.repository)
                    .firstOrNull { it.habit.id == habitId }
                if (tide != null) ReminderNotifications.postHabitReminder(context, tide.habit)
                else ReminderNotifications.cancelHabitReminder(context, habitId)
            } finally {
                pending.finish()
            }
        }
    }
}
