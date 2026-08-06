package com.pauta.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.StreakResult
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaFloatStrip
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PeriodLabel
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.CellState
import com.pauta.app.ui.cellStateFor
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.rememberNotificationAsk
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
import java.time.YearMonth

/**
 * The Marés (tides) tab, to the web grid's spec (tab-mares.jsx): serif month
 * header with the "Maré actual/passada" eyebrow and the overall % block, the
 * how-it-works hint, and one row per habit — name + recurrence/count chips,
 * month % with the maturity progress or the tier badge, the 22dp day strip
 * with all nine cell states, and the best-streak line. Tap marks done /
 * increments; long-press an empty day marks a respiro; tapping the name opens the
 * detail sheet (where edit / archive / remove live — A7 dropped the
 * undiscoverable long-press-to-delete). // PT: tab Marés segundo a grelha da web.
 *
 * @param bookMode when true (R7) the tab is [BookHabitsScreen] — the reading
 *   rhythm, with these tides embedded at its foot as the user's ordinary habits.
 *   Off = the planner Marés, untouched. The branch is an early return, like
 *   Hoje→Estante and Pauta→Sessão. // PT: no modo livro, a tab é o ritmo de
 *   leitura, com estas marés lá dentro.
 */
@Composable
fun MaresScreen(bookMode: Boolean = false) {
    // R7: the third tab finally transforms like the other two. K7's injection of
    // a goal card at the top of this list is gone; the reading screen owns the
    // list now and hands the tides back as its own trailing content.
    // // PT: a terceira tab também se transforma — o ecrã de leitura passa a dono
    // da lista e as marés entram lá dentro.
    if (bookMode) {
        BookHabitsScreen()
        return
    }
    MaresContent()
}

/**
 * The Marés list itself. [leading] is emitted between the top spacer and the
 * month navigation — the seam book mode's reading rhythm hangs off, and nothing
 * at all with the lens off, which is what keeps the planner's tab byte-identical
 * to before R7. // PT: a lista das marés; [leading] é a costura do modo livro.
 */
