package com.pauta.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.pauta.app.MainActivity

/**
 * Handles notification action buttons (Pause / Resume / Conclude / Switch).
 *
 *   1. Pause/Resume/Conclude drive FocusService directly so the visible
 *      notification flips immediately, even if the app process was reclaimed.
 *   2. Switch brings the app to the front (picking a block needs UI).
 *   3. The action is also emitted on FocusActionBus so a live ViewModel can
 *      reconcile the Room store; if the process is dead the next launch
 *      reconciles from persisted state.
 */
class FocusActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS_ALERT) {
            NotificationManagerCompat.from(context).cancel(FocusService.NOTIF_ALERT_ID)
            return
        }

        val kind = when (intent.action) {
            ACTION_PAUSE    -> "pause"
            ACTION_RESUME   -> "resume"
            ACTION_CONCLUDE -> "conclude"
            ACTION_SWITCH   -> "switch"
            else            -> return
        }

        if (kind == "switch") {
            val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { context.startActivity(launch) } catch (e: Exception) { /* no launcher */ }
            FocusActionBus.emit("switch")
            return
        }

        val svcIntent = Intent(context, FocusService::class.java)
        when (kind) {
            "pause" -> {
                svcIntent.action = FocusService.ACTION_UPDATE
                svcIntent.putExtra(FocusService.EXTRA_PAUSED, true)
            }
            "resume" -> {
                svcIntent.action = FocusService.ACTION_UPDATE
                svcIntent.putExtra(FocusService.EXTRA_PAUSED, false)
            }
            "conclude" -> {
                svcIntent.action = FocusService.ACTION_STOP
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }

        FocusActionBus.emit(kind)
    }

    companion object {
        const val ACTION_PAUSE         = "com.pauta.app.FOCUS_PAUSE"
        const val ACTION_RESUME        = "com.pauta.app.FOCUS_RESUME"
        const val ACTION_CONCLUDE      = "com.pauta.app.FOCUS_CONCLUDE"
        const val ACTION_SWITCH        = "com.pauta.app.FOCUS_SWITCH"
        const val ACTION_DISMISS_ALERT = "com.pauta.app.FOCUS_DISMISS_ALERT"
    }
}
