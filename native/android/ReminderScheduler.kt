package com.pauta.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the daily habit/reflection reminder alarms via AlarmManager so they
 * fire even when the app (and its WebView) is fully closed — the JS-only
 * reminder loop in extras.jsx only runs while the page is open, so without this
 * a closed app never reminded the user of anything.
 *
 * State (enabled + the two HH:mm times + the localized title/body to display) is
 * mirrored to SharedPreferences so that:
 *   - the fired alarm can re-arm itself for the next day (exact alarms are
 *     one-shot; AlarmManager.setRepeating is inexact on modern Android), and
 *   - BootReceiver can re-arm everything after a reboot or app update (alarms do
 *     not survive either).
 *
 * Exactness: we use setExactAndAllowWhileIdle when allowed (Android 12+ gates it
 * behind SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM) and fall back to
 * setAndAllowWhileIdle (inexact, Doze-friendly) otherwise, so a reminder still
 * arrives — just not necessarily to the minute. Honest limitation: aggressive
 * OEM battery managers (e.g. MIUI/Xiaomi) can still delay or drop background
 * alarms unless the app is exempt from battery optimization / allowed to autostart.
 */
object ReminderScheduler {
    const val PREFS = "pauta_reminders"
    const val K_ENABLED = "enabled"
    // A count-specific habits body pushed by JS while the app is open, plus the
    // dayKey it was computed for — so the app-closed reminder can say "3 tides
    // left today" yet never show a stale count from a previous day.
    const val K_HABITS_DYN_BODY = "habits_dyn_body"
    const val K_HABITS_DYN_DAY = "habits_dyn_day"

    // Each reminder kind: a stable key + a distinct alarm request code.
    enum class Kind(val key: String, val requestCode: Int, val timePref: String,
                    val titlePref: String, val bodyPref: String, val tag: String) {
        PLANNER("planner", 103, "planner_time", "planner_title", "planner_body", "pauta-planner"),
        HABITS("habits", 101, "habits_time", "habits_title", "habits_body", "pauta-habits"),
        REFLECTION("reflection", 102, "reflection_time", "reflection_title", "reflection_body", "pauta-reflection"),
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Persist the latest settings (so re-arm on fire / boot has what it needs). */
    fun save(context: Context, enabled: Boolean,
             habitsTime: String?, reflectionTime: String?, plannerTime: String?,
             habitsTitle: String?, habitsBody: String?,
             reflectionTitle: String?, reflectionBody: String?,
             plannerTitle: String?, plannerBody: String?) {
        prefs(context).edit()
            .putBoolean(K_ENABLED, enabled)
            .putString(Kind.HABITS.timePref, habitsTime)
            .putString(Kind.REFLECTION.timePref, reflectionTime)
            .putString(Kind.PLANNER.timePref, plannerTime)
            .putString(Kind.HABITS.titlePref, habitsTitle)
            .putString(Kind.HABITS.bodyPref, habitsBody)
            .putString(Kind.REFLECTION.titlePref, reflectionTitle)
            .putString(Kind.REFLECTION.bodyPref, reflectionBody)
            .putString(Kind.PLANNER.titlePref, plannerTitle)
            .putString(Kind.PLANNER.bodyPref, plannerBody)
            .apply()
    }

    /** (Re)schedule both kinds from persisted state, or cancel if disabled. */
    fun rescheduleAll(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(K_ENABLED, false)) { cancelAll(context); return }
        scheduleKind(context, Kind.PLANNER, p.getString(Kind.PLANNER.timePref, null))
        scheduleKind(context, Kind.HABITS, p.getString(Kind.HABITS.timePref, null))
        scheduleKind(context, Kind.REFLECTION, p.getString(Kind.REFLECTION.timePref, null))
    }

    fun cancelAll(context: Context) {
        Kind.values().forEach { cancel(context, it) }
    }

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

    /** True if the OS will currently let us set exact alarms. */
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
            if (canExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // No exact-alarm permission → inexact but still wakes from Doze.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, kind: Kind) {
        alarmManager(context).cancel(pendingIntent(context, kind))
    }

    /** Next occurrence of hh:mm: today if still ahead, otherwise tomorrow. */
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

    /** Localized title/body persisted for a kind (set from JS via the plugin). */
    fun textFor(context: Context, kind: Kind): Pair<String, String> {
        val p = prefs(context)
        val title = p.getString(kind.titlePref, "Pauta") ?: "Pauta"
        var body = p.getString(kind.bodyPref, "") ?: ""
        // Habits: prefer the count-specific body JS pushed — but only if it was
        // computed TODAY. An empty body for today means nothing is pending, so
        // the receiver suppresses that reminder (no false nag). If the app wasn't
        // opened today we have no fresh count, so we keep the generic nudge.
        if (kind == Kind.HABITS && (p.getString(K_HABITS_DYN_DAY, "") ?: "") == todayKey()) {
            body = p.getString(K_HABITS_DYN_BODY, "") ?: ""
        }
        return Pair(title, body)
    }

    /** Store the JS-computed, count-specific habits body + the day it's for. */
    fun saveHabitsDynamicBody(context: Context, body: String?, dayKey: String?) {
        prefs(context).edit()
            .putString(K_HABITS_DYN_BODY, body ?: "")
            .putString(K_HABITS_DYN_DAY, dayKey ?: "")
            .apply()
    }

    /** Local-date "YYYY-MM-DD", matching the app's dayKey format. */
    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun kindFromKey(key: String?): Kind? = Kind.values().firstOrNull { it.key == key }
}
