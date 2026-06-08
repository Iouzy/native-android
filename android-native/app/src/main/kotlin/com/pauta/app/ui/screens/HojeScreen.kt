package com.pauta.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeScreen(vm: AppViewModel, onOpenSettings: () -> Unit = {}, onOpenHistory: () -> Unit = {}) {
    val lang      by vm.prefs.collectAsState()
    val todayKey  by vm.todayKey.collectAsState()
    val intentions by vm.intentions.collectAsState()
    val habits    by vm.habits.collectAsState()
    val todayBlocks by vm.todayBlocks.collectAsState()
    val reflection by vm.reflection.collectAsState()
    val dayFocusMs by vm.dayFocusMs.collectAsState()
    val monthData by vm.habitMonthData.collectAsState()

    // Sheets
    var showAddForm     by remember { mutableStateOf(false) }
    var showCarryOver   by remember { mutableStateOf(false) }
    var showRoutines    by remember { mutableStateOf(false) }
    var editIntention   by remember { mutableStateOf<IntentionEntity?>(null) }

    val tr = { pt: String -> Strings.tr(pt, lang.lang) }

    // Date header
    val dateLabel = remember(todayKey) {
        SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt")).format(
            DateUtils.calFromKey(todayKey).time
        ).replaceFirstChar { it.uppercase() }
    }

    // Pulse stats
    val intentionsDone = intentions.count { it.done }
    val habitsToday = habits.filter { h ->
        val dk = todayKey
        val created = DateUtils.keyFromMs(h.createdAt)
        dk >= created && (h.endsAt == null || dk <= DateUtils.keyFromMs(h.endsAt))
    }
    // Load today's habit data once so the pulse can count completions.
    LaunchedEffect(habitsToday.map { it.id }, todayKey) {
        val (y, m) = DateUtils.yearMonth(todayKey)
        habitsToday.forEach { vm.loadHabitMonthData(it.id, y, m) }
    }
    val habitsDone = habitsToday.count { h ->
        monthData[h.id]?.logDays?.contains(todayKey) == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tr("O que importa hoje?"),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRoutines = true }) {
                        Icon(Icons.Default.PlaylistAdd, tr("Rotinas"))
                    }
                    IconButton(onClick = { showCarryOver = true }) {
                        Icon(Icons.Default.ContentCopy, tr("Trazer intenções de ontem"))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, tr("Histórico"))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, tr("Definições"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddForm = true }) {
                Icon(Icons.Default.Add, contentDescription = tr("Adicionar"))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Pulse stats row
            item {
                PulseRow(
                    intentionsDone = intentionsDone,
                    intentionsTotal = intentions.size,
                    focusMs = dayFocusMs,
                    habitsDone = habitsDone,
                    habitsTotal = habitsToday.size,
                    tr = tr
                )
            }

            // Intentions
            if (intentions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                tr("Sem intenções hoje"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { showAddForm = true }) {
                                Text(tr("Adicionar primeira intenção"))
                            }
                        }
                    }
                }
            } else {
                items(intentions, key = { it.id }) { intention ->
                    IntentionRow(
                        intention = intention,
                        tr = tr,
                        onToggle = { vm.toggleIntention(intention.id) },
                        onEdit   = { editIntention = intention },
                        onDelete = { vm.removeIntention(intention.id) }
                    )
                }
            }

            // Today's tides
            if (habitsToday.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Marés de hoje"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(habitsToday.take(5), key = { "h_${it.id}" }) { habit ->
                    TodayHabitRow(habit = habit, todayKey = todayKey, vm = vm, tr = tr)
                }
            }

            // Reflection
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
                ReflectionSection(
                    text = reflection?.reflection ?: "",
                    onTextChange = { vm.setReflection(it) },
                    tr = tr
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Add intention sheet
    if (showAddForm) {
        AddIntentionSheet(
            onDismiss = { showAddForm = false },
            onAdd = { text, priority, targetMin, timeOfDay ->
                vm.addIntention(text, priority, targetMin, timeOfDay)
                showAddForm = false
            },
            tr = tr
        )
    }

    // Edit intention sheet
    editIntention?.let { intention ->
        EditIntentionSheet(
            intention = intention,
            onDismiss = { editIntention = null },
            onSave = { updated ->
                vm.updateIntention(updated)
                editIntention = null
            },
            onDelete = {
                vm.removeIntention(intention.id)
                editIntention = null
            },
            tr = tr
        )
    }

    // Carry over sheet
    if (showCarryOver) {
        AlertDialog(
            onDismissRequest = { showCarryOver = false },
            title = { Text(tr("Trazer intenções de ontem")) },
            text = { Text(tr("Intenções não concluídas") + " → " + DateUtils.addDays(todayKey, -1)) },
            confirmButton = {
                TextButton(onClick = { vm.carryOverIntentions(); showCarryOver = false }) {
                    Text(tr("Confirmar"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCarryOver = false }) { Text(tr("Cancelar")) }
            }
        )
    }

    // Routines sheet
    if (showRoutines) {
        RoutinesSheet(vm = vm, onDismiss = { showRoutines = false }, tr = tr)
    }
}

@Composable
private fun PulseRow(
    intentionsDone: Int,
    intentionsTotal: Int,
    focusMs: Long,
    habitsDone: Int,
    habitsTotal: Int,
    tr: (String) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PulseStat(
            icon = Icons.Default.CheckCircle,
            label = tr("feito"),
            value = "$intentionsDone/$intentionsTotal",
            modifier = Modifier.weight(1f)
        )
        PulseStat(
            icon = Icons.Default.Timer,
            label = tr("em foco"),
            value = DateUtils.formatMinutes(focusMs / 60_000),
            modifier = Modifier.weight(1f)
        )
        PulseStat(
            icon = Icons.Default.Waves,
            label = tr("marés"),
            value = "$habitsDone/$habitsTotal",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PulseStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntentionRow(
    intention: IntentionEntity,
    tr: (String) -> String,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (intention.priority) {
        1 -> MaterialTheme.colorScheme.error
        3 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Priority indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(priorityColor)
        )
        Spacer(Modifier.width(10.dp))
        // Checkbox
        Checkbox(
            checked = intention.done,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = intention.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (intention.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (intention.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val chips = buildList {
                if (intention.targetMin != null && intention.targetMin > 0)
                    add("${intention.targetMin}min")
                intention.timeOfDay?.let { t ->
                    add(when (t) {
                        "morning"   -> tr("Manhã")
                        "afternoon" -> tr("Tarde")
                        "night"     -> tr("Noite")
                        else -> t
                    })
                }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                chip,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TodayHabitRow(
    habit: HabitEntity,
    todayKey: String,
    vm: AppViewModel,
    tr: (String) -> String
) {
    val monthData by vm.habitMonthData.collectAsState()
    val data = monthData[habit.id]
    val isDone = data?.logDays?.contains(todayKey) == true
    val isRespiro = data?.respiroKeys?.contains(todayKey) == true

    LaunchedEffect(habit.id) {
        val (y, m) = DateUtils.yearMonth(todayKey)
        vm.loadHabitMonthData(habit.id, y, m)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isDone) vm.toggleHabitDay(habit.id, todayKey)
                else vm.toggleHabitDay(habit.id, todayKey)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isDone -> MaterialTheme.colorScheme.primary
                        isRespiro -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            else if (isRespiro) Text("~", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(habit.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (habit.clock.isNotBlank()) {
            Text(habit.clock, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        data?.streak?.let { streak ->
            if (streak > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        "🔥$streak",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReflectionSection(text: String, onTextChange: (String) -> Unit, tr: (String) -> String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            tr("Reflexão do dia"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(tr("O que valeu a pena hoje?"), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ── Bottom Sheets ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIntentionSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Int?, String?) -> Unit,
    tr: (String) -> String
) {
    var text      by remember { mutableStateOf("") }
    var priority  by remember { mutableIntStateOf(2) }
    var targetMin by remember { mutableStateOf("") }
    var timeOfDay by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Nova intenção"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(tr("Nova intenção")) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) onAdd(text.trim(), priority, targetMin.toIntOrNull(), timeOfDay)
                }),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            // Priority
            Text(tr("Prioridade"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "!", 2 to "•", 3 to "↓").forEach { (p, label) ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Duration
            OutlinedTextField(
                value = targetMin,
                onValueChange = { targetMin = it.filter { c -> c.isDigit() } },
                label = { Text(tr("Duração (min)")) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            // Time of day
            Text(tr("Quando"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "—", "morning" to tr("Manhã"), "afternoon" to tr("Tarde"), "night" to tr("Noite"))
                    .forEach { (tod, label) ->
                        FilterChip(selected = timeOfDay == tod, onClick = { timeOfDay = tod }, label = { Text(label) })
                    }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (text.isNotBlank()) onAdd(text.trim(), priority, targetMin.toIntOrNull(), timeOfDay)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank()
            ) { Text(tr("Adicionar")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIntentionSheet(
    intention: IntentionEntity,
    onDismiss: () -> Unit,
    onSave: (IntentionEntity) -> Unit,
    onDelete: () -> Unit,
    tr: (String) -> String
) {
    var text      by remember { mutableStateOf(intention.text) }
    var priority  by remember { mutableIntStateOf(intention.priority) }
    var targetMin by remember { mutableStateOf(intention.targetMin?.toString() ?: "") }
    var timeOfDay by remember { mutableStateOf(intention.timeOfDay) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Editar"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(tr("Nova intenção")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "!", 2 to "•", 3 to "↓").forEach { (p, label) ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = targetMin, onValueChange = { targetMin = it.filter { c -> c.isDigit() } },
                label = { Text(tr("Duração (min)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(tr("Eliminar")) }
                Button(
                    onClick = {
                        onSave(intention.copy(text = text.trim(), priority = priority, targetMin = targetMin.toIntOrNull(), timeOfDay = timeOfDay))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = text.isNotBlank()
                ) { Text(tr("Guardar")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinesSheet(vm: AppViewModel, onDismiss: () -> Unit, tr: (String) -> String) {
    val routines by vm.routines.collectAsState()
    var showSave by remember { mutableStateOf(false) }
    var routineName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Rotinas"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            if (routines.isEmpty()) {
                Text(tr("Sem dados"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                routines.forEach { routine ->
                    ListItem(
                        headlineContent = { Text(routine.name) },
                        trailingContent = {
                            IconButton(onClick = { vm.applyRoutine(routine.id) }) {
                                Icon(Icons.Default.PlayArrow, tr("Aplicar rotina"))
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (showSave) {
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text(tr("Nome da rotina")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (routineName.isNotBlank()) { vm.saveRoutineFromToday(routineName); onDismiss() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(tr("Guardar")) }
            } else {
                OutlinedButton(onClick = { showSave = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Guardar como rotina"))
                }
            }
        }
    }
}
