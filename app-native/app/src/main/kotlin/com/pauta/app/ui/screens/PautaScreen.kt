package com.pauta.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator.DayState
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PeriodLabel
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.computeTodayTides
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * The Pauta (focus) tab, to the web's spec (tab-pauta.jsx + sub-components.jsx):
 * the mono date eyebrow + the serif "N blocos. X em foco." headline with the
 * histórico/registar chips, the dark start CTA, the full ActiveBlockCard
 * (status row, início time, zen + discard, current-segment timer), the paused
 * cards, the filter chips, and today's event timeline (iniciado / pausa /
 * retomado / concluído with rails, durations and inline pause notes).
 * // PT: tab Pauta fiel à web — cabeçalho, cartão activo, pausados e linha do
 * tempo de eventos do dia.
 */
@Composable
fun PautaScreen() {
    val colors = LocalPautaColors.current
    val vm: com.pauta.app.ui.viewmodel.AppViewModel = viewModel()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val active by vm.activeBlock.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val intentions by vm.intentions.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val habitLogs by vm.habitLogs.collectAsStateWithLifecycle()
    val habitRespiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val habitCounts by vm.habitCounts.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    // 1s clock tick driving the live timer.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val segsByBlock = remember(allSessions) { allSessions.groupBy { it.blockId } }
    fun sessionsOf(id: String): List<FocusSessionEntity> = segsByBlock[id].orEmpty()
    fun blockMs(id: String): Long =
        FocusMath.blockElapsedMs(sessionsOf(id).map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)

    // Filter — the web's {kind, id, label} lifted state.
    var filter by remember { mutableStateOf<PautaFilter?>(null) }
    fun matches(b: FocusBlockEntity): Boolean = when (filter?.kind) {
        "project" -> b.project == filter?.id
        "intention" -> b.linkedToId == filter?.id
        "block" -> b.id == filter?.id
        else -> true
    }

    // Today's event timeline, built like the web's buildTimeline(blocks, dayK).
    val blockById = remember(blocks) { blocks.associateBy { it.id } }
    val allEvents = remember(blocks, allSessions, today) {
        buildTimelineEvents(blocks, allSessions, today)
    }
    val events = allEvents.filter { e -> blockById[e.blockId]?.let { matches(it) } ?: false }

    // Header numbers: distinct blocks in the (filtered) timeline + their focus today.
    val distinctBlockCount = events.map { it.blockId }.toSet().size
    val eventBlockIds = events.map { it.blockId }.toSet()
    val totalFocus = FocusMath.dailyFocusMs(
        allSessions.filter { it.blockId in eventBlockIds }.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
        today, now,
    )
    // A3: the headline block count ticks up to its new value when a block is
    // started or concluded (snap when reduced motion). // PT: a contagem sobe.
    val animBlockCount by animateIntAsState(
        targetValue = distinctBlockCount,
        animationSpec = if (prefs.reducedMotion) snap() else tween(450, easing = FastOutSlowInEasing),
        label = "pauta-blocks",
    )

    val pausedBlocks = blocks.filter {
        it.status == "paused" && DateUtils.dayKeyOf(it.createdAt) == today && matches(it)
    }

    // Sheet feeds, mirroring tab-pauta.jsx.
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
    var showHistory by remember { mutableStateOf(false) }
    var editFor by remember { mutableStateOf<FocusBlockEntity?>(null) }
    var discardFor by remember { mutableStateOf<FocusBlockEntity?>(null) }
    var zen by remember { mutableStateOf(false) }
    // The pause sheet opens AFTER the optimistic pause (timer already stopped).
    var pauseNoteFor by remember { mutableStateOf<FocusBlockEntity?>(null) }
    // (block, wasActive): active blocks are optimistically concluded; cancel resumes.
    var concludeFor by remember { mutableStateOf<Pair<FocusBlockEntity, Boolean>?>(null) }
    // The in-app "meta cumprida" prompt; reset whenever the active block changes.
    var reachedPrompt by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(active?.id) { reachedPrompt = null }

    fun pauseWithNote(a: FocusBlockEntity) { vm.pauseActive(""); pauseNoteFor = a }
    fun concludeOptimistic(a: FocusBlockEntity) { vm.concludeActive("", false); concludeFor = a to true }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            // ── Header — eyebrow date + serif count line + the two chips ──
            item(key = "header") {
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        val todayDate = LocalDate.parse(today)
                        PeriodLabel(
                            prefix = I18n.fmtWeekdayDay(todayDate) + " ",
                            month = I18n.fmtMonthShort(todayDate.monthValue),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("$animBlockCount ")
                                append(if (animBlockCount == 1) tr("bloco") else tr("blocos"))
                                append(". ")
                                withStyle(SpanStyle(color = colors.accent, fontStyle = FontStyle.Italic)) {
                                    append(FocusMath.fmtDuration(totalFocus))
                                }
                                append(" " + tr("em foco."))
                            },
                            color = colors.ink,
                            fontFamily = SerifFamily,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            letterSpacing = (-0.3).sp, // -0.01em of 30sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderChip(tr("histórico") + " ↗") { showHistory = true }
                        HeaderChip("+ " + tr("registar")) { showManual = true }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // ── Active block / start CTA ──
            item(key = "active-or-start") {
                active?.let { a ->
                    ActiveBlockCard(
                        block = a,
                        sessions = sessionsOf(a.id),
                        now = now,
                        intention = a.linkedToId?.let { id -> intentions.firstOrNull { it.id == id } },
                        reducedMotion = prefs.reducedMotion,
                        onReached = {
                            val min = a.targetMs?.let { (it / 60_000L).toInt() } ?: 0
                            reachedPrompt = min
                        },
                        onPause = { pauseWithNote(a) },
                        onSwitch = { showSwitch = true },
                        onConclude = { concludeOptimistic(a) },
                        onCancel = { discardFor = a },
                        onZen = { zen = true },
                    )
                    Spacer(Modifier.height(22.dp))
                }
                if (active == null) {
                    StartCtaCard { showStart = true }
                    Spacer(Modifier.height(22.dp))
                }
            }

            // ── Paused blocks ── keyed cards so A3 can animate them.
            if (pausedBlocks.isNotEmpty()) {
                item(key = "paused-header") {
                    MonoSectionLabel(tr("Em pausa"))
                    Spacer(Modifier.height(10.dp))
                }
                itemsIndexed(pausedBlocks, key = { _, b -> "paused-${b.id}" }) { index, b ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    PausedBlockCard(
                        block = b,
                        sessions = sessionsOf(b.id),
                        now = now,
                        onResume = { vm.resumeBlock(b.id) },
                        onEdit = { editFor = b },
                    )
                }
                item(key = "paused-footer") { Spacer(Modifier.height(22.dp)) }
            }

            // ── Filter chips ──
            if (intentions.isNotEmpty() || filter != null) {
                item(key = "filter-chips") {
                    val todayBlocks = blocks.filter { DateUtils.dayKeyOf(it.createdAt) == today }
                    PautaFilterChips(
                        intentions = intentions,
                        todayBlocks = todayBlocks,
                        projects = projects,
                        filter = filter,
                        onFilter = { filter = it },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ── Timeline ──
            item(key = "timeline-header") {
                Spacer(Modifier.height(18.dp))
                MonoSectionLabel(if (filter != null) tr("Filtrado") else tr("Hoje"))
                Spacer(Modifier.height(14.dp))
            }
            if (events.isEmpty()) {
                item(key = "timeline-empty") {
                    Text(
                        text = if (filter != null) tr("Nada por aqui ainda.") else tr("Ainda nenhum bloco hoje. Comece quando quiser."),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                    if (filter == null) {
                        Spacer(Modifier.height(14.dp))
                        StarterChip(tr("Começar um bloco de foco")) { showStart = true }
                    }
                }
            } else {
                // Timeline events as keyed items; connectUp/connectDown still read
                // the neighbours by index, exactly like TimelineList. // PT: eventos
                // da linha do tempo como itens com chave.
                itemsIndexed(events, key = { _, e -> "${e.sessionRowId}-${e.kind}" }) { i, e ->
                    val block = blockById[e.blockId]
                    if (block != null) {
                        TimelineRow(
                            event = e,
                            block = block,
                            isLast = i == events.size - 1,
                            connectUp = i > 0 && events[i - 1].blockId == e.blockId,
                            connectDown = i < events.size - 1 && events[i + 1].blockId == e.blockId,
                            onFilter = { filter = PautaFilter("block", e.blockId, block.title) },
                            onEdit = { editFor = blockById[e.blockId] },
                            onEditNote = { rowId, text -> vm.setSessionNote(rowId, text) },
                        )
                    }
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(96.dp)) }
        }

        // In-app goal-reached prompt, mirroring the native heads-up notification.
        reachedPrompt?.let { targetMin ->
            if (active != null) {
                GoalReachedPrompt(
                    targetMin = targetMin,
                    onContinue = { reachedPrompt = null },
                    onConclude = {
                        val a = active ?: return@GoalReachedPrompt
                        reachedPrompt = null
                        concludeOptimistic(a)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
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
                    vm.startBlock(title, linkedToId)
                    showSwitch = false
                },
                onConcludeFirst = {
                    concludeOptimistic(a)
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
    if (showHistory) {
        PautaHistorySheet(
            blocks = blocks,
            allSessions = allSessions,
            today = today,
            onClose = { showHistory = false },
        )
    }
    editFor?.let { b ->
        EditBlockSheet(
            block = blockById[b.id] ?: b,
            sessions = sessionsOf(b.id),
            now = now,
            onSave = { title, project, targetMs, reflection, notes ->
                vm.updateBlock(b.id, title, project, targetMs)
                vm.setBlockReflection(b.id, reflection)
                notes.forEach { (rowId, text) -> vm.setSessionNote(rowId, text) }
                editFor = null
            },
            onDelete = { vm.deleteBlock(b.id); editFor = null },
            onClose = { editFor = null },
        )
    }
    discardFor?.let { b ->
        PautaSheetConfirm(
            message = tr("Descartar este bloco? Não fica guardado."),
            confirmLabel = tr("Descartar"),
            onConfirm = { vm.deleteBlock(b.id); discardFor = null },
            onClose = { discardFor = null },
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
                tideIds.forEach { vm.toggleHabitToday(it) }
                concludeFor = null
            },
            onCancel = {
                if (wasActive) vm.resumeBlock(block.id)
                concludeFor = null
            },
        )
    }

    if (zen) {
        val a = active
        if (a == null) {
            zen = false
        } else {
            ZenFocus(
                block = a,
                sessions = sessionsOf(a.id),
                now = now,
                onExit = { zen = false },
                onPause = { zen = false; pauseWithNote(a) },
                onConclude = { zen = false; concludeOptimistic(a) },
            )
        }
    }
}

/** The mono uppercase section label ("EM PAUSA", "HOJE", "FILTRADO"). */
@Composable
internal fun MonoSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        color = LocalPautaColors.current.ink3,
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp, // 0.18em of 10sp
    )
}

/** The header's bordered mono chips ("HISTÓRICO ↗", "+ REGISTAR"). */
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

/** The dark "COMEÇAR / Um novo bloco" hero card with the accent play circle. */
@Composable
private fun StartCtaCard(onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceDark)
            .clickableNoRipple(onClick)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = tr("começar").uppercase(),
                color = colors.onDark2,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 2.sp, // 0.2em of 10sp
            )
            Spacer(Modifier.height(4.dp))
            Text(tr("Um novo bloco"), color = colors.onDark, fontFamily = SerifFamily, fontSize = 22.sp)
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) { PlayTriangle(color = colors.onDark, size = 14.dp) }
    }
}

/** A small filled play triangle (the web's Icon.Play). */
@Composable
internal fun PlayTriangle(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.22f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.88f)
            close()
        }
        drawPath(p, color)
    }
}

