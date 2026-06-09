package com.pauta.app.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.pauta.app.MainActivity

/**
 * Quick-Settings tile that opens the app straight on the focus (Pauta) tab,
 * using the same SHORTCUT_FOCUS contract as the launcher shortcut. // PT: azulejo
 * de definições rápidas — abre direto na tab de foco.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FocusTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction("com.pauta.app.SHORTCUT_FOCUS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
