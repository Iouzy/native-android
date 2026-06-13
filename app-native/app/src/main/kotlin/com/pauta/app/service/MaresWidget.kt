package com.pauta.app.service

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pauta.app.MainActivity
import com.pauta.app.PautaApplication
import com.pauta.app.R
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.computeTodayTides
import kotlinx.coroutines.flow.first

/**
 * The Marés home-screen widget (Glance). It renders today's actionable tides as
 * circles — filled accent = done, hatched = respiro, outline = pending — and
 * marks them done straight from the home screen via the repository, no app
 * launch needed. Unlike the old three-line text widget it replaces, it reads the
 * data itself (Glance composes off an off-thread snapshot of the same
 * `computeTodayTides` slice the Hoje strip uses) and writes back through the
 * process-wide repo, exactly like [FocusActionReceiver]. // PT: widget de Marés —
 * marés de hoje em círculos, marcadas a partir do ecrã inicial pelo repositório.
 */
class MaresWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as PautaApplication).repository
        val today = DateUtils.todayKey()
        // One-shot snapshot of the reactive flows: the widget lives outside Compose
        // and the ViewModel, so it reads Room directly (then re-renders on demand
        // via [refresh]). // PT: leitura pontual dos flows — o widget vive fora da UI.
        val tides = computeTodayTides(
            habits = repo.habits().first(),
            logs = repo.habitLogs().first(),
            respiros = repo.habitRespiros().first(),
            counts = repo.habitCounts().first(),
            today = today,
        )
        val accentHex = repo.prefs.first().accent

        // Header count mirrors the Hoje strip: done over the non-respiro total
        // (an honest rest is neither done nor pending). // PT: como no separador Hoje.
        val done = tides.count { it.state == DayState.DONE }
        val denom = tides.count { it.state != DayState.RESPIRO }
        val eyebrow = tr("Marés de hoje").uppercase()
        val countLabel = if (denom > 0) trf("{d}/{t} marés", "d" to done, "t" to denom) else null
        val emptyLabel = tr("Sem marés para hoje")

        provideContent { MaresWidgetContent(tides, eyebrow, countLabel, emptyLabel, accentHex) }
    }

    companion object {
        /** Re-render every placed Marés widget — called after the app changes a
         *  tide or the day rolls over, and after a tap marks one from the widget.
         *  // PT: re-renderiza os widgets de Marés colocados. */
        suspend fun refresh(context: Context) = MaresWidget().updateAll(context)
    }
}

/** Wires the Glance widget into the system; the manifest points here. */
class MaresWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MaresWidget()
}

/** Carries the tapped habit's id to [ToggleTideAction]. */
private val habitIdKey = ActionParameters.Key<String>("habitId")

/**
 * Marks (or, for countables, increments) a tide from the widget, then refreshes
 * it. Runs in the broadcast's own coroutine on the process — no Activity — so it
 * works with the app fully closed, the same pattern as [FocusActionReceiver].
 * Re-reads the habit's live state rather than trusting the rendered snapshot.
 * // PT: marca a maré pelo widget e atualiza-o, mesmo com a app fechada.
 */
class ToggleTideAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[habitIdKey] ?: return
        val repo = (context.applicationContext as? PautaApplication)?.repository ?: return
        val today = DateUtils.todayKey()
        val habit = repo.getHabit(id) ?: return
        // Countable daily tides increment toward their target; the rest toggle done.
        // (Same branch as the Hoje strip's onAct.) // PT: contáveis somam; resto alterna.
        if (habit.target != null && habit.cadence == "daily") {
            val current = repo.habitCounts().first().firstOrNull { it.habitId == id && it.dayKey == today }?.count ?: 0
            repo.setHabitCount(id, today, current + 1)
        } else {
            repo.toggleHabitDay(id, today)
        }
        MaresWidget().updateAll(context)
    }
}

// ── rendering ─────────────────────────────────────────────────────────────────

@Composable
private fun MaresWidgetContent(
    tides: List<TideToday>,
    eyebrow: String,
    countLabel: String?,
    emptyLabel: String,
    accentHex: String?,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WPaper).padding(14.dp),
    ) {
        // Header — the same mono eyebrow + n/m as the Hoje strip; tapping opens
        // the app. // PT: cabeçalho — sobrescrito mono + n/m; toca para abrir a app.
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = eyebrow,
                maxLines = 1,
                style = TextStyle(color = WInk3, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    maxLines = 1,
                    style = TextStyle(color = WInk3, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        if (tides.isEmpty()) {
            Text(
                text = emptyLabel,
                style = TextStyle(color = WInk3, fontSize = 13.sp),
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            LazyColumn {
                items(tides.size) { i -> TideRow(tides[i], accentHex) }
            }
        }
    }
}

/** One tide: its state circle + name. Pending/done rows toggle on tap; a respiro
 *  row (not actionable in-app either) opens the app instead. */
@Composable
private fun TideRow(tide: TideToday, accentHex: String?) {
    val isDone = tide.state == DayState.DONE
    val respiro = tide.state == DayState.RESPIRO
    val drawable = when {
        isDone -> R.drawable.widget_tide_done
        respiro -> R.drawable.widget_tide_respiro
        else -> R.drawable.widget_tide_pending
    }
    val tint = when {
        isDone -> ColorProvider(accentColorFor(tide.habit.color, accentHex))
        respiro -> WInk4
        else -> WInk3
    }
    val action =
        if (respiro) actionStartActivity<MainActivity>()
        else actionRunCallback<ToggleTideAction>(actionParametersOf(habitIdKey to tide.habit.id))

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 7.dp).clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(drawable),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(GlanceModifier.width(11.dp))
        Text(
            text = tide.habit.name,
            maxLines = 1,
            style = TextStyle(
                color = if (isDone || respiro) WInk3 else WInk,
                fontSize = 14.sp,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

// ── palette ───────────────────────────────────────────────────────────────────
// The widget is RemoteViews, so it can't read LocalPautaColors; these mirror the
// same paper/ink tokens (light + dark) from ui/theme/Color.kt and follow the
// system dark mode via day/night ColorProviders. // PT: tokens espelhados de
// Color.kt — o widget não acede ao LocalPautaColors.
private val WPaper = ColorProvider(day = Color(0xFFF5F1EA), night = Color(0xFF1B1A17))
private val WInk = ColorProvider(day = Color(0xFF1A1815), night = Color(0xFFECE6DA))
private val WInk3 = ColorProvider(day = Color(0xFF6D665A), night = Color(0xFF8A8275))
private val WInk4 = ColorProvider(day = Color(0xFFB5AC9C), night = Color(0xFF5F5A50))
private val WDefaultAccent = Color(0xFFB8533A) // Color.kt's build-default accent

/** A done tide fills with the tide's own colour if it has one, else the user's
 *  accent (or the build default) — the accent is theme-independent in Pauta. */
private fun accentColorFor(habitColor: String?, accentHex: String?): Color =
    parseHexColor(habitColor) ?: parseHexColor(accentHex) ?: WDefaultAccent

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrNull()
}