/** Two pause bars (the web's Icon.Pause). */
@Composable
internal fun PauseBars(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width * 0.26f
        val r = androidx.compose.ui.geometry.CornerRadius(w * 0.35f)
        drawRoundRect(color, topLeft = Offset(this.size.width * 0.14f, 0f), size = androidx.compose.ui.geometry.Size(w, this.size.height), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(this.size.width * 0.6f, 0f), size = androidx.compose.ui.geometry.Size(w, this.size.height), cornerRadius = r)
    }
}

/** The web's kebab (three dots) icon. */
@Composable
internal fun KebabDots(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val r = 1.2.dp.toPx()
        val cy = size.height / 2
        drawCircle(color, r, Offset(size.width * 0.2f, cy))
        drawCircle(color, r, Offset(size.width * 0.5f, cy))
        drawCircle(color, r, Offset(size.width * 0.8f, cy))
    }
}

// ─── Active block card ─────────────────────────────────────

@Composable
private fun ActiveBlockCard(
    block: FocusBlockEntity,
    sessions: List<FocusSessionEntity>,
    now: Long,
    intention: IntentionEntity?,
    reducedMotion: Boolean,
    onReached: () -> Unit,
    onPause: () -> Unit,
    onSwitch: () -> Unit,
    onConclude: () -> Unit,
    onCancel: () -> Unit,
    onZen: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val current = sessions.lastOrNull()
    val elapsed = current?.let { now - it.startedAt } ?: 0L
    val totalElapsed = FocusMath.blockElapsedMs(sessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)
    val hasResumed = sessions.size > 1

    // Soft target: fire onReached exactly on the false→true transition while
    // mounted (seeded so reopening an over-target block doesn't re-fire).
    val target = block.targetMs ?: 0L
    val reached = target > 0 && totalElapsed >= target
    var reachedSeen by remember(block.id) { mutableStateOf(reached) }
    LaunchedEffect(reached) {
        if (reached && !reachedSeen) { reachedSeen = true; onReached() }
        if (!reached) reachedSeen = false
    }
    val targetMin = if (target > 0) (target / 60_000L).toInt() else 0

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceDark)
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
    ) {
        // status row: pulsing dot + label · início + zen + discard
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = colors.accent, animate = !reducedMotion)
            Spacer(Modifier.width(8.dp))
            Text(
                text = (if (hasResumed) tr("retomado · em curso") else tr("em curso")).uppercase(),
                color = colors.onDark2,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 2.sp, // 0.2em of 10sp
                modifier = Modifier.weight(1f),
            )
            current?.let {
                Text(
                    text = trf("início {t}", "t" to DateUtils.fmtClock(it.startedAt)),
                    color = colors.onDark2,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp, // 0.06em of 10sp
                )
            }
            Spacer(Modifier.width(10.dp))
            DarkRoundButton(onClick = onZen) { ZenCorners(colors.onDark2) }
            Spacer(Modifier.width(10.dp))
            DarkRoundButton(onClick = onCancel) {
                Text("×", color = colors.onDark2, fontSize = 14.sp, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (intention != null) {
            Text(
                text = tr("intenção do dia →"),
                color = colors.onDark2,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = block.title,
            color = colors.onDark,
            fontFamily = SerifFamily,
            fontSize = 24.sp,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = FocusMath.fmtTimer(elapsed),
                    color = colors.onDark,
                    fontFamily = MonoFamily,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.84).sp, // -0.02em of 42sp
                    lineHeight = 40.sp,
                )
                if (hasResumed) {
                    Text(
                        text = trf("{d} no total", "d" to FocusMath.fmtDuration(totalElapsed)),
                        color = colors.onDark2,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.54.sp,
                    )
                }
                if (targetMin > 0) {
                    Text(
                        text = if (reached) "✓ " + tr("meta cumprida") else trf("meta {n} min", "n" to targetMin),
                        color = if (reached) colors.accent else colors.onDark2,
                        fontFamily = MonoFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.72.sp, // 0.08em of 9sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DarkPillButton(tr("Pausar"), icon = { PauseBars(colors.onDark, 10.dp) }, onClick = onPause)
                    DarkPillButton(tr("Trocar"), icon = { Text("›", color = colors.onDark, fontSize = 12.sp, lineHeight = 12.sp) }, onClick = onSwitch)
                }
                Text(
                    text = tr("Concluir").uppercase(),
                    color = colors.onDark,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp, // 0.1em of 11sp
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accent)
                        .clickableNoRipple(onConclude)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** The web's `pulse` keyframes on the live dot. */
@Composable
private fun PulsingDot(color: Color, animate: Boolean) {
    val alpha = if (animate) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

/** The 22dp translucent round buttons on the dark card (zen / discard). */
@Composable
private fun DarkRoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0x14F5F1EA)) // rgba(245,241,234,0.08)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The web's zen icon: four corner brackets. */
@Composable
private fun ZenCorners(color: Color) {
    Canvas(Modifier.size(11.dp)) {
        val s = size.width
        val l = s * 0.32f
        val stroke = Stroke(width = 1.6.dp.toPx())
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawPath(
                Path().apply {
                    moveTo(x + dx * l, y)
                    lineTo(x, y)
                    lineTo(x, y + dy * l)
                },
                color, style = stroke,
            )
        }
        corner(0f, 0f, 1f, 1f)
        corner(s, 0f, -1f, 1f)
        corner(0f, s, 1f, -1f)
        corner(s, s, -1f, -1f)
    }
}

/** The dark card's outlined mono pill buttons (Pausar / Trocar). */
@Composable
private fun DarkPillButton(label: String, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x38F5F1EA), RoundedCornerShape(999.dp)) // rgba(245,241,234,0.22)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.invoke()
        Text(
            text = label.uppercase(),
            color = colors.onDark,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp, // 0.08em of 10sp
        )
    }
}

