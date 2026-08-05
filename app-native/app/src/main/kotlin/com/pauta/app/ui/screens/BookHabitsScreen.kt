package com.pauta.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.domain.BookMath
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.ReadingStats
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.CellState
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.theme.rememberMotionEnabled
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlin.math.roundToInt

/**
 * native-only (R7): book mode's third tab — the reading rhythm.
 *
 * K7 left this tab as the planner's tide grid with a goal card glued on top and
 * a header calling those tides "hábitos de leitura", which they never were. This
 * is the honest version: the annual goal, the days you actually read (filled by
 * sessions, not by tapping), what the charts can say about pace, the books
 * finished this year — and then, under a plain `HÁBITOS`, the same tides the
 * planner has, doing exactly what they always did.
 *
 * Everything above the tides is derived in [ReadingStats] from data that already
 * exists; nothing here has a table of its own. The tides are rendered by
 * [MaresContent], which takes this screen's sections as its leading items — one
 * LazyColumn, because two nested scrollables would clash.
 * // PT: a terceira tab do modo livro — o ritmo de leitura, com as marés normais
 * ao fundo, numa só lista.
 */
@Composable
fun BookHabitsScreen() {
    val vm: AppViewModel = viewModel()
    val today by vm.todayKey.collectAsStateWithLifecycle()
    val sessionBlocks by vm.bookSessionBlocks.collectAsStateWithLifecycle()
    val allSessions by vm.allSessions.collectAsStateWithLifecycle()
    val reading by vm.booksReading.collectAsStateWithLifecycle()
    val tbr by vm.booksTbr.collectAsStateWithLifecycle()
    val paused by vm.booksPaused.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    val animate = rememberMotionEnabled()

    // L3: paused included, or the sessions of a book you put down would lose the
    // book they belong to and stop counting pages. // PT: com os livros em pausa,
    // senão as sessões deles perdem o livro.
    val books = remember(reading, tbr, paused, done) { reading + tbr + paused + done }
    // Three tables reduced to the flat shape the pure math takes. Keyed on the
    // flows, so a concluded session lands here live. // PT: as três tabelas
    // achatadas na forma que a matemática pura recebe.
    val sessions = remember(sessionBlocks, allSessions, books) {
        readingSessionsOf(sessionBlocks, allSessions, books)
    }

    MaresContent {
        readingRhythmItems(sessions = sessions, books = books, today = today, animate = animate)
    }
}

/**
 * Sections 1–5 of the tab, as items of [MaresContent]'s list. The last of them is
 * only an eyebrow: everything below it is the tides themselves.
 * // PT: as secções do ritmo de leitura; a última é só o eyebrow das marés.
 */
private fun LazyListScope.readingRhythmItems(
    sessions: List<ReadingStats.Session>,
    books: List<BookEntity>,
    today: String,
    animate: Boolean,
) {
    item(key = "book-goal") {
        Spacer(Modifier.height(6.dp))
        BookAnnualGoalCard()
        Spacer(Modifier.height(26.dp))
    }

    if (sessions.isEmpty()) {
        // Nothing read yet: one quiet line instead of three empty charts and a
        // grid of blanks. // PT: uma linha em vez de gráficos vazios.
        item(key = "book-no-reading") {
            Text(
                text = tr("Ainda sem leituras registadas."),
                color = LocalPautaColors.current.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(26.dp))
        }
    } else {
        item(key = "book-days") { ReadingDaysSection(sessions, today, animate) }
        item(key = "book-charts") { ReadingChartsSection(sessions, today) }
        item(key = "book-finished") { FinishedBooksSection(books, today) }
    }

    item(key = "book-habits-eyebrow") {
        // The honest label: these are the user's ordinary tides, the same ones
        // the planner shows. // PT: o rótulo honesto — são as marés de sempre.
        SectionEyebrow(tr("Hábitos"))
        Spacer(Modifier.height(14.dp))
    }
}

/**
 * Every concluded reading session, flattened. The day is the one the session
 * *ended* on (a block started at 23:50 belongs to the day it finished), the
 * minutes come from the session spans, and the words only exist where something
 * counted them.
 *
 * [ReadingStats.Session.pages] is deliberately null unless the book counts its
 * progress in pages: an audiobook counts minutes, and an EPUB the reader has
 * read through counts percentage points (R6) — neither adds up with a page.
 * // PT: as sessões achatadas; páginas só onde a unidade é mesmo a página.
 */
