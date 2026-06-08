package com.pauta.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.theme.tierColor
import com.pauta.app.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MaresScreen(vm: AppViewModel, onOpenSettings: () -> Unit = {}, onOpenInsights: () -> Unit = {}) {
    val prefs    by vm.prefs.collectAsState()
    val habits   by vm.habits.collectAsState()
    val year     by vm.maresYear.collectAsState()
    val month    by vm.maresMonth.collectAsState()
    val monthData by vm.habitMonthData.collectAsState()
    val todayKey by vm.todayKey.collectAsState()

    val tr = { pt: String -> Strings.tr(pt, prefs.lang) }

    var showAdd     by remember { mutableStateOf(false) }
    var editHabit   by remember { mutableStateOf<HabitEntity?>(null) }
    var respiroFor  by remember { mutableStateOf<Pair<String, String>?>(null) }  // habitId, dayKey

    // Load month data for every habit when the month changes.
    LaunchedEffect(habits.map { it.id }, year, month) {
        habits.forEach { vm.loadHabitMonthData(it.id, year, month) }
    }

    // Overall % for the month
    val overallPct = remember(monthData, habits, year, month) {
        val scores = habits.mapNotNull { monthData[it.id]?.pct }
        if (scores.isEmpty()) null else scores.average().toInt()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.maresGoToPrevMonth() }) {
                            Icon(Icons.Default.ChevronLeft, "‹")
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${Strings.monthName(month, prefs.lang)} $year",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (overallPct != null) {
                                Text("$overallPct%", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { vm.maresGoToNextMonth() }) {
                            Icon(Icons.Default.ChevronRight, "›")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.maresGoToToday() }) { Icon(Icons.Default.Today, tr("Hoje")) }
                    IconButton(onClick = onOpenInsights) { Icon(Icons.Default.Insights, tr("Resumo")) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, tr("Definições")) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, tr("Nova maré"))
            }
        }
    ) { padding ->
        if (habits.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Waves, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Sem dados"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showAdd = true }) { Text(tr("Nova maré")) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 90.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(habits, key = { it.id }) { habit ->
                    HabitCard(
                        habit = habit,
                        data = monthData[habit.id],
                        year = year,
                        month = month,
                        todayKey = todayKey,
                        tr = tr,
                        onToggleDay = { dk -> vm.toggleHabitDay(habit.id, dk) },
                        onLongPressDay = { dk -> respiroFor = habit.id to dk },
                        onEdit = { editHabit = habit }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddHabitSheet(tr = tr, onDismiss = { showAdd = false }, onAdd = { name, cadence, target, unit, clock, color ->
            vm.addHabit(name = name, cadence = cadence, target = target, unit = unit, clock = clock, color = color)
            showAdd = false
        })
    }

    editHabit?.let { habit ->
        EditHabitSheet(
            habit = habit,
            tr = tr,
            onDismiss = { editHabit = null },
            onSave = { vm.updateHabit(it); editHabit = null },
            onDelete = { vm.removeHabit(habit.id); editHabit = null }
        )
    }

    respiroFor?.let { (habitId, dayKey) ->
        RespiroSheet(
            tr = tr,
            isRespiro = monthData[habitId]?.respiroKeys?.contains(dayKey) == true,
            onDismiss = { respiroFor = null },
            onMark = { reason -> vm.markRespiro(habitId, dayKey, reason); respiroFor = null },
            onUnmark = { vm.unmarkRespiro(habitId, dayKey); respiroFor = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    habit: HabitEntity,
    data: AppViewModel.HabitMonthData?,
    year: Int,
    month: Int,
    todayKey: String,
    tr: (String) -> String,
    onToggleDay: (String) -> Unit,
    onLongPressDay: (String) -> Unit,
    onEdit: () -> Unit
) {
    val monthKey = DateUtils.monthKey(year, month)
    val daysInMonth = DateUtils.daysInMonth(monthKey)
    val streak = data?.streak ?: 0
    val accent = parseHabitColor(habit.color) ?: tierColor(streak)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.titleMedium)
                    val sub = buildString {
                        append(when (habit.cadence) {
                            "weekly"  -> tr("Semanal")
                            "monthly" -> tr("Mensal")
                            else      -> tr("Diária")
                        })
                        if (habit.clock.isNotBlank()) append(" · ${habit.clock}")
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Percentage or maturity hint
                val pctText = data?.pct?.let { "$it%" }
                    ?: data?.observed?.let { "${it}/7" }
                    ?: ""
                if (pctText.isNotBlank()) {
                    Text(pctText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                }
                if (streak > 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.15f)) {
                        Text("🔥$streak", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, tr("Editar"), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Day grid — 7 columns
            val logSet = data?.logDays ?: emptySet()
            val respiroSet = data?.respiroKeys ?: emptySet()
            val countMap = data?.counts ?: emptyMap()
            val respiroMap = respiroSet.associateWith {
                com.pauta.app.data.entity.HabitRespiroEntity(habit.id, it)
            }

            val cols = 7
            val rows = (daysInMonth + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until cols) {
                            val dayNum = r * cols + c + 1
                            if (dayNum <= daysInMonth) {
                                val dk = DateUtils.addDays(monthKey, dayNum - 1)
                                val status = HabitCalculator.dayStatus(
                                    habit, logSet, respiroMap, countMap, dk, todayKey
                                )
                                DayCell(
                                    dayNum = dayNum,
                                    status = status,
                                    accent = accent,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (status.state == DayState.EMPTY || status.state == DayState.DONE || status.state == DayState.RESPIRO) {
                                            onToggleDay(dk)
                                        }
                                    },
                                    onLongClick = { onLongPressDay(dk) }
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    dayNum: Int,
    status: HabitCalculator.DayStatus,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val outline = MaterialTheme.colorScheme.outline
    val (bg, border, content) = when (status.state) {
        DayState.DONE    -> Triple(accent, accent, Color.White)
        DayState.RESPIRO -> Triple(accent.copy(alpha = 0.2f), accent.copy(alpha = 0.4f), accent)
        DayState.EMPTY   -> Triple(Color.Transparent, outline, MaterialTheme.colorScheme.onSurfaceVariant)
        DayState.LOCKED  -> Triple(accent.copy(alpha = 0.08f), Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        DayState.OFF     -> Triple(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
        DayState.PRE     -> Triple(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        DayState.FUTURE  -> Triple(Color.Transparent, outline.copy(alpha = 0.3f), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(
                if (border != Color.Transparent) Modifier.border(1.dp, border, RoundedCornerShape(6.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        when (status.state) {
            DayState.DONE -> {
                if (status.target > 0) {
                    Text("${status.count}", color = content, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Check, null, tint = content, modifier = Modifier.size(12.dp))
                }
            }
            DayState.RESPIRO -> Text("~", color = content, fontSize = 12.sp)
            DayState.OFF     -> Text("–", color = content, fontSize = 10.sp)
            DayState.PRE     -> Box(Modifier.size(3.dp).clip(CircleShape).background(content))
            else             -> Text("$dayNum", color = content, fontSize = 9.sp)
        }
    }
}

private fun parseHabitColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrNull()
}

// ── Sheets ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHabitSheet(
    tr: (String) -> String,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String, String, String) -> Unit
) {
    var name      by remember { mutableStateOf("") }
    var cadence   by remember { mutableStateOf("daily") }
    var countable by remember { mutableStateOf(false) }
    var target    by remember { mutableStateOf("") }
    var unit      by remember { mutableStateOf("") }
    var clock     by remember { mutableStateOf("") }
    var color     by remember { mutableStateOf("") }

    val palette = listOf("#2563EB", "#059669", "#DC2626", "#F59E0B", "#7C3AED", "#0891B2")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Nova maré"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(tr("Nome do hábito")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text(tr("Cadência"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("daily" to tr("Diária"), "weekly" to tr("Semanal"), "monthly" to tr("Mensal"))
                    .forEach { (c, label) ->
                        FilterChip(selected = cadence == c, onClick = { cadence = c }, label = { Text(label) })
                    }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = countable, onCheckedChange = { countable = it })
                Text(tr("Hábito contável"), style = MaterialTheme.typography.bodyMedium)
            }
            if (countable) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = target, onValueChange = { target = it.filter { c -> c.isDigit() } },
                        label = { Text(tr("Meta diária")) }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it },
                        label = { Text(tr("Unidade")) }, modifier = Modifier.weight(1f), singleLine = true
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = clock, onValueChange = { clock = it },
                label = { Text(tr("Hora preferida")) }, placeholder = { Text("08:00") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Text(tr("Cor"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { hex ->
                    val c = parseHabitColor(hex)!!
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(c)
                            .border(if (color == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = if (color == hex) "" else hex }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAdd(name.trim(), cadence, if (countable) target.toIntOrNull() ?: 0 else 0, unit.trim(), clock.trim(), color) },
                modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank()
            ) { Text(tr("Adicionar")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHabitSheet(
    habit: HabitEntity,
    tr: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
    onDelete: () -> Unit
) {
    var name  by remember { mutableStateOf(habit.name) }
    var clock by remember { mutableStateOf(habit.clock) }
    var desc  by remember { mutableStateOf(habit.description) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Detalhes da maré"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(tr("Nome do hábito")) }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = clock, onValueChange = { clock = it },
                label = { Text(tr("Hora preferida")) }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                label = { Text(tr("Descrição")) }, modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(if (confirmDelete) "${tr("Confirmar")}?" else tr("Eliminar maré")) }
                Button(
                    onClick = { onSave(habit.copy(name = name.trim(), clock = clock.trim(), description = desc.trim())) },
                    modifier = Modifier.weight(1f), enabled = name.isNotBlank()
                ) { Text(tr("Guardar")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RespiroSheet(
    tr: (String) -> String,
    isRespiro: Boolean,
    onDismiss: () -> Unit,
    onMark: (String) -> Unit,
    onUnmark: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Registar respiro"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(tr("Respiro"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (isRespiro) {
                Button(onClick = onUnmark, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Eliminar"))
                }
            } else {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text(tr("Motivo (opcional)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onMark(reason.trim()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Marcar como respiro"))
                }
            }
        }
    }
}
