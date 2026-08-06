package com.pauta.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules the daily reminder alarms (planner / habits / reflection) via
 * AlarmManager so they fire with the app fully closed. State lives in
 * SharedPreferences so the fired alarm can re-arm itself for the next day
 * (exact alarms are one-shot) and [BootReceiver] can restore them after a reboot
 * or app update. Uses setExactAndAllowWhileIdle when allowed (Android 12+ gates
 * it behind SCHEDULE_EXACT_ALARM), else the inexact, Doze-friendly fallback.
 * // PT: agenda os lembretes diários via AlarmManager, com re-arme no disparo e
 * no arranque do sistema.
 */
object ReminderScheduler {
    private const val PREFS = "pauta_reminders"
    private const val CHANNEL = "pauta_reminders"
    const val ACTION_FIRE = "com.pauta.app.REMINDER_FIRE"
    const val EXTRA_KIND = "kind"

    enum class Kind(val code: Int, val timeKey: String) {
        PLANNER(103, "plannerTime"),
        HABITS(101, "habitsTime"),
        REFLECTION(102, "reflectionTime"),

        // L10: a third caller, not a new system. The scheduling machinery is
        // built, tested and boot-persistent; reading joins it.
        // // PT: mais um utilizador da máquina que já existe.
        READING(104, "readingTime"),
    }

    /**
     * L11 · the lens, for components that run with the app closed — the QS tile
     * reads it when the shade opens, which may be long before Room has loaded
     * anything. It lives here because this is already the file background
     * components read their state from, and L10 had just put `bookMode` in it.
     * // PT: a lente, para quem corre com a app fechada; já se guardava aqui.
     */
    fun savedBookMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("bookMode", false)

    /** The persisted app language ("pt"/"en") — readable at process start,
     *  before Room/prefs load, so background components speak the right one. */
    fun savedLang(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "pt") ?: "pt"

    /**
     * Persist the reminder settings (called whenever prefs change).
     *
     * L10 adds [reading] and [bookMode]. The reading reminder has **two** gates —
     * its own switch and the lens — because a planner user has not asked to be
     * reminded to read, and a preference they cannot see should not be able to
     * wake their phone. Kept in SharedPreferences with the rest so the alarm can
     * re-arm itself and survive a reboot without Room.
     * // PT: o lembrete de leitura tem dois interruptores — o seu e a lente.
     */
    fun save(
        context: Context,
        enabled: Boolean,
        planner: String,
        habits: String,
        reflection: String,
        lang: String,
        reading: Boolean = false,
        readingTime: String = "",
        bookMode: Boolean = false,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .putString(Kind.PLANNER.timeKey, planner)
            .putString(Kind.HABITS.timeKey, habits)
            .putString(Kind.REFLECTION.timeKey, reflection)
            .putString(Kind.READING.timeKey, readingTime)
            .putBoolean("reading", reading)
            .putBoolean("bookMode", bookMode)
            .putString("lang", lang)
            .apply()
    }

    /** Cancel + re-arm every alarm from the saved settings. */
    fun reschedule(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = sp.getBoolean("enabled", false)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // L10: the reading reminder is gated on its own switch and the lens, not on
        // the master reminders toggle — someone can want a nudge to read without
        // wanting a plan-your-day notification. // PT: o lembrete de leitura tem os
        // seus próprios interruptores.
        val reading = sp.getBoolean("reading", false) && sp.getBoolean("bookMode", false)
        for (kind in Kind.entries) {
            am.cancel(pendingIntent(context, kind))
            if (kind == Kind.READING) {
                if (!reading) continue
            } else if (!enabled) {
                continue
            }
            val hhmm = sp.getString(kind.timeKey, "") ?: ""
            if (!Regex("^\\d{1,2}:\\d{2}$").matches(hhmm)) continue
            val trigger = nextTrigger(hhmm) ?: continue
            val pi = pendingIntent(context, kind)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    private fun pendingIntent(context: Context, kind: Kind): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_KIND, kind.name)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, kind.code, intent, flags)
    }

    /** The next future epoch-ms for an HH:mm clock time. */
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

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            // Re-creating just refreshes the channel's display name, so it
            // follows the saved language. // PT: recriar só atualiza o nome.
            val name = if (savedLang(context) == "en") "Reminders" else "Lembretes"
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    fun channelId(): String = CHANNEL

    /** Localized title/body per reminder kind (lang read from saved settings). */
    fun titleBody(context: Context, kind: Kind): Pair<String, String> {
        val en = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "pt") == "en"
        return when (kind) {
            Kind.PLANNER -> if (en) "Plan your day" to "What matters today?" else "Planeie o seu dia" to "O que importa hoje?"
            Kind.HABITS -> if (en) "Your tides" to "You have tides to complete today." else "As suas marés" to "Tens marés por completar hoje."
            Kind.REFLECTION -> if (en) "Nightly reflection" to "What was worth it today?" else "Reflexão da noite" to "O que valeu hoje?"
            // L10: the generic form. The receiver names the book instead when
            // there is exactly one being read — naming one of four would be
            // picking a favourite. // PT: a forma genérica; o receptor nomeia o
            // livro quando há só um.
            Kind.READING -> if (en) "Time to read" to "A few pages?" else "Hora de ler" to "Umas páginas?"
        }
    }
}