// ─── Paused block card ─────────────────────────────────────

@Composable
private fun PausedBlockCard(
    block: FocusBlockEntity,
    sessions: List<FocusSessionEntity>,
    now: Long,
    onResume: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val lastEnd = sessions.lastOrNull { it.endedAt != null }?.endedAt
    val totalMs = FocusMath.blockElapsedMs(sessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.paper2)
            .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(colors.paper3),
            contentAlignment = Alignment.Center,
        ) { PauseBars(colors.ink2, 12.dp) }
        Column(Modifier.weight(1f)) {
            Text(
                text = block.title,
                color = colors.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = trf(
                    "pausado às {t} · {d} acumulado",
                    "t" to (lastEnd?.let { DateUtils.fmtClock(it) } ?: "—"),
                    "d" to FocusMath.fmtDuration(totalMs),
                ),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp, // 0.04em of 10sp
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent)
                .clickableNoRipple(onResume)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PlayTriangle(colors.onDark, 10.dp)
            Text(
                text = tr("Retomar").uppercase(),
                color = colors.onDark,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
        }
        Box(Modifier.size(30.dp).clickableNoRipple(onEdit), contentAlignment = Alignment.Center) {
            KebabDots(colors.ink3)
        }
    }
}

// ─── Timeline ──────────────────────────────────────────────