@Composable
internal fun MaresContent(leading: (LazyListScope.() -> Unit)? = null) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val logs by vm.habitLogs.collectAsStateWithLifecycle()
    val respiros by vm.habitRespiros.collectAsStateWithLifecycle()
    val counts by vm.habitCounts.collectAsStateWithLifecycle()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val askNotifications = rememberNotificationAsk(vm, prefs.notifAskedAt)
    // A3: cell fills, respiro hatching and row add/remove all snap when reduced.
    // // PT: animações das células respeitam "movimento reduzido".
    val animate = rememberMotionEnabled()
    // P10: the day-fill tick and the tides' one-shot list entrance.
    // // PT: o toque háptico ao preencher um dia e a entrada da lista.
    val haptic = LocalHapticFeedback.current
    val entrance = rememberEntrancePlay("mares-tides", animate)

    val nowYm = remember(today) { YearMonth.parse(today.substring(0, 7)) }
    var year by remember { mutableIntStateOf(nowYm.year) }
    var month by remember { mutableIntStateOf(nowYm.monthValue) }
    var showAdd by remember { mutableStateOf(false) }
    var showTrend by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var detailTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var editTarget by remember { mutableStateOf<HabitEntity?>(null) }

    val logsByHabit = remember(logs) { logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    val respByHabit = remember(respiros) { respiros.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() } }
    val countsByHabit = remember(counts) {
        counts.groupBy { it.habitId }.mapValues { e -> e.value.associate { it.dayKey to it.count } }
    }
    // P8: the derived models are built once per data change instead of three
    // times per habit per composition pass (the visibility filter, the overall
    // %, and every row each called `modelOf` on their own). Because HabitModel
    // is a data class, an unrelated edit rebuilds the map but leaves the
    // untouched entries *equal* — so the `remember`s keyed on them downstream
    // survive. // PT: os modelos são construídos uma vez por mudança de dados,
    // não a cada recomposição; os iguais mantêm as memoizações a jusante.
    val modelsById = remember(habits, logsByHabit, respByHabit) {
        habits.associate { h ->
            h.id to habitModelOf(h, logsByHabit[h.id].orEmpty(), respByHabit[h.id].orEmpty())
        }
    }
    // Fallback for the frame where a habit exists but the map hasn't caught up.
    // // PT: recurso para o instante em que o mapa ainda não acompanhou.
    fun modelOf(h: HabitEntity): HabitModel = modelsById[h.id]
        ?: habitModelOf(h, logsByHabit[h.id].orEmpty(), respByHabit[h.id].orEmpty())

    val isCurrentMonth = year == nowYm.year && month == nowYm.monthValue
    // N3 · one month, one strip.
    //
    // Every habit row used to own its own `horizontalScroll` state, so scrolling
    // "Beber água" to day 24 left "Meditar" showing day 1 and the month stopped
    // being readable as a grid — which is the whole point of the tab. One state,
    // hoisted here, means every row moves together and a habit added later starts
    // aligned. // PT: uma só posição de scroll para o mês inteiro — comparar as
    // marés entre si é a funcionalidade.
    val monthStrip = rememberScrollState()
    val stripDensity = LocalDensity.current
    // Open on today, not on day 1: the useful end of the month is the one you are
    // in. // PT: abre no dia de hoje.
    LaunchedEffect(year, month, isCurrentMonth) {
        if (!isCurrentMonth) {
            monthStrip.scrollTo(0)
            return@LaunchedEffect
        }
        val todayD = today.substring(8).toInt()
        // 31dp pitch = 28dp cell + 3dp gap. // PT: passo = célula + intervalo.
        val target = with(stripDensity) {
            ((todayD - 1) * 31).dp.toPx().toInt() - 150.dp.toPx().toInt()
        }
        monthStrip.scrollTo(target.coerceAtLeast(0))
    }
    val monthEnd = "%04d-%02d-%02d".format(year, month, DateUtils.daysInMonth(year, month))
    // Only tides that already existed in the viewed month; the rest are counted
    // in the footer note, like the web. // PT: só marés que já existiam no mês.
    // `habits` stays in the keys: a rename leaves `modelsById` equal (the model
    // carries no name) but must still reach the rows. // PT: renomear não muda o
    // modelo, por isso `habits` continua a ser chave.
    val visibleHabits = remember(habits, modelsById, monthEnd) {
        habits.filter { HabitCalculator.createdKey(modelOf(it)) <= monthEnd }
    }
    val models = remember(visibleHabits, modelsById) { visibleHabits.map { modelOf(it) } }
    // Three history walks per habit (range + stats + %) — worth not repeating on
    // every recomposition. // PT: três varrimentos por maré; não se repetem.
    val overall = remember(models, year, month, today) {
        HabitCalculator.overallPctInMonth(models, year, month, today)
    }
    val ymIndex = year * 12 + (month - 1)

    Box(Modifier.fillMaxSize()) {
        // A single LazyColumn; horizontal content padding replaces the per-section
        // padding the old Column applied. The per-habit month strip stays a nested
        // horizontalScroll Row inside its item. // PT: LazyColumn única; tiras
        // mensais continuam em scroll horizontal dentro do item.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            item(key = "top") { Spacer(Modifier.height(8.dp)) }

            leading?.invoke(this)

            // Month navigation (stands in for the web's MonthStrip).
            item(key = "month-nav") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft, contentDescription = tr("mês anterior"), tint = colors.ink3,
                        modifier = Modifier.size(26.dp).clickableNoRipple {
                            val ym = YearMonth.of(year, month).minusMonths(1); year = ym.year; month = ym.monthValue
                        },
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .clickableNoRipple { year = nowYm.year; month = nowYm.monthValue },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Accent "JUN '26" period label — the web MonthStrip styling,
                        // shared with Hoje/Pauta. // PT: mês em destaque, como nas outras tabs.
                        MonthSlide(ymIndex, animate, Modifier.fillMaxWidth()) { y, m ->
                            PeriodLabel(
                                month = I18n.fmtMonthShort(m),
                                suffix = "'%02d".format(y % 100),
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = tr("mês seguinte"), tint = colors.ink3,
                        modifier = Modifier.size(26.dp).clickableNoRipple {
                            val ym = YearMonth.of(year, month).plusMonths(1); year = ym.year; month = ym.monthValue
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    GridLegend()
                }
            }

            // N3 · the ruler. A `DayCell` is 28dp and carries no date, so after one
            // scroll nothing on screen said which days you were looking at — and
            // *which days* is the tab's whole subject. The number does not fit
            // inside a cell at any text scale, so it goes **under** the strip as a
            // sparse ruler: 1, 8, 15, 22 and the last day, in the mono meta
            // treatment. That reads at a glance, survives `textScale`, and costs
            // one row per tab rather than one per habit.
            // // PT: uma régua esparsa por baixo das tiras — os números não cabem
            // nas células, e sem eles não se sabe que dias estão à vista.
            item(key = "day-ruler") {
                Spacer(Modifier.height(10.dp))
                MonthRuler(strip = monthStrip, days = DateUtils.daysInMonth(year, month))
            }

            // Header — eyebrow + serif month, with the overall % at the right.
            item(key = "header") {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    // P8: eyebrow + month travel together in the direction of the
                    // navigation. // PT: eyebrow e mês deslizam juntos.
                    MonthSlide(
                        ymIndex, animate, Modifier.weight(1f),
                        contentAlignment = Alignment.BottomStart,
                    ) { y, m ->
                        Column(Modifier.fillMaxWidth()) {
                            SectionEyebrow(
                                if (y == nowYm.year && m == nowYm.monthValue) tr("Maré actual")
                                else tr("Maré passada"),
                            )
                            Spacer(Modifier.height(4.dp))
                            // P5: shared ScreenTitle role — was 38sp, the odd one out of the
                            // three tabs. // PT: o mês no papel partilhado de título.
                            Text(
                                text = monthLongName(m),
                                color = colors.ink,
                                style = PautaType.ScreenTitle,
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.clickableNoRipple { showTrend = true },
                    ) {
                        // P8 (P5's leftover): the display numeral is a sized CardTitle
                        // rather than a loose serif, so the family travels with the role.
                        // // PT: o número herda o papel serif, sem família solta.
                        Text(
                            text = buildAnnotatedString {
                                if (overall == null) append("—") else {
                                    append(overall.toString())
                                    withStyle(SpanStyle(fontSize = 14.sp, color = colors.ink3)) { append("%") }
                                }
                            },
                            color = if (overall == null) colors.ink3 else colors.accent,
                            style = PautaType.CardTitle.copy(fontSize = 30.sp, lineHeight = 30.sp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("marés passadas").uppercase() + " ↗",
                            color = colors.ink3,
                            style = PautaType.MetaSmall,
                            letterSpacing = 1.35.sp,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            if (habits.isEmpty()) {
                // The web's empty state: an intro phrase, the explanation,
                // and the "Marés comuns" starter chips. // PT: estado vazio
                // com frase, explicação e marés comuns.
                item(key = "empty") {
                    // P10: the shared empty state. Marés is the one that needs both
                    // slots — the day's intro phrase leads, the explanation is the
                    // line — and it keeps the taller Pip the tab always had.
                    // // PT: o estado vazio partilhado, com frase de abertura por
                    // cima da explicação e o Pip maior desta tab.
                    EmptyState(
                        line = tr("Adicione comportamentos que quer praticar regularmente. Cada mês tem o seu grid."),
                        title = tr(introPhraseFor(today)),
                        pip = true,
                        pipHeight = 44.dp,
                    )
                    Spacer(Modifier.height(14.dp))
                    SectionEyebrow(tr("Marés comuns"), color = colors.ink4)
                    Spacer(Modifier.height(9.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Beber água", "Ler", "Meditar", "Exercício", "Dormir cedo").forEach { name ->
                            StarterChip(tr(name)) { vm.addHabit(name = tr(name)) }
                        }
                    }
                }
            } else {
                // Como funciona — persistent, subtle hint.
                item(key = "hint") {
                    PautaCard(
                        Modifier.fillMaxWidth(),
                        radius = PautaRadius.Chip,
                        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = colors.ink2)) { append(tr("toque")) }
                                append(" " + tr("marca feito") + " · ")
                                withStyle(SpanStyle(color = colors.ink2)) { append(tr("pressão longa")) }
                                append(" " + tr("num dia vazio marca respiro"))
                            },
                            color = colors.ink3,
                            style = PautaType.MetaSmall,
                            letterSpacing = 0.4.sp,
                            lineHeight = 15.sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tr("dias passados são editáveis — a honestidade é o melhor amigo da maré."),
                            color = colors.ink3,
                            fontFamily = SerifFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // One item per tide, keyed by id; a 22dp gap before every row but
                // the first reproduces the old spacedBy(22). // PT: um item por
                // maré, com chave estável.
                itemsIndexed(visibleHabits, key = { _, h -> "habit-${h.id}" }) { index, h ->
                    // A3: the whole item (its leading gap + row) slides on add/remove.
                    // P10: …and the list's first build staggers into place.
                    Column(
                        (if (animate) Modifier.animateItem() else Modifier)
                            .entranceStagger(index, entrance),
                    ) {
                        if (index > 0) Spacer(Modifier.height(22.dp))
                        MaresHabitRow(
                            habit = h,
                            model = modelOf(h),
                            countsForHabit = countsByHabit[h.id].orEmpty(),
                            year = year,
                            month = month,
                            today = today,
                            isCurrentMonth = isCurrentMonth,
                            strip = monthStrip,
                            animate = animate,
                            // P10 · the haptic map: filling a day (tap, count bump or
                            // respiro) is the tab's gesture, so each one ticks.
                            // // PT: preencher um dia dá um toque háptico.
                            onToggle = { dayKey -> vm.toggleHabitDay(h.id, dayKey); haptic.tick(prefs) },
                            onIncrement = { dayKey, current ->
                                vm.setHabitCount(h.id, dayKey, current + 1)
                                haptic.tick(prefs)
                            },
                            onRespiro = { dayKey -> vm.markRespiro(h.id, dayKey); haptic.tick(prefs) },
                            onUnmarkRespiro = { dayKey -> vm.unmarkRespiro(h.id, dayKey) },
                            onOpenDetail = { detailTarget = h },
                        )
                    }
                }

                if (habits.size > visibleHabits.size) {
                    item(key = "hidden-note") {
                        val hidden = habits.size - visibleHabits.size
                        Spacer(Modifier.height(18.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "$hidden " +
                                (if (hidden == 1) tr("maré ainda não existia") else tr("marés ainda não existiam")) +
                                " " + trf("em {month}.", "month" to monthLongName(month)),
                            color = colors.ink3,
                            style = PautaType.Body,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // The web's dashed full-width "adicionar maré" button.
            item(key = "add") {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PautaRadius.Card))
                        .dashedRectBorder(colors.rule, PautaRadius.Card)
                        .clickableNoRipple { showAdd = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", color = colors.ink3, fontSize = 16.sp, lineHeight = 16.sp)
                    Text(tr("adicionar maré"), color = colors.ink3, style = PautaType.Label)
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(PautaFloatStrip)) }
        }
    }

    detailTarget?.let { h ->
        HabitDetailSheet(
            habit = h,
            model = modelOf(h),
            countsForHabit = countsByHabit[h.id].orEmpty(),
            today = today,
            onEdit = { editTarget = h; detailTarget = null },
            onClose = { detailTarget = null },
        )
    }
    editTarget?.let { h ->
        EditHabitSheet(
            habit = h,
            onSave = { updated ->
                // N1: a tide with a clock is a tide that will try to notify. Ask at
                // the moment the reminder is set, not at launch — and only when the
                // habit actually gained one. // PT: pede a permissão quando a maré
                // ganha hora certa.
                if (updated.clock.isNotBlank() && h.clock.isBlank()) askNotifications()
                vm.updateHabit(updated)
                editTarget = null
            },
            onArchive = { vm.setHabitArchived(h.id, true); editTarget = null },
            onRemove = { removeTarget = h; editTarget = null },
            onClose = { editTarget = null },
        )
    }
    if (showTrend) {
        // Every tide, not just the visible ones — reused from the memoised map.
        // // PT: todas as marés, a partir do mapa memoizado.
        val trendModels = remember(habits, modelsById) { habits.map { modelOf(it) } }
        TrendSheet(
            habits = trendModels,
            today = today,
            onPickMonth = { y, m -> year = y; month = m },
            onClose = { showTrend = false },
        )
    }
    if (showAdd) {
        AddHabitSheet(
            onSubmit = { d ->
                // N1: same reason as the edit sheet above. // PT: idem.
                if (d.clock.isNotBlank()) askNotifications()
                vm.addHabit(
                    name = d.name, time = d.time, cadence = d.cadence, anchor = d.anchor,
                    weekdays = d.weekdays, target = d.target, unit = d.unit, clock = d.clock,
                    recurrence = d.recurrence, endsAt = d.endsAt, description = d.description,
                )
                showAdd = false
            },
            onClose = { showAdd = false },
        )
    }
    removeTarget?.let { h ->
        PautaSheet(title = tr("Marés"), onClose = { removeTarget = null }) {
            Text(
                text = tr("Remover esta maré? Todo o histórico será perdido."),
                color = colors.ink,
                style = PautaType.CardTitle,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { removeTarget = null }
                PautaButton(tr("remover"), Modifier.weight(2f), PautaButtonVariant.InkPrimary) {
                    vm.removeHabit(h.id)
                    removeTarget = null
                }
            }
        }
    }
}

/** One day of a month strip. Internal since R7: the reading-days grid draws the
 *  tides' own cells rather than a second renderer. // PT: um dia da tira; o R7
 *  reutiliza estas células. */
internal data class CellDay(
    val d: Int,
    val key: String,
    val state: CellState,
    val isToday: Boolean,
    val count: Int = 0,
)

/** The habit's static fields plus its marked days — everything the pure
 *  [HabitCalculator] math needs. // PT: o modelo puro de uma maré. */
private fun habitModelOf(h: HabitEntity, log: Set<String>, respiros: Set<String>) = HabitModel(
    id = h.id, createdAt = h.createdAt, cadence = h.cadence, anchor = h.anchor, weekdays = h.weekdays,
    recurrence = h.recurrence, endsAt = h.endsAt, log = log, respiros = respiros,
)

/**
 * P8: one row's month/streak numbers, computed together so they can be
 * memoised as a unit. Every field costs a day-by-day walk of the tide's
 * history — `bestStreak` walks from creation to today — and they used to run
 * on each recomposition of each row, so marking a single day re-walked every
 * tide on screen. // PT: as contas de um mês/streak, calculadas em bloco para
 * poderem ser memoizadas juntas.
 */
private data class RowStats(
    val pct: Int?,
    val obs: Int,
    val maturityTotal: Int,
    val isMature: Boolean,
    val streak: StreakResult?,
    val bestStreak: Int,
)

@Composable
private fun MaresHabitRow(
    habit: HabitEntity,
    model: HabitModel,
    countsForHabit: Map<String, Int>,
    year: Int,
    month: Int,
    today: String,
    isCurrentMonth: Boolean,
    // N3: the tab's one scroll state, shared by every row. // PT: o scroll do mês,
    // partilhado por todas as linhas.
    strip: ScrollState,
    animate: Boolean,
    onToggle: (String) -> Unit,
    onIncrement: (String, Int) -> Unit,
    onRespiro: (String) -> Unit,
    onUnmarkRespiro: (String) -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val accent = remember(habit.color, colors.accent) {
        habit.color
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
            ?: colors.accent
    }

    val ndays = DateUtils.daysInMonth(year, month)
    val isCount = habit.target != null && habit.cadence == "daily"
    // F4: what a stored count means, not the raw row. A tide written before the
    // ceiling existed can hold 39 against a target of 2, and printing 39 would be
    // printing a number the tide can no longer reach — reading it as "at the
    // target" is what makes the repair one tap. // PT: lê-se a contagem já
    // limitada; um 39 antigo mostra-se como "na meta".
    val todayCount = HabitCalculator.shownCount(countsForHabit[today] ?: 0, habit.target)

    // P8: keyed on the model, the viewed month and today — a mark on one tide
    // leaves the others' models equal, so only the tide that actually changed
    // pays for the walk. // PT: só a maré que mudou volta a fazer as contas.
    val stats = remember(model, year, month, today, isCurrentMonth) {
        val range = HabitCalculator.observedRangeInMonth(model, year, month, today)
        val p = range?.let { HabitCalculator.periodStats(model, it.first, it.second) }
        val obs = (p?.observed ?: 0) - (p?.respiros ?: 0)
        val maturityTotal = HabitCalculator.maturityUnits(model)
        RowStats(
            pct = HabitCalculator.pctInMonth(model, year, month, today),
            obs = obs,
            maturityTotal = maturityTotal,
            isMature = obs >= maturityTotal,
            streak = if (isCurrentMonth) HabitCalculator.currentStreak(model, today) else null,
            bestStreak = HabitCalculator.bestStreak(model, today),
        )
    }

    // `isCount` joins the keys: a target added to an existing tide doesn't change
    // its model, but it does change every cell's state. // PT: a meta entra nas
    // chaves — muda as células sem mudar o modelo.
    val days = remember(model, countsForHabit, isCount, year, month, today) {
        (1..ndays).map { d ->
            val key = "%04d-%02d-%02d".format(year, month, d)
            val state = cellStateFor(model, habit.cadence, isCount, countsForHabit, key, today)
            CellDay(d, key, state, key == today, countsForHabit[key] ?: 0)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    // Tap the name for the full history; edit/archive/remove live
                    // inside that detail → edit sheet now (A7). // PT: toca no nome
                    // para o histórico; editar/arquivar/remover estão lá dentro.
                    .clickableNoRipple(onClick = onOpenDetail),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.name,
                        color = colors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                    )
                    cadenceChipLabel(habit)?.let { label ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = colors.ink3,
                            style = PautaType.MetaSmall,
                            letterSpacing = 0.54.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (isCount) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$todayCount/${habit.target}" + (habit.unit.ifBlank { tr("×") }.let { if (habit.unit.isNotBlank()) " $it" else it }),
                            color = accent,
                            style = PautaType.MetaSmall,
                            letterSpacing = 0.54.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (habit.time.isNotBlank() || habit.clock.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (habit.clock.isNotBlank()) {
                            Text(habit.clock, color = colors.ink3, style = PautaType.Meta)
                            if (habit.time.isNotBlank()) Spacer(Modifier.width(6.dp))
                        }
                        if (habit.time.isNotBlank()) {
                            Text(habit.time, color = colors.ink3, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (stats.pct == null) {
                    Text("—", color = colors.ink3, style = PautaType.MetaSmall)
                } else {
                    Text(
                        text = "${stats.pct}%",
                        color = if (stats.isMature) colors.ink2 else colors.ink3,
                        style = PautaType.Meta,
                        fontStyle = if (stats.isMature) FontStyle.Normal else FontStyle.Italic,
                    )
                    if (!stats.isMature) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when (habit.cadence) {
                                "weekly" -> trf("semana {obs}/{total}", "obs" to stats.obs, "total" to stats.maturityTotal)
                                "monthly" -> trf("mês {obs}/{total}", "obs" to stats.obs, "total" to stats.maturityTotal)
                                else -> trf("dia {obs}/{total}", "obs" to stats.obs, "total" to stats.maturityTotal)
                            },
                            color = colors.ink3,
                            style = PautaType.MetaSmall,
                        )
                    } else if (stats.streak != null && stats.streak.days >= 1) {
                        val streak = stats.streak
                        HabitCalculator.tideTier(streak.days)?.let { tier ->
                            Spacer(Modifier.height(4.dp))
                            // Kept at the eyebrow's tighter tracking + SemiBold: this is a
                            // badge on a row, not a section header, and SectionEyebrow's
                            // 1.6sp would push the right column into the headline.
                            // // PT: emblema de linha — mantém o peso e o espaçamento curto.
                            Text(
                                text = tr(tier.name).uppercase(),
                                color = accent,
                                style = PautaType.MetaSmall,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.54.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("${streak.units} ${streak.unit}")
                                    if (streak.respiros > 0) {
                                        withStyle(SpanStyle(color = colors.ink3)) {
                                            append(" · ${streak.respiros} " + tr("resp."))
                                        }
                                    }
                                },
                                color = colors.ink2,
                                style = PautaType.MetaSmall,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Month strip — the pulse of days. N3: the scroll state and the
        // scroll-to-today are the tab's, not this row's. // PT: o scroll é da tab.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(strip)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            days.forEach { day ->
                MaresDayCell(
                    day = day,
                    accent = accent,
                    target = if (isCount) habit.target else null,
                    animate = animate,
                    onTap = {
                        when {
                            isCount -> if (day.state == CellState.RESPIRO) onUnmarkRespiro(day.key) else onIncrement(day.key, day.count)
                            day.state == CellState.EMPTY || day.state == CellState.DONE -> onToggle(day.key)
                            day.state == CellState.RESPIRO -> onUnmarkRespiro(day.key)
                        }
                    },
                    onLongPress = { if (day.state == CellState.EMPTY) onRespiro(day.key) },
                )
            }
        }

        // Best streak.
        if (stats.bestStreak >= 3) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append(trf("melhor: {n} dias", "n" to stats.bestStreak))
                    HabitCalculator.tideTier(stats.bestStreak)?.let {
                        withStyle(SpanStyle(color = colors.ink4)) { append(" · " + tr(it.name)) }
                    }
                },
                color = colors.ink3,
                style = PautaType.MetaSmall,
                letterSpacing = 0.72.sp,
            )
        }
    }
}

/**
 * N3 · the sparse day ruler under the month strips.
 *
 * It scrolls with them — same [ScrollState], so the numbers stay over the cells
 * they name — and marks 1, 8, 15, 22 and the last day of the month. The pitch is
 * the cells' own: 28dp wide plus a 3dp gap.
 * // PT: a régua que acompanha as tiras; marca 1, 8, 15, 22 e o último dia.
 */
@Composable
private fun MonthRuler(strip: ScrollState, days: Int) {
    val colors = LocalPautaColors.current
    val marks = remember(days) { (listOf(1, 8, 15, 22) + days).distinct().filter { it in 1..days } }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(strip),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (day in 1..days) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (day in marks) {
                    Text(
                        text = day.toString(),
                        color = colors.ink4,
                        style = PautaType.MetaSmall,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }
}

/**
 * One 28dp day cell, with all nine states. [interactive] is false for R7's
 * reading-days grid: those cells are filled by sessions, not by tapping, so the
 * same renderer draws them without offering a gesture that would mean nothing.
 * // PT: a célula de um dia; [interactive] = false na grelha de leituras, que se
 * preenche sozinha.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MaresDayCell(
    day: CellDay,
    accent: Color,
    target: Int?,
    animate: Boolean,
    interactive: Boolean = true,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val colors = LocalPautaColors.current
    val state = day.state
    val filled = state == CellState.DONE
    val clickable = interactive && (
        state == CellState.EMPTY || state == CellState.DONE ||
            state == CellState.RESPIRO || state == CellState.PARTIAL
        )
    val partialFrac = if (state == CellState.PARTIAL && target != null && target > 0) {
        (day.count.toFloat() / target).coerceAtMost(1f)
    } else 0f

    val cellAlpha = when (state) {
        CellState.FUTURE -> 0.45f
        CellState.PRE -> 0.55f
        CellState.AFTER -> 0.35f
        CellState.OFF, CellState.LOCKED -> 0.3f
        else -> 1f
    }
    // Today's accent ring only on actionable days; a soft glow when done.
    val borderColor: Color? = when {
        day.isToday && !filled && state != CellState.OFF -> accent
        state == CellState.EMPTY || state == CellState.PRE -> colors.ink3
        state == CellState.AFTER || state == CellState.LOCKED -> colors.rule
        state == CellState.RESPIRO || state == CellState.PARTIAL -> colors.ink3
        else -> null // DONE (none), FUTURE (dashed below), OFF (none)
    }
    val borderWidth = if (day.isToday && !filled && state != CellState.OFF) 1.5.dp else 1.dp

    val fillColor = if (day.isToday) accent else colors.ink
    // A3: a marked day's fill springs out from the centre; a respiro's hatch
    // wipes in along the diagonal. Reduced motion snaps both to their final look.
    // // PT: o preenchimento nasce do centro; o respiro entra na diagonal.
    val fillScale by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = if (animate) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ) else snap(),
        label = "cell-fill",
    )
    val hatch by animateFloatAsState(
        targetValue = if (state == CellState.RESPIRO) 1f else 0f,
        animationSpec = if (animate) PautaMotion.tween() else snap(),
        label = "cell-hatch",
    )

    Box(
        Modifier
            // Bigger tap target (was 22dp) so deliberate marking on the strip is
            // reliable and tiny-cell misclicks are far less likely.
            // PT: alvo de toque maior para marcar sem enganos.
            .size(28.dp)
            .alpha(cellAlpha)
            .drawBehind {
                // The accent/ink fill, scaled from the centre by the spring (clamped
                // so a bouncy overshoot never spills into the 3dp gap).
                if (fillScale > 0f) {
                    val s = fillScale.coerceIn(0f, 1f)
                    val w = size.width * s
                    val h = size.height * s
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                if (day.isToday && fillScale > 0f) {
                    // boxShadow 0 0 0 2px accent@20% — a ring just outside the cell,
                    // growing in step with the fill. // PT: anel cresce com o preenchimento.
                    drawRoundRect(
                        color = accent.copy(alpha = 0.2f * fillScale.coerceIn(0f, 1f)),
                        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                        size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                if (state == CellState.FUTURE) {
                    drawRoundRect(
                        color = colors.rule,
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                    )
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .then(borderColor?.let { Modifier.border(borderWidth, it, RoundedCornerShape(4.dp)) } ?: Modifier)
            .then(
                if (clickable) Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CellState.PRE -> Box(
                Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(colors.ink3.copy(alpha = 0.7f)),
            )
            CellState.OFF -> Box(
                Modifier.size(width = 10.dp, height = 2.dp).clip(RoundedCornerShape(2.dp)).background(colors.rule),
            )
            CellState.RESPIRO -> Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // The web's 45° hatch pattern.
                        val path = Path().apply {
                            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(4.dp.toPx())))
                        }
                        clipPath(path) {
                            val step = 4.dp.toPx()
                            val startX = -size.height
                            // Only lines up to this x are drawn, so the hatch wipes
                            // in diagonally as `hatch` runs 0→1. // PT: entra na diagonal.
                            val threshold = startX + (size.width - startX) * hatch.coerceIn(0f, 1f)
                            var x = startX
                            while (x < size.width) {
                                if (x <= threshold) {
                                    drawLine(
                                        color = accent.copy(alpha = 0.7f),
                                        start = Offset(x, size.height),
                                        end = Offset(x + size.height, 0f),
                                        strokeWidth = 1.4.dp.toPx(),
                                    )
                                }
                                x += step
                            }
                        }
                    },
            )
            CellState.PARTIAL -> {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((28 * maxOf(0.15f, partialFrac)).dp)
                        .background(accent.copy(alpha = 0.55f)),
                )
                Text(
                    text = day.count.toString(),
                    color = if (partialFrac >= 0.6f) colors.paper else colors.ink2,
                    fontFamily = MonoFamily,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 8.sp,
                )
            }
            else -> {}
        }
    }
}

/** "semanal · domingo" / "mensal · dia 1" — the web's cadence chip label;
 *  null for daily tides (no chip). */
private fun cadenceChipLabel(habit: HabitEntity): String? {
    if (habit.cadence != "weekly" && habit.cadence != "monthly") return null
    val base = if (habit.cadence == "weekly") tr("semanal") else tr("mensal")
    val anchor = habit.anchor ?: return base
    return if (habit.cadence == "weekly") {
        val days = listOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
        base + " · " + tr(days.getOrElse(anchor) { "" })
    } else {
        base + " · " + trf("dia {n}", "n" to anchor)
    }
}

private fun monthLongName(month: Int): String = I18n.fmtMonthLong(month)

/**
 * P8: month navigation reads as movement through time. [ymIndex] is
 * `year * 12 + (month - 1)`, so comparing the two states gives the direction
 * for free — forwards enters from the right, backwards from the left — and
 * jumping several months (the tap-to-today reset, the trend sheet's month
 * picker) still animates the correct way. Half-width offsets keep it a nudge
 * rather than a carousel. Reduced motion swaps the content with no travel and
 * no size animation. // PT: a navegação de mês desliza no sentido do tempo; com
 * movimento reduzido, troca sem animação.
 */
@Composable
private fun MonthSlide(
    ymIndex: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (year: Int, month: Int) -> Unit,
) {
    AnimatedContent(
        targetState = ymIndex,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (!animate) {
                (EnterTransition.None togetherWith ExitTransition.None)
                    .using(SizeTransform { _, _ -> snap() })
            } else {
                val dir = if (targetState > initialState) 1 else -1
                (
                    (
                        slideInHorizontally(PautaMotion.tween(PautaMotion.Base)) { w -> dir * w / 2 } +
                            fadeIn(PautaMotion.tween(PautaMotion.Base))
                        ) togetherWith (
                        slideOutHorizontally(PautaMotion.tween(PautaMotion.Base)) { w -> -dir * w / 2 } +
                            fadeOut(PautaMotion.tween(PautaMotion.Fast))
                        )
                    ).using(SizeTransform { _, _ -> PautaMotion.tween(PautaMotion.Base) })
            }
        },
        label = "month-slide",
    ) { idx -> content(idx / 12, idx % 12 + 1) }
}

/** The empty state's intro phrase (mares-phrases.jsx `intro`), picked
 *  deterministically by day so it doesn't change every render. */
private val INTRO_PHRASES = listOf(
    "As marés têm história. Sobem e descem ao longo do ano.",
    "Toda onda começa pequena.",
    "Pés na água. O resto vem com o tempo.",
)

private fun introPhraseFor(dayKey: String): String =
    INTRO_PHRASES[((dayKey.hashCode() % INTRO_PHRASES.size) + INTRO_PHRASES.size) % INTRO_PHRASES.size]

/** A dashed rounded-rect outline (the web's `border: 1.5px dashed var(--rule)`). */
private fun Modifier.dashedRectBorder(color: Color, radius: androidx.compose.ui.unit.Dp): Modifier = this.then(
    Modifier.drawBehind {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            ),
        )
    },
)

// ─── Grid legend ───────────────────────────────────────────
// tab-mares.jsx GridLegend: a small toggle (three mini swatches + "legenda")
// opening a popover that explains all nine cell states. // PT: a legenda da
// grelha, igual à web.
@Composable
private fun GridLegend() {
    val colors = LocalPautaColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(PautaRadius.Chip))
                .background(if (open) colors.paper2 else Color.Transparent)
                .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip))
                .clickableNoRipple { open = !open }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            LegendSwatch { drawDone(colors.ink) }
            LegendSwatch { drawEmptyBox(colors.ink3) }
            LegendSwatch { drawPre(colors.ink3) }
            SectionEyebrow(tr("legenda"), modifier = Modifier.padding(start = 3.dp))
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(LocalDensity.current) { 34.dp.roundToPx() }),
                onDismissRequest = { open = false },
            ) {
                Column(
                    Modifier
                        .width(210.dp)
                        .shadow(12.dp, RoundedCornerShape(PautaRadius.Field))
                        .clip(RoundedCornerShape(PautaRadius.Field))
                        .background(colors.paper)
                        .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Field))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    LegendRow(tr("feito")) { drawDone(colors.ink) }
                    LegendRow(tr("feito hoje")) { drawDoneToday(colors.accent) }
                    LegendRow(tr("não feito")) { drawEmptyBox(colors.ink3) }
                    LegendRow(tr("hoje (por fazer)")) { drawTodayPending(colors.accent) }
                    LegendRow(tr("respiro")) { drawRespiro(colors.ink3, colors.accent) }
                    LegendRow(tr("antes da maré")) { drawPre(colors.ink3) }
                    LegendRow(tr("fora do horário")) { drawOff(colors.rule) }
                    LegendRow(tr("ainda não chegou"), last = true) { drawFuture(colors.rule) }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = tr("Pressão longa num dia não feito para marcar respiro."),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.size(9.dp)) { draw() }
}

