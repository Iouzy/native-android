package com.pauta.app.service

import android.content.Context

/**
 * Stores the three pre-localized lines the home widget renders, then nudges the
 * widget to refresh. The app computes the lines (it has Room + i18n); the widget
 * provider stays dumb. // PT: guarda as três linhas localizadas do widget e
 * pede-lhe que atualize.
 */
object WidgetSnapshot {
    const val PREFS = "pauta_widget"
    const val K_LINE1 = "line1"
    const val K_LINE2 = "line2"
    const val K_LINE3 = "line3"

    fun publish(context: Context, line1: String, line2: String, line3: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_LINE1, line1)
            .putString(K_LINE2, line2)
            .putString(K_LINE3, line3)
            .apply()
        PautaWidgetProvider.refresh(context)
    }
}