/** One web buildTimeline() event: start/resume at startedAt, pause/conclude at endedAt. */
internal data class TimelineEvent(
    val kind: String, // "start" | "resume" | "pause" | "conclude"
    val time: Long,
    val blockId: String,
    val sessionRowId: Long,
    val segStart: Long,
    val segEnd: Long?,
    val note: String,
)

internal fun buildTimelineEvents(
    blocks: List<FocusBlockEntity>,
    sessions: List<FocusSessionEntity>,
    dayKey: String,
): List<TimelineEvent> {
    val events = mutableListOf<TimelineEvent>()
    val byBlock = sessions.groupBy { it.blockId }
    for (b in blocks) {
        val segs = byBlock[b.id].orEmpty().sortedBy { it.position }
        segs.forEachIndexed { i, seg ->
            events += TimelineEvent(
                kind = if (i == 0) "start" else "resume",
                time = seg.startedAt, blockId = b.id, sessionRowId = seg.rowId,
                segStart = seg.startedAt, segEnd = seg.endedAt, note = seg.note,
            )
            val end = seg.endedAt
            if (end != null) {
                val isLast = i == segs.size - 1
                events += TimelineEvent(
                    kind = if (isLast && b.status == "done") "conclude" else "pause",
                    time = end, blockId = b.id, sessionRowId = seg.rowId,
                    segStart = seg.startedAt, segEnd = end, note = seg.note,
                )
            }
        }
    }
    return events.filter { DateUtils.dayKeyOf(it.time) == dayKey }.sortedBy { it.time }
}

