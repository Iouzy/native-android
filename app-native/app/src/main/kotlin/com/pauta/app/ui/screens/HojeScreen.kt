package com.pauta.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import com.pauta.app.domain.Memory
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PeriodLabel
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.computeTodayTides
import com.pauta.app.ui.entranceStagger
import com.pauta.app.ui.rememberEntrancePlay
import com.pauta.app.ui.tick
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaMotion
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.rememberMotionEnabled
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate

// P6 · one card rhythm for the tab: every paper card on Hoje keeps PautaCard's
// radius and one of these two gutters, so the pulse, carry, memórias and
// reflection cards line their text up on the same inner edge (they used to
// drift 12/14/22 horizontal and 12/20/24 vertical). // PT: um só ritmo de
// cartões — mesmos raios e margens internas em toda a tab.
private val HojeCardPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
private val HojeCardPaddingTight = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/**
 * The Hoje (Today) tab — date header with ±7-day navigation (like Marés) and
 * three evenly-spaced chips. Today shows the full interactive view; past days
 * show a read-only snapshot of intentions and reflection.
 * // PT: tab Hoje com navegação de dias e cabeçalho arrumado.
 *
 * @param onOpenHistory opens the past-days history — a navigation destination
 *   (A8), so it peels back predictively rather than swapping in place.
 * @param bookMode when true the tab becomes the library shelf (K5); the planner
 *   view below is untouched and restored the moment book mode is off.
 */
