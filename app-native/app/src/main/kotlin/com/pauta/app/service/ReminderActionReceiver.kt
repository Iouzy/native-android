package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.pauta.app.PautaApplication
import com.pauta.app.data.PautaRepository
import com.pauta.app.domain.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * C2: handles the actionable reminder notifications' buttons — the reflection
 * direct-reply and the habits "done" actions. Both run their data write on the
 * process-wide app scope (no Activity), then refresh the notification, so they
 * work with the app fully backgrounded — the same pattern as [FocusActionReceiver]
 * and the widget's [ToggleTideAction]. // PT: trata os botões dos lembretes —
 * resposta da reflexão e "feito" das marés — mesmo com a app em segundo plano.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PautaApplication ?: return
        when (intent.action) {
            ReminderNotifications.ACTION_REFLECTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(ReminderNotifications.KEY_REFLECTION)?.toString()?.trim().orEmpty()
                if (text.isBlank()) return
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        val today = DateUtils.todayKey()
                        // Append to whatever's already there so a shade reply never
                        // overwrites a reflection typed in the app. // PT: acrescenta,
                        // nunca sobrepõe o que já foi escrito na app.
                        val existing = app.repository.dayReflection(today).first()
                        val merged = if (existing.isBlank()) text else "$existing\n$text"
                        app.repository.setReflection(today, merged)
                        ReminderNotifications.postReflection(context, savedText = merged)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ReminderNotifications.ACTION_HABIT_DONE -> {
                val id = intent.getStringExtra(ReminderNotifications.EXTRA_HABIT_ID) ?: return
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        markTideDone(app.repository, id)
                        // D2: clear this tide's own per-habit reminder if the tap came
                        // from one (a no-op when it came from the global digest — that
                        // id has no live per-habit notification).
                        ReminderNotifications.cancelHabitReminder(context, id)
                        // Refresh the inbox (drops the completed tide, or clears it
                        // when none remain) and nudge the home-screen widget.
                        ReminderNotifications.postHabits(context, ReminderNotifications.digestTides(app.repository))
                        MaresWidget.refresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /** Mark a tide the way the Hoje strip and the widget do: a countable daily
     *  tide increments toward its target; everything else toggles done. // PT:
     *  marca a maré — contáveis somam até ao alvo; o resto alterna feito. */
    private suspend fun markTideDone(repo: PautaRepository, id: String) {
        val today = DateUtils.todayKey()
        val habit = repo.getHabit(id) ?: return
        if (habit.target != null && habit.cadence == "daily") {
            val current = repo.habitCounts().first().firstOrNull { it.habitId == id && it.dayKey == today }?.count ?: 0
            repo.setHabitCount(id, today, current + 1)
        } else {
            repo.toggleHabitDay(id, today)
        }
    }
}
