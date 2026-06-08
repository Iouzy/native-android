package com.pauta.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.pauta.app.MainActivity

/**
 * Quick-Settings tile: tap it to jump straight into the app's focus screen.
 * Best-effort throughout — a tile tap must never crash.
 */
class FocusTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.SHORTCUT_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val launch = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val pi = PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (e: Exception) { /* a tile tap must never crash */ }
        }

        if (isLocked) unlockAndRun(launch) else launch.run()
    }
}