private fun readingSessionsOf(
    blocks: List<FocusBlockEntity>,
    allSessions: List<FocusSessionEntity>,
    books: List<BookEntity>,
): List<ReadingStats.Session> {
    val segsByBlock = allSessions.groupBy { it.blockId }
    val bookById = books.associateBy { it.id }
    val now = System.currentTimeMillis()
    return blocks
        .filter { it.status == "done" }
        .sortedBy { it.createdAt }
        .map { b ->
            val segs = segsByBlock[b.id].orEmpty()
            val ms = FocusMath.blockElapsedMs(
                segs.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now,
            )
            val endedAt = segs.mapNotNull { it.endedAt }.maxOrNull() ?: b.createdAt
            val book = b.project?.removePrefix("book:")?.let { bookById[it] }
            val countsPages = book != null &&
                book.format != "audiobook" && !BookMath.hasCountedWords(book)
            ReadingStats.Session(
                dayKey = DateUtils.dayKeyOf(endedAt),
                minutes = (ms / 60_000L).toInt(),
                pages = b.pagesDelta?.takeIf { countsPages && it >= 0 },
                words = book?.let { bk ->
                    val perUnit = BookMath.wordsPerUnit(bk) ?: return@let null
                    b.pagesDelta?.takeIf { it >= 0 }?.let { it * perUnit }
                },
            )
        }
}

// ─── 2 · Dias de leitura ───────────────────────────────────

/** The month grid, drawn with the tides' own cells but read-only — reading is
 *  proven by a session, never self-reported. // PT: a grelha do mês, só de
 *  leitura: preenche-se sozinha. */
