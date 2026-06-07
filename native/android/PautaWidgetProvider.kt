package com.pauta.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * Home-screen widget showing today's snapshot: intentions done/total, focus
 * minutes, and tides done/total. The WebView can't feed a widget directly, so
 * the web layer pushes three already-localized stat lines into SharedPreferences
 * via FocusActivity.setWidgetSnapshot({ line1, line2, line3 }) whenever it's
 * open; this provider just renders them (and re-renders on the OS update tick).
 *
 * Honest limitation: the numbers are as of the last time the app was open — a
 * widget process can't read the app's localStorage on its own.
 */
class PautaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, mgr, id)
    }

    companion object {
        const val PREFS = "pauta_widget"
        const val K_LINE1 = "line1"
        const val K_LINE2 = "line2"
        const val K_LINE3 = "line3"

        /** Persist the snapshot strings and refresh every placed widget. */
        fun saveSnapshot(context: Context, line1: String?, line2: String?, line3: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_LINE1, line1 ?: "")
                .putString(K_LINE2, line2 ?: "")
                .putString(K_LINE3, line3 ?: "")
                .apply()
            updateAll(context)
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, PautaWidgetProvider::class.java)) ?: return
            for (id in ids) render(context, mgr, id)
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_pauta)
            views.setTextViewText(R.id.widget_line1, p.getString(K_LINE1, "") ?: "")
            views.setTextViewText(R.id.widget_line2, p.getString(K_LINE2, "") ?: "")
            views.setTextViewText(R.id.widget_line3, p.getString(K_LINE3, "") ?: "")

            // Tap anywhere on the widget → open the app.
            val launch = (context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, launch, flags))

            mgr.updateAppWidget(id, views)
        }
    }
}
