package com.pauta.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
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
 * Fires when a scheduled reminder alarm goes off — even with the app closed.
 * Posts the reminder on a HIGH-importance channel and re-arms the same kind for
 * the next day.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val kind = ReminderScheduler.kindFromKey(intent.getStringExtra(EXTRA_KIND)) ?: return

        val (title, body) = ReminderScheduler.textFor(context, kind)
        if (body.isNotEmpty()) postNotification(context, kind, title, body)

        val p = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(ReminderScheduler.K_ENABLED, false)) {
            ReminderScheduler.scheduleKind(context, kind, p.getString(kind.timePref, null))
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de hábitos pendentes e da reflexão da noite"
                setShowBadge(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun postNotification(context: Context, kind: ReminderScheduler.Kind, title: String, body: String) {
        ensureChannel(context)
        val notifId = kind.tag.hashCode()

        val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
        val launchPi = PendingIntent.getActivity(context, notifId, launch, piFlags)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(launchPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notif)
        } catch (e: SecurityException) { /* permission revoked */ }
    }

    companion object {
        const val ACTION_FIRE = "com.pauta.app.REMINDER_FIRE"
        const val EXTRA_KIND  = "kind"
        private const val CHANNEL_ID = "pauta_reminders_v2"
    }
}
