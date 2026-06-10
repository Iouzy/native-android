package com.pauta.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.domain.HabitModel
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Marés (tides) tab: a month at a time, an overall consistency %, and a row
 * per habit with a tap-to-complete / long-press-for-respiro day grid plus its
 * current tide level. Add via the + dialog. Charts (wave / heatmap / trend) and
 * the habit detail sheet follow. // PT: tab Marés — grelha mensal por hábito,
 * % de constância e nível da maré.
 */
@Composable
fun MaresScreen() {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val logs by vm.habitLogs.collectAsStateWithLifecycle()
    val respiros by vm.habitRespiros.collectAsStateWithLifecycle()

    val today by vm.todayKey.collectAsStateWithLifecycle()
    val nowYm = remember { YearMonth.now() }
    var year by remember { mutableIntStateOf(nowYm.year) }
    var month by remember { mutableIntStateOf(nowYm.monthValue) }
    var showAdd by remember { mutableStateOf(false) }

    val logsByHabit = remember(logs) { logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    val respByHabit = remember(respiros) { respiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    fun modelOf(h: HabitEntity) = HabitModel(
        id = h.id, createdAt = h.createdAt, cadence = h.cadence, anchor = h.anchor, weekdays = h.weekdays,
        recurrence = h.recurrence, endsAt = h.endsAt,
        log = logsByHabit[h.id].orEmpty(), respiros = respByHabit[h.id].orEmpty(),
    )

    val models = remember(habits, logs, respiros) { habits.map { modelOf(it) } }
    val overall = HabitCalculator.overallPctInMonth(models, year, month, today)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(tr("Marés"), color = colors.ink, fontFamily = SerifFamily, fontSize = 30.sp)

            // Month nav.
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ChevronLeft, contentDescription = tr("mês anterior"), tint = colors.ink3,
                    modifier = Modifier.size(26.dp).clickableNoRipple {
                        val ym = YearMonth.of(year, month).minusMonths(1); year = ym.year; month = ym.monthValue
                    },
                )
                Text(
                    text = monthLabel(year, month).replaceFirstChar { it.uppercase() },
                    color = colors.ink2,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).clickableNoRipple { year = nowYm.year; month = nowYm.monthValue },
                )
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = tr("mês seguinte"), tint = colors.ink3,
                    modifier = Modifier.size(26.dp).clickableNoRipple {
                        val ym = YearMonth.of(year, month).plusMonths(1); year = ym.year; month = ym.monthValue
                    },
                )
            }

            if (overall != null) {
                Spacer(Modifier.height(6.dp))
                Text(trf("{p}% de constância", "p" to overall), color = colors.ink3, fontSize = 13.sp)
            }

            if (habits.isEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text(tr("Sem marés ainda. Toca em + para criar a primeira."), color = colors.ink4, fontSize = 14.sp)
            } else {
                habits.forEach { h ->
                    HabitRow(
                        habit = h,
                        model = modelOf(h),
                        year = year,
                        month = month,
                        today = today,
                        onToggle = { dayKey -> vm.toggleHabitDay(h.id, dayKey) },
                        onRespiro = { dayKey -> vm.markRespiro(h.id, dayKey) },
                    )
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

    if (showAdd) {
        AddHabitDialog(
            onAdd = { name, cadence, target, unit ->
                vm.addHabit(name = name, cadence = cadence, target = target, unit = unit)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun HabitRow(
    habit: HabitEntity,
    model: HabitModel,
    year: Int,
    month: Int,
    today: String,
    onToggle: (String) -> Unit,
    onRespiro: (String) -> Unit,
) {
    val colors = LocalPautaColors.current
    val streak = remember(model) { HabitCalculator.currentStreak(model, today) }
    val tier = HabitCalculator.tideTier(streak.days)
    val days = DateUtils.daysInMonth(year, month)

    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(habit.name, color = colors.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
            tier?.let { Text(tr(it.name), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (d in 1..days) {
                val dayKey = "%04d-%02d-%02d".format(year, month, d)
                DayCell(
                    state = HabitCalculator.dayState(model, dayKey, today),
                    isToday = dayKey == today,
                    onToggle = { onToggle(dayKey) },
                    onRespiro = { onRespiro(dayKey) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(state: DayState, isToday: Boolean, onToggle: () -> Unit, onRespiro: () -> Unit) {
    val colors = LocalPautaColors.current
    val fill: Color = when (state) {
        DayState.DONE -> colors.accent
        DayState.RESPIRO -> colors.accentSoft
        DayState.EMPTY -> colors.paper3
        DayState.FUTURE, DayState.LOCKED, DayState.OFF, DayState.PRE -> colors.paper2
    }
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .combinedClickable(onClick = onToggle, onLongClick = onRespiro),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state == DayState.DONE) colors.onDark else colors.ink3),
            )
        }
    }
}

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

private fun monthLabel(year: Int, month: Int): String {
    val locale = if (com.pauta.app.i18n.I18n.lang == com.pauta.app.i18n.Lang.EN) Locale.ENGLISH else Locale("pt", "PT")
    return YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}
