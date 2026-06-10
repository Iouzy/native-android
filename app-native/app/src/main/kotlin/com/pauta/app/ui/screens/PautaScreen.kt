package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.computeTodayTides
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
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
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val intentions by vm.intentions.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val habitCounts by vm.habitCounts.collectAsStateWithLifecycle()

    // 1s clock tick driving the live timer; restarts when the active block changes.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedTick { now = it }

    val segsByBlock = remember(allSessions) { allSessions.groupBy { it.blockId } }
    fun blockMs(id: String): Long =
        FocusMath.blockElapsedMs(segsByBlock[id].orEmpty().map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)

    val dailyMs = remember(allSessions, now, today) {
        FocusMath.dailyFocusMs(allSessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, today, now)
    }
    val paused = blocks.filter { it.status == "paused" }
    val done = blocks.filter { it.status == "done" }

    // Sheet feeds, mirroring tab-pauta.jsx: distinct projects, the 5 most
    // recent distinct titles as templates, and the still-pending tides.
    val projects = remember(blocks) { blocks.mapNotNull { it.project }.distinct() }
    val recentBlocks = remember(blocks) {
        val seen = mutableSetOf<String>()
        blocks.filter { it.status == "done" || it.status == "paused" }
            .sortedByDescending { it.createdAt }
            .filter { seen.add(it.title) }
            .take(5)
    }
    val pendingTides = remember(habits, habitLogs, habitRespiros, habitCounts, today) {
        computeTodayTides(habits, habitLogs, habitRespiros, habitCounts, today)
            .filter { it.state == DayState.EMPTY }
    }

    var showStart by remember { mutableStateOf(false) }
    var showSwitch by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    // The pause sheet opens AFTER the optimistic pause (timer already stopped).
    var pauseNoteFor by remember { mutableStateOf<FocusBlockEntity?>(null) }
    // (block, wasActive): active blocks are optimistically concluded; cancel resumes.
    var concludeFor by remember { mutableStateOf<Pair<FocusBlockEntity, Boolean>?>(null) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trf("{n} blocos · {t} de foco hoje", "n" to blocks.size, "t" to FocusMath.fmtDuration(dailyMs)),
                    color = colors.ink3,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                // "registar" — log a past block by hand, like the web's chip.
                Text(
                    text = "+ " + tr("registar").uppercase(),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.26.sp, // 0.14em of 9sp
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                        .clickableNoRipple { showManual = true }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }

            active?.let { a ->
                Spacer(Modifier.height(18.dp))
                ActiveCard(
                    block = a,
                    elapsedMs = blockMs(a.id),
                    // Optimistic pause/conclude: stop the timer first, collect
                    // the note/reflection after — no seconds lost while typing.
                    onPause = { vm.pauseActive(""); pauseNoteFor = a },
                    onSwitch = { showSwitch = true },
                    onConclude = { vm.concludeActive("", false); concludeFor = a to true },
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
                            ActionText(tr("Concluir")) { concludeFor = b to false }
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
        StartSheet(
            intentions = intentions,
            projects = projects,
            recentBlocks = recentBlocks,
            hasActive = active != null,
            activeTitle = active?.title.orEmpty(),
            onStart = { title, linkedToId, project, targetMin ->
                vm.startBlock(title, linkedToId, project, targetMin)
                showStart = false
            },
            onClose = { showStart = false },
        )
    }
    if (showSwitch) {
        active?.let { a ->
            SwitchSheet(
                currentBlock = a,
                intentions = intentions,
                onPick = { linkedToId, title ->
                    // startBlock auto-pauses the running block, like the web flow.
                    vm.startBlock(title, linkedToId)
                    showSwitch = false
                },
                onConcludeFirst = {
                    vm.concludeActive("", false)
                    concludeFor = a to true
                    showSwitch = false
                },
                onClose = { showSwitch = false },
            )
        }
    }
    if (showManual) {
        ManualBlockSheet(
            today = today,
            onAdd = { title, startMs, endMs -> vm.addManualBlock(title, startMs, endMs) },
            onClose = { showManual = false },
        )
    }
    pauseNoteFor?.let { block ->
        PauseSheet(
            block = block,
            onResume = { vm.resumeBlock(block.id); pauseNoteFor = null },
            onConfirm = { note ->
                if (note.isNotBlank()) vm.setLastSessionNote(block.id, note)
                pauseNoteFor = null
            },
        )
    }
    concludeFor?.let { (block, wasActive) ->
        ConcludeSheet(
            block = block,
            totalMs = blockMs(block.id),
            intention = block.linkedToId?.let { id -> intentions.firstOrNull { it.id == id } },
            todayTides = pendingTides,
            wasActive = wasActive,
            onConfirm = { reflection, markDone, tideIds ->
                vm.concludeBlock(block.id, reflection, markDone)
                // Drawn from the pending set, so toggling marks done — never
                // un-does an already-done tide.
                tideIds.forEach { vm.toggleHabitToday(it) }
                concludeFor = null
            },
            onCancel = {
                if (wasActive) vm.resumeBlock(block.id)
                concludeFor = null
            },
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
private fun ActiveCard(block: FocusBlockEntity, elapsedMs: Long, onPause: () -> Unit, onSwitch: () -> Unit, onConclude: () -> Unit) {
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
            ActionText(tr("Trocar"), color = colors.onDark2, onClick = onSwitch)
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
