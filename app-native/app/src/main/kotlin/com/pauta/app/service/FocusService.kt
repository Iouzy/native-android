package com.pauta.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pauta.app.MainActivity
import com.pauta.app.R
import com.pauta.app.domain.FocusMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf

/**
 * Foreground service that owns the ongoing focus-timer notification — a live
 * chronometer for the running block, with Pause / Conclude actions that work even
 * when the app is backgrounded (they broadcast to [FocusActionReceiver]). When the
 * block carries a soft target (C2) the chronometer counts *down* to it and a
 * one-shot alarm ([FocusTargetScheduler]) fires the lock-screen "target reached"
 * alert. Started/stopped by [FocusServiceController] as the active block comes and
 * goes. // PT: serviço em primeiro plano com a notificação do cronómetro; conta
 * para o alvo e avisa quando o atinge, mesmo em segundo plano.
 */
class FocusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky restart after a process kill (null intent): we've lost the block's
        // details, so show a minimal ongoing notification and leave any pending
        // target alarm untouched — the AlarmManager entry survived the kill and the
        // ViewModel will re-issue the full state when the UI returns. // PT: reinício
        // sticky — notificação mínima e mantém o alarme de alvo intacto.
        if (intent == null) {
            startForegroundCompat(buildNotification("", 0L, null, null, System.currentTimeMillis()))
            return START_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val elapsed = intent.getLongExtra(EXTRA_ELAPSED_MS, 0L)
        val target = intent.getLongExtra(EXTRA_TARGET_MS, 0L).takeIf { it > 0L }
        val now = System.currentTimeMillis()
        // The instant the soft target is reached — only when one is set and not
        // already passed. While the single session runs, focus time tracks the wall
        // clock, so now + remaining is the true ETA. // PT: instante do alvo (se
        // houver e ainda não passou).
        val targetAt = target?.let { now + (it - elapsed) }?.takeIf { it > now }
        startForegroundCompat(buildNotification(title, elapsed, target, targetAt, now))
        // A one-shot alarm fires the gentle "target reached" alert from the lock
        // screen even with the app backgrounded; cancelled when the block
        // pauses/concludes (the service is torn down → onDestroy). // PT: alarme
        // único do aviso de alvo, cancelado ao pausar/concluir.
        if (targetAt != null) FocusTargetScheduler.schedule(this, targetAt)
        else FocusTargetScheduler.cancel(this)
        return START_STICKY
    }

    override fun onDestroy() {
        // No running block → no pending target. // PT: sem bloco a correr, sem alvo.
        FocusTargetScheduler.cancel(this)
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun buildNotification(
        title: String,
        elapsedMs: Long,
        targetMs: Long?,
        targetAt: Long?,
        now: Long,
    ): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags(),
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title.ifBlank { "Pauta" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setContentIntent(open)
            .addAction(0, tr("Pausar"), broadcast(FocusActionReceiver.ACTION_PAUSE))
            .addAction(0, tr("Concluir"), broadcast(FocusActionReceiver.ACTION_CONCLUDE))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (targetAt != null) {
            // Live count-DOWN to the target: the system renders it tick-by-tick with
            // no per-second wakeups, and it lands on 0 exactly as the alarm fires.
            // // PT: contagem decrescente até ao alvo, desenhada pelo sistema.
            builder.setChronometerCountDown(true)
                .setWhen(targetAt)
                .setContentText(trf("Alvo · {t}", "t" to FocusMath.fmtDuration(targetMs!!)))
        } else {
            // No target (or already reached): count UP the total focus — when = now
            // − elapsed, so it includes earlier sessions of this block. // PT: sem
            // alvo, conta o tempo total de foco como antes.
            builder.setWhen(now - elapsedMs)
                .setContentText("")
        }
        return builder.build()
    }

    private fun broadcast(action: String): PendingIntent =
        PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(this, FocusActionReceiver::class.java).setAction(action),
            pendingFlags(),
        )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            // Re-creating an existing channel just updates its name, so the
            // channel label follows the app language. // PT: recriar o canal só
            // atualiza o nome — acompanha a língua da app.
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tr("Foco"), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "pauta_focus"
        private const val NOTIF_ID = 1001
        const val EXTRA_TITLE = "title"
        const val EXTRA_ELAPSED_MS = "elapsed"
        const val EXTRA_TARGET_MS = "target"
    }
}

/** Starts/stops [FocusService] from app code (no Context plumbing at call sites).
 *  [targetMs] is the running block's soft target, or null for none. */
object FocusServiceController {
    fun start(context: Context, title: String, elapsedMs: Long, targetMs: Long?) {
        val intent = Intent(context, FocusService::class.java)
            .putExtra(FocusService.EXTRA_TITLE, title)
            .putExtra(FocusService.EXTRA_ELAPSED_MS, elapsedMs)
            .putExtra(FocusService.EXTRA_TARGET_MS, targetMs ?: 0L)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FocusService::class.java))
    }
}

/**
 * C2: the one-time "focus target reached" alert. Posted by [FocusTargetReceiver]
 * when the soft target's alarm fires — on its own high-importance channel (the
 * ongoing timer's channel is silent and low) so it surfaces on the lock screen.
 * // PT: aviso único de "alvo de foco alcançado", em canal de alta importância
 * para chegar ao ecrã de bloqueio.
 */
object FocusNotifications {
    private const val CHANNEL_ID = "pauta_focus_alert"
    private const val NOTIF_ID = 1002

    fun postTargetReached(context: Context, blockTitle: String) {
        ensureChannel(context)
        // Open straight on the Pauta (focus) tab so the user can conclude.
        val open = PendingIntent.getActivity(
            context, NOTIF_ID,
            Intent(context, MainActivity::class.java)
                .setAction("com.pauta.app.SHORTCUT_FOCUS")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags(),
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(tr("Alvo de foco alcançado"))
            .setContentText(blockTitle.ifBlank { "Pauta" })
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notif) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, tr("Alvo de foco"), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
}
