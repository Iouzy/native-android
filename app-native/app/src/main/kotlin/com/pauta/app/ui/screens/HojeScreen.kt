package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
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
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.domain.HojeLogic
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.computeTodayTides
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate

/**
 * The Hoje (Today) tab — first interactive slice: the date + the question, a day
 * pulse, the priority-sorted intention list with add / toggle / cycle-priority /
 * delete, and the nightly reflection. Carry-over, the tide strip, week planner,
 * history and time-of-day grouping land in later increments toward full parity
 * with tab-hoje.jsx. // PT: primeira fatia interativa da tab Hoje.
 */
@Composable
fun HojeScreen() {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val intentions by vm.intentions.collectAsStateWithLifecycle()
    val reflection by vm.reflection.collectAsStateWithLifecycle()
    val carry by vm.carry.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val habitCounts by vm.habitCounts.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showWeek by remember { mutableStateOf(false) }

    // Auto-sort by priority level (1 highest; unset sinks to 4), stable within a
    // level via stored position — matching the web list.
    val sorted = remember(intentions) {
        intentions.sortedWith(compareBy({ it.priority ?: 4 }, { it.position }))
    }
    val done = intentions.count { it.done }
    val total = intentions.size

    if (showHistory) {
        HistoryView(days = history, onClose = { showHistory = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Date line: mono, uppercase, wide tracking — the web header's
            // `fontSize 10, letterSpacing 0.18em, textTransform uppercase`.
            Text(
                text = I18n.fmtDateLong(LocalDate.parse(today)).uppercase(),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 1.8.sp, // 0.18em of 10sp
                modifier = Modifier.weight(1f),
            )
            // The web's header chips: history + the week planner.
            HeaderChip(tr("dias anteriores") + " \u2197") { showHistory = true }
            Spacer(Modifier.width(6.dp))
            HeaderChip(tr("a semana") + " \u2197") { showWeek = true }
        }
        Spacer(Modifier.height(6.dp))
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

        // Today's tides — the actionable slice of Marés surfaced in Hoje (the
        // web's todayTides). Feeds both the day pulse and the tide strip so
        // they never diverge. // PT: a fatia acionável das Marés no Hoje.
        val todayTides = remember(habits, habitLogs, habitRespiros, habitCounts, today) {
            computeTodayTides(habits, habitLogs, habitRespiros, habitCounts, today)
        }
        val tideDone = todayTides.count { it.state == DayState.DONE }
        val tideDenom = todayTides.count { it.state != DayState.RESPIRO }

        // Day pulse — one quiet mono line tying the three tabs together
        // (intentions · focus · tides), exactly like the web header. Respiros
        // stay out of the tide denominator. // PT: pulso do dia.
        val pulseParts = mutableListOf<String>()
        if (total > 0) pulseParts += trf("{d}/{t} intenções", "d" to done, "t" to total)
        val focusMsToday = FocusMath.dailyFocusMs(
            allSessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
            today,
            System.currentTimeMillis(),
        )
        if (focusMsToday > 0) pulseParts += trf("{d} em foco", "d" to FocusMath.fmtDuration(focusMsToday))
        if (tideDenom > 0) pulseParts += trf("{d}/{t} marés", "d" to tideDone, "t" to tideDenom)
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
        val groups = remember(sorted) { HojeLogic.groupByTimeOfDay(sorted) }
        val showHeaders = groups.size > 1
        groups.forEach { (w, items) ->
            if (showHeaders) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = whenLabel(w),
                    color = colors.ink3,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
            items.forEach { item ->
                IntentionRow(
                    item = item,
                    onToggle = { vm.toggleIntention(item.id) },
                    onDelete = { vm.removeIntention(item.id) },
                    onCyclePriority = { vm.setIntentionPriority(item.id, nextPriority(item.priority)) },
                )
            }
        }
        if (total == 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = tr("Ainda sem intenções para hoje."),
                color = colors.ink4,
                fontSize = 14.sp,
            )
        }

        // Marés de hoje — placed between intentions and the night reflection so
        // Hoje reads as one nested day: now → today's rhythm → the night.
        // // PT: a fatia de hoje das Marés, entre as intenções e a reflexão.
        if (todayTides.isNotEmpty()) {
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
                        text = "$tideDone/$tideDenom",
                        color = colors.ink4,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.54.sp, // 0.06em of 9sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            todayTides.forEachIndexed { i, tide ->
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
        Spacer(Modifier.height(40.dp))
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
                onChange = { vm.setReflection(it) },
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
        Spacer(Modifier.height(48.dp))
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
}

/** A small mono uppercase chip, as in the web header ("dias anteriores ↗"). */
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
            modifier = Modifier
                .size(22.dp)
                .clickableNoRipple(onToggle),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text,
            color = if (item.done) colors.ink4 else colors.ink,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
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
