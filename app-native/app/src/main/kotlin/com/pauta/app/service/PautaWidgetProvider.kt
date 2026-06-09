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
 * Home-screen widget showing three pre-localized stat lines. The app computes
 * the lines and stores them via [WidgetSnapshot]; this just renders whatever is
 * stored and refreshes on the OS tick or when the app nudges it. Tapping opens
 * the app. // PT: widget de ecrã — mostra três linhas de estatísticas guardadas
 * pela app.
 */
class PautaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, render(context)) }
    }

    companion object {
        /** Re-render every widget instance (called after the app updates the snapshot). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PautaWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = render(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun render(context: Context): RemoteViews {
            val sp = context.getSharedPreferences(WidgetSnapshot.PREFS, Context.MODE_PRIVATE)
            return RemoteViews(context.packageName, R.layout.widget_pauta).apply {
                setTextViewText(R.id.widget_line1, sp.getString(WidgetSnapshot.K_LINE1, "") ?: "")
                setTextViewText(R.id.widget_line2, sp.getString(WidgetSnapshot.K_LINE2, "") ?: "")
                setTextViewText(R.id.widget_line3, sp.getString(WidgetSnapshot.K_LINE3, "") ?: "")
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
                val open = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    flags,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
