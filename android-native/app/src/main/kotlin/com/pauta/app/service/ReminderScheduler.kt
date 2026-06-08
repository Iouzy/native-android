package com.pauta.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules daily habit/reflection/planner reminder alarms via AlarmManager so
 * they fire even with the app fully closed. State is mirrored to
 * SharedPreferences so a fired alarm can re-arm itself for the next day and
 * BootReceiver can restore everything after a reboot/update.
 */
object ReminderScheduler {
    const val PREFS = "pauta_reminders"
    const val K_ENABLED = "enabled"

    enum class Kind(val key: String, val requestCode: Int, val timePref: String,
                    val titlePref: String, val bodyPref: String, val tag: String) {
        PLANNER("planner", 103, "planner_time", "planner_title", "planner_body", "pauta-planner"),
        HABITS("habits", 101, "habits_time", "habits_title", "habits_body", "pauta-habits"),
        REFLECTION("reflection", 102, "reflection_time", "reflection_title", "reflection_body", "pauta-reflection"),
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, enabled: Boolean,
             plannerTime: String?, habitsTime: String?, reflectionTime: String?) {
        prefs(context).edit()
            .putBoolean(K_ENABLED, enabled)
            .putString(Kind.PLANNER.timePref, plannerTime)
            .putString(Kind.HABITS.timePref, habitsTime)
            .putString(Kind.REFLECTION.timePref, reflectionTime)
            .putString(Kind.PLANNER.titlePref, "Pauta")
            .putString(Kind.PLANNER.bodyPref, "Planeie o seu dia.")
            .putString(Kind.HABITS.titlePref, "Marés")
            .putString(Kind.HABITS.bodyPref, "Tem marés por completar hoje.")
            .putString(Kind.REFLECTION.titlePref, "Reflexão")
            .putString(Kind.REFLECTION.bodyPref, "O que valeu a pena hoje?")
            .apply()
        rescheduleAll(context)
    }

    fun rescheduleAll(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(K_ENABLED, false)) { cancelAll(context); return }
        scheduleKind(context, Kind.PLANNER, p.getString(Kind.PLANNER.timePref, null))
        scheduleKind(context, Kind.HABITS, p.getString(Kind.HABITS.timePref, null))
        scheduleKind(context, Kind.REFLECTION, p.getString(Kind.REFLECTION.timePref, null))
    }

    fun cancelAll(context: Context) = Kind.values().forEach { cancel(context, it) }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, kind: Kind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_KIND, kind.key)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, kind.requestCode, intent, flags)
    }

    fun canExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager(context).canScheduleExactAlarms() else true

    fun scheduleKind(context: Context, kind: Kind, hhmm: String?) {
        if (hhmm.isNullOrBlank() || !hhmm.contains(":")) { cancel(context, kind); return }
        val parts = hhmm.split(":")
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val triggerAt = nextTrigger(hour, minute)
        val am = alarmManager(context)
        val pi = pendingIntent(context, kind)
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, kind: Kind) = alarmManager(context).cancel(pendingIntent(context, kind))

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    fun textFor(context: Context, kind: Kind): Pair<String, String> {
        val p = prefs(context)
        val title = p.getString(kind.titlePref, "Pauta") ?: "Pauta"
        val body = p.getString(kind.bodyPref, "") ?: ""
        return Pair(title, body)
    }

    fun kindFromKey(key: String?): Kind? = Kind.values().firstOrNull { it.key == key }
}
