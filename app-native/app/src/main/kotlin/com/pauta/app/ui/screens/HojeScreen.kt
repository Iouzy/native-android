package com.pauta.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.domain.HojeLogic
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.PeriodLabel
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.computeTodayTides
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate

/**
 * The Hoje (Today) tab — date header with ±7-day navigation (like Marés) and
 * three evenly-spaced chips. Today shows the full interactive view; past days
 * show a read-only snapshot of intentions and reflection.
 * // PT: tab Hoje com navegação de dias e cabeçalho arrumado.
 *
 * @param onOpenHistory opens the past-days history — a navigation destination
 *   (A8), so it peels back predictively rather than swapping in place.
 */
@Composable
fun HojeScreen(onOpenHistory: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val intentions by vm.intentions.collectAsStateWithLifecycle()
    val reflection by vm.reflection.collectAsStateWithLifecycle()
    val carry by vm.carry.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val allIntentions by vm.allIntentions.collectAsStateWithLifecycle()
    val allDays by vm.allDays.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val habitCounts by vm.habitCounts.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val routineItems by vm.routineItems.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    // A3: every micro-animation below is gated on this — reduced motion snaps to
    // the old instant behaviour. // PT: animações respeitam "movimento reduzido".
    val animate = !prefs.reducedMotion
    var showWeek by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showRoutines by remember { mutableStateOf(false) }

    // 0 = today; -1 … -7 = that many days back (limited to 7 days).
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    val selectedDayKey = remember(today, selectedDayOffset) {
        if (selectedDayOffset == 0) today
        else LocalDate.parse(today).plusDays(selectedDayOffset.toLong()).toString()
    }
    val isToday = selectedDayOffset == 0
    val canGoBack = selectedDayOffset > -7
    val canGoForward = selectedDayOffset < 0

    // Auto-sort by priority level (1 highest; unset sinks to 4), stable within a
    // level via stored position — matching the web list.
    val sorted = remember(intentions) {
        intentions.sortedWith(compareBy({ it.priority ?: 4 }, { it.position }))
    }
    val done = intentions.count { it.done }
    val total = intentions.size

    // Past-day snapshot: intentions + reflection for the selected day key.
    val pastSorted = remember(allIntentions, selectedDayKey) {
        allIntentions
            .filter { it.dayKey == selectedDayKey }
            .sortedWith(compareBy({ it.priority ?: 4 }, { it.position }))
    }
    val pastReflection = remember(allDays, selectedDayKey) {
        allDays.find { it.dayKey == selectedDayKey }?.reflection.orEmpty()
    }

    // Today-derived state, hoisted out of the LazyColumn so several items can
    // share it: the day pulse shows above the list and again in the reflection
    // card, and the tide rows reuse the same computeTodayTides slice.
    // // PT: estado derivado de hoje, partilhado pelos itens da lista.
    val todayTides = remember(habits, habitLogs, habitRespiros, habitCounts, today) {
        computeTodayTides(habits, habitLogs, habitRespiros, habitCounts, today)
    }
    val tideDone = todayTides.count { it.state == DayState.DONE }
    val tideDenom = todayTides.count { it.state != DayState.RESPIRO }
    val focusMsToday = FocusMath.dailyFocusMs(
        allSessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
        today,
        System.currentTimeMillis(),
    )
    // A3: the pulse numerals tick up to their new value when a count changes
    // (snap to the value when reduced motion is on). // PT: os números do pulso
    // sobem até ao novo valor.
    val animDone by animateIntAsState(
        targetValue = done,
        animationSpec = if (animate) tween(450, easing = FastOutSlowInEasing) else snap(),
        label = "pulse-intentions",
    )
    val animTideDone by animateIntAsState(
        targetValue = tideDone,
        animationSpec = if (animate) tween(450, easing = FastOutSlowInEasing) else snap(),
        label = "pulse-tides",
    )
    // Day pulse — one quiet mono line tying the three tabs together
    // (intentions · focus · tides). Respiros stay out of the tide denominator.
    // // PT: pulso do dia.
    val pulseParts = buildList {
        if (total > 0) add(trf("{d}/{t} intenções", "d" to animDone, "t" to total))
        if (focusMsToday > 0) add(trf("{d} em foco", "d" to FocusMath.fmtDuration(focusMsToday)))
        if (tideDenom > 0) add(trf("{d}/{t} marés", "d" to animTideDone, "t" to tideDenom))
    }
    val groups = remember(sorted) { HojeLogic.groupByTimeOfDay(sorted) }
    val showHeaders = groups.size > 1

    // A single LazyColumn so list rows can carry stable keys (and, in A3,
    // animateItem). Horizontal content padding stands in for the old Column
    // padding. // PT: uma LazyColumn única, com chaves estáveis nas linhas.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        // Header — web-style two columns: the date (top-left, with the 7-day
        // back-nav) and the big serif question fill the left; the three actions
        // stack as quiet outlined chips top-right, exactly like the Pauta tab.
        // // PT: cabeçalho em duas colunas, como na web — data à esquerda, ações à direita.
        item(key = "header") {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    val selDate = LocalDate.parse(selectedDayKey)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canGoBack) {
                            Icon(
                                Icons.Filled.ChevronLeft,
                                contentDescription = tr("dia anterior"),
                                tint = colors.ink3,
                                modifier = Modifier.size(20.dp).clickableNoRipple { selectedDayOffset-- },
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        PeriodLabel(
                            prefix = I18n.fmtWeekdayDay(selDate) + " ",
                            month = I18n.fmtMonthShort(selDate.monthValue),
                        )
                        if (canGoForward) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = tr("dia seguinte"),
                                tint = colors.ink3,
                                modifier = Modifier.size(20.dp).clickableNoRipple { selectedDayOffset++ },
                            )
                        }
                    }
                    if (isToday) {
                        Spacer(Modifier.height(10.dp))
                        // The headline question, with "hoje" in accent italic — the web's
                        // `{tr("O que importa")} <em style={{color: accent}}>{tr("hoje")}</em>?`.
                        Text(
                            text = buildAnnotatedString {
                                append(tr("O que importa"))
                                append(" ")
                                withStyle(SpanStyle(color = colors.accent, fontStyle = FontStyle.Italic)) {
                                    append(tr("hoje"))
                                }
                                append("?")
                            },
                            color = colors.ink,
                            fontFamily = SerifFamily,
                            fontSize = 44.sp,
                            lineHeight = 44.sp,
                            letterSpacing = (-0.66).sp, // -0.015em of 44sp
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip(tr("dias anteriores") + " ↗") { onOpenHistory() }
                    HeaderChip(tr("a semana") + " ↗") { showWeek = true }
                    HeaderChip(tr("Rotinas") + " ↗") { showRoutines = true }
                    HeaderChip(tr("revisão") + " ↗") { showInsights = true }
                }
            }
        }

        if (isToday) {
            // ── Today's interactive view ──────────────────────────────
            // Day pulse + carry banner + add form sit above the intention list as
            // one quiet block. // PT: pulso, faixa de arrasto e formulário.
            item(key = "today-top") {
                Spacer(Modifier.height(8.dp))
                if (pulseParts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = pulseParts.joinToString("   ·   "),
                        color = colors.ink3,
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.22.sp, // 0.02em of 11sp
                    )
                }
                carry?.let { source ->
                    Spacer(Modifier.height(16.dp))
                    CarryBanner(source = source, onCarry = { vm.carryOver() })
                }
                Spacer(Modifier.height(18.dp))
                AddIntentionForm(
                    accent = colors.accent,
                    onAdd = { text, priority, target, w -> vm.addIntention(text, priority, target, w) },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Intentions, grouped by time-of-day; rows keyed by id so A3 can
            // animate add/remove. // PT: intenções por período, com chaves estáveis.
            groups.forEach { (w, groupItems) ->
                if (showHeaders) {
                    item(key = "when-$w") {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = whenLabel(w),
                            color = colors.ink3,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }
                }
                items(groupItems, key = { "intent-${it.id}" }) { item ->
                    IntentionRow(
                        item = item,
                        animate = animate,
                        // A3: add/remove/reorder slides into place (LazyItemScope).
                        modifier = if (animate) Modifier.animateItem() else Modifier,
                        onToggle = { vm.toggleIntention(item.id) },
                        onDelete = { vm.removeIntention(item.id) },
                        onCyclePriority = { vm.setIntentionPriority(item.id, nextPriority(item.priority)) },
                    )
                }
            }
            if (total == 0) {
                item(key = "no-intentions") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = tr("Ainda sem intenções para hoje."),
                        color = colors.ink4,
                        fontSize = 14.sp,
                    )
                }
            }

            // Marés de hoje — placed between intentions and the night reflection so
            // Hoje reads as one nested day: now → today's rhythm → the night.
            // // PT: a fatia de hoje das Marés, entre as intenções e a reflexão.
            if (todayTides.isNotEmpty()) {
                item(key = "tides-header") {
                    Spacer(Modifier.height(36.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = tr("Marés de hoje").uppercase(),
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 1.8.sp, // 0.2em of 9sp
                            modifier = Modifier.weight(1f),
                        )
                        if (tideDenom > 0) {
                            Text(
                                text = "$animTideDone/$tideDenom",
                                color = colors.ink4,
                                fontFamily = MonoFamily,
                                fontSize = 9.sp,
                                letterSpacing = 0.54.sp, // 0.06em of 9sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                itemsIndexed(todayTides, key = { _, tide -> "tide-${tide.habit.id}" }) { i, tide ->
                    TodayTideRow(
                        tide = tide,
                        last = i == todayTides.lastIndex,
                        onAct = if (tide.state == DayState.RESPIRO) null else {
                            {
                                if (tide.isCount) vm.setHabitCount(tide.habit.id, today, tide.count + 1)
                                else vm.toggleHabitToday(tide.habit.id)
                            }
                        },
                    )
                }
            }

            // Evening reflection — the web's paper-2 card: mono eyebrow, the serif
            // question, the day pulse again (the header one has scrolled away), and
            // the free-form field. // PT: cartão da reflexão, como na web.
            item(key = "reflection") {
                Spacer(Modifier.height(40.dp))
                // A6: a debounced, quiet "guardado ✓" — the reflection writes on every
                // keystroke, so once the user pauses we confirm it's saved (hidden
                // while typing). The fade honours reduced motion. // PT: confirmação
                // discreta de que a reflexão ficou guardada, depois de uma pausa.
                var reflectionEditedAt by remember { mutableStateOf<Long?>(null) }
                var reflectionSaved by remember { mutableStateOf(false) }
                LaunchedEffect(reflectionEditedAt) {
                    if (reflectionEditedAt == null) return@LaunchedEffect
                    reflectionSaved = false
                    delay(900)
                    reflectionSaved = true
                }
                val savedAlpha by animateFloatAsState(
                    targetValue = if (reflectionSaved) 1f else 0f,
                    animationSpec = if (animate) tween(300) else snap(),
                    label = "reflection-saved",
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, colors.rule, RoundedCornerShape(14.dp))
                        .background(colors.paper2)
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tr("Reflexão da noite").uppercase(),
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 1.8.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = tr("guardado") + " ✓",
                            color = colors.ink4,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 1.0.sp,
                            modifier = Modifier.alpha(savedAlpha),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "“" + tr("O que valeu hoje?") + "”",
                        color = colors.ink2,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (pulseParts.isNotEmpty()) {
                        Text(
                            text = pulseParts.joinToString("   ·   "),
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.21.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                        Spacer(Modifier.height(14.dp))
                    }
                    ReflectionField(
                        value = reflection,
                        accent = colors.accent,
                        onChange = {
                            vm.setReflection(it)
                            reflectionSaved = false
                            reflectionEditedAt = System.currentTimeMillis()
                        },
                    )
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    text = tr("amanhã, recomeça."),
                    color = colors.ink4,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp, // 0.04em of 10sp
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // ── Past-day read-only snapshot ───────────────────────────
            if (pastSorted.isEmpty() && pastReflection.isBlank()) {
                item(key = "past-empty") {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = tr("Sem conteúdo neste dia."),
                        color = colors.ink4,
                        fontSize = 14.sp,
                    )
                }
            } else {
                if (pastSorted.isNotEmpty()) {
                    item(key = "past-top") { Spacer(Modifier.height(18.dp)) }
                    items(pastSorted, key = { "past-${it.id}" }) { item -> PastIntentionRow(item) }
                }
                if (pastReflection.isNotBlank()) {
                    item(key = "past-reflection") {
                        Spacer(Modifier.height(32.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, colors.rule, RoundedCornerShape(14.dp))
                                .background(colors.paper2)
                                .padding(horizontal = 22.dp, vertical = 24.dp),
                        ) {
                            Text(
                                text = tr("Reflexão da noite").uppercase(),
                                color = colors.ink3,
                                fontFamily = MonoFamily,
                                fontSize = 9.sp,
                                letterSpacing = 1.8.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "“" + pastReflection + "”",
                                color = colors.ink2,
                                fontFamily = SerifFamily,
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(48.dp)) }
    }

    if (showWeek) {
        WeekAheadSheet(
            today = today,
            plans = plans,
            onAdd = { dayKey, text -> vm.addPlan(dayKey, text) },
            onRemove = { id -> vm.removePlan(id) },
            onClose = { showWeek = false },
        )
    }
    if (showInsights) {
        InsightsSheet(onClose = { showInsights = false })
    }
    if (showRoutines) {
        // D1: the routines manager — create/edit/reorder + one-tap "aplicar" that
        // seeds today's intentions. Opened from Hoje so applying lands right here.
        // // PT: gestor de rotinas, aberto a partir de Hoje.
        RoutinesSheet(
            routines = routines,
            items = routineItems,
            todayHasIntentions = intentions.isNotEmpty(),
            onApply = { vm.applyRoutine(it) },
            onCreate = { vm.addRoutine(it) },
            onSaveFromToday = { vm.saveRoutineFromToday(it) },
            onRename = { id, name -> vm.renameRoutine(id, name) },
            onDelete = { vm.deleteRoutine(it) },
            onAddItem = { id, text -> vm.addRoutineItem(id, text) },
            onUpdateItem = { rowId, text, prio, tgt -> vm.updateRoutineItem(rowId, text, prio, tgt) },
            onRemoveItem = { vm.removeRoutineItem(it) },
            onReorderItems = { id, ids -> vm.reorderRoutineItems(id, ids) },
            onClose = { showRoutines = false },
        )
    }
}

/** The header's bordered mono chips, stacked top-right ("DIAS ANTERIORES ↗",
 *  "A SEMANA ↗", "REVISÃO ↗") — the same treatment as the Pauta tab. */
@Composable
private fun HeaderChip(label: String, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Text(
        text = label.uppercase(),
        color = colors.ink3,
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        letterSpacing = 1.26.sp, // 0.14em of 9sp
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** A row of the "Marés de hoje" strip — the web's TodayTideRow: state circle,
 *  name (+ clock/time or "respiro" subtitle), and the count for countables.
 *  Tapping marks done / increments; respiro rows are quiet. */
@Composable
private fun TodayTideRow(tide: TideToday, last: Boolean, onAct: (() -> Unit)?) {
    val colors = LocalPautaColors.current
    val accent = tide.habit.color
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: colors.accent
    val isDone = tide.state == DayState.DONE
    val respiro = tide.state == DayState.RESPIRO
    val partial = !isDone && !respiro && tide.isCount && tide.count > 0

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onAct != null) Modifier.clickableNoRipple(onAct) else Modifier),
    ) {
        Row(
            Modifier.padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDone) accent else Color.Transparent)
                    .border(
                        width = 1.6.dp,
                        color = when { isDone -> accent; respiro -> colors.ink4; else -> colors.ink3 },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isDone -> Icon(
                        imageVector = PautaIcons.Check,
                        contentDescription = null,
                        tint = colors.paper,
                        modifier = Modifier.size(13.dp),
                    )
                    respiro -> Box(
                        Modifier
                            .size(width = 9.dp, height = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.ink4),
                    )
                    partial -> Text(
                        text = tide.count.toString(),
                        color = accent,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tide.habit.name,
                    color = if (isDone || respiro) colors.ink3 else colors.ink,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                )
                val clock = tide.habit.clock
                val time = tide.habit.time
                if (respiro || clock.isNotBlank() || time.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    if (respiro) {
                        Text(
                            text = tr("respiro"),
                            color = colors.ink3,
                            fontFamily = SerifFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.5.sp,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (clock.isNotBlank()) {
                                Text(text = clock, color = colors.ink3, fontFamily = MonoFamily, fontSize = 11.sp)
                                if (time.isNotBlank()) Spacer(Modifier.width(6.dp))
                            }
                            if (time.isNotBlank()) {
                                Text(
                                    text = time,
                                    color = colors.ink3,
                                    fontFamily = SerifFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (tide.isCount) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${tide.count}/${tide.target}" + if (tide.habit.unit.isNotBlank()) " ${tide.habit.unit}" else "",
                    color = if (isDone) accent else colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                )
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

private fun nextPriority(current: Int?): Int? = when (current) {
    null -> 1; 1 -> 2; 2 -> 3; else -> null
}

/** One-tap "bring forward" of the most recent past day's unfinished intentions. */
@Composable
private fun CarryBanner(source: CarrySource, onCarry: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accentBg)
            .clickableNoRipple(onCarry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = trf("Trazer {n} de {d}", "n" to source.items.size, "d" to shortDate(source.dayKey)),
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(text = "↓", color = colors.accent, fontSize = 16.sp)
    }
}

private fun shortDate(dayKey: String): String = I18n.fmtDateShort(LocalDate.parse(dayKey))

@Composable
private fun IntentionRow(
    item: IntentionEntity,
    animate: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCyclePriority: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val priorityColor: Color = when (item.priority) {
        1 -> colors.accent
        2 -> colors.ink2
        3 -> colors.ink3
        else -> colors.ink4
    }
    // A3: the strike is painted by hand so it can sweep left→right (~250ms) as the
    // intention is ticked; reduced motion uses the instant built-in decoration.
    // // PT: o risco é desenhado à mão para correr da esquerda para a direita.
    val strike by animateFloatAsState(
        targetValue = if (item.done) 1f else 0f,
        animationSpec = tween(250),
        label = "intention-strike",
    )
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoneCircle(done = item.done, animate = animate, onToggle = onToggle)
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text,
            color = if (animate) lerp(colors.ink, colors.ink4, strike)
            else if (item.done) colors.ink4 else colors.ink,
            textDecoration = if (!animate && item.done) TextDecoration.LineThrough else null,
            fontSize = 16.sp,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (animate) Modifier.drawWithContent {
                        drawContent()
                        val tl = textLayout
                        if (tl != null && strike > 0f) {
                            // One line sweeping across every wrapped row of glyphs.
                            val widths = (0 until tl.lineCount).map { tl.getLineRight(it) - tl.getLineLeft(it) }
                            var remaining = widths.sum() * strike
                            for (line in 0 until tl.lineCount) {
                                val w = widths[line]
                                if (w <= 0f) continue
                                val drawW = remaining.coerceIn(0f, w)
                                if (drawW <= 0f) break
                                val y = (tl.getLineTop(line) + tl.getLineBottom(line)) / 2f
                                drawLine(
                                    color = colors.ink4,
                                    start = Offset(tl.getLineLeft(line), y),
                                    end = Offset(tl.getLineLeft(line) + drawW, y),
                                    strokeWidth = 1.5.dp.toPx(),
                                )
                                remaining -= drawW
                            }
                        }
                    } else Modifier,
                ),
        )
        // Priority dot — tap cycles 1 → 2 → 3 → none.
        Box(
            Modifier
                .size(26.dp)
                .clickableNoRipple(onCyclePriority),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.priority?.toString() ?: "·",
                color = priorityColor,
                fontWeight = if (item.priority != null) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = tr("Apagar"),
            tint = colors.ink4,
            modifier = Modifier
                .size(20.dp)
                .clickableNoRipple(onDelete),
        )
    }
}

/** The intention's tick. When animating, an accent disc springs out from the
 *  centre (with the check) over the empty ink ring; reduced motion keeps the
 *  plain filled/outlined icon swap. // PT: o círculo da intenção, com mola. */
@Composable
private fun DoneCircle(done: Boolean, animate: Boolean, onToggle: () -> Unit) {
    val colors = LocalPautaColors.current
    if (!animate) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done) colors.accent else colors.ink4,
            modifier = Modifier
                .size(22.dp)
                .clickableNoRipple(onToggle),
        )
        return
    }
    val fill by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "intention-fill",
    )
    Box(
        Modifier
            .size(22.dp)
            .clickableNoRipple(onToggle),
        contentAlignment = Alignment.Center,
    ) {
        // Empty ring, fading out as the disc fills in.
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .border(1.6.dp, colors.ink4.copy(alpha = (1f - fill).coerceIn(0f, 1f)), CircleShape),
        )
        // Accent disc, springing from the centre (a little overshoot is welcome).
        Box(
            Modifier
                .matchParentSize()
                .scale(fill.coerceAtLeast(0f))
                .clip(CircleShape)
                .background(colors.accent),
        )
        Icon(
            imageVector = PautaIcons.Check,
            contentDescription = null,
            tint = colors.paper,
            modifier = Modifier
                .size(12.dp)
                .scale(fill.coerceIn(0f, 1f)),
        )
    }
}

/** Read-only intention row used in the past-day snapshot view. */
@Composable
private fun PastIntentionRow(item: IntentionEntity) {
    val colors = LocalPautaColors.current
    val priorityColor: Color = when (item.priority) {
        1 -> colors.accent
        2 -> colors.ink2
        3 -> colors.ink3
        else -> colors.ink4
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (item.done) colors.accent else colors.ink4,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text,
            color = if (item.done) colors.ink4 else colors.ink3,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        item.priority?.let { p ->
            Text(
                text = p.toString(),
                color = priorityColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun AddIntentionForm(accent: Color, onAdd: (String, Int?, Int?, String?) -> Unit) {
    val colors = LocalPautaColors.current
    var text by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf("") }
    var whenSel by remember { mutableStateOf<String?>(null) }
    val expanded = text.isNotBlank()

    fun commit() {
        if (text.isBlank()) return
        onAdd(text.trim(), priority, target.toIntOrNull()?.takeIf { it > 0 }, whenSel)
        text = ""; priority = null; target = ""; whenSel = null
    }

    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(tr("Nova intenção…"), color = colors.ink4) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            colors = transparentFieldColors(accent, colors.ink),
        )
        // The priority / time-of-day / duration controls appear once you start
        // typing, so the empty state stays a single quiet line. // PT: controlos
        // surgem ao começar a escrever.
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("1", priority == 1, accent) { priority = if (priority == 1) null else 1 }
                Pill("2", priority == 2, accent) { priority = if (priority == 2) null else 2 }
                Pill("3", priority == 3, accent) { priority = if (priority == 3) null else 3 }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(tr("manhã"), whenSel == "manha", accent) { whenSel = if (whenSel == "manha") null else "manha" }
                Pill(tr("tarde"), whenSel == "tarde", accent) { whenSel = if (whenSel == "tarde") null else "tarde" }
                Pill(tr("noite"), whenSel == "noite", accent) { whenSel = if (whenSel == "noite") null else "noite" }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = target,
                    onValueChange = { target = it.filter { c -> c.isDigit() }.take(3) },
                    placeholder = { Text(tr("min"), color = colors.ink4) },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                    textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    colors = transparentFieldColors(accent, colors.ink),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = tr("Adicionar"),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickableNoRipple { commit() }
                        .padding(8.dp),
                )
            }
        }
    }
}

