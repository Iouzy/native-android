package com.pauta.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pauta.app.MainActivity
import com.pauta.app.R

/**
 * Foreground service that owns the ongoing focus-timer notification.
 *
 * Ported from the Capacitor build's native plugin — identical behaviour, but it
 * is now driven directly by the Compose ViewModel (via startForegroundService)
 * rather than a JS bridge. The notification mirrors the in-app active card: the
 * block title, a "Pauta" app label, an accent tint, and a LIVE system
 * chronometer that ticks without per-second updates — counting UP from
 * accumulated focus time, or DOWN to the Pomodoro target when set.
 */
class FocusService : Service() {

    private val CHANNEL_ID = "focus_timer_v2"
    private val NOTIF_ID   = 0xF0C05

    private var lastTitle     = "Focus"
    private var lastStartedAt = 0L
    private var lastElapsedMs = 0L
    private var lastPaused    = false
    private var lastTargetMs  = 0L
    private var lastAccent    = ""
    private var lastAlerted   = false

    private val alertHandler = Handler(Looper.getMainLooper())
    private var alertRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        loadPersistedState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastTitle     = intent.getStringExtra(EXTRA_TITLE) ?: lastTitle
                lastStartedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                lastElapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L)
                lastTargetMs  = intent.getLongExtra(EXTRA_TARGET_MS, 0L)
                lastAccent    = intent.getStringExtra(EXTRA_ACCENT) ?: ""
                lastPaused    = false
                lastAlerted   = lastTargetMs > 0 && lastElapsedMs >= lastTargetMs
                persistState()
            }
            ACTION_UPDATE -> {
                lastElapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, lastElapsedMs)
                lastPaused    = intent.getBooleanExtra(EXTRA_PAUSED, lastPaused)
                if (lastStartedAt == 0L) lastStartedAt = System.currentTimeMillis()
                persistState()
            }
        }

        ensureChannel()
        startForeground(NOTIF_ID,
            buildNotification(lastTitle, lastStartedAt, lastElapsedMs, lastPaused, lastTargetMs, lastAccent))

        if (intent?.action == ACTION_STOP) {
            cancelScheduledAlert()
            NotificationManagerCompat.from(this).cancel(NOTIF_ALERT_ID)
            clearPersistedState()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } else {
            scheduleGoalAlert()
        }
        return START_NOT_STICKY
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun loadPersistedState() {
        val p = prefs()
        lastTitle     = p.getString(EXTRA_TITLE, lastTitle) ?: lastTitle
        lastStartedAt = p.getLong(EXTRA_STARTED_AT, lastStartedAt)
        lastElapsedMs = p.getLong(EXTRA_ELAPSED_MS, lastElapsedMs)
        lastPaused    = p.getBoolean(EXTRA_PAUSED, lastPaused)
        lastTargetMs  = p.getLong(EXTRA_TARGET_MS, lastTargetMs)
        lastAccent    = p.getString(EXTRA_ACCENT, lastAccent) ?: lastAccent
        lastAlerted   = p.getBoolean(EXTRA_ALERTED, lastAlerted)
    }

    private fun persistState() {
        prefs().edit()
            .putString(EXTRA_TITLE, lastTitle)
            .putLong(EXTRA_STARTED_AT, lastStartedAt)
            .putLong(EXTRA_ELAPSED_MS, lastElapsedMs)
            .putBoolean(EXTRA_PAUSED, lastPaused)
            .putLong(EXTRA_TARGET_MS, lastTargetMs)
            .putString(EXTRA_ACCENT, lastAccent)
            .putBoolean(EXTRA_ALERTED, lastAlerted)
            .apply()
    }

    private fun clearPersistedState() {
        prefs().edit().clear().apply()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Focus Timer", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows the ongoing focus session and timer controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val broadcast = Intent(action).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(this, requestCode, broadcast, flags)
    }

    private fun buildNotification(
        title: String, startedAt: Long, elapsedMs: Long,
        paused: Boolean, targetMs: Long, accent: String,
    ): Notification {
        val launchIntent = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val launchPi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val targetMin = if (targetMs > 0) Math.round(targetMs / 60000.0).toInt() else 0
        val reached   = targetMs > 0 && elapsedMs >= targetMs
        val subtitle = when {
            paused        -> "Pausado"
            reached       -> "✓ meta cumprida"
            targetMin > 0 -> "Em foco · meta $targetMin min"
            else          -> "Em foco"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Pauta")
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle).setBigContentTitle(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        parseColor(accent)?.let { builder.setColorized(true).setColor(it) }

        if (!paused) {
            if (targetMs > 0 && !reached) {
                val finishAt = System.currentTimeMillis() + (targetMs - elapsedMs)
                builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(finishAt)
            } else {
                builder.setUsesChronometer(true).setChronometerCountDown(false)
                    .setWhen(System.currentTimeMillis() - elapsedMs)
            }
            builder.setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        if (paused) {
            builder.addAction(R.drawable.ic_focus_resume, "Retomar",
                actionPendingIntent(FocusActionReceiver.ACTION_RESUME, 1))
        } else {
            builder.addAction(R.drawable.ic_focus_pause, "Pausar",
                actionPendingIntent(FocusActionReceiver.ACTION_PAUSE, 0))
            builder.addAction(R.drawable.ic_focus_switch, "Trocar",
                actionPendingIntent(FocusActionReceiver.ACTION_SWITCH, 3))
        }
        builder.addAction(R.drawable.ic_focus_conclude, "Concluir",
            actionPendingIntent(FocusActionReceiver.ACTION_CONCLUDE, 2))

        return builder.build()
    }

    private fun parseColor(hex: String): Int? {
        if (hex.isBlank()) return null
        return try { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }
        catch (e: IllegalArgumentException) { null }
    }

    private fun cancelScheduledAlert() {
        alertRunnable?.let { alertHandler.removeCallbacks(it) }
        alertRunnable = null
    }

    private fun scheduleGoalAlert() {
        cancelScheduledAlert()
        if (lastTargetMs <= 0 || lastAlerted || lastPaused) return
        val remaining = lastTargetMs - lastElapsedMs
        if (remaining <= 0L) {
            fireGoalAlert()
        } else {
            val r = Runnable { fireGoalAlert() }
            alertRunnable = r
            alertHandler.postDelayed(r, remaining)
        }
    }

    private fun fireGoalAlert() {
        cancelScheduledAlert()
        if (lastAlerted || lastTargetMs <= 0 || lastPaused) return
        lastAlerted = true
        if (lastElapsedMs < lastTargetMs) lastElapsedMs = lastTargetMs
        persistState()
        startForeground(NOTIF_ID,
            buildNotification(lastTitle, lastStartedAt, lastElapsedMs, lastPaused, lastTargetMs, lastAccent))
        postAlertNotification()
    }

    private fun ensureAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                ALERT_CHANNEL_ID, "Meta cumprida", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando o tempo planeado de um bloco é atingido"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun postAlertNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        ensureAlertChannel()

        val targetMin = Math.round(lastTargetMs / 60000.0).toInt()
        val body = "Cumpriu os $targetMin min planeados."

        val launchIntent = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val launchPi = PendingIntent.getActivity(
            this, 7, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(lastTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(launchPi)
            .addAction(R.drawable.ic_focus_resume, "Continuar",
                actionPendingIntent(FocusActionReceiver.ACTION_DISMISS_ALERT, 5))
            .addAction(R.drawable.ic_focus_conclude, "Concluir",
                actionPendingIntent(FocusActionReceiver.ACTION_CONCLUDE, 2))
        parseColor(lastAccent)?.let { builder.setColor(it) }

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ALERT_ID, builder.build())
        } catch (e: Exception) { /* permission revoked between gate and post */ }
    }

    companion object {
        const val ACTION_START  = "com.pauta.app.SERVICE_START"
        const val ACTION_UPDATE = "com.pauta.app.SERVICE_UPDATE"
        const val ACTION_STOP   = "com.pauta.app.SERVICE_STOP"

        const val EXTRA_TITLE      = "title"
        const val EXTRA_STARTED_AT = "startedAt"
        const val EXTRA_ELAPSED_MS = "elapsedMs"
        const val EXTRA_PAUSED     = "paused"
        const val EXTRA_TARGET_MS  = "targetMs"
        const val EXTRA_ACCENT     = "accent"
        const val EXTRA_ALERTED    = "alerted"

        const val NOTIF_ALERT_ID = 0xF0C06

        private const val ALERT_CHANNEL_ID = "focus_alert_v1"
        private const val PREFS_NAME = "focus_service_state"
    }
}