@Composable
internal fun TimelineList(
    events: List<TimelineEvent>,
    blockById: Map<String, FocusBlockEntity>,
    onFilterBlock: ((String, String) -> Unit)?,
    onEditBlock: ((String) -> Unit)?,
    onEditNote: ((Long, String) -> Unit)?,
) {
    Column {
        events.forEachIndexed { i, e ->
            val block = blockById[e.blockId] ?: return@forEachIndexed
            TimelineRow(
                event = e,
                block = block,
                isLast = i == events.size - 1,
                connectUp = i > 0 && events[i - 1].blockId == e.blockId,
                connectDown = i < events.size - 1 && events[i + 1].blockId == e.blockId,
                onFilter = onFilterBlock?.let { f -> { f(e.blockId, block.title) } },
                onEdit = onEditBlock?.let { f -> { f(e.blockId) } },
                onEditNote = onEditNote,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    event: TimelineEvent,
    block: FocusBlockEntity,
    isLast: Boolean,
    connectUp: Boolean,
    connectDown: Boolean,
    onFilter: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onEditNote: ((Long, String) -> Unit)?,
) {
    val colors = LocalPautaColors.current
    val kind = event.kind
    val isStartish = kind == "start" || kind == "resume"
    val segDuration = (event.segEnd ?: 0L) - event.segStart

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // time + marker rail
        Column(
            Modifier.width(52.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = DateUtils.fmtClock(event.time),
                color = colors.ink2,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            TimelineMarker(kind)
            if (connectDown) {
                Canvas(Modifier.width(2.dp).weight(1f)) {
                    drawLine(
                        color = colors.accent.copy(alpha = 0.5f),
                        start = Offset(size.width / 2, 2.dp.toPx()),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // body
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = block.title,
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp,
                        modifier = onFilter?.let { Modifier.clickableNoRipple(it) } ?: Modifier,
                    )
                    KindTag(kind)
                    block.project?.let { pr ->
                        Text(
                            text = "#$pr",
                            color = colors.ink3,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                            letterSpacing = 0.36.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (onEdit != null) {
                    Box(Modifier.size(26.dp).clickableNoRipple(onEdit), contentAlignment = Alignment.Center) {
                        KebabDots(colors.ink3)
                    }
                }
            }
            if (isStartish && event.segEnd != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "└─ " + FocusMath.fmtDuration(segDuration),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                )
            }
            if (isStartish && event.segEnd == null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colors.accent))
                    Text(
                        text = tr("em curso"),
                        color = colors.accent,
                        fontFamily = MonoFamily,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
            if (kind == "pause") {
                Spacer(Modifier.height(6.dp))
                if (onEditNote != null) {
                    InlineNoteField(
                        initial = event.note,
                        placeholder = tr("adicionar nota…"),
                        onCommit = { onEditNote(event.sessionRowId, it) },
                    )
                } else if (event.note.isNotBlank()) {
                    Text(
                        text = "“${event.note}”",
                        color = colors.ink2,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            if (kind == "conclude" && block.reflection.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "— ${block.reflection}",
                    color = colors.ink2,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun TimelineMarker(kind: String) {
    val colors = LocalPautaColors.current
    val size = 14.dp
    when (kind) {
        "pause" -> Box(
            Modifier.size(size).clip(CircleShape).background(colors.paper)
                .border(1.6.dp, colors.ink3, CircleShape),
            contentAlignment = Alignment.Center,
        ) { PauseBars(colors.ink3, 5.dp) }
        "conclude" -> Box(
            Modifier.size(size).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(7.dp)) {
                val w = this.size.width
                val h = this.size.height
                drawPath(
                    Path().apply {
                        moveTo(w * 0.1f, h * 0.55f)
                        lineTo(w * 0.4f, h * 0.85f)
                        lineTo(w * 0.9f, h * 0.2f)
                    },
                    colors.onDark,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
            }
        }
        "resume" -> Box(
            Modifier.size(size).clip(CircleShape).background(colors.paper)
                .border(1.6.dp, colors.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(8.dp)) {
                // A small circular arrow, like the web's resume marker.
                drawArc(
                    color = colors.ink,
                    startAngle = -60f, sweepAngle = 290f, useCenter = false,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }
        else -> Box( // start
            Modifier.size(size).clip(CircleShape).background(colors.ink),
            contentAlignment = Alignment.Center,
        ) { PlayTriangle(colors.paper, 6.dp) }
    }
}

@Composable
private fun KindTag(kind: String) {
    val colors = LocalPautaColors.current
    val (label, color) = when (kind) {
        "resume" -> tr("retomado") to colors.accent
        "pause" -> tr("pausa") to colors.ink3
        "conclude" -> tr("concluído") to colors.accent
        else -> tr("iniciado") to colors.ink3
    }
    Text(
        text = label.uppercase(),
        color = color,
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.08.sp, // 0.12em of 9sp
    )
}

// ─── Small shared atoms ────────────────────────────────────

/** The web's StarterChips item: "+ label" on a soft accent pill. */
@Composable
internal fun StarterChip(label: String, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accent.copy(alpha = 0.07f))
            .border(1.dp, colors.accent.copy(alpha = 0.27f), RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", color = colors.accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.ink2, fontSize = 13.5.sp, lineHeight = 16.sp)
    }
}

/** The in-app "meta cumprida" prompt mirroring the native heads-up. */
@Composable
private fun GoalReachedPrompt(
    targetMin: Int,
    onContinue: () -> Unit,
    onConclude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPautaColors.current
    Column(
        modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceDark)
            .border(1.dp, colors.accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = tr("meta cumprida").uppercase(),
            color = colors.accent,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 1.8.sp, // 0.2em of 9sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (targetMin > 0) trf("Cumpriu os {n} min planeados.", "n" to targetMin) else tr("Cumpriu a meta planeada."),
            color = colors.onDark,
            fontFamily = SerifFamily,
            fontSize = 19.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = tr("Continuar"),
                color = colors.onDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, Color(0x4DF5F1EA), RoundedCornerShape(999.dp))
                    .clickableNoRipple(onContinue)
                    .padding(vertical = 11.dp),
            )
            Text(
                text = tr("Concluir"),
                color = colors.onDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent)
                    .clickableNoRipple(onConclude)
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/** The web's Pauta filter: {kind: "project" | "intention" | "block", id, label}. */
internal data class PautaFilter(val kind: String, val id: String, val label: String)

/** sub-components.jsx FilterChips: project chips ("# name"), today's intention
 *  chips, unlinked block-title chips (dashed), and "limpar" when active. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PautaFilterChips(
    intentions: List<IntentionEntity>,
    todayBlocks: List<FocusBlockEntity>,
    projects: List<String>,
    filter: PautaFilter?,
    onFilter: (PautaFilter?) -> Unit,
) {
    val colors = LocalPautaColors.current
    // Unique titles among today's blocks NOT linked to intentions.
    val unlinked = remember(todayBlocks) {
        val seen = LinkedHashMap<String, String>()
        for (b in todayBlocks) if (b.linkedToId == null && b.title !in seen) seen[b.title] = b.id
        seen.entries.map { it.key to it.value }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        projects.forEach { pr ->
            val active = filter?.kind == "project" && filter.id == pr
            FilterChip("# $pr", active = active, muted = false) {
                onFilter(if (active) null else PautaFilter("project", pr, pr))
            }
        }
        intentions.forEach { i ->
            val active = filter?.kind == "intention" && filter.id == i.id
            FilterChip(i.text, active = active, muted = false) {
                onFilter(if (active) null else PautaFilter("intention", i.id, i.text))
            }
        }
        unlinked.forEach { (title, id) ->
            val active = filter?.kind == "block" && filter.label == title
            FilterChip(title, active = active, muted = true) {
                onFilter(if (active) null else PautaFilter("block", id, title))
            }
        }
        if (filter != null) {
            Text(
                text = "× " + tr("limpar"),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp, // 0.06em of 10sp
                modifier = Modifier
                    .clickableNoRipple { onFilter(null) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, muted: Boolean, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    val bg = when {
        active -> colors.accent
        muted -> Color.Transparent
        else -> colors.paper2
    }
    Text(
        text = label,
        color = if (active) colors.onDark else colors.ink2,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(
                if (muted && !active) {
                    Modifier.dashedBorder(colors.rule)
                } else {
                    Modifier.border(1.dp, if (active) colors.accent else colors.rule, RoundedCornerShape(999.dp))
                },
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

/** A 1dp dashed pill outline (the web's `border: 1px dashed var(--rule)`). */
internal fun Modifier.dashedBorder(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            ),
        )
    },
)
