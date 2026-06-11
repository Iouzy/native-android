package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.InsightsMath
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * "Revisão" — the web's InsightsSheet (extras.jsx): the week at a glance, the
 * month review, narrative patterns, the best-hour chart, habit × focus
 * correlations and the focus calendar. All math lives in [InsightsMath],
 * web-identical. // PT: a Revisão — a semana de relance, o mês, padrões,
 * melhor hora, hábitos × foco e o calendário de foco.
 */
@Composable
fun InsightsSheet(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val allIntentions by vm.allIntentions.collectAsStateWithLifecycle()
    val allDays by vm.allDays.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()

    // A review reads a moment in time; no per-second tick needed.
    val now = remember { System.currentTimeMillis() }

    val blockSegs = remember(blocks, allSessions) {
        val byBlock = allSessions.groupBy { it.blockId }
        blocks.map { b ->
            InsightsMath.BlockSegs(b.id, byBlock[b.id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) })
        }
    }
    val namedHabits = remember(habits, habitLogs, habitRespiros) {
        val logSet = habitLogs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        val respSet = habitRespiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
        habits.map { h ->
            InsightsMath.NamedHabit(
                h.name,
                HabitModel(
                    h.id, h.createdAt, h.cadence, h.anchor, h.weekdays, h.recurrence, h.endsAt,
                    logSet[h.id].orEmpty(), respSet[h.id].orEmpty(),
                ),
            )
        }
    }
    val reflectionByDay = remember(allDays) { allDays.associate { it.dayKey to it.reflection } }

    val week = remember(blockSegs, namedHabits, allIntentions, reflectionByDay, today) {
        InsightsMath.weeklyReview(blockSegs, namedHabits, allIntentions, reflectionByDay, today, now)
    }
    val prevWeek = remember(blockSegs, namedHabits, allIntentions, reflectionByDay, today) {
        InsightsMath.weeklyReview(
            blockSegs, namedHabits, allIntentions, reflectionByDay, DateUtils.addDays(today, -7), now,
        )
    }
    val best = remember(blockSegs) { InsightsMath.bestHourStats(blockSegs, now) }
    val correlations = remember(namedHabits, blockSegs, today) {
        InsightsMath.habitFocusCorrelation(namedHabits, blockSegs, 30, today, now)
    }
    val narrative = remember(namedHabits, blockSegs, allIntentions, today) {
        InsightsMath.narrativeStats(namedHabits, blockSegs, allIntentions, today, now)
    }

    val nowYm = remember(today) { YearMonth.parse(today.substring(0, 7)) }

    PautaSheet(title = tr("Revisão"), onClose = onClose) {
        Text(
            text = tr("A sua semana, de relance."),
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 22.sp,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tr("Sem julgamento. Só o que aconteceu, para reparar no padrão."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        // ── Week ──
        Spacer(Modifier.height(16.dp))
        Text(
            text = I18n.fmtDateShort(LocalDate.parse(week.startKey)) + " – " +
                I18n.fmtDateShort(LocalDate.parse(week.endKey)),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(10.dp))
        ReviewGrid(
            focusValue = FocusMath.fmtDuration(week.focusMs),
            focusCaption = trf(
                "foco · {n} {label}", "n" to week.blockCount,
                "label" to if (week.blockCount == 1) tr("bloco") else tr("blocos"),
            ),
            activeText = "${week.activeDays}/7",
            intText = "${week.intDone}/${week.intTotal}",
            habitText = week.habitPct?.let { "$it%" } ?: "—",
            habitCaption = trf("hábitos · {n} feitos", "n" to week.habitDone),
        )

        // Week-over-week focus delta — only with a previous week on record.
        if (prevWeek.focusMs > 0) {
            val deltaPct = Math.round((week.focusMs - prevWeek.focusMs).toDouble() / prevWeek.focusMs * 100).toInt()
            val prevTxt = FocusMath.fmtDuration(prevWeek.focusMs)
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    deltaPct > 0 -> trf("↑ +{pct}% foco vs. semana anterior ({prev})", "pct" to deltaPct, "prev" to prevTxt)
                    deltaPct < 0 -> trf("↓ {pct}% foco vs. semana anterior ({prev})", "pct" to deltaPct, "prev" to prevTxt)
                    else -> trf("= mesmo foco da semana anterior ({prev})", "prev" to prevTxt)
                },
                color = colors.ink2,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.paper2)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        PeakLine(week.topKey, week.topMs, week.reflections)

        // Marés esta semana — the top three tides with their 7 mini cells.
        val topHabits = remember(namedHabits, week.days) {
            namedHabits
                .map { nh -> nh to week.days.count { k -> HabitCalculator.isActiveOn(nh.model, k) && k in nh.model.log } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)
        }
        if (topHabits.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = tr("Marés esta semana").uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 1.35.sp,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topHabits.forEach { (nh, done) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = nh.name,
                            color = colors.ink,
                            fontFamily = SerifFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.days.forEach { k ->
                                val cellColor = when {
                                    k in nh.model.log -> colors.accent
                                    k in nh.model.respiros -> colors.accent.copy(alpha = 0.33f)
                                    else -> colors.rule
                                }
                                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(cellColor))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("$done/7", color = colors.ink3, fontFamily = MonoFamily, fontSize = 10.sp)
                    }
                }
            }
        }
        val weekRespiros = remember(namedHabits, week.days) {
            namedHabits.sumOf { nh -> week.days.count { it in nh.model.respiros } }
        }
        if (weekRespiros > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (weekRespiros == 1) tr("1 respiro esta semana. Honesto.")
                else trf("{n} respiros esta semana. Honesto.", "n" to weekRespiros),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
        }

        // ── Month ──
        Spacer(Modifier.height(22.dp))
        SheetEyebrow(tr("Mês"))
        Spacer(Modifier.height(8.dp))
        var monthYm by remember { mutableStateOf(nowYm) }
        MonthNav(label = monthLabel(monthYm), canForward = monthYm < nowYm, onPrev = { monthYm = monthYm.minusMonths(1) }, onNext = { monthYm = monthYm.plusMonths(1) })
        Spacer(Modifier.height(10.dp))
        val month = remember(blockSegs, namedHabits, allIntentions, reflectionByDay, monthYm, today) {
            InsightsMath.monthReview(
                blockSegs, namedHabits, allIntentions, reflectionByDay,
                monthYm.year, monthYm.monthValue, today, now,
            )
        }
        ReviewGrid(
            focusValue = FocusMath.fmtDuration(month.focusMs),
            focusCaption = tr("foco"),
            activeText = month.activeDays.toString(),
            intText = "${month.intDone}/${month.intTotal}",
            habitText = month.habitPct?.let { "$it%" } ?: "—",
            habitCaption = trf("hábitos · {n} feitos", "n" to month.tideDoneDays),
        )
        PeakLine(month.topKey, month.topMs, month.reflections)

        // ── Patterns ──
        Spacer(Modifier.height(22.dp))
        SheetEyebrow(tr("Padrões"))
        Spacer(Modifier.height(10.dp))
        val lines = buildList {
            if (narrative.topHabitName != null) {
                add(trf("A tua maré mais constante é {name} — {n} dias seguidos.", "name" to narrative.topHabitName, "n" to narrative.topHabitDays))
            }
            if (narrative.peakHour != null) add(trf("Focas mais por volta das {h}h.", "h" to narrative.peakHour))
            if (narrative.highPrioPct != null) add(trf("Concluíste {pct}% das intenções de prioridade alta.", "pct" to narrative.highPrioPct))
        }
        if (lines.isEmpty()) {
            EmptyNote(tr("Ainda a juntar padrões. Continue a usar a app — em poucos dias aparecem aqui."))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { line ->
                    Row {
                        Text("—", color = colors.accent, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(line, color = colors.ink, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                }
            }
        }

        // ── Best hour ──
        Spacer(Modifier.height(22.dp))
        SheetEyebrow(tr("Melhor hora do dia"))
        Spacer(Modifier.height(10.dp))
        if (best.total <= 0L) {
            EmptyNote(tr("Ainda sem blocos suficientes para encontrar a sua melhor hora."))
        } else {
            BestHourChart(best)
        }

        // ── Habits × focus ──
        Spacer(Modifier.height(22.dp))
        SheetEyebrow(tr("Hábitos × foco"))
        Spacer(Modifier.height(10.dp))
        if (correlations.isEmpty()) {
            EmptyNote(tr("Sem padrões suficientes ainda. Continue a registar hábitos e blocos — em poucas semanas aparecem aqui ligações."))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                correlations.take(4).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildAnnotatedString {
                                append(tr("Nos dias com") + " ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(r.name) }
                                append(tr(", foca") + " ")
                                withStyle(
                                    SpanStyle(color = if (r.deltaPct > 0) colors.accent else colors.ink2, fontWeight = FontWeight.SemiBold),
                                ) {
                                    append((if (r.deltaPct > 0) "+" else "") + "${r.deltaPct}%.")
                                }
                            },
                            color = colors.ink,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(FocusMath.fmtDuration(r.doneAvg), color = colors.ink2, fontFamily = MonoFamily, fontSize = 9.sp)
                            Text("vs " + FocusMath.fmtDuration(r.missAvg), color = colors.ink3, fontFamily = MonoFamily, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // ── Focus calendar ──
        Spacer(Modifier.height(22.dp))
        SheetEyebrow(tr("Calendário de foco"))
        Spacer(Modifier.height(8.dp))
        var calYm by remember { mutableStateOf(nowYm) }
        MonthNav(label = monthLabel(calYm), canForward = calYm < nowYm, onPrev = { calYm = calYm.minusMonths(1) }, onNext = { calYm = calYm.plusMonths(1) })
        Spacer(Modifier.height(10.dp))
        FocusCalendar(blockSegs = blockSegs, ym = calYm, today = today, now = now)
        Spacer(Modifier.height(6.dp))
    }
}

// ─── pieces ────────────────────────────────────────────────

/** The 2×2 review grid: foco / dias activos / intenções / hábitos. */
@Composable
private fun ReviewGrid(
    focusValue: String,
    focusCaption: String,
    activeText: String,
    intText: String,
    habitText: String,
    habitCaption: String,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
            .background(colors.rule),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            ReviewCell(focusValue, focusCaption, accent = true, Modifier.weight(1f))
            ReviewCell(activeText, tr("dias activos"), accent = false, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            ReviewCell(intText, tr("intenções feitas"), accent = false, Modifier.weight(1f))
            ReviewCell(habitText, habitCaption, accent = true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewCell(value: String, caption: String, accent: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    Column(modifier.background(colors.paper).padding(horizontal = 14.dp, vertical = 14.dp)) {
        Text(
            text = value,
            color = if (accent) colors.accent else colors.ink,
            fontFamily = SerifFamily,
            fontSize = 24.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption.uppercase(),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 0.72.sp,
        )
    }
}

/** "Pico: segunda, 2 jun com 2h30 em foco." + reflections written. */
@Composable
private fun PeakLine(topKey: String?, topMs: Long, reflections: Int) {
    val colors = LocalPautaColors.current
    if (topKey == null || topMs <= 0L) return
    Spacer(Modifier.height(10.dp))
    var text = trf(
        "Pico: {d} com {t} em foco.",
        "d" to I18n.fmtDateLong(LocalDate.parse(topKey)),
        "t" to FocusMath.fmtDuration(topMs),
    )
    if (reflections > 0) {
        text += " " + trf(
            "Escreveu {n} {label}.",
            "n" to reflections,
            "label" to if (reflections == 1) tr("reflexão") else tr("reflexões"),
        )
    }
    Text(text, color = colors.ink2, fontFamily = SerifFamily, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun MonthNav(label: String, canForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.ChevronLeft, contentDescription = tr("mês anterior"), tint = colors.ink3,
            modifier = Modifier.size(22.dp).clickableNoRipple(onPrev),
        )
        Text(
            text = label,
            color = colors.ink2,
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.44.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.ChevronRight, contentDescription = tr("mês seguinte"),
            tint = if (canForward) colors.ink3 else colors.rule,
            modifier = Modifier
                .size(22.dp)
                .then(if (canForward) Modifier.clickableNoRipple(onNext) else Modifier),
        )
    }
}

/** "Junho '26" — the web's calendar header label. */
private fun monthLabel(ym: YearMonth): String =
    I18n.fmtMonthLong(ym.monthValue) + " '" + (ym.year % 100).toString().padStart(2, '0')

@Composable
private fun EmptyNote(text: String) {
    val colors = LocalPautaColors.current
    Text(
        text = text,
        color = colors.ink3,
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

/** 24 bars (dead early hours skipped, like the web): accent on the peak. */
@Composable
private fun BestHourChart(best: InsightsMath.BestHours) {
    val colors = LocalPautaColors.current
    val firstNz = best.hours.indexOfFirst { it > 0 }
    val startH = minOf(6, if (firstNz < 0) 6 else firstNz)
    val maxMs = best.hours.max().coerceAtLeast(1L)

    Text(
        text = "%02d:00–%02d:00".format(best.peak, (best.peak + 1) % 24),
        color = colors.accent,
        fontFamily = SerifFamily,
        fontSize = 22.sp,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = tr("a sua hora mais focada"),
        color = colors.ink3,
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().height(70.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (startH..23).forEach { h ->
            val v = best.hours[h]
            val barH = maxOf(3f, v.toFloat() / maxMs * 64f)
            Box(
                Modifier
                    .weight(1f)
                    .height(barH.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(if (h == best.peak) colors.accent else colors.ink.copy(alpha = 0.5f)),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (startH..23).forEach { h ->
            Text(
                text = if (h == best.peak || h % 6 == 0) h.toString() else "",
                color = if (h == best.peak) colors.accent else colors.ink4,
                fontFamily = MonoFamily,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The month heat grid: Sunday-first columns, four accent levels, today ring. */
@Composable
private fun FocusCalendar(blockSegs: List<InsightsMath.BlockSegs>, ym: YearMonth, today: String, now: Long) {
    val colors = LocalPautaColors.current
    data class Cell(val d: Int, val key: String, val ms: Long, val isToday: Boolean, val future: Boolean)

    val (cells, maxMs, totalMs, activeDays) = remember(blockSegs, ym, today) {
        val nd = ym.lengthOfMonth()
        val firstDow = LocalDate.of(ym.year, ym.monthValue, 1).dayOfWeek.value % 7 // 0=Sun
        val arr = mutableListOf<Cell?>()
        repeat(firstDow) { arr.add(null) }
        var mx = 0L
        var tot = 0L
        var act = 0
        for (d in 1..nd) {
            val key = "%04d-%02d-%02d".format(ym.year, ym.monthValue, d)
            val ms = InsightsMath.dailyFocus(blockSegs, key, now)
            if (ms > mx) mx = ms
            tot += ms
            if (ms > 0) act++
            arr.add(Cell(d, key, ms, key == today, key > today))
        }
        Quad(arr.toList(), mx, tot, act)
    }

    fun level(ms: Long): Int {
        if (ms <= 0L) return 0
        if (maxMs <= 0L) return 1
        val r = ms.toDouble() / maxMs
        return if (r > 0.66) 4 else if (r > 0.33) 3 else 2
    }
    val mixes = listOf(0f, 0.25f, 0.45f, 0.70f, 1f)

    // Day-of-week letters, Sunday-first ("d,s,t,q,q,s,s").
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tr("d,s,t,q,q,s,s").split(",").forEach { l ->
            Text(
                text = l,
                color = colors.ink4,
                fontFamily = MonoFamily,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowCells.forEach { c ->
                    if (c == null) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val lv = level(c.ms)
                        val bg = if (lv == 0) Color.Transparent else lerp(colors.paper, colors.accent, mixes[lv])
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .alpha(if (c.future) 0.4f else 1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .then(
                                    when {
                                        c.isToday -> Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(6.dp))
                                        lv == 0 -> Modifier.border(1.dp, colors.rule, RoundedCornerShape(6.dp))
                                        else -> Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = c.d.toString(),
                                color = if (lv >= 3) colors.onDark else colors.ink3,
                                fontFamily = MonoFamily,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
                repeat(7 - rowCells.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = trf(
            "{focus} em foco · {days} {label}",
            "focus" to FocusMath.fmtDuration(totalMs),
            "days" to activeDays,
            "label" to if (activeDays == 1) tr("dia activo") else tr("dias activos"),
        ),
        color = colors.ink3,
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    )
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
