package com.pauta.app.service

import android.content.Context
import android.content.res.Configuration
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
import com.pauta.app.data.PautaRepository
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.ReadingStats
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
        val prefs = repo.prefs.first()

        // Resolve the paper/ink palette for the host's current mode now (RemoteViews
        // can't read LocalPautaColors); on a mode flip the next update re-resolves.
        // // PT: resolve a paleta clara/escura conforme o modo do anfitrião.
        val night = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = paletteFor(night)

        // L11 · one widget, two faces — exactly like the tabs. Someone using the
        // book launcher icon had a home screen that still talked about the planner;
        // a *second* widget would have been a second thing to place and to keep in
        // step, and the lens is already the app's answer to this question.
        // // PT: um widget, duas faces — como as tabs. Um segundo widget seria mais
        // uma coisa para colocar e manter alinhada.
        if (prefs.bookMode) {
            val stats = readingSnapshot(repo, today)
            provideContent { ReadingWidgetContent(stats, palette, prefs.accent) }
            return
        }
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
        val accentHex = prefs.accent

        // Header count mirrors the Hoje strip: done over the non-respiro total
        // (an honest rest is neither done nor pending). // PT: como no separador Hoje.
        val done = tides.count { it.state == DayState.DONE }
        val denom = tides.count { it.state != DayState.RESPIRO }
        val eyebrow = tr("Marés de hoje").uppercase()
        val countLabel = if (denom > 0) trf("{d}/{t} marés", "d" to done, "t" to denom) else null
        val emptyLabel = tr("Sem marés para hoje")

        provideContent { MaresWidgetContent(tides, eyebrow, countLabel, emptyLabel, accentHex, palette) }
    }

    /**
     * L11 · what the reading face shows, read off the same data the Hábitos tab
     * derives from. Nothing new is stored and nothing is self-reported: a reading
     * day is proven by a session, and `ReadingStats` already owns what that means
     * (F13). // PT: a face de leitura, derivada dos mesmos dados da tab.
     */
    private suspend fun readingSnapshot(repo: PautaRepository, today: String): ReadingWidgetStats {
        val books = repo.booksReading().first()
        val bookIds = books.mapTo(HashSet()) { it.id }
        val blocks = repo.blocks().first().filter { it.project?.startsWith("book:") == true }
        val spans = repo.allSessions().first().groupBy { it.blockId }
        val sessions = blocks.mapNotNull { b ->
            val segs = spans[b.id].orEmpty()
            val endedAt = segs.mapNotNull { it.endedAt }.maxOrNull() ?: return@mapNotNull null
            val ms = FocusMath.blockElapsedMs(
                segs.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
                System.currentTimeMillis(),
            )
            ReadingStats.Session(dayKey = DateUtils.dayKeyOf(endedAt), minutes = (ms / 60_000L).toInt())
        }
        val days = ReadingStats.daysRead(sessions)
        val (streak, _) = ReadingStats.streaks(days, today)
        // The last seven days, which is what "this week" means on a widget you
        // glance at. // PT: os últimos sete dias.
        val weekMinutes = ReadingStats.minutesLastDays(sessions, today, 7).sum()
        return ReadingWidgetStats(
            streakDays = streak,
            weekMinutes = weekMinutes,
            currentTitle = books.singleOrNull()?.title,
            hasBooks = bookIds.isNotEmpty(),
        )
    }

    companion object {
        /** Re-render every placed Marés widget — called after the app changes a
         *  tide or the day rolls over, and after a tap marks one from the widget.
         *  // PT: re-renderiza os widgets de Marés colocados. */
        suspend fun refresh(context: Context) = MaresWidget().updateAll(context)
    }
}

/** L11 · the reading face's four facts. // PT: os quatro factos da face de leitura. */
private data class ReadingWidgetStats(
    val streakDays: Int,
    val weekMinutes: Int,
    /** Named only when exactly one book is being read — naming one of four would
     *  be picking a favourite. // PT: só se houver um livro em curso. */
    val currentTitle: String?,
    val hasBooks: Boolean,
)

