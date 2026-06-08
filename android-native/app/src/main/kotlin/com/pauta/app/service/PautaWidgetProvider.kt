package com.pauta.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.pauta.app.MainActivity
import com.pauta.app.R

/**
 * Home-screen widget showing today's snapshot. The app pushes three already-
 * localized stat lines into SharedPreferences via [saveSnapshot]; this provider
 * renders them (and re-renders on the OS update tick).
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
            views.setTextViewText(R.id.widget_line1, p.getString(K_LINE1, "Pauta") ?: "Pauta")
            views.setTextViewText(R.id.widget_line2, p.getString(K_LINE2, "") ?: "")
            views.setTextViewText(R.id.widget_line3, p.getString(K_LINE3, "") ?: "")

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
