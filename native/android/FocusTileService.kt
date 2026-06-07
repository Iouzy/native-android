package com.pauta.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

// Quick-Settings tile: tap it in the notification shade to jump straight into
// "Iniciar foco". It reuses the exact same launch contract as the launcher
// shortcut — MainActivity receives SHORTCUT_FOCUS and the web layer opens the
// start sheet (via FocusActivity.consumePendingShortcut / the action listener) —
// so there's no new JS plumbing. Best-effort throughout: a tile tap must never
// crash. / Mosaico de Definições Rápidas para iniciar um bloco de foco.
class FocusTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.SHORTCUT_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val launch = Runnable {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    // API 34+ requires a PendingIntent for startActivityAndCollapse.
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

        // The tile can be tapped from the lock screen; unlock first if needed.
        if (isLocked) unlockAndRun(launch) else launch.run()
    }
}
