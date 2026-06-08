package com.pauta.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PautaScreen(vm: AppViewModel, onOpenSettings: () -> Unit = {}) {
    val prefs        by vm.prefs.collectAsState()
    val activeBlock  by vm.activeBlock.collectAsState()
    val pausedBlocks by vm.pausedBlocks.collectAsState()
    val todayBlocks  by vm.todayBlocks.collectAsState()
    val intentions   by vm.intentions.collectAsState()
    val dayFocusMs   by vm.dayFocusMs.collectAsState()

    val tr = { pt: String -> Strings.tr(pt, prefs.lang) }

    var showStart    by remember { mutableStateOf(false) }
    var showConclude by remember { mutableStateOf<FocusBlockEntity?>(null) }
    var showPause    by remember { mutableStateOf(false) }

    val doneBlocks = todayBlocks.filter { it.status == "done" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr("Blocos de foco"), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${todayBlocks.size} ${tr("blocos")} · ${DateUtils.formatMinutes(dayFocusMs / 60_000)} ${tr("em foco")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
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
            if (activeBlock == null) {
                ExtendedFloatingActionButton(
                    onClick = { showStart = true },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    text = { Text(tr("Iniciar foco")) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active block card
            activeBlock?.let { block ->
                item {
                    ActiveBlockCard(
                        block = block,
                        vm = vm,
                        tr = tr,
                        onPause = { showPause = true },
                        onSwitch = { showStart = true },
                        onConclude = { showConclude = block }
                    )
                }
            }

            // Paused blocks
            if (pausedBlocks.isNotEmpty()) {
                item {
                    Text(
                        tr("Pausado"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(pausedBlocks, key = { it.id }) { block ->
                    PausedBlockCard(
                        block = block,
                        vm = vm,
                        tr = tr,
                        onResume = { vm.resumeBlock(block.id) },
                        onConclude = { showConclude = block }
                    )
                }
            }

            // Done blocks (timeline)
            if (doneBlocks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Linha do tempo"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(doneBlocks, key = { it.id }) { block ->
                    DoneBlockRow(block = block, vm = vm, tr = tr, onDelete = { vm.deleteBlock(block.id) })
                }
            }

            if (todayBlocks.isEmpty() && activeBlock == null) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Timer, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(tr("Nenhum bloco hoje"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Start sheet
    if (showStart) {
        StartFocusSheet(
            intentions = intentions.filter { !it.done },
            tr = tr,
            onDismiss = { showStart = false },
            onStart = { title, linkedId, project, targetMs ->
                vm.startBlock(title, linkedId, project, targetMs)
                showStart = false
            }
        )
    }

    // Pause sheet
    if (showPause) {
        PauseSheet(
            tr = tr,
            onDismiss = { showPause = false },
            onPause = { note -> vm.pauseActive(note); showPause = false }
        )
    }

    // Conclude sheet
    showConclude?.let { block ->
        ConcludeSheet(
            block = block,
            linkedIntention = intentions.firstOrNull { it.id == block.linkedIntentionId },
            tr = tr,
            onDismiss = { showConclude = null },
            onConclude = { reflection, markDone ->
                vm.concludeBlock(block.id, reflection, markDone)
                showConclude = null
            }
        )
    }
}

/** A live ticking elapsed-ms value for [block], recomputed each second. */
@Composable
private fun rememberElapsedMs(block: FocusBlockEntity, vm: AppViewModel): Long {
    var elapsed by remember(block.id, block.status) { mutableLongStateOf(0L) }
    LaunchedEffect(block.id, block.status) {
        while (true) {
            elapsed = vm.elapsedMs(block.id)
            if (block.status != "active") break
            delay(1000)
        }
    }
    return elapsed
}

@Composable
private fun ActiveBlockCard(
    block: FocusBlockEntity,
    vm: AppViewModel,
    tr: (String) -> String,
    onPause: () -> Unit,
    onSwitch: () -> Unit,
    onConclude: () -> Unit
) {
    val elapsed = rememberElapsedMs(block, vm)
    val reached = block.targetMs > 0 && elapsed >= block.targetMs

    // Subtle breathing pulse on the active card.
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(block.title, style = MaterialTheme.typography.titleLarge)
            block.project?.let {
                if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            val display = if (block.targetMs > 0 && !reached)
                DateUtils.formatDuration(block.targetMs - elapsed)
            else DateUtils.formatDuration(elapsed)
            Text(
                display,
                fontFamily = FontFamily.Monospace,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(pulseAlpha),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                when {
                    reached       -> tr("✓ meta cumprida")
                    block.targetMs > 0 -> "${tr("Em foco")} · ${tr("Meta (min)")} ${block.targetMs / 60_000}"
                    else          -> tr("Em foco")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Pausar"))
                }
                OutlinedButton(onClick = onSwitch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Trocar"))
                }
                Button(onClick = onConclude, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Concluir"))
                }
            }
        }
    }
}

@Composable
private fun PausedBlockCard(
    block: FocusBlockEntity,
    vm: AppViewModel,
    tr: (String) -> String,
    onResume: () -> Unit,
    onConclude: () -> Unit
) {
    val elapsed = rememberElapsedMs(block, vm)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    DateUtils.formatDuration(elapsed),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, tr("Retomar"), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onConclude) {
                Icon(Icons.Default.CheckCircle, tr("Concluir"))
            }
        }
    }
}

@Composable
private fun DoneBlockRow(
    block: FocusBlockEntity,
    vm: AppViewModel,
    tr: (String) -> String,
    onDelete: () -> Unit
) {
    val sessions by vm.getSessionsForBlock(block.id).collectAsState(initial = emptyList())
    val totalMs = sessions.sumOf { s -> (s.endedAt ?: s.startedAt) - s.startedAt }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(block.title, style = MaterialTheme.typography.bodyMedium)
            if (block.reflection.isNotBlank()) {
                Text(block.reflection, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            DateUtils.formatMinutes(totalMs / 60_000),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, tr("Eliminar"), modifier = Modifier.size(16.dp))
        }
    }
}

// ── Sheets ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartFocusSheet(
    intentions: List<IntentionEntity>,
    tr: (String) -> String,
    onDismiss: () -> Unit,
    onStart: (String, String?, String?, Long) -> Unit
) {
    var title    by remember { mutableStateOf("") }
    var project  by remember { mutableStateOf("") }
    var target   by remember { mutableStateOf("") }
    var linkedId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Iniciar foco"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            if (intentions.isNotEmpty()) {
                Text(tr("Ligado a"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    intentions.take(5).forEach { intention ->
                        FilterChip(
                            selected = linkedId == intention.id,
                            onClick = {
                                linkedId = if (linkedId == intention.id) null else intention.id
                                if (linkedId != null) title = intention.text
                            },
                            label = { Text(intention.text, maxLines = 1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(tr("Título do bloco")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = project, onValueChange = { project = it },
                label = { Text(tr("Projeto")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = target, onValueChange = { target = it.filter { c -> c.isDigit() } },
                label = { Text(tr("Meta (min)")) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val targetMs = (target.toLongOrNull() ?: 0L) * 60_000L
                    onStart(title.trim(), linkedId, project.trim().ifBlank { null }, targetMs)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) { Text(tr("Iniciar")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseSheet(tr: (String) -> String, onDismiss: () -> Unit, onPause: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Pausar"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(tr("Nota da pausa")) },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onPause(note.trim()) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Pausar"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConcludeSheet(
    block: FocusBlockEntity,
    linkedIntention: IntentionEntity?,
    tr: (String) -> String,
    onDismiss: () -> Unit,
    onConclude: (String, Boolean) -> Unit
) {
    var reflection by remember { mutableStateOf(block.reflection) }
    var markDone   by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 16.dp)) {
            Text(tr("Concluir"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = reflection, onValueChange = { reflection = it },
                label = { Text(tr("Reflexão do bloco")) },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )
            if (linkedIntention != null && !linkedIntention.done) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = markDone, onCheckedChange = { markDone = it })
                    Text(tr("Marcar intenção como concluída"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onConclude(reflection.trim(), markDone) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Concluir"))
            }
        }
    }
}
