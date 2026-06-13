package com.pauta.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PeriodLabel
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.CellState
import com.pauta.app.ui.cellStateFor
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.YearMonth

/**
 * The Marés (tides) tab, to the web grid's spec (tab-mares.jsx): serif month
 * header with the "Maré actual/passada" eyebrow and the overall % block, the
 * how-it-works hint, and one row per habit — name + recurrence/count chips,
 * month % with the maturity progress or the tier badge, the 22dp day strip
 * with all nine cell states, and the best-streak line. Tap marks done /
 * increments; long-press an empty day marks a respiro; tapping the name opens the
 * detail sheet (where edit / archive / remove live — A7 dropped the
 * undiscoverable long-press-to-delete). // PT: tab Marés segundo a grelha da web.
 */
@Composable
fun MaresScreen() {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val logs by vm.habitLogs.collectAsStateWithLifecycle()
    val respiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val counts by vm.habitCounts.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    // A3: cell fills, respiro hatching and row add/remove all snap when reduced.
    // // PT: animações das células respeitam "movimento reduzido".
    val animate = !prefs.reducedMotion

    val nowYm = remember(today) { YearMonth.parse(today.substring(0, 7)) }
    var year by remember { mutableIntStateOf(nowYm.year) }
    var month by remember { mutableIntStateOf(nowYm.monthValue) }
    var showAdd by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var detailTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var editTarget by remember { mutableStateOf<HabitEntity?>(null) }

    val logsByHabit = remember(logs) { logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    val respByHabit = remember(respiros) { respiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    val countsByHabit = remember(counts) {
        counts.groupBy { it.habitId }.mapValues { e -> e.value.associate { it.dayKey to it.count } }
    }
    fun modelOf(h: HabitEntity) = HabitModel(
        id = h.id, createdAt = h.createdAt, cadence = h.cadence, anchor = h.anchor, weekdays = h.weekdays,
        recurrence = h.recurrence, endsAt = h.endsAt,
        log = logsByHabit[h.id].orEmpty(), respiros = respByHabit[h.id].orEmpty(),
    )

    val isCurrentMonth = year == nowYm.year && month == nowYm.monthValue
    val monthEnd = "%04d-%02d-%02d".format(year, month, DateUtils.daysInMonth(year, month))
    // Only tides that already existed in the viewed month; the rest are counted
    // in the footer note, like the web. // PT: só marés que já existiam no mês.
    val visibleHabits = habits.filter { HabitCalculator.createdKey(modelOf(it)) <= monthEnd }
    val models = visibleHabits.map { modelOf(it) }
    val overall = HabitCalculator.overallPctInMonth(models, year, month, today)

    Box(Modifier.fillMaxSize()) {
        // A single LazyColumn; horizontal content padding replaces the per-section
        // padding the old Column applied. The per-habit month strip stays a nested
        // horizontalScroll Row inside its item. // PT: LazyColumn única; tiras
        // mensais continuam em scroll horizontal dentro do item.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            item(key = "top") { Spacer(Modifier.height(8.dp)) }

            // Month navigation (stands in for the web's MonthStrip).
            item(key = "month-nav") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft, contentDescription = tr("mês anterior"), tint = colors.ink3,
                        modifier = Modifier.size(26.dp).clickableNoRipple {
                            val ym = YearMonth.of(year, month).minusMonths(1); year = ym.year; month = ym.monthValue
                        },
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .clickableNoRipple { year = nowYm.year; month = nowYm.monthValue },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Accent "JUN '26" period label — the web MonthStrip styling,
                        // shared with Hoje/Pauta. // PT: mês em destaque, como nas outras tabs.
                        PeriodLabel(
                            month = I18n.fmtMonthShort(month),
                            suffix = "'%02d".format(year % 100),
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = tr("mês seguinte"), tint = colors.ink3,
                        modifier = Modifier.size(26.dp).clickableNoRipple {
                            val ym = YearMonth.of(year, month).plusMonths(1); year = ym.year; month = ym.monthValue
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    GridLegend()
                }
            }

            // Header — eyebrow + serif month, with the overall % at the right.
            item(key = "header") {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = (if (isCurrentMonth) tr("Maré actual") else tr("Maré passada")).uppercase(),
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 10.sp,
                            letterSpacing = 1.8.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = monthLongName(month),
                            color = colors.ink,
                            fontFamily = SerifFamily,
                            fontSize = 38.sp,
                            lineHeight = 38.sp,
                            letterSpacing = (-0.57).sp, // -0.015em of 38sp
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.clickableNoRipple { showTrend = true },
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                if (overall == null) append("—") else {
                                    append(overall.toString())
                                    withStyle(SpanStyle(fontSize = 14.sp, color = colors.ink3)) { append("%") }
                                }
                            },
                            color = if (overall == null) colors.ink3 else colors.accent,
                            fontFamily = SerifFamily,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("marés passadas").uppercase() + " ↗",
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 1.35.sp, // 0.15em of 9sp
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            if (habits.isEmpty()) {
                // The web's empty state: an intro phrase, the explanation,
                // and the "Marés comuns" starter chips. // PT: estado vazio
                // com frase, explicação e marés comuns.
                item(key = "empty") {
                    Text(
                        text = tr(introPhraseFor(today)),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = tr("Adicione comportamentos que quer praticar regularmente. Cada mês tem o seu grid."),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = tr("Marés comuns").uppercase(),
                        color = colors.ink4,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 1.44.sp, // 0.16em of 9sp
                    )
                    Spacer(Modifier.height(9.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Beber água", "Ler", "Meditar", "Exercício", "Dormir cedo").forEach { name ->
                            StarterChip(tr(name)) { vm.addHabit(name = tr(name)) }
                        }
                    }
                }
            } else {
                // Como funciona — persistent, subtle hint.
                item(key = "hint") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.paper2)
                            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.ink2)) { append(tr("toque")) }
                                append(" " + tr("marca feito") + " · ")
                                withStyle(SpanStyle(color = colors.ink2)) { append(tr("pressão longa")) }
                                append(" " + tr("num dia vazio marca respiro"))
                            },
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 10.sp,
                            letterSpacing = 0.4.sp,
                            lineHeight = 15.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("dias passados são editáveis — a honestidade é o melhor amigo da maré."),
                            color = colors.ink3,
                            fontFamily = SerifFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // One item per tide, keyed by id; a 22dp gap before every row but
                // the first reproduces the old spacedBy(22). // PT: um item por
                // maré, com chave estável.
                itemsIndexed(visibleHabits, key = { _, h -> "habit-${h.id}" }) { index, h ->
                    // A3: the whole item (its leading gap + row) slides on add/remove.
                    Column(if (animate) Modifier.animateItem() else Modifier) {
                        if (index > 0) Spacer(Modifier.height(22.dp))
                        MaresHabitRow(
                            habit = h,
                            model = modelOf(h),
                            countsForHabit = countsByHabit[h.id].orEmpty(),
                            year = year,
                            month = month,
                            today = today,
                            isCurrentMonth = isCurrentMonth,
                            animate = animate,
                            onToggle = { dayKey -> vm.toggleHabitDay(h.id, dayKey) },
                            onIncrement = { dayKey, current -> vm.setHabitCount(h.id, dayKey, current + 1) },
                            onRespiro = { dayKey -> vm.markRespiro(h.id, dayKey) },
                            onUnmarkRespiro = { dayKey -> vm.unmarkRespiro(h.id, dayKey) },
                            onOpenDetail = { detailTarget = h },
                        )
                    }
                }

                if (habits.size > visibleHabits.size) {
                    item(key = "hidden-note") {
                        val hidden = habits.size - visibleHabits.size
                        Spacer(Modifier.height(18.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "$hidden " +
                                (if (hidden == 1) tr("maré ainda não existia") else tr("marés ainda não existiam")) +
                                " " + trf("em {month}.", "month" to monthLongName(month)),
                            color = colors.ink3,
                            fontFamily = SerifFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // The web's dashed full-width "adicionar maré" button.
            item(key = "add") {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .dashedRectBorder(colors.rule, 12.dp)
                        .clickableNoRipple { showAdd = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", color = colors.ink3, fontSize = 16.sp, lineHeight = 16.sp)
                    Text(tr("adicionar maré"), color = colors.ink3, fontSize = 14.sp)
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
        }
    }

    detailTarget?.let { h ->
        HabitDetailSheet(
            habit = h,
            model = modelOf(h),
            countsForHabit = countsByHabit[h.id].orEmpty(),
            today = today,
            onToggleDay = { k -> vm.toggleHabitDay(h.id, k) },
            onIncrementDay = { k, c -> vm.setHabitCount(h.id, k, c + 1) },
            onMarkRespiro = { k -> vm.markRespiro(h.id, k) },
            onUnmarkRespiro = { k -> vm.unmarkRespiro(h.id, k) },
            onEdit = { editTarget = h; detailTarget = null },
            onClose = { detailTarget = null },
        )
    }
    editTarget?.let { h ->
        EditHabitSheet(
            habit = h,
            onSave = { updated -> vm.updateHabit(updated); editTarget = null },
            onArchive = { vm.setHabitArchived(h.id, true); editTarget = null },
            onRemove = { removeTarget = h; editTarget = null },
            onClose = { editTarget = null },
        )
    }
    if (showTrend) {
        TrendSheet(
            habits = habits.map { modelOf(it) },
            today = today,
            onPickMonth = { y, m -> year = y; month = m },
            onClose = { showTrend = false },
        )
    }
    if (showAdd) {
        AddHabitSheet(
            onSubmit = { d ->
                vm.addHabit(
                    name = d.name, time = d.time, cadence = d.cadence, anchor = d.anchor,
                    weekdays = d.weekdays, target = d.target, unit = d.unit, clock = d.clock,
                    recurrence = d.recurrence, endsAt = d.endsAt, description = d.description,
                )
                showAdd = false
            },
            onClose = { showAdd = false },
        )
    }
    removeTarget?.let { h ->
        PautaSheet(title = tr("Marés"), onClose = { removeTarget = null }) {
            Text(
                text = tr("Remover esta maré? Todo o histórico será perdido."),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 18.sp,
                lineHeight = 25.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { removeTarget = null }
                PautaButton(tr("remover"), Modifier.weight(2f), PautaButtonVariant.InkPrimary) {
                    vm.removeHabit(h.id)
                    removeTarget = null
                }
            }
        }
    }
}

private data class CellDay(val d: Int, val key: String, val state: CellState, val isToday: Boolean, val count: Int)

@Composable
private fun MaresHabitRow(
    habit: HabitEntity,
    model: HabitModel,
    countsForHabit: Map<String, Int>,
    year: Int,
    month: Int,
    today: String,
    isCurrentMonth: Boolean,
    animate: Boolean,
    onToggle: (String) -> Unit,
    onIncrement: (String, Int) -> Unit,
    onRespiro: (String) -> Unit,
    onUnmarkRespiro: (String) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val accent = habit.color
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: colors.accent

    val ndays = DateUtils.daysInMonth(year, month)
    val isCount = habit.target != null && habit.cadence == "daily"
    val todayCount = countsForHabit[today] ?: 0

    val pct = HabitCalculator.pctInMonth(model, year, month, today)
    val range = HabitCalculator.observedRangeInMonth(model, year, month, today)
    val stats = range?.let { HabitCalculator.periodStats(model, it.first, it.second) }
    val obs = (stats?.observed ?: 0) - (stats?.respiros ?: 0)
    val maturityTotal = HabitCalculator.maturityUnits(model)
    val isMature = obs >= maturityTotal
    val streak = if (isCurrentMonth) HabitCalculator.currentStreak(model, today) else null
    val bestStreak = HabitCalculator.bestStreak(model, today)

    val days = remember(model, countsForHabit, year, month, today) {
        (1..ndays).map { d ->
            val key = "%04d-%02d-%02d".format(year, month, d)
            val state = cellStateFor(model, habit.cadence, isCount, countsForHabit, key, today)
            CellDay(d, key, state, key == today, countsForHabit[key] ?: 0)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    // Tap the name for the full history; edit/archive/remove live
                    // inside that detail → edit sheet now (A7). // PT: toca no nome
                    // para o histórico; editar/arquivar/remover estão lá dentro.
                    .clickableNoRipple(onClick = onOpenDetail),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.name,
                        color = colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                    )
                    cadenceChipLabel(habit)?.let { label ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 0.54.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (isCount) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$todayCount/${habit.target}" + (habit.unit.ifBlank { tr("×") }.let { if (habit.unit.isNotBlank()) " $it" else it }),
                            color = accent,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 0.54.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (habit.time.isNotBlank() || habit.clock.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (habit.clock.isNotBlank()) {
                            Text(habit.clock, color = colors.ink3, fontFamily = MonoFamily, fontSize = 11.sp)
                            if (habit.time.isNotBlank()) Spacer(Modifier.width(6.dp))
                        }
                        if (habit.time.isNotBlank()) {
                            Text(habit.time, color = colors.ink3, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (pct == null) {
                    Text("—", color = colors.ink3, fontFamily = MonoFamily, fontSize = 10.sp)
                } else {
                    Text(
                        text = "$pct%",
                        color = if (isMature) colors.ink2 else colors.ink3,
                        fontFamily = MonoFamily,
                        fontStyle = if (isMature) FontStyle.Normal else FontStyle.Italic,
                        fontSize = 11.sp,
                    )
                    if (!isMature) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when (habit.cadence) {
                                "weekly" -> trf("semana {obs}/{total}", "obs" to obs, "total" to maturityTotal)
                                "monthly" -> trf("mês {obs}/{total}", "obs" to obs, "total" to maturityTotal)
                                else -> trf("dia {obs}/{total}", "obs" to obs, "total" to maturityTotal)
                            },
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                        )
                    } else if (streak != null && streak.days >= 1) {
                        HabitCalculator.tideTier(streak.days)?.let { tier ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = tr(tier.name).uppercase(),
                                color = accent,
                                fontFamily = MonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.54.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("${streak.units} ${streak.unit}")
                                    if (streak.respiros > 0) {
                                        withStyle(SpanStyle(color = colors.ink3)) {
                                            append(" · ${streak.respiros} " + tr("resp."))
                                        }
                                    }
                                },
                                color = colors.ink2,
                                fontFamily = MonoFamily,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Month strip — the pulse of days, auto-scrolled so today is visible.
        val strip = rememberScrollState()
        val density = LocalDensity.current
        if (isCurrentMonth) {
            val todayD = today.substring(8).toInt()
            LaunchedEffect(year, month) {
                strip.scrollTo(with(density) { ((todayD - 1) * 25).dp.toPx().toInt() - 150.dp.toPx().toInt() }.coerceAtLeast(0))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(strip)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            days.forEach { day ->
                MaresDayCell(
                    day = day,
                    accent = accent,
                    target = if (isCount) habit.target else null,
                    animate = animate,
                    onTap = {
                        when {
                            isCount -> if (day.state == CellState.RESPIRO) onUnmarkRespiro(day.key) else onIncrement(day.key, day.count)
                            day.state == CellState.EMPTY || day.state == CellState.DONE -> onToggle(day.key)
                            day.state == CellState.RESPIRO -> onUnmarkRespiro(day.key)
                        }
                    },
                    onLongPress = { if (day.state == CellState.EMPTY) onRespiro(day.key) },
                )
            }
        }

        // Best streak.
        if (bestStreak >= 3) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append(trf("melhor: {n} dias", "n" to bestStreak))
                    HabitCalculator.tideTier(bestStreak)?.let {
                        withStyle(SpanStyle(color = colors.ink4)) { append(" · " + tr(it.name)) }
                    }
                },
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 0.72.sp, // 0.08em of 9sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaresDayCell(
    day: CellDay,
    accent: Color,
    target: Int?,
    animate: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val state = day.state
    val filled = state == CellState.DONE
    val clickable = state == CellState.EMPTY || state == CellState.DONE ||
        state == CellState.RESPIRO || state == CellState.PARTIAL
    val partialFrac = if (state == CellState.PARTIAL && target != null && target > 0) {
        (day.count.toFloat() / target).coerceAtMost(1f)
    } else 0f

    val cellAlpha = when (state) {
        CellState.FUTURE -> 0.45f
        CellState.PRE -> 0.55f
        CellState.AFTER -> 0.35f
        CellState.OFF, CellState.LOCKED -> 0.3f
        else -> 1f
    }
    // Today's accent ring only on actionable days; a soft glow when done.
    val borderColor: Color? = when {
        day.isToday && !filled && state != CellState.OFF -> accent
        state == CellState.EMPTY || state == CellState.PRE -> colors.ink3
        state == CellState.AFTER || state == CellState.LOCKED -> colors.rule
        state == CellState.RESPIRO || state == CellState.PARTIAL -> colors.ink3
        else -> null // DONE (none), FUTURE (dashed below), OFF (none)
    }
    val borderWidth = if (day.isToday && !filled && state != CellState.OFF) 1.5.dp else 1.dp

    val fillColor = if (day.isToday) accent else colors.ink
    // A3: a marked day's fill springs out from the centre; a respiro's hatch
    // wipes in along the diagonal. Reduced motion snaps both to their final look.
    // // PT: o preenchimento nasce do centro; o respiro entra na diagonal.
    val fillScale by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = if (animate) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ) else snap(),
        label = "cell-fill",
    )
    val hatch by animateFloatAsState(
        targetValue = if (state == CellState.RESPIRO) 1f else 0f,
        animationSpec = if (animate) tween(300) else snap(),
        label = "cell-hatch",
    )

    Box(
        Modifier
            .size(22.dp)
            .alpha(cellAlpha)
            .drawBehind {
                // The accent/ink fill, scaled from the centre by the spring (clamped
                // so a bouncy overshoot never spills into the 3dp gap).
                if (fillScale > 0f) {
                    val s = fillScale.coerceIn(0f, 1f)
                    val w = size.width * s
                    val h = size.height * s
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                if (day.isToday && fillScale > 0f) {
                    // boxShadow 0 0 0 2px accent@20% — a ring just outside the cell,
                    // growing in step with the fill. // PT: anel cresce com o preenchimento.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.2f * fillScale.coerceIn(0f, 1f)),
                        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                        size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (state == CellState.FUTURE) {
                    drawRoundRect(
                        color = colors.rule,
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                    )
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .then(borderColor?.let { Modifier.border(borderWidth, it, RoundedCornerShape(4.dp)) } ?: Modifier)
            .then(
                if (clickable) Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CellState.PRE -> Box(
                Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(colors.ink3.copy(alpha = 0.7f)),
            )
            CellState.OFF -> Box(
                Modifier.size(width = 10.dp, height = 2.dp).clip(RoundedCornerShape(2.dp)).background(colors.rule),
            )
            CellState.RESPIRO -> Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // The web's 45° hatch pattern.
                        val path = Path().apply {
                            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(4.dp.toPx())))
                        }
                        clipPath(path) {
                            val step = 4.dp.toPx()
                            val startX = -size.height
                            // Only lines up to this x are drawn, so the hatch wipes
                            // in diagonally as `hatch` runs 0→1. // PT: entra na diagonal.
                            val threshold = startX + (size.width - startX) * hatch.coerceIn(0f, 1f)
                            var x = startX
                            while (x < size.width) {
                                if (x <= threshold) {
                                    drawLine(
                                        color = accent.copy(alpha = 0.7f),
                                        start = Offset(x, size.height),
                                        end = Offset(x + size.height, 0f),
                                        strokeWidth = 1.4.dp.toPx(),
                                    )
                                }
                                x += step
                            }
                        }
                    },
            )
            CellState.PARTIAL -> {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((22 * maxOf(0.15f, partialFrac)).dp)
                        .background(accent.copy(alpha = 0.55f)),
                )
                Text(
                    text = day.count.toString(),
                    color = if (partialFrac >= 0.6f) colors.paper else colors.ink2,
                    fontFamily = MonoFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 8.sp,
                )
            }
            else -> {}
        }
    }
}

/** "semanal · domingo" / "mensal · dia 1" — the web's cadence chip label;
 *  null for daily tides (no chip). */
private fun cadenceChipLabel(habit: HabitEntity): String? {
    if (habit.cadence != "weekly" && habit.cadence != "monthly") return null
    val base = if (habit.cadence == "weekly") tr("semanal") else tr("mensal")
    val anchor = habit.anchor ?: return base
    return if (habit.cadence == "weekly") {
        val days = listOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
        base + " · " + tr(days.getOrElse(anchor) { "" })
    } else {
        base + " · " + trf("dia {n}", "n" to anchor)
    }
}

private fun monthLongName(month: Int): String = I18n.fmtMonthLong(month)

/** The empty state's intro phrase (mares-phrases.jsx `intro`), picked
 *  deterministically by day so it doesn't change every render. */
private val INTRO_PHRASES = listOf(
    "As marés têm história. Sobem e descem ao longo do ano.",
    "Toda onda começa pequena.",
    "Pés na água. O resto vem com o tempo.",
)

private fun introPhraseFor(dayKey: String): String =
    INTRO_PHRASES[((dayKey.hashCode() % INTRO_PHRASES.size) + INTRO_PHRASES.size) % INTRO_PHRASES.size]

/** A dashed rounded-rect outline (the web's `border: 1.5px dashed var(--rule)`). */
private fun Modifier.dashedRectBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            ),
        )
    },
)

// ─── Grid legend ───────────────────────────────────────────
// tab-mares.jsx GridLegend: a small toggle (three mini swatches + "legenda")
// opening a popover that explains all nine cell states. // PT: a legenda da
// grelha, igual à web.
@Composable
private fun GridLegend() {
    val colors = LocalPautaColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (open) colors.paper2 else Color.Transparent)
                .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                .clickableNoRipple { open = !open }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            LegendSwatch { drawDone(colors.ink) }
            LegendSwatch { drawEmptyBox(colors.ink3) }
            LegendSwatch { drawPre(colors.ink3) }
            Text(
                text = tr("legenda").uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 0.72.sp, // 0.08em of 9sp
                modifier = Modifier.padding(start = 3.dp),
            )
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(LocalDensity.current) { 34.dp.roundToPx() }),
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .width(210.dp)
                        .shadow(12.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.paper)
                        .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    LegendRow(tr("feito")) { drawDone(colors.ink) }
                    LegendRow(tr("feito hoje")) { drawDoneToday(colors.accent) }
                    LegendRow(tr("não feito")) { drawEmptyBox(colors.ink3) }
                    LegendRow(tr("hoje (por fazer)")) { drawTodayPending(colors.accent) }
                    LegendRow(tr("respiro")) { drawRespiro(colors.ink3, colors.accent) }
                    LegendRow(tr("antes da maré")) { drawPre(colors.ink3) }
                    LegendRow(tr("fora do horário")) { drawOff(colors.rule) }
                    LegendRow(tr("ainda não chegou"), last = true) { drawFuture(colors.rule) }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = tr("Pressão longa num dia não feito para marcar respiro."),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.size(9.dp)) { draw() }
}

