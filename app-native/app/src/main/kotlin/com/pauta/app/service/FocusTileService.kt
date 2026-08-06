package com.pauta.app.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.pauta.app.MainActivity
import com.pauta.app.i18n.tr

/**
 * Quick-Settings tile that opens the app straight on the focus (Pauta) tab,
 * using the same SHORTCUT_FOCUS contract as the launcher shortcut.
 *
 * **L11 · one tile, two faces**, exactly like the tabs and the widget. The
 * shortcut action is the *same* — it opens tab 2 — and in book mode tab 2 is
 * Sessão, so the destination follows the lens for free. What has to change is
 * what the tile *says*: a tile labelled "Foco" on a home screen in book mode is
 * the planner talking over the reading. The label is refreshed on every
 * `onStartListening`, which is when the shade opens, so it is never stale.
 *
 * // PT: um azulejo, duas faces. A acção é a mesma (abre a tab 2, que no modo
 * livro é a Sessão); o que muda é o que o azulejo diz — e é relido sempre que a
 * gaveta abre.
 */
@RequiresApi(Build.VERSION_CODES.N)
class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        // The lens, read straight from the saved settings rather than from Room:
        // this runs when the shade opens, which may be long before the app's
        // process has anything loaded. // PT: a lente vem das definições guardadas,
        // não da base de dados — isto corre com a app fechada.
        val bookMode = ReminderScheduler.savedBookMode(this)
        tile.label = if (bookMode) tr("Sessão de leitura") else tr("Foco")
        tile.updateTile()
    }

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