@Composable
fun HojeScreen(
    onOpenHistory: () -> Unit,
    bookMode: Boolean = false,
    // R3: the shelf's way into the reader — a navigation destination, so it has
    // to be handed down from the shell. // PT: a entrada da estante no leitor.
    onOpenReader: (String) -> Unit = {},
) {
    // Book mode is a lens, not a fork: swap in the shelf and leave the planner
    // view entirely intact. // PT: modo livro troca a estante; o planner fica igual.
    if (bookMode) {
        BookShelfScreen(onOpenReader = onOpenReader)
        return
    }
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
    val animate = rememberMotionEnabled()
    // P10: the haptic map's intention-check tick, and the one-shot list entrance.
    // // PT: o toque háptico ao marcar e a entrada escalonada da lista.
    val haptic = LocalHapticFeedback.current
    val entrance = rememberEntrancePlay("hoje-intentions", animate)
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

    // E2 · Memórias: past reflections that fell on today's month-day in earlier
    // years (newest first) — a pure scan over the day rows. Shown only on the
    // today view, and dismissible for the day (the stamp lives in prefs, so it
    // survives a restart and clears at midnight). // PT: memórias — reflexões de
    // anos anteriores no mesmo dia, dispensáveis só por hoje.
    val memories = remember(allDays, today) { HojeLogic.memories(allDays, today) }
    val memoriaDismissed = prefs.memoriaDismissedDay == today

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
        animationSpec = if (animate) PautaMotion.tween(450) else snap(),
        label = "pulse-intentions",
    )
    val animTideDone by animateIntAsState(
        targetValue = tideDone,
        animationSpec = if (animate) PautaMotion.tween(450) else snap(),
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
        // Header — the date (with the 7-day back-nav) top-left and the actions as
        // quiet outlined chips top-right, then the big serif question across the
        // full width below. // PT: data e ações em cima, a pergunta a toda a
        // largura por baixo.
        item(key = "header") {
            Spacer(Modifier.height(22.dp))
            // U3: the four actions used to stack in a right-hand Column, so four
            // different chip widths made a staircase — and the column stole width
            // from the headline. They now flow, right-aligned, beside the date
            // only; the question below gets the full measure. // PT: as ações
            // passam a fluir alinhadas à direita, em vez de uma escada.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                val selDate = LocalDate.parse(selectedDayKey)
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(14.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // The eyebrow uppercases anyway, so the source strings are all
                    // lowercase now (Rotinas was the odd one). // PT: minúsculas em
                    // todas — o eyebrow trata das maiúsculas.
                    HeaderChip(tr("dias anteriores") + " ↗") { onOpenHistory() }
                    HeaderChip(tr("a semana") + " ↗") { showWeek = true }
                    HeaderChip(tr("rotinas") + " ↗") { showRoutines = true }
                    HeaderChip(tr("revisão") + " ↗") { showInsights = true }
                }
            }
            if (isToday) {
                Spacer(Modifier.height(8.dp))
                // The headline question, with "hoje" in accent italic. P5: on the
                // shared ScreenTitle role (and the same 8dp drop as Pauta) so the
                // headline no longer jumps size/baseline between tabs.
                // // PT: a pergunta do dia, no papel partilhado de título.
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
                    style = PautaType.ScreenTitle,
                )
            }
        }

        if (isToday) {
            // ── Today's interactive view ──────────────────────────────
            // Day pulse + carry banner + add form sit above the intention list as
            // one quiet block. // PT: pulso, faixa de arrasto e formulário.
            item(key = "today-top") {
                Spacer(Modifier.height(16.dp))
                // P6: the pulse used to float as a bare mono line; it is now a card
                // of its own — the same surface as memórias/reflexão — carrying the
                // three counts plus a hairline of the day's intention progress.
                // // PT: o pulso do dia passa a cartão, com a linha de progresso.
                if (pulseParts.isNotEmpty()) {
                    DayPulseCard(
                        parts = pulseParts,
                        progress = if (total > 0) done.toFloat() / total else null,
                        animate = animate,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                carry?.let { source ->
                    CarryBanner(source = source, onCarry = { vm.carryOver() })
                    Spacer(Modifier.height(16.dp))
                }
                AddIntentionForm(
                    onAdd = { text, priority, target, w -> vm.addIntention(text, priority, target, w) },
                )
                Spacer(Modifier.height(8.dp))
            }

            // Intentions, grouped by time-of-day; rows keyed by id so A3 can
            // animate add/remove. // PT: intenções por período, com chaves estáveis.
            // P10: the entrance stagger counts across the groups, so the list reads
            // as one sequence rather than restarting under each time-of-day header.
            // // PT: o escalonamento conta ao longo dos grupos — a lista entra como
            // uma sequência só.
            var entranceIndex = 0
            groups.forEach { (w, groupItems) ->
                if (showHeaders) {
                    item(key = "when-$w") {
                        // P6: the time-of-day headers were the odd one out (sans 12sp
                        // medium) next to "Marés de hoje"; they now share the one
                        // eyebrow. // PT: os períodos usam o eyebrow partilhado.
                        Spacer(Modifier.height(14.dp))
                        SectionEyebrow(whenLabel(w))
                        Spacer(Modifier.height(2.dp))
                    }
                }
                val groupStart = entranceIndex
                entranceIndex += groupItems.size
                itemsIndexed(groupItems, key = { _, row -> "intent-${row.id}" }) { i, item ->
                    IntentionRow(
                        item = item,
                        animate = animate,
                        // A3: add/remove/reorder slides into place (LazyItemScope).
                        modifier = (if (animate) Modifier.animateItem() else Modifier)
                            .entranceStagger(groupStart + i, entrance),
                        onToggle = {
                            vm.toggleIntention(item.id)
                            // The check is the tab's one decisive gesture — it gets
                            // the tick. // PT: marcar é o gesto do separador.
                            haptic.tick(prefs)
                        },
                        onDelete = { vm.removeIntention(item.id) },
                        onCyclePriority = { vm.setIntentionPriority(item.id, nextPriority(item.priority)) },
                    )
                }
            }
            if (total == 0) {
                item(key = "no-intentions") {
                    Spacer(Modifier.height(16.dp))
                    // P10: the shared empty state — same serif-italic line and Pip
                    // pose as Pauta's and Marés'. // PT: o estado vazio partilhado.
                    EmptyState(tr("Ainda sem intenções para hoje."), pip = true)
                }
            }

            // Marés de hoje — placed between intentions and the night reflection so
            // Hoje reads as one nested day: now → today's rhythm → the night.
            // // PT: a fatia de hoje das Marés, entre as intenções e a reflexão.
            if (todayTides.isNotEmpty()) {
                item(key = "tides-header") {
                    Spacer(Modifier.height(36.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionEyebrow(tr("Marés de hoje"), Modifier.weight(1f))
                        if (tideDenom > 0) {
                            Text(
                                text = "$animTideDone/$tideDenom",
                                color = colors.ink4,
                                style = PautaType.MetaSmall,
                                letterSpacing = 0.6.sp,
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

            // E2: a quiet "on this day" memory, sitting just before tonight's
            // reflection — a past night's words beside the one about to be written.
            // Only when a prior-year reflection exists and not dismissed for today.
            // // PT: memória discreta, mesmo antes da reflexão da noite.
            if (memories.isNotEmpty() && !memoriaDismissed) {
                item(key = "memorias") {
                    Spacer(Modifier.height(40.dp))
                    MemoriasCard(memories = memories, onDismiss = { vm.dismissMemoria() })
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
                    animationSpec = if (animate) PautaMotion.tween() else snap(),
                    label = "reflection-saved",
                )
                PautaCard(Modifier.fillMaxWidth(), padding = HojeCardPadding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionEyebrow(tr("Reflexão da noite"), Modifier.weight(1f))
                        Text(
                            text = tr("guardado") + " ✓",
                            color = colors.ink4,
                            style = PautaType.MetaSmall,
                            letterSpacing = 1.0.sp,
                            modifier = Modifier.alpha(savedAlpha),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "“" + tr("O que valeu hoje?") + "”",
                        color = colors.ink2,
                        style = PautaType.CardTitle,
                        fontStyle = FontStyle.Italic,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (pulseParts.isNotEmpty()) {
                        // The same pulse line as the card at the top of the tab (that
                        // one has scrolled away by now) — one composable, so the two
                        // can't drift again. // PT: a mesma linha de pulso do topo.
                        DayPulseLine(pulseParts)
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
                    style = PautaType.MetaSmall,
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
                        style = PautaType.Label,
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
                        PautaCard(Modifier.fillMaxWidth(), padding = HojeCardPadding) {
                            SectionEyebrow(tr("Reflexão da noite"))
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "“" + pastReflection + "”",
                                color = colors.ink2,
                                style = PautaType.Body,
                                fontStyle = FontStyle.Italic,
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
 *  "A SEMANA ↗", "REVISÃO ↗") — the same treatment as the Pauta tab. P6 puts
 *  them on the MetaSmall role (they were the last 9sp mono left on the tab). */
@Composable
private fun HeaderChip(label: String, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Text(
        text = label.uppercase(),
        color = colors.ink3,
        style = PautaType.MetaSmall,
        letterSpacing = 1.4.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(PautaRadius.Chip))
            .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip))
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
                                Text(text = clock, color = colors.ink3, style = PautaType.Meta)
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
                    style = PautaType.MetaSmall,
                    letterSpacing = 0.4.sp,
                )
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

/** E2 · Memórias — a quiet paper-2 card echoing the reflection card: a small mono
 *  "HÁ UM ANO" / "HÁ N ANOS" eyebrow over each past reflection in serif italic,
 *  newest year first. The corner × dismisses the whole card for the day. // PT:
 *  cartão discreto de memórias — reflexões de anos anteriores, dispensável por hoje. */
@Composable
private fun MemoriasCard(memories: List<Memory>, onDismiss: () -> Unit) {
    val colors = LocalPautaColors.current
    PautaCard(Modifier.fillMaxWidth(), padding = HojeCardPadding) {
        memories.forEachIndexed { i, mem ->
            if (i > 0) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                Spacer(Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionEyebrow(
                    label = if (mem.yearsAgo == 1) tr("há um ano") else trf("há {n} anos", "n" to mem.yearsAgo),
                    modifier = Modifier.weight(1f),
                )
                // One × on the first row dismisses the whole card for the day.
                if (i == 0) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = tr("dispensar"),
                        tint = colors.ink4,
                        modifier = Modifier
                            .size(18.dp)
                            .clickableNoRipple(onDismiss),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "“" + mem.reflection + "”",
                color = colors.ink2,
                style = PautaType.Body,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

private fun nextPriority(current: Int?): Int? = when (current) {
    null -> 1; 1 -> 2; 2 -> 3; else -> null
}

/** P6 · The day pulse as a card — the one surface tying the three tabs together
 *  (intentions · focus · tides), with a hairline of the day's intention progress
 *  underneath when there are intentions to progress through. The bar rides the
 *  same 450ms as the counting numerals, so they settle together.
 *  // PT: o pulso do dia num cartão, com a barra a acompanhar os números. */
@Composable
private fun DayPulseCard(parts: List<String>, progress: Float?, animate: Boolean) {
    PautaCard(Modifier.fillMaxWidth(), padding = HojeCardPaddingTight) {
        DayPulseLine(parts)
        if (progress != null) {
            val shown by animateFloatAsState(
                targetValue = progress,
                animationSpec = if (animate) PautaMotion.tween(450) else snap(),
                label = "pulse-progress",
            )
            Spacer(Modifier.height(12.dp))
            ProgressBar(shown)
        }
    }
}

/** The pulse itself — shown at the top of the tab and again inside the night
 *  reflection card. // PT: a linha do pulso, partilhada pelos dois sítios. */
@Composable
private fun DayPulseLine(parts: List<String>) {
    Text(
        text = parts.joinToString("   ·   "),
        color = LocalPautaColors.current.ink3,
        style = PautaType.Meta,
        letterSpacing = 0.22.sp, // 0.02em of 11sp
    )
}

/** One-tap "bring forward" of the most recent past day's unfinished intentions. */
@Composable
private fun CarryBanner(source: CarrySource, onCarry: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PautaRadius.Card))
            .background(colors.accentBg)
            .clickableNoRipple(onCarry)
            // P6: the accent banner keeps the cards' inner edge (was 14dp) so the
            // block reads as one column. // PT: mesma margem interna dos cartões.
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = trf("Trazer {n} de {d}", "n" to source.items.size, "d" to shortDate(source.dayKey)),
            color = colors.accent,
            style = PautaType.Label,
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
    // A3: the strike is painted by hand so it can sweep left→right (Base ms) as
    // the intention is ticked; reduced motion uses the instant built-in decoration.
    // // PT: o risco é desenhado à mão para correr da esquerda para a direita.
    val strike by animateFloatAsState(
        targetValue = if (item.done) 1f else 0f,
        animationSpec = PautaMotion.tween(),
        label = "intention-strike",
    )
    // P6: the settle — ticking an intention flashes the text accent (Fast) and
    // then eases back down to the faded ink of a done row (Base), so the check
    // lands instead of snapping. Only on the transition: rows that arrive already
    // done (a tab hop, a scroll back) don't flash. // PT: ao marcar, o texto
    // acende em acento e assenta na tinta apagada; só na transição.
    val flash = remember { Animatable(0f) }
    var wasDone by remember { mutableStateOf(item.done) }
    LaunchedEffect(item.done) {
        val justChecked = item.done && !wasDone
        wasDone = item.done
        if (!justChecked || !animate) {
            flash.snapTo(0f)
            return@LaunchedEffect
        }
        flash.animateTo(1f, PautaMotion.tween(PautaMotion.Fast))
        flash.animateTo(0f, PautaMotion.tween())
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DoneCircle(done = item.done, animate = animate, onToggle = onToggle)
        Spacer(Modifier.width(12.dp))
        val settled = if (animate) lerp(colors.ink, colors.ink4, strike)
        else if (item.done) colors.ink4 else colors.ink
        Text(
            text = item.text,
            color = if (flash.value > 0f) lerp(settled, colors.accent, flash.value) else settled,
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

/**
 * U3 · The intention composer. At rest it is still one underlined line reading
 * "Nova intenção…". Typing used to unfold *three* stacked rows of unlabeled grey
 * pills; it now reveals a single wrapped row where each group carries a small
 * mono label — the pills were always fine, it was never saying what `1 2 3` meant
 * that made this the least finished surface in the planner. Both fields are the
 * app's own primitives now, so the composer matches every sheet.
 * // PT: o compositor — uma linha em repouso, e uma só linha de pílulas
 * etiquetadas (prioridade · quando · min) ao começar a escrever.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddIntentionForm(onAdd: (String, Int?, Int?, String?) -> Unit) {
    val colors = LocalPautaColors.current
    val accent = colors.accent
    val animate = rememberMotionEnabled()
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
        UnderlineField(
            value = text,
            onChange = { text = it },
            placeholder = tr("Nova intenção…"),
            // The composer writes 16sp intentions; the sheets' 18sp headline would
            // out-shout the list it feeds. // PT: do tamanho das intenções da lista.
            fontSize = 16.sp,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        // The details appear once you start typing, so the resting state stays a
        // single quiet line — and they fold away again on commit or on clearing
        // the field, rather than vanishing. // PT: os detalhes surgem ao escrever e
        // recolhem ao concluir; instantâneo com movimento reduzido.
        AnimatedVisibility(
            visible = expanded,
            enter = if (animate) {
                fadeIn(PautaMotion.tween()) + expandVertically(PautaMotion.tween())
            } else EnterTransition.None,
            exit = if (animate) {
                fadeOut(PautaMotion.tween(PautaMotion.Fast)) + shrinkVertically(PautaMotion.tween())
            } else ExitTransition.None,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    // One flow, not three rows: labels and pills are all direct
                    // children so a big textScale wraps between them instead of
                    // clipping a group that can't fit. // PT: um só fluxo — com
                    // texto grande quebra de linha em vez de cortar.
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Everything on the line rides its vertical centre — the
                        // minutes field is a few dp taller than a pill, and top
                        // alignment would show it. // PT: tudo centrado na linha.
                        val mid = Modifier.align(Alignment.CenterVertically)
                        ComposerLabel(tr("prioridade"), mid)
                        Pill("1", priority == 1, accent, mid) { priority = if (priority == 1) null else 1 }
                        Pill("2", priority == 2, accent, mid) { priority = if (priority == 2) null else 2 }
                        Pill("3", priority == 3, accent, mid) { priority = if (priority == 3) null else 3 }
                        ComposerLabel(tr("quando"), mid)
                        Pill(tr("manhã"), whenSel == "manha", accent, mid) { whenSel = if (whenSel == "manha") null else "manha" }
                        Pill(tr("tarde"), whenSel == "tarde", accent, mid) { whenSel = if (whenSel == "tarde") null else "tarde" }
                        Pill(tr("noite"), whenSel == "noite", accent, mid) { whenSel = if (whenSel == "noite") null else "noite" }
                        ComposerLabel(tr("min"), mid)
                        Box(Modifier.width(62.dp).align(Alignment.CenterVertically)) {
                            BoxedField(
                                value = target,
                                onChange = { target = it.filter { c -> c.isDigit() }.take(3) },
                                placeholder = "40",
                                singleLine = true,
                                fontFamily = MonoFamily,
                                fontSize = 13.sp,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                keyboardActions = KeyboardActions(onDone = { commit() }),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // The commit action for the whole form — a real button now, not
                    // a bare accent word. // PT: o botão que fecha o formulário.
                    PautaButton(tr("Adicionar"), compact = true) { commit() }
                }
            }
        }
    }
}

/** The composer's group labels — the fix that makes `1 2 3` mean something. On
 *  the shared eyebrow (mono, uppercase, 10sp) rather than a fourth bespoke mono
 *  size, tinted ink4 so it stays quieter than the pills it names. // PT: as
 *  etiquetas dos grupos de pílulas, no eyebrow partilhado. */
@Composable
private fun ComposerLabel(label: String, modifier: Modifier = Modifier) {
    SectionEyebrow(
        label = label,
        modifier = modifier,
        color = LocalPautaColors.current.ink4,
    )
}

/** A small pill toggle used for priority / time-of-day selection. */
@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(PautaRadius.Chip))
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
                style = PautaType.Body,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        textStyle = PautaType.Body.copy(color = colors.ink),
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
