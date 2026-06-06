package com.pauta.app

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

/**
 * Foreground service that owns the ongoing focus-timer notification.
 *
 * Commands are delivered as startService() calls with an action string:
 *   ACTION_START  — create the notification and enter the foreground
 *   ACTION_UPDATE — refresh the notification text / chronometer base
 *   ACTION_STOP   — remove the notification and stop the service
 *
 * The notification mirrors the in-app Pauta card so it reads like an app
 * control: the block title, a "Pauta" app label (setSubText), an accent tint
 * (setColorized + setColor), and a LIVE system chronometer that ticks without
 * per-second JS updates — counting UP from accumulated focus time, or DOWN to
 * the Pomodoro target (setChronometerCountDown) when the block has one. Paused
 * shows a static "Pausado" label. Action buttons (Pause/Resume — whichever
 * matches the state — and Conclude) fire broadcasts caught by FocusActionReceiver,
 * which routes them back into the JS timer state, keeping both in sync.
 */
class FocusService : Service() {

    private val CHANNEL_ID = "focus_timer_v2"
    // Distinctive ID to avoid colliding with @capacitor/local-notifications,
    // which lets the JS side pick its own IDs from a low range.
    private val NOTIF_ID   = 0xF0C05

    // Last-known state so ACTION_UPDATE can rebuild the notification without
    // repeating all the start parameters. Mirrored to SharedPreferences so it
    // survives process death — notification PendingIntents outlive the process,
    // and a user tapping Pause/Resume/Conclude after the OS reclaimed us would
    // otherwise restart the service with a zeroed chronometer.
    private var lastTitle     = "Focus"
    private var lastStartedAt = 0L
    private var lastElapsedMs = 0L
    private var lastPaused    = false
    // Optional Pomodoro target (ms; 0 = open-ended) and accent tint (hex; "" =
    // none). Carried so ACTION_UPDATE rebuilds with the same look, and persisted
    // so a button tap after process death keeps the countdown/colour.
    private var lastTargetMs  = 0L
    private var lastAccent    = ""
    // Whether the goal-reached heads-up has already fired for this run, so it pops
    // exactly once — even across UPDATE deliveries and a process restart.
    private var lastAlerted   = false

