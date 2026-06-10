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
import com.pauta.app.MainActivity
import com.pauta.app.R
import com.pauta.app.i18n.tr

/**
 * Foreground service that owns the ongoing focus-timer notification — a live
 * count-up chronometer for the running block, with Pause / Conclude actions that
 * work even when the app is backgrounded (they broadcast to
 * [FocusActionReceiver]). Started/stopped by [FocusServiceController] as the
 * active block comes and goes. // PT: serviço em primeiro plano com a
 * notificação do cronómetro de foco e botões Pausar/Concluir.
 */
class FocusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val elapsed = intent?.getLongExtra(EXTRA_ELAPSED_MS, 0L) ?: 0L
        startForegroundCompat(buildNotification(title, elapsed))
        return START_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun buildNotification(title: String, elapsedMs: Long): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingFlags(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title.ifBlank { "Pauta" })
            .setContentText("")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            // when = now - elapsed, so the chronometer shows total focus including
            // earlier sessions of this block.
            .setWhen(System.currentTimeMillis() - elapsedMs)
            .setContentIntent(open)
            .addAction(0, tr("Pausar"), broadcast(FocusActionReceiver.ACTION_PAUSE))
            .addAction(0, tr("Concluir"), broadcast(FocusActionReceiver.ACTION_CONCLUDE))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
    }
}

/** Starts/stops [FocusService] from app code (no Context plumbing at call sites). */
object FocusServiceController {
    fun start(context: Context, title: String, elapsedMs: Long) {
        val intent = Intent(context, FocusService::class.java)
            .putExtra(FocusService.EXTRA_TITLE, title)
            .putExtra(FocusService.EXTRA_ELAPSED_MS, elapsedMs)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FocusService::class.java))
    }
}
