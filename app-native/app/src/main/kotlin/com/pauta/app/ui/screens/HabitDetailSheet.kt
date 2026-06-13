package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.CellState
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.cellStateFor
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.combinedClickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import java.time.LocalDate

/**
 * "Histórico da maré" — one tide's full story (mares-sheets.jsx
 * HabitDetailSheet): serif header with "desde {date} · {n} dias", the current-
 * tier card with the distance to the next tier, the all-time heatmap (weeks as
 * columns, same cell states and gestures as the grid), and the Total / Actual /
 * Melhor / Respiros stat row. "editar" hands off to EditHabitSheet. // PT: a
 * história completa de uma maré.
 */
@Composable
fun HabitDetailSheet(
    habit: HabitEntity,
    model: HabitModel,
    countsForHabit: Map<String, Int>,
    today: String,
    onToggleDay: (String) -> Unit,
    onIncrementDay: (String, Int) -> Unit,
    onMarkRespiro: (String) -> Unit,
    onUnmarkRespiro: (String) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val accent = habit.color
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: colors.accent
    val isCount = habit.target != null && habit.cadence == "daily"

    // All-time stats: observed/done/respiros from creation → today (or end).
    val created = HabitCalculator.createdKey(model)
    val end = HabitCalculator.endKey(model)
    val stop = if (end != null && end < today) end else today
    val stats = HabitCalculator.periodStats(model, created, stop)
    val pctAll = (stats.observed - stats.respiros).let { denom ->
        if (denom > 0) Math.round(stats.done.toDouble() / denom * 100).toInt() else null
    }
    val current = HabitCalculator.currentStreak(model, today)
    val best = HabitCalculator.bestStreak(model, today)
    val tier = HabitCalculator.tideTier(current.days)
    val nextTier = HabitCalculator.TIDE_TIERS.lastOrNull { it.min > current.days }
    val perLen = when (current.unit) { "sem" -> 7; "mês" -> 30; else -> 1 }

    PautaSheet(title = tr("Histórico da maré"), onClose = onClose) {
        // Header, with the "editar" pill on the right (web's edit entry).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = habit.name,
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 26.sp,
                lineHeight = 29.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = tr("editar").uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 1.35.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(999.dp))
                    .clickableNoRipple(onEdit)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        if (habit.time.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(habit.time, color = colors.ink3, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = trf(
                "desde {date} · {n} {unit}",
                "date" to I18n.fmtDateShort(LocalDate.parse(created)),
                "n" to stats.observed,
                "unit" to if (stats.observed == 1) tr("dia") else tr("dias"),
            ),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.sp, // 0.1em of 10sp
        )
        if (habit.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = habit.description,
                color = colors.ink2,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.paper2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        // Current tier card.
        if (tier != null) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.04f))
                    .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tr("maré actual").uppercase(),
                        color = colors.ink3,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.9.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(tr(tier.name), color = accent, fontFamily = SerifFamily, fontSize = 22.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tr(tier.subtitle),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = buildAnnotatedString {
                            append(current.units.toString())
                            withStyle(SpanStyle(fontSize = 11.sp, color = colors.ink3)) { append(" " + current.unit) }
                        },
                        color = colors.ink,
                        fontFamily = MonoFamily,
                        fontSize = 20.sp,
                    )
                    if (current.respiros > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${current.respiros} " + (if (current.respiros == 1) tr("respiro") else tr("respiros")),
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 9.sp,
                        )
                    }
                    nextTier?.let { nt ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (habit.cadence == "daily") {
                                trf("→ {name} em {n}d", "name" to tr(nt.name), "n" to (nt.min - current.days))
                            } else {
                                trf(
                                    "→ {name} em {n} {u}",
                                    "name" to tr(nt.name),
                                    "n" to maxOf(1, Math.ceil((nt.min - current.days) / perLen.toDouble()).toInt()),
                                    "u" to current.unit,
                                )
                            },
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }

        // All-time heatmap — weeks as columns, Sunday-first, same cell language
        // as the grid. // PT: heatmap de sempre, semanas em colunas.
        Spacer(Modifier.height(20.dp))
        val weeks = remember(model, countsForHabit, today) {
            val startDate = LocalDate.parse(created).let { d -> d.minusDays((d.dayOfWeek.value % 7).toLong()) }
            val endDate = LocalDate.parse(today)
            val cols = mutableListOf<List<Pair<String, CellState>>>()
            var cursor = startDate
            while (cursor <= endDate) {
                cols += (0..6).map { i ->
                    val k = cursor.plusDays(i.toLong()).toString()
                    k to cellStateFor(model, habit.cadence, isCount, countsForHabit, k, today)
                }
                cursor = cursor.plusDays(7)
            }
            cols
        }
        val heatScroll = rememberScrollState()
        LaunchedScrollToEnd(heatScroll, weeks.size)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(heatScroll),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            weeks.forEachIndexed { wi, week ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Month tick above the first week of each month. A fixed-size
                    // box keeps every column's cells aligned; the label itself is
                    // free to overflow the 11dp cell width — months sit ~4 columns
                    // apart so labels never collide — and renders at full height so
                    // the glyph tops aren't clipped.
                    // PT: marca do mês acima da primeira semana de cada mês; a caixa
                    // fixa mantém as células alinhadas e a etiqueta transborda sem corte.
                    val firstDay = LocalDate.parse(week.first().first)
                    val prevFirst = if (wi > 0) LocalDate.parse(weeks[wi - 1].first().first) else null
                    Box(Modifier.height(14.dp).width(11.dp), contentAlignment = Alignment.BottomStart) {
                        if (prevFirst == null || prevFirst.month != firstDay.month) {
                            Text(
                                text = I18n.fmtMonthShort(firstDay.monthValue),
                                color = colors.ink4,
                                fontFamily = MonoFamily,
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                    week.forEach { (key, state) ->
                        HeatCell(
                            state = state,
                            isToday = key == today,
                            count = countsForHabit[key] ?: 0,
                            target = habit.target,
                            accent = accent,
                            onTap = {
                                when {
                                    isCount -> if (state == CellState.RESPIRO) onUnmarkRespiro(key) else onIncrementDay(key, countsForHabit[key] ?: 0)
                                    state == CellState.EMPTY || state == CellState.DONE -> onToggleDay(key)
                                    state == CellState.RESPIRO -> onUnmarkRespiro(key)
                                }
                            },
                            onLongPress = { if (state == CellState.EMPTY) onMarkRespiro(key) },
                        )
                    }
                }
            }
        }

        // Stats: Total / Actual / Melhor / Respiros.
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                .background(colors.rule),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            DetailStat(tr("Total"), pctAll?.let { "$it%" } ?: "—", Modifier.weight(1f))
            DetailStat(tr("Actual"), if (current.units > 0) "${current.units} ${current.unit}" else "—", Modifier.weight(1f))
            DetailStat(tr("Melhor"), if (best > 0) "$best ${current.unit}" else "—", Modifier.weight(1f))
            DetailStat(tr("Respiros"), if (stats.respiros > 0) stats.respiros.toString() else "—", Modifier.weight(1f))
        }

        // Hints.
        Spacer(Modifier.height(16.dp))
        Text(
            text = tr("Toque num dia para marcar como feito.") + "\n" +
                tr("Pressão longa num dia falhado para marcar respiro."),
            color = colors.ink4,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.36.sp,
        )
    }
}

