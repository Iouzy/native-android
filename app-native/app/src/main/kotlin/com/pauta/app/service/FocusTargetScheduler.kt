package com.pauta.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * C2: schedules (and cancels) the one-shot alarm that fires the focus "target
 * reached" alert, so it reaches the lock screen with the app backgrounded. Exact
 * when the OS allows it (Android 12+ gates exact alarms behind
 * SCHEDULE_EXACT_ALARM), else the Doze-friendly inexact fallback — mirroring
 * [ReminderScheduler]. The single PendingIntent (fixed request code) makes
 * schedule and cancel refer to the same alarm. // PT: agenda/cancela o alarme
 * único do aviso de alvo de foco atingido.
 */
object FocusTargetScheduler {
    const val ACTION = "com.pauta.app.FOCUS_TARGET"
    private const val REQUEST = 1102

    fun schedule(context: Context, triggerAtMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FocusTargetReceiver::class.java).setAction(ACTION)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
    }
}
