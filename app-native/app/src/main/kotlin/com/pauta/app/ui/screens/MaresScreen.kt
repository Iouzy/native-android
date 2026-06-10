package com.pauta.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.MarkKind
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.combinedClickableNoRipple
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
 * increments; long-press an empty day marks a respiro; long-press the name
 * removes (with confirmation). // PT: tab Marés segundo a grelha da web.
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

    val nowYm = remember(today) { YearMonth.parse(today.substring(0, 7)) }
    var year by remember { mutableIntStateOf(nowYm.year) }
    var month by remember { mutableIntStateOf(nowYm.monthValue) }
    var showAdd by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<HabitEntity?>(null) }

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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            // Month navigation (stands in for the web's MonthStrip).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.ChevronLeft, contentDescription = tr("mês anterior"), tint = colors.ink3,
                    modifier = Modifier.size(26.dp).clickableNoRipple {
                        val ym = YearMonth.of(year, month).minusMonths(1); year = ym.year; month = ym.monthValue
                    },
                )
                Text(
                    text = I18n.fmtMonthYear(year, month),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    letterSpacing = 0.44.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickableNoRipple { year = nowYm.year; month = nowYm.monthValue },
                )
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = tr("mês seguinte"), tint = colors.ink3,
                    modifier = Modifier.size(26.dp).clickableNoRipple {
                        val ym = YearMonth.of(year, month).plusMonths(1); year = ym.year; month = ym.monthValue
                    },
                )
            }

            Column(Modifier.padding(horizontal = 24.dp)) {
                // Header — eyebrow + serif month, with the overall % at the right.
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

                if (habits.isEmpty()) {
                    Text(tr("Sem marés ainda. Toca em + para criar a primeira."), color = colors.ink4, fontSize = 14.sp)
                } else {
                    // Como funciona — persistent, subtle hint.
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

                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        visibleHabits.forEach { h ->
                            MaresHabitRow(
                                habit = h,
                                model = modelOf(h),
                                countsForHabit = countsByHabit[h.id].orEmpty(),
                                year = year,
                                month = month,
                                today = today,
                                isCurrentMonth = isCurrentMonth,
                                onToggle = { dayKey -> vm.toggleHabitDay(h.id, dayKey) },
                                onIncrement = { dayKey, current -> vm.setHabitCount(h.id, dayKey, current + 1) },
                                onRespiro = { dayKey -> vm.markRespiro(h.id, dayKey) },
                                onUnmarkRespiro = { dayKey -> vm.unmarkRespiro(h.id, dayKey) },
                                onRemove = { removeTarget = h },
                            )
                        }
                    }

                    if (habits.size > visibleHabits.size) {
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
            Spacer(Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            containerColor = colors.accent,
            contentColor = colors.onDark,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = tr("Nova maré")) }
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
        AddHabitDialog(
            onAdd = { name, cadence, target, unit ->
                vm.addHabit(name = name, cadence = cadence, target = target, unit = unit)
                showAdd = false
            },
            onDismiss = { showAdd = false },
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

/** Cell states, the web's nine. */
private enum class CellState { DONE, EMPTY, RESPIRO, PARTIAL, LOCKED, OFF, PRE, AFTER, FUTURE }
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
    onToggle: (String) -> Unit,
    onIncrement: (String, Int) -> Unit,
    onRespiro: (String) -> Unit,
    onUnmarkRespiro: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val accent = habit.color
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: colors.accent

    val ndays = DateUtils.daysInMonth(year, month)
    val created = HabitCalculator.createdKey(model)
    val end = HabitCalculator.endKey(model)
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
            val state = when {
                key > today -> CellState.FUTURE
                key < created -> CellState.PRE
                end != null && key > end -> CellState.AFTER
                habit.cadence != "daily" -> {
                    val (kind, mk) = HabitCalculator.periodMark(model, key)
                    when (kind) {
                        MarkKind.DONE -> if (mk == key) CellState.DONE else CellState.LOCKED
                        MarkKind.RESPIRO -> if (mk == key) CellState.RESPIRO else CellState.LOCKED
                        else -> if (HabitCalculator.isAnchorDay(model, key)) CellState.EMPTY else CellState.LOCKED
                    }
                }
                !HabitCalculator.dailyDueOn(model, key) -> CellState.OFF
                key in model.log -> CellState.DONE
                key in model.respiros -> CellState.RESPIRO
                isCount && (countsForHabit[key] ?: 0) > 0 -> CellState.PARTIAL
                else -> CellState.EMPTY
            }
            CellDay(d, key, state, key == today, countsForHabit[key] ?: 0)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    // Long-press the name to remove (the detail sheet arrives next).
                    .combinedClickableNoRipple(onClick = {}, onLongClick = onRemove),
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

    Box(
        Modifier
            .size(22.dp)
            .alpha(cellAlpha)
            .drawBehind {
                if (day.isToday && filled) {
                    // boxShadow 0 0 0 2px accent@20% — a ring just outside the cell.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.2f),
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
            .background(if (filled) (if (day.isToday) accent else colors.ink) else Color.Transparent)
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
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(
                                    color = accent.copy(alpha = 0.7f),
                                    start = Offset(x, size.height),
                                    end = Offset(x + size.height, 0f),
                                    strokeWidth = 1.4.dp.toPx(),
                                )
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

@Composable
private fun AddHabitDialog(onAdd: (String, String, Int?, String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPautaColors.current
    var name by remember { mutableStateOf("") }
    var cadence by remember { mutableStateOf("daily") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        title = { Text(tr("Nova maré"), color = colors.ink) },
        text = {
            Column {
                MaresField(name, { name = it }, tr("Nome do hábito"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegChip(tr("Diária"), cadence == "daily") { cadence = "daily" }
                    SegChip(tr("Semanal"), cadence == "weekly") { cadence = "weekly" }
                    SegChip(tr("Mensal"), cadence == "monthly") { cadence = "monthly" }
                }
                if (cadence == "daily") {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(90.dp)) {
                            MaresField(target, { target = it.filter { c -> c.isDigit() }.take(4) }, tr("meta"), number = true)
                        }
                        Box(Modifier.weight(1f)) { MaresField(unit, { unit = it }, tr("unidade (ex: L, km)")) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onAdd(name.trim(), cadence, target.toIntOrNull()?.takeIf { it > 1 }, unit.trim())
            }) { Text(tr("Criar"), color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar"), color = colors.ink3) } },
    )
}

@Composable
private fun SegChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.paper2)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (selected) colors.accent else colors.ink3, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun MaresField(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false) {
    val colors = LocalPautaColors.current
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = colors.ink4) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 16.sp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
        else KeyboardOptions(imeAction = ImeAction.Done),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = colors.accent,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = colors.rule,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
        ),
    )
}