@Composable
private fun ReadingDaysSection(sessions: List<ReadingStats.Session>, today: String, animate: Boolean) {
    val colors = LocalPautaColors.current
    val year = today.take(4).toInt()
    val month = today.substring(5, 7).toInt()
    val minutes = remember(sessions, year, month) { ReadingStats.minutesByDay(sessions, year, month) }
    val days = remember(sessions) { ReadingStats.daysRead(sessions) }
    val streaks = remember(days, today) { ReadingStats.streaks(days, today) }

    val cells = remember(minutes, year, month, today) {
        (1..DateUtils.daysInMonth(year, month)).map { d ->
            val key = "%04d-%02d-%02d".format(year, month, d)
            CellDay(
                d = d,
                key = key,
                state = when {
                    key > today -> CellState.FUTURE
                    minutes.containsKey(key) -> CellState.DONE
                    else -> CellState.EMPTY
                },
                isToday = key == today,
            )
        }
    }
    // A Sunday-first calendar, like the Revisão's focus calendar.
    val lead = remember(year, month) { DateUtils.weekdayJs("%04d-%02d-01".format(year, month)) }
    val readThisMonth = cells.count { it.state == CellState.DONE }

    SectionEyebrow(tr("Dias de leitura"))
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            // One summary for TalkBack: 31 undescribed cells would be noise, and
            // the streak line below carries the rest. // PT: um resumo para o
            // TalkBack em vez de 31 células.
            .semantics(mergeDescendants = true) {
                contentDescription = trf("{n} dias de leitura este mês", "n" to readThisMonth)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            tr("d,s,t,q,q,s,s").split(",").forEach { l ->
                Text(
                    text = l,
                    color = colors.ink4,
                    style = PautaType.MetaSmall,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        val rows = remember(cells, lead) { (List<CellDay?>(lead) { null } + cells).chunked(7) }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { c ->
                        if (c == null) Spacer(Modifier.size(28.dp))
                        else {
                            MaresDayCell(
                                day = c,
                                accent = colors.accent,
                                target = null,
                                animate = animate,
                                interactive = false,
                            )
                        }
                    }
                    repeat(7 - row.size) { Spacer(Modifier.size(28.dp)) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = tr("Sequência atual") + ": " + streakDays(streaks.first) +
            " · " + tr("melhor") + ": " + streakDays(streaks.second),
        color = colors.ink3,
        style = PautaType.MetaSmall,
        letterSpacing = 0.4.sp,
    )
    Spacer(Modifier.height(26.dp))
}

private fun streakDays(n: Int): String = "$n " + if (n == 1) tr("dia") else tr("dias")

// ─── 3 · Gráficos ──────────────────────────────────────────

/** The three Canvas charts, in the Revisão's manner: no gridlines, no axes, one
 *  mono caption each. Each hides itself when its data can't say anything.
 *  // PT: os três gráficos; cada um esconde-se sem dados que digam algo. */
@Composable
private fun ReadingChartsSection(sessions: List<ReadingStats.Session>, today: String) {
    val colors = LocalPautaColors.current

    val minutes = remember(sessions, today) { ReadingStats.minutesLastDays(sessions, today, 30) }
    if (minutes.any { it > 0 }) {
        SectionEyebrow(tr("Minutos por dia"))
        Spacer(Modifier.height(8.dp))
        BarChart(minutes.map { it.toFloat() })
        Spacer(Modifier.height(6.dp))
        ChartCaption(
            trf("últimos {n} dias", "n" to 30) + " · " +
                trf("máx {n} min", "n" to (minutes.max())),
        )
        Spacer(Modifier.height(22.dp))
    }

    val pages = remember(sessions, today) { ReadingStats.pagesByWeek(sessions, today, 12) }
    if (pages.any { it > 0 }) {
        SectionEyebrow(tr("Páginas por semana"))
        Spacer(Modifier.height(8.dp))
        BarChart(pages.map { it.toFloat() })
        Spacer(Modifier.height(6.dp))
        ChartCaption(trf("últimas {n} semanas", "n" to 12))
        Spacer(Modifier.height(22.dp))
    }

    // Two points are a line through two points, not a trend — the third is what
    // makes it worth drawing. // PT: com menos de 3 sessões não há tendência.
    val speed = remember(sessions) { ReadingStats.speedPoints(sessions) }
    if (speed.size >= 3) {
        SectionEyebrow(tr("Ritmo ao longo do tempo"))
        Spacer(Modifier.height(8.dp))
        LineChart(speed)
        Spacer(Modifier.height(6.dp))
        // The "≈" stays: this line mixes books, and any of them may be counting
        // 280 words to the page. R6 owns that distinction per book; here the
        // honest reading is the estimate. // PT: mistura livros, logo estimativa.
        ChartCaption(trf("Ritmo: ≈ {n} palavras/min", "n" to speed.average().roundToInt()))
        Spacer(Modifier.height(22.dp))
    }

    if (minutes.none { it > 0 } && pages.none { it > 0 } && speed.size < 3) {
        // Sessions exist but none of them can be plotted (all under a minute, or
        // all uncounted). // PT: há sessões, mas nenhuma dá gráfico.
        Text(
            text = tr("Ainda sem leituras registadas."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun ChartCaption(text: String) {
    Text(text, color = LocalPautaColors.current.ink4, style = PautaType.MetaSmall, letterSpacing = 0.4.sp)
}

/** Bars on paper: accent for what happened, a hairline on the baseline for what
 *  didn't. // PT: barras a accent; o que não aconteceu fica um traço. */
@Composable
private fun BarChart(values: List<Float>) {
    val colors = LocalPautaColors.current
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Canvas(Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { }) {
        if (values.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val radius = CornerRadius(1.5.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            if (v <= 0f) {
                drawLine(
                    color = colors.rule,
                    start = Offset(x, size.height),
                    end = Offset(x + w, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            } else {
                val h = (v / max * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = if (i == values.lastIndex) colors.accent else colors.accent.copy(alpha = 0.55f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(w, h),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** One thin accent stroke through the points, with a dot on each. A flat run
 *  draws down the middle rather than dividing by a zero range.
 *  // PT: uma linha fina pelos pontos; um ritmo constante fica a meio. */
@Composable
private fun LineChart(values: List<Float>) {
    val colors = LocalPautaColors.current
    Canvas(Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { }) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f }
        val inset = 3.dp.toPx()
        val usable = (size.height - inset * 2).coerceAtLeast(1f)
        val step = size.width / (values.size - 1)
        fun pointAt(i: Int): Offset {
            val frac = range?.let { (values[i] - min) / it } ?: 0.5f
            return Offset(i * step, inset + (1f - frac) * usable)
        }
        val path = Path().apply {
            moveTo(pointAt(0).x, pointAt(0).y)
            for (i in 1 until values.size) lineTo(pointAt(i).x, pointAt(i).y)
        }
        drawPath(path, color = colors.accent, style = Stroke(width = 1.5.dp.toPx()))
        values.indices.forEach { i ->
            drawCircle(color = colors.accent, radius = 2.dp.toPx(), center = pointAt(i))
        }
    }
}

// ─── 4 · Livros terminados ─────────────────────────────────

/** A twelve-cell year strip, one cell per month. // PT: a tira do ano. */
@Composable
private fun FinishedBooksSection(books: List<BookEntity>, today: String) {
    val colors = LocalPautaColors.current
    val year = today.take(4).toInt()
    val counts = remember(books, year) { ReadingStats.finishedByMonth(books, year) }
    val max = counts.max().coerceAtLeast(1)
    val thisMonth = today.substring(5, 7).toInt()

    SectionEyebrow(tr("Livros terminados"))
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        counts.forEachIndexed { i, n ->
            val month = i + 1
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (n == 0) colors.paper
                            else lerp(colors.paper, colors.accent, 0.3f + 0.7f * (n.toFloat() / max)),
                        )
                        .then(
                            when {
                                month == thisMonth -> Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(4.dp))
                                n == 0 -> Modifier.border(1.dp, colors.rule, RoundedCornerShape(4.dp))
                                else -> Modifier
                            },
                        )
                        .semantics {
                            contentDescription = "${I18n.fmtMonthShort(month)}: $n"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (n > 0) {
                        Text(
                            text = n.toString(),
                            color = if (n.toFloat() / max > 0.6f) colors.onDark else colors.ink2,
                            fontFamily = MonoFamily,
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = I18n.fmtMonthShort(month).take(1).uppercase(),
                    color = if (month == thisMonth) colors.ink3 else colors.ink4,
                    style = PautaType.MetaSmall,
                    fontSize = 8.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "${counts.sum()} " + tr("livros este ano"),
        color = colors.ink3,
        style = PautaType.MetaSmall,
        letterSpacing = 0.4.sp,
    )
    Spacer(Modifier.height(26.dp))
}

// ─── 1 · Objetivo anual ────────────────────────────────────

/**
 * native-only (K7): the annual reading goal — N books this year, with the goal
 * editable in place. Unchanged by R7: it works, and it still leads the tab.
 * // PT: o cartão do objetivo anual, intocado pelo R7.
 */
@Composable
fun BookAnnualGoalCard() {
    val vm: AppViewModel = viewModel()
    val colors = LocalPautaColors.current
    val goal by vm.bookAnnualGoal.collectAsStateWithLifecycle()
    val done by vm.booksDone.collectAsStateWithLifecycle()
    var showGoalSheet by remember { mutableStateOf(false) }

    // N is re-counted whenever the finished shelf changes (a book concluded or
    // un-concluded elsewhere lands here live). // PT: recontado quando os lidos mudam.
    var booksThisYear by remember { mutableIntStateOf(0) }
    LaunchedEffect(done) { booksThisYear = vm.booksFinishedThisYear() }

    PautaCard(
        Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        SectionEyebrow(tr("Objetivo anual"))
        Spacer(Modifier.height(8.dp))
        if (goal <= 0) {
            // No goal yet: just the count + a quiet link to set one.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$booksThisYear " + tr("livros este ano"),
                    color = colors.ink,
                    style = PautaType.CardTitle,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tr("Definir objetivo") + " →",
                    color = colors.ink3,
                    style = PautaType.MetaSmall,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier
                        .clickableNoRipple { showGoalSheet = true }
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$booksThisYear / $goal " + tr("livros este ano"),
                    color = colors.ink,
                    style = PautaType.CardTitle,
                    modifier = Modifier.weight(1f),
                )
                // The small edit affordance for updating the goal.
                Text(
                    text = "✎",
                    color = colors.ink3,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickableNoRipple { showGoalSheet = true }
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(booksThisYear.toFloat() / goal.coerceAtLeast(1))
        }
    }

    if (showGoalSheet) {
        AnnualGoalSheet(current = goal, onClose = { showGoalSheet = false })
    }
}

/** Single number input for the annual book goal; IME Done submits. 0 clears.
 *  U4: also opened from Settings → Modo, so it's internal — one sheet, two ways
 *  in. // PT: também aberto pelas Definições; um só sheet. */
@Composable
internal fun AnnualGoalSheet(current: Int, onClose: () -> Unit) {
    val vm: AppViewModel = viewModel()
    var value by remember { mutableStateOf(current.takeIf { it > 0 }?.toString() ?: "") }

    fun submit() {
        vm.setAnnualGoal(value.toIntOrNull() ?: 0)
        onClose()
    }

    PautaSheet(title = tr("Objetivo anual"), onClose = onClose) {
        // U1: inside the body, so the number field waits for the sheet to settle.
        // // PT: espera que a folha assente antes de focar.
        val focus = rememberAutoFocusRequester()
        SheetEyebrow(tr("Objetivo de livros por ano"))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(120.dp)) {
            BoxedField(
                value = value,
                onChange = { raw -> value = raw.filter { it.isDigit() }.take(3) },
                placeholder = "12",
                modifier = Modifier.focusRequester(focus),
                singleLine = true,
                fontFamily = MonoFamily,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Guardar"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}