@Composable
private fun LegendRow(label: String, last: Boolean = false, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val colors = LocalPautaColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) { LegendSwatch(draw) }
            Text(
                text = label,
                color = colors.ink2,
                style = PautaType.Meta,
                letterSpacing = 0.22.sp, // 0.02em of 11sp
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
    }
}

// Tiny 9×9 swatch painters mirroring the web's legend kinds.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.swatchRadius() = CornerRadius(2.dp.toPx())

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDone(ink: Color) {
    drawRoundRect(ink, cornerRadius = swatchRadius())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoneToday(accent: Color) {
    drawRoundRect(accent.copy(alpha = 0.2f), cornerRadius = CornerRadius(3.dp.toPx()), topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()), size = Size(size.width + 3.dp.toPx(), size.height + 3.dp.toPx()))
    drawRoundRect(accent, cornerRadius = swatchRadius())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyBox(ink3: Color) {
    drawRoundRect(ink3, cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTodayPending(accent: Color) {
    drawRoundRect(accent, cornerRadius = swatchRadius(), style = Stroke(width = 1.5.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRespiro(ink3: Color, accent: Color) {
    drawRoundRect(ink3, cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
    val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, swatchRadius())) }
    clipPath(path) {
        var x = -size.height
        while (x < size.width) {
            drawLine(accent.copy(alpha = 0.7f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
            x += 3.dp.toPx()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPre(ink3: Color) {
    drawRoundRect(ink3.copy(alpha = 0.5f), cornerRadius = swatchRadius(), style = Stroke(width = 1.dp.toPx()))
    drawCircle(ink3.copy(alpha = 0.7f), radius = 1.25.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOff(rule: Color) {
    val w = 7.dp.toPx(); val h = 2.dp.toPx()
    drawRoundRect(rule, topLeft = Offset((size.width - w) / 2, (size.height - h) / 2), size = Size(w, h), cornerRadius = CornerRadius(1.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFuture(rule: Color) {
    drawRoundRect(
        rule.copy(alpha = 0.5f), cornerRadius = swatchRadius(),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
    )
}