    // The block runs as a foreground service, so a main-looper Handler reliably
    // fires the goal alert at the exact crossing even when the app is backgrounded
    // and no JS is ticking. Cancelled on pause / stop / reschedule.
    private val alertHandler = Handler(Looper.getMainLooper())
    private var alertRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        loadPersistedState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Mutate last-known state from intent extras (no-op if extras are absent).
        when (intent?.action) {
            ACTION_START -> {
                lastTitle     = intent.getStringExtra(EXTRA_TITLE) ?: lastTitle
                lastStartedAt = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                lastElapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L)
                lastTargetMs  = intent.getLongExtra(EXTRA_TARGET_MS, 0L)
                lastAccent    = intent.getStringExtra(EXTRA_ACCENT) ?: ""
                lastPaused    = false
                // Reset so a fresh crossing alerts — but if we're (re)starting a
                // block ALREADY past its target (resume past the goal), treat the
                // alert as spent so it doesn't pop a second time.
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

        // Always enter (or re-enter) the foreground before dispatching the action.
        // This satisfies the startForegroundService 5-second contract for every
        // delivery — including ACTION_UPDATE on a freshly-spawned process and
        // ACTION_STOP after the OS killed us — preventing
        // ForegroundServiceDidNotStartInTimeException ANRs.
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
            // START, UPDATE, or a null-intent process restart: (re)evaluate when the
            // goal-reached alert should fire from the latest known state.
            scheduleGoalAlert()
        }
        return START_NOT_STICKY
    }

    // ── State persistence (survives process death) ───────────────

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

    // ── Notification helpers ─────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer",
                // DEFAULT = shows on lock screen; sound/vibration disabled below
                // so it behaves silently but is still visible on the lock screen.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows the ongoing focus session and timer controls"
                setShowBadge(false)
                setSound(null, null)          // no sound
                enableVibration(false)        // no vibration
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val broadcast = Intent(action).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(this, requestCode, broadcast, flags)
    }

    private fun buildNotification(
        title: String,
        startedAt: Long,
        elapsedMs: Long,
        paused: Boolean,
        targetMs: Long,
        accent: String,
    ): Notification {

        // Tap → bring the app to the front. NEW_TASK is required when launching
        // from a non-Activity context (service / notification); SINGLE_TOP avoids
        // stacking duplicate instances. Fall back to an explicit MainActivity
        // intent if the launcher intent is null (rare OEM edge case).
        val launchIntent = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val launchPi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Subtitle mirrors the in-app active card: "Em foco" (open-ended) or
        // "Em foco · meta N min" / "✓ meta cumprida" when a Pomodoro target is set.
        val targetMin = if (targetMs > 0) Math.round(targetMs / 60000.0).toInt() else 0
        val reached   = targetMs > 0 && elapsedMs >= targetMs
        val subtitle = when {
            paused          -> "Pausado"
            reached         -> "✓ meta cumprida"
            targetMin > 0   -> "Em foco · meta $targetMin min"
            else            -> "Em foco"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Pauta")                 // app label, like a media control
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle).setBigContentTitle(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Accent tint — makes the notification read like the app's focus control
        // (coloured app name + a coloured left bar on supporting launchers). Only
        // honoured on an ongoing foreground-service notification, which this is.
        parseColor(accent)?.let { builder.setColorized(true).setColor(it) }

        if (!paused) {
            if (targetMs > 0 && !reached) {
                // Count DOWN to the target: chronometer base in the FUTURE so the
                // system ticks toward zero without per-second JS updates.
                val finishAt = System.currentTimeMillis() + (targetMs - elapsedMs)
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(finishAt)
            } else {
                // Count UP from total accumulated focus time (base = now − elapsed).
                builder.setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .setWhen(System.currentTimeMillis() - elapsedMs)
            }
            builder.setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }

        // Action buttons mirror the Pauta tab's controls. Real white vector icons
        // sit next to the labels in the expanded view (a 0 icon would render an
        // empty slot on some OEM skins).
        if (paused) {
            builder.addAction(R.drawable.ic_focus_resume, "Retomar",
                actionPendingIntent(FocusActionReceiver.ACTION_RESUME, 1))
        } else {
            builder.addAction(R.drawable.ic_focus_pause, "Pausar",
                actionPendingIntent(FocusActionReceiver.ACTION_PAUSE, 0))
            // "Trocar" mirrors the active card's switch control: it opens the app
            // to pick another block. The running block keeps ticking until the
            // user actually chooses one, so there's no native state change here.
            // Shown only while running (like the in-app card; the paused card has
            // no switch), which also keeps the collapsed view at three buttons.
            builder.addAction(R.drawable.ic_focus_switch, "Trocar",
                actionPendingIntent(FocusActionReceiver.ACTION_SWITCH, 3))
        }
        builder.addAction(R.drawable.ic_focus_conclude, "Concluir",
            actionPendingIntent(FocusActionReceiver.ACTION_CONCLUDE, 2))

        return builder.build()
    }

    /** Parse a #RRGGBB / #AARRGGBB hex string; null if absent or malformed. */
    private fun parseColor(hex: String): Int? {
        if (hex.isBlank()) return null
        return try { Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }
        catch (e: IllegalArgumentException) { null }
    }

    // ── Goal-reached heads-up alert ──────────────────────────────
    // A second, dismissable notification on a HIGH-importance channel that pops
    // when the block's Pomodoro target lands, offering the same two choices as the
    // in-app prompt: keep going (just dismiss) or conclude. The ongoing service
    // notification is separate and keeps ticking either way.

    private fun cancelScheduledAlert() {
        alertRunnable?.let { alertHandler.removeCallbacks(it) }
        alertRunnable = null
    }

    /** (Re)arm the one-shot goal alert from the current state, or fire it now if
     *  the target is already behind us. No-op without a target, when paused, or
     *  once it has already fired this run. */
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
        // Snap the stored elapsed up to the target so the ongoing notification's
        // count-up resumes from the target — not from the (stale) start value the
        // countdown was anchored to.
        if (lastElapsedMs < lastTargetMs) lastElapsedMs = lastTargetMs
        persistState()
        // Refresh the ongoing notification so it flips to "✓ meta cumprida" and its
        // chronometer switches from count-down to count-up.
        startForeground(NOTIF_ID,
            buildNotification(lastTitle, lastStartedAt, lastElapsedMs, lastPaused, lastTargetMs, lastAccent))
        postAlertNotification()
    }

    private fun ensureAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Meta cumprida",
                // HIGH so it pops as a heads-up with sound — this is the moment the
                // user is waiting for, unlike the silent ongoing timer channel.
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa quando o tempo planeado de um bloco é atingido"
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun postAlertNotification() {
        // Needs POST_NOTIFICATIONS on 13+; areNotificationsEnabled() reflects that
        // grant plus the master switch, so it's the right gate for this extra post.
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
            // "Continuar" just dismisses (block keeps running, now counting up);
            // "Concluir" stops the block via the same path as the ongoing buttons.
            .addAction(R.drawable.ic_focus_resume, "Continuar",
                actionPendingIntent(FocusActionReceiver.ACTION_DISMISS_ALERT, 5))
            .addAction(R.drawable.ic_focus_conclude, "Concluir",
                actionPendingIntent(FocusActionReceiver.ACTION_CONCLUDE, 2))
        parseColor(lastAccent)?.let { builder.setColor(it) }

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ALERT_ID, builder.build())
        } catch (e: Exception) { /* permission revoked between the gate and the post */ }
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

        // Goal-reached heads-up id — distinct from the ongoing NOTIF_ID so the two
        // coexist; public so FocusActionReceiver can cancel it on "Continuar"/stop.
        const val NOTIF_ALERT_ID = 0xF0C06

        private const val ALERT_CHANNEL_ID = "focus_alert_v1"
        private const val PREFS_NAME = "focus_service_state"
    }
}