/** Jump the heatmap to its end (today) once laid out. */
@Composable
private fun LaunchedScrollToEnd(state: androidx.compose.foundation.ScrollState, key: Int) {
    androidx.compose.runtime.LaunchedEffect(key) { state.scrollTo(state.maxValue) }
}

@Composable
private fun HeatCell(
    state: CellState,
    isToday: Boolean,
    count: Int,
    target: Int?,
    accent: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val filled = state == CellState.DONE
    val clickable = state == CellState.EMPTY || state == CellState.DONE ||
        state == CellState.RESPIRO || state == CellState.PARTIAL
    val frac = if (state == CellState.PARTIAL && target != null && target > 0) {
        (count.toFloat() / target).coerceAtMost(1f)
    } else 0f
    val bg = when {
        filled -> if (isToday) accent else colors.ink
        state == CellState.PARTIAL -> accent.copy(alpha = 0.25f + 0.4f * frac)
        state == CellState.RESPIRO -> accent.copy(alpha = 0.18f)
        else -> colors.paper2
    }
    val cellAlpha = when (state) {
        CellState.FUTURE -> 0f // the heatmap simply ends at today
        CellState.PRE -> 0.35f
        CellState.AFTER -> 0.3f
        CellState.OFF, CellState.LOCKED -> 0.45f
        else -> 1f
    }
    Box(
        Modifier
            .size(11.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg.copy(alpha = bg.alpha * cellAlpha.coerceAtLeast(0.0f)))
            .then(
                if (isToday) Modifier.border(1.dp, accent, RoundedCornerShape(2.dp)) else Modifier,
            )
            .then(
                if (clickable) Modifier.combinedClickableNoRipple(onClick = onTap, onLongClick = onLongPress)
                else Modifier,
            ),
    )
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    Column(
        modifier
            .background(colors.paper)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 8.sp,
            letterSpacing = 0.72.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.ink, fontFamily = SerifFamily, fontSize = 17.sp, lineHeight = 17.sp, maxLines = 1)
    }
}
