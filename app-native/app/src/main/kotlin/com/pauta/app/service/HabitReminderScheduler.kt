package com.pauta.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * D2: per-habit (per-maré) daily reminder alarms. A tide whose form carries a
 * non-blank HH:MM `clock` gets its OWN daily alarm here, separate from the three
 * global reminder kinds in [ReminderScheduler]; when it fires, [HabitReminderReceiver]
 * checks whether the tide is actually actionable that day (due on its schedule and
 * not yet done/respiro'd) before posting, so off-days and finished tides stay quiet.
 *
 * The active tides' (id → clock) projection plus the master reminders flag are
 * mirrored to SharedPreferences whenever they change, so the alarms can be re-armed
 * at process start / after a reboot without loading Room — the same trick
 * [ReminderScheduler] uses for the global ones. Exact alarms are one-shot, so the
 * receiver re-arms the whole set for the next day on every fire.
 *
 * Request codes derive from the habit id's hashCode — a space distinct from the
 * global kinds' fixed small codes (101–103); and each per-habit alarm targets this
 * receiver by class, so it can never collide with a global alarm even on an unlucky
 * hash. // PT: lembretes diários por maré — espelha (id, hora) + interruptor em
 * SharedPreferences para re-armar sem Room; cada disparo re-arma o conjunto.
 */
object HabitReminderScheduler {
    private const val PREFS = "pauta_habit_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_IDS = "ids"
    private const val CLOCK_PREFIX = "clock_"
    const val ACTION_FIRE = "com.pauta.app.HABIT_REMINDER_FIRE"
    const val EXTRA_HABIT_ID = "habitId"

    private val HHMM = Regex("^\\d{1,2}:\\d{2}$")

    /**
     * Persist the master reminders flag + the active tides' (id → clock) map (only
     * well-formed, non-blank clocks survive). Alarms for ids that dropped their
     * clock — or whose tide was archived/removed — are cancelled here, since
     * [reschedule] only walks the ids it still knows about. // PT: guarda o estado
     * e cancela os alarmes das marés que deixaram de ter hora.
     */
    fun save(context: Context, enabled: Boolean, clocks: Map<String, String>) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val valid = clocks.filterValues { HHMM.matches(it) }
        val oldIds = sp.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val am = context.getSystemService(AlarmManager::class.java)
        (oldIds - valid.keys).forEach { am?.cancel(pendingIntent(context, it)) }
        val editor = sp.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putStringSet(KEY_IDS, valid.keys)
        oldIds.forEach { editor.remove(CLOCK_PREFIX + it) }
        valid.forEach { (id, clock) -> editor.putString(CLOCK_PREFIX + id, clock) }
        editor.apply()
    }

    /** Cancel + re-arm every per-habit alarm from the saved state (after a save, on
     *  boot, and on each fire). With reminders disabled every alarm is just
     *  cancelled. // PT: cancela e re-arma todos os alarmes por maré. */
    fun reschedule(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = sp.getBoolean(KEY_ENABLED, false)
        val ids = sp.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (id in ids) {
            val pi = pendingIntent(context, id)
            am.cancel(pi)
            if (!enabled) continue
            val clock = sp.getString(CLOCK_PREFIX + id, "") ?: ""
            if (!HHMM.matches(clock)) continue
            val trigger = nextTrigger(clock) ?: continue
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntent(context: Context, habitId: String): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_HABIT_ID, habitId)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, habitId.hashCode(), intent, flags)
    }

    /** The next future epoch-ms for an HH:mm clock time — mirrors the helper
     *  [ReminderScheduler] keeps private for the global alarms. */
    private fun nextTrigger(hhmm: String): Long? {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
