package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.domain.FocusMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

/**
 * The Pauta (focus) tab: the running block as a live-ticking dark card with
 * pause/conclude, the paused blocks (resume/conclude), and a timeline of
 * concluded blocks — plus a start dialog. Live timing is a 1-second clock tick
 * fed into [FocusMath]. Filters, the switch flow, manual logging and the
 * foreground service follow. // PT: tab Pauta — bloco activo a contar, pausados,
 * e linha do tempo dos concluídos.
 */
@Composable
fun PautaScreen() {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val active by vm.activeBlock.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()

    // 1s clock tick driving the live timer; restarts when the active block changes.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedTick { now = it }

    val segsByBlock = remember(allSessions) { allSessions.groupBy { it.blockId } }
    fun blockMs(id: String): Long =
        FocusMath.blockElapsedMs(segsByBlock[id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)

    val dailyMs = remember(allSessions, now) {
        FocusMath.dailyFocusMs(allSessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, vm.todayKey, now)
    }
    val paused = blocks.filter { it.status == "paused" }
    val done = blocks.filter { it.status == "done" }

    var showStart by remember { mutableStateOf(false) }
    var concludeTarget by remember { mutableStateOf<FocusBlockEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(tr("Pauta"), color = colors.ink, fontFamily = SerifFamily, fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = trf("{n} blocos · {t} de foco hoje", "n" to blocks.size, "t" to FocusMath.fmtDuration(dailyMs)),
                color = colors.ink3,
                fontSize = 13.sp,
            )

            active?.let { a ->
                Spacer(Modifier.height(18.dp))
                ActiveCard(
                    block = a,
                    elapsedMs = blockMs(a.id),
                    onPause = { vm.pauseActive() },
                    onConclude = { concludeTarget = a },
                )
            }

            if (paused.isNotEmpty()) {
                SectionHeader(tr("Em pausa"))
                paused.forEach { b ->
                    BlockRow(
                        block = b,
                        elapsedMs = blockMs(b.id),
                        trailing = {
                            ActionText(tr("Retomar")) { vm.resumeBlock(b.id) }
                            Spacer(Modifier.width(14.dp))
                            ActionText(tr("Concluir")) { concludeTarget = b }
                        },
                    )
                }
            }

            if (done.isNotEmpty()) {
                SectionHeader(tr("Concluídos"))
                done.forEach { b -> BlockRow(block = b, elapsedMs = blockMs(b.id), reflection = b.reflection) }
            }

            if (blocks.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(tr("Sem blocos de foco ainda. Toca em + para começar."), color = colors.ink4, fontSize = 14.sp)
            }
            Spacer(Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = { showStart = true },
            containerColor = colors.accent,
            contentColor = colors.onDark,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = tr("Começar bloco"))
        }
    }

    if (showStart) {
        StartDialog(
            onStart = { title, targetMin -> vm.startBlock(title, targetMin = targetMin); showStart = false },
            onDismiss = { showStart = false },
        )
    }
    concludeTarget?.let { target ->
        ConcludeDialog(
            block = target,
            onConclude = { reflection, markDone ->
                if (active?.id == target.id) vm.concludeActive(reflection, markDone)
                else vm.concludeBlock(target.id, reflection, markDone)
                concludeTarget = null
            },
            onDismiss = { concludeTarget = null },
        )
    }
}

@Composable
private fun LaunchedTick(onTick: (Long) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            onTick(System.currentTimeMillis())
            delay(1000)
        }
    }
}

@Composable
private fun ActiveCard(block: FocusBlockEntity, elapsedMs: Long, onPause: () -> Unit, onConclude: () -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceDark)
            .padding(20.dp),
    ) {
        Text(block.title, color = colors.onDark, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(FocusMath.fmtTimer(elapsedMs), color = colors.onDark, fontSize = 44.sp, fontWeight = FontWeight.Light)
        block.targetMs?.let { target ->
            Spacer(Modifier.height(4.dp))
            Text(trf("alvo {t}", "t" to FocusMath.fmtDuration(target)), color = colors.onDark2, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            ActionText(tr("Pausar"), color = colors.onDark, onClick = onPause)
            Spacer(Modifier.width(20.dp))
            ActionText(tr("Concluir"), color = colors.accentSoft, onClick = onConclude)
        }
    }
}

@Composable
private fun BlockRow(
    block: FocusBlockEntity,
    elapsedMs: Long,
    reflection: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(block.title, color = colors.ink, fontSize = 16.sp)
                Text(FocusMath.fmtDuration(elapsedMs), color = colors.ink3, fontSize = 12.sp)
            }
            trailing?.invoke()
        }
        if (reflection.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(reflection, color = colors.ink3, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    val colors = LocalPautaColors.current
    Spacer(Modifier.height(22.dp))
    Text(label, color = colors.ink3, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun ActionText(label: String, color: Color = LocalPautaColors.current.accent, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.clickableNoRipple(onClick),
    )
}

@Composable
private fun StartDialog(onStart: (String, Int?) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPautaColors.current
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        title = { Text(tr("Novo bloco"), color = colors.ink) },
        text = {
            Column {
                DialogField(title, { title = it }, tr("Em que vais focar?"))
                Spacer(Modifier.height(8.dp))
                DialogField(minutes, { minutes = it.filter { c -> c.isDigit() }.take(3) }, tr("alvo em minutos (opcional)"), number = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onStart(title.trim(), minutes.toIntOrNull()?.takeIf { it > 0 }) }) {
                Text(tr("Iniciar agora"), color = colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar"), color = colors.ink3) } },
    )
}

@Composable
private fun ConcludeDialog(block: FocusBlockEntity, onConclude: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPautaColors.current
    var reflection by remember { mutableStateOf("") }
    var markDone by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper,
        title = { Text(tr("Concluir bloco"), color = colors.ink) },
        text = {
            Column {
                DialogField(reflection, { reflection = it }, tr("O que aconteceu?"))
                if (block.linkedToId != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = markDone, onCheckedChange = { markDone = it })
                        Text(tr("Marcar a intenção como feita"), color = colors.ink2, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConclude(reflection.trim(), markDone) }) {
                Text(tr("Concluir"), color = colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar"), color = colors.ink3) } },
    )
}

@Composable
private fun DialogField(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false) {
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
