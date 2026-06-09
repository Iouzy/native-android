package com.pauta.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pauta.app.MainActivity
import com.pauta.app.R

/**
 * Fires when a daily reminder alarm goes off: posts the notification, then
 * re-arms all alarms for the next day (exact alarms are one-shot). // PT: dispara
 * o lembrete, mostra a notificação e re-arma para o dia seguinte.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching {
            ReminderScheduler.Kind.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: "")
        }.getOrNull() ?: return

        ReminderScheduler.ensureChannel(context)
        val (title, body) = ReminderScheduler.titleBody(context, kind)

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            context, kind.code,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )

        val notif = NotificationCompat.Builder(context, ReminderScheduler.channelId())
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .build()

        // Same-kind reminders replace each other (one id per kind).
        runCatching { NotificationManagerCompat.from(context).notify(kind.code, notif) }

        // Re-arm for the next occurrence.
        ReminderScheduler.reschedule(context)
    }
}