/**
 * L11 · the widget in book mode: the reading streak and this week's minutes, and
 * the book being read when there is one. Tapping opens the app, as the planner
 * face's header already does. // PT: a face de leitura do widget.
 */
@Composable
private fun ReadingWidgetContent(
    stats: ReadingWidgetStats,
    palette: WPalette,
    accentHex: String?,
) {
    val accent = ColorProvider(parseHexColor(accentHex) ?: WDefaultAccent)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.paper)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            text = tr("Ritmo de leitura").uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = palette.ink3,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        if (!stats.hasBooks) {
            Text(
                text = tr("Nenhum livro em curso"),
                style = TextStyle(color = palette.ink3, fontSize = 13.sp),
            )
            return@Column
        }
        Text(
            text = trf("{n} dias seguidos", "n" to stats.streakDays),
            maxLines = 1,
            style = TextStyle(color = if (stats.streakDays > 0) accent else palette.ink3, fontSize = 17.sp),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = trf("{n} min esta semana", "n" to stats.weekMinutes),
            maxLines = 1,
            style = TextStyle(color = palette.ink3, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
        )
        if (stats.currentTitle != null) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = stats.currentTitle,
                maxLines = 2,
                style = TextStyle(color = palette.ink, fontSize = 13.sp),
            )
        }
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
    palette: WPalette,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(palette.paper).padding(14.dp),
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
                style = TextStyle(color = palette.ink3, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (countLabel != null) {
                Text(
                    text = countLabel,
                    maxLines = 1,
                    style = TextStyle(color = palette.ink3, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        if (tides.isEmpty()) {
            Text(
                text = emptyLabel,
                style = TextStyle(color = palette.ink3, fontSize = 13.sp),
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            LazyColumn {
                items(tides.size) { i -> TideRow(tides[i], accentHex, palette) }
            }
        }
    }
}

/** One tide: its state circle + name. Pending/done rows toggle on tap; a respiro
 *  row (not actionable in-app either) opens the app instead. */
@Composable
private fun TideRow(tide: TideToday, accentHex: String?, palette: WPalette) {
    val isDone = tide.state == DayState.DONE
    val respiro = tide.state == DayState.RESPIRO
    val drawable = when {
        isDone -> R.drawable.widget_tide_done
        respiro -> R.drawable.widget_tide_respiro
        else -> R.drawable.widget_tide_pending
    }
    val tint = when {
        isDone -> ColorProvider(accentColorFor(tide.habit.color, accentHex))
        respiro -> palette.ink4
        else -> palette.ink3
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
                color = if (isDone || respiro) palette.ink3 else palette.ink,
                fontSize = 14.sp,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

// ── palette ───────────────────────────────────────────────────────────────────
// The widget is RemoteViews, so it can't read LocalPautaColors. We resolve the
// paper/ink tokens (mirrored from ui/theme/Color.kt) to a single set of fixed
// ColorProviders per mode in provideGlance — the most portable Glance colour API.
// // PT: tokens espelhados de Color.kt, resolvidos por modo (claro/escuro).
private class WPalette(
    val paper: ColorProvider,
    val ink: ColorProvider,
    val ink3: ColorProvider,
    val ink4: ColorProvider,
)

private fun paletteFor(night: Boolean) = WPalette(
    paper = ColorProvider(if (night) Color(0xFF1B1A17) else Color(0xFFF5F1EA)),
    ink = ColorProvider(if (night) Color(0xFFECE6DA) else Color(0xFF1A1815)),
    ink3 = ColorProvider(if (night) Color(0xFF8A8275) else Color(0xFF6D665A)),
    ink4 = ColorProvider(if (night) Color(0xFF5F5A50) else Color(0xFFB5AC9C)),
)

private val WDefaultAccent = Color(0xFFB8533A) // Color.kt's build-default accent

/** A done tide fills with the tide's own colour if it has one, else the user's
 *  accent (or the build default) — the accent is theme-independent in Pauta. */
private fun accentColorFor(habitColor: String?, accentHex: String?): Color =
    parseHexColor(habitColor) ?: parseHexColor(accentHex) ?: WDefaultAccent

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex.trim())) }.getOrNull()
}