@Composable
private fun LegendRow(label: String, last: Boolean = false, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val colors = LocalPautaColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) { LegendSwatch(draw) }
            Text(
                text = label,
                color = colors.ink2,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.22.sp, // 0.02em of 11sp
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

// Tiny 9×9 swatch painters mirroring the web's legend kinds.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.swatchRadius() = CornerRadius(2.dp.toPx())

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDone(ink: Color) {
    drawRoundRect(ink, cornerRadius = swatchRadius())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoneToday(accent: Color) {
    drawRoundRect(accent.copy(alpha = 0.2f), cornerRadius = CornerRadius(3.dp.toPx()), topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()), size = Size(size.width + 3.dp.toPx(), size.height + 3.dp.toPx()))
    drawRoundRect(accent, cornerRadius = swatchRadius())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyBox(ink3: Color) {
    drawRoundRect(ink3, cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTodayPending(accent: Color) {
    drawRoundRect(accent, cornerRadius = swatchRadius(), style = Stroke(width = 1.5.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRespiro(ink3: Color, accent: Color) {
    drawRoundRect(ink3, cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
    val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, swatchRadius())) }
    clipPath(path) {
        var x = -size.height
        while (x < size.width) {
            drawLine(accent.copy(alpha = 0.7f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
            x += 3.dp.toPx()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPre(ink3: Color) {
    drawRoundRect(ink3.copy(alpha = 0.5f), cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
    drawCircle(ink3.copy(alpha = 0.7f), radius = 1.25.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOff(rule: Color) {
    val w = 7.dp.toPx(); val h = 2.dp.toPx()
    drawRoundRect(rule, topLeft = Offset((size.width - w) / 2, (size.height - h) / 2), size = Size(w, h), cornerRadius = CornerRadius(1.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFuture(rule: Color) {
    drawRoundRect(
        rule.copy(alpha = 0.5f), cornerRadius = swatchRadius(),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
    )
}