/** A small pill toggle used for priority / time-of-day selection. */
@Composable
private fun Pill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else colors.paper2)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) accent else colors.ink3,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

private fun whenLabel(w: String?): String = when (w) {
    "manha" -> tr("manhã")
    "tarde" -> tr("tarde")
    "noite" -> tr("noite")
    else -> tr("sem hora")
}

@Composable
private fun ReflectionField(value: String, accent: Color, onChange: (String) -> Unit) {
    val colors = LocalPautaColors.current
    // Borderless serif field inside the reflection card, like the web's bare
    // AutoTextarea (no underline; the card is the frame).
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text(
                text = tr("Escreva quando quiser. Não precisa de ser longo."),
                color = colors.ink4,
                fontFamily = SerifFamily,
                fontSize = 15.sp,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        textStyle = TextStyle(color = colors.ink, fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 22.sp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            cursorColor = accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
        ),
    )
}

/** A TextField with a transparent paper-coloured container and an accent caret/
 *  underline, so inputs read as part of the paper surface rather than Material
 *  boxes. */
@Composable
private fun transparentFieldColors(accent: Color, ink: Color) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    cursorColor = accent,
    focusedIndicatorColor = accent,
    unfocusedIndicatorColor = LocalPautaColors.current.rule,
    focusedTextColor = ink,
    unfocusedTextColor = ink,
)
