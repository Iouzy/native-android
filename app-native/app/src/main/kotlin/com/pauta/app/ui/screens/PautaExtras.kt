package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.SansFamily
import com.pauta.app.ui.theme.SerifFamily
import java.time.LocalDate

// Pauta extras, ported from the web: the full-screen Zen focus mode
// (tab-pauta.jsx ZenFocus), the block editor (sheets.jsx EditBlockSheet), the
// 14-day history sheet (tab-pauta.jsx PautaHistorySheet) and small shared
// pieces. // PT: extras da Pauta — modo foco, editor de bloco e histórico.

// ─── Zen focus mode ────────────────────────────────────────
// Full-screen, distraction-free timer. Tap anywhere to leave; explicit
// Pausar/Concluir mirror the card's actions. A full-screen Dialog so it covers
// the tab bar and Pip, like the web's portal to <body>.
@Composable
internal fun ZenFocus(
    block: FocusBlockEntity,
    sessions: List<FocusSessionEntity>,
    now: Long,
    reducedMotion: Boolean,
    onExit: () -> Unit,
    onPause: () -> Unit,
    onConclude: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val totalElapsed = FocusMath.blockElapsedMs(sessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)
    // F1: the same tide-rise as the active card, now full-screen — the water
    // climbs to the target and a quiet crest sweeps once on reaching it.
    // // PT: a mesma maré do cartão activo, agora em ecrã inteiro.
    val target = block.targetMs ?: 0L
    val reached = target > 0 && totalElapsed >= target
    val crest = rememberTideCrest(block.id, reached, reducedMotion)
    val tideProgress = if (target > 0) totalElapsed.toFloat() / target else 0f
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.surfaceDark)
                .then(
                    if (target > 0) Modifier.drawBehind { drawTide(tideProgress, crest.value, colors.accent) }
                    else Modifier,
                )
                .clickableNoRipple(onExit),
        ) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SectionEyebrow(tr("em foco"), color = colors.onDark2)
                Text(
                    text = block.title,
                    color = colors.onDark,
                    style = PautaType.CardTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                // P7: zen's digits are the same role as the card's, scaled up — the
                // `.copy()` carries the tabular figures, so the centred line stops
                // twitching as the seconds roll. // PT: os mesmos dígitos, maiores.
                Text(
                    text = FocusMath.fmtTimer(totalElapsed),
                    color = colors.accent,
                    style = PautaType.Timer.copy(
                        fontSize = 64.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.28.sp, // 0.02em of 64sp
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ZenButton(tr("Pausar"), filled = false, onClick = onPause)
                    ZenButton(tr("Concluir"), filled = true, onClick = onConclude)
                }
            }
            Text(
                text = tr("toque para sair"),
                color = colors.onDark2,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
private fun ZenButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Text(
        text = label,
        color = if (filled) Color.White else colors.onDark,
        fontFamily = SansFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(PautaRadius.Field))
            .then(
                if (filled) Modifier.background(colors.accent)
                else Modifier.border(1.dp, Color(0x40F5F1EA), RoundedCornerShape(PautaRadius.Field)) // rgba(245,241,234,0.25)
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    )
}

// ─── Inline pause-note field ───────────────────────────────
// The timeline's "adicionar nota…": serif italic inline editor that commits on
// focus loss, so typing doesn't hammer the database. // PT: nota da pausa,
// gravada ao sair do campo.
@Composable
internal fun InlineNoteField(initial: String, placeholder: String, onCommit: (String) -> Unit) {
    val colors = LocalPautaColors.current
    var text by remember(initial) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(
            color = colors.ink2,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused && !state.isFocused && text != initial) onCommit(text)
                focused = state.isFocused
            },
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.ink4,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    )
                }
                inner()
            }
        },
    )
}

// ─── Confirm sheet ─────────────────────────────────────────
// The web's pautaConfirm() modal, as a PautaSheet (used for discard/delete).
@Composable
internal fun PautaSheetConfirm(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    PautaSheet(title = tr("Pauta"), onClose = onClose) {
        Text(
            text = message,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 18.sp,
            lineHeight = 25.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(confirmLabel, Modifier.weight(2f), PautaButtonVariant.InkPrimary) { onConfirm() }
        }
    }
}

// ─── Edit block sheet ──────────────────────────────────────
// sheets.jsx EditBlockSheet: status line, serif title field, project, reflection,
// per-session notes and the delete action.
/** F2 · one corrected span: the row it belongs to and its new bounds. */
internal data class SessionTimes(val rowId: Long, val startedAt: Long, val endedAt: Long)

/**
 * F2 · everything this sheet can change, in one payload. It grew from five
 * positional arguments to a named one when the sheet learned to edit a span's
 * clock and a reading session's page delta — five was already at the limit of
 * what a call site can read. // PT: tudo o que a folha pode alterar, num objecto.
 */
internal data class BlockEdit(
    val title: String,
    val project: String?,
    val targetMs: Long?,
    val reflection: String,
    val notes: List<Pair<Long, String>>,
    /** Only the spans whose clock actually moved. */
    val times: List<SessionTimes> = emptyList(),
    /** Null when this isn't a reading session, or when nobody counted. */
    val pagesDelta: Int? = null,
    /** False when the delta editor wasn't shown — so null can't be mistaken for
     *  "the user cleared it". // PT: distingue "não mexeu" de "apagou". */
    val pagesDeltaChanged: Boolean = false,
)

@Composable
internal fun EditBlockSheet(
    block: FocusBlockEntity,
    sessions: List<FocusSessionEntity>,
    now: Long,
    // F2: set in book mode, and it is what tells the sheet to offer the page
    // delta — and in which unit. Correcting a session's time without its delta
    // leaves the pace exactly as wrong as it was, because BookMath reads the
    // delta and not the clock. // PT: em modo livro, traz a unidade e o delta.
    book: BookEntity? = null,
    onSave: (BlockEdit) -> Unit,
    onDeleteSession: (rowId: Long) -> Unit = {},
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var title by remember(block.id) { mutableStateOf(block.title) }
    var project by remember(block.id) { mutableStateOf(block.project.orEmpty()) }
    var reflection by remember(block.id) { mutableStateOf(block.reflection) }
    val ordered = remember(sessions) { sessions.sortedBy { it.position } }
    var notes by remember(block.id, ordered.size) { mutableStateOf(ordered.map { it.note }) }
    // F2: the spans' clocks, as "HH:MM" text. Keyed on the row ids so a delete
    // rebuilds them rather than leaving an edit pointing at a row that is gone.
    // // PT: as horas em texto, refeitas quando uma sessão desaparece.
    val rowKey = remember(ordered) { ordered.map { it.rowId } }
    var starts by remember(block.id, rowKey) {
        mutableStateOf(ordered.map { DateUtils.fmtClock(it.startedAt) })
    }
    var ends by remember(block.id, rowKey) {
        mutableStateOf(ordered.map { s -> s.endedAt?.let { DateUtils.fmtClock(it) } ?: "" })
    }
    var deleteSession by remember { mutableStateOf<FocusSessionEntity?>(null) }
    var pagesDelta by remember(block.id) {
        mutableStateOf(block.pagesDelta?.toString() ?: "")
    }
    var confirmDelete by remember { mutableStateOf(false) }

    if (deleteSession != null) {
        val target = deleteSession!!
        PautaSheetConfirm(
            message = tr("Apagar esta sessão? O bloco fica, sem este tempo."),
            confirmLabel = tr("Apagar sessão"),
            onConfirm = { onDeleteSession(target.rowId); deleteSession = null },
            onClose = { deleteSession = null },
        )
        return
    }

    if (confirmDelete) {
        PautaSheetConfirm(
            message = tr("Apagar este bloco? Não dá pra desfazer."),
            confirmLabel = tr("Apagar bloco"),
            onConfirm = { confirmDelete = false; onDelete() },
            onClose = { confirmDelete = false },
        )
        return
    }

    val totalMs = FocusMath.blockElapsedMs(ordered.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, now)
    val statusLabel = when (block.status) {
        "done" -> tr("concluído")
        "paused" -> tr("pausado")
        else -> tr("em curso")
    }

    PautaSheet(title = tr("Editar bloco"), onClose = onClose) {
        SectionEyebrow("$statusLabel · ${FocusMath.fmtDuration(totalMs)}")
        Spacer(Modifier.height(10.dp))

        // Title — the serif underline input.
        BasicTextField(
            value = title,
            onValueChange = { title = it },
            // P7: the block title reads at the same size wherever a sheet shows it.
            // // PT: o título do bloco no mesmo papel em todas as folhas.
            textStyle = PautaType.CardTitle.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .drawUnderline(colors.ink)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (title.isEmpty()) {
                        Text(tr("título do bloco"), color = colors.ink4, style = PautaType.CardTitle)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(18.dp))

        SectionEyebrow(tr("Projecto"))
        Spacer(Modifier.height(8.dp))
        BoxedField(value = project, onChange = { project = it }, placeholder = tr("(sem projecto)"), singleLine = true, fontFamily = SansFamily, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        SectionEyebrow(tr("Reflexão"))
        Spacer(Modifier.height(8.dp))
        BoxedField(
            value = reflection,
            onChange = { reflection = it },
            placeholder = if (block.status == "done") tr("o que aconteceu?") else tr("ainda não concluído"),
            minHeight = 64.dp,
        )
        Spacer(Modifier.height(18.dp))

        if (ordered.isNotEmpty()) {
            SectionEyebrow(trf("Sessões ({n})", "n" to ordered.size))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.forEachIndexed { i, seg ->
                    val dur = (seg.endedAt ?: now) - seg.startedAt
                    PautaCard(
                        Modifier.fillMaxWidth(),
                        radius = PautaRadius.Field,
                        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        // F2: the span used to be read-only text — twelve junk
                        // sessions with no way to reach them is what this task
                        // exists for. Both ends are now pickers (no keyboard, so
                        // F3's trap never applies here) and the duration follows.
                        // // PT: os dois extremos passam a editáveis, com selector.
                        if (seg.endedAt == null) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = trf("{a} · em curso", "a" to DateUtils.fmtClock(seg.startedAt)),
                                    color = colors.ink2,
                                    style = PautaType.Meta,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = FocusMath.fmtDuration(dur),
                                    color = colors.ink3,
                                    style = PautaType.MetaSmall,
                                )
                            }
                        } else {
                            val startMs = DateUtils.withClock(seg.startedAt, starts.getOrElse(i) { "" })
                            val endMs = DateUtils.withClock(seg.endedAt, ends.getOrElse(i) { "" })
                            val backwards = startMs != null && endMs != null && endMs < startMs
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(92.dp)) {
                                    PautaTimeField(
                                        value = starts.getOrElse(i) { "" },
                                        onChange = { v -> starts = starts.toMutableList().also { it[i] = v } },
                                        title = tr("Início"),
                                    )
                                }
                                Text("→", color = colors.ink3, style = PautaType.Meta, modifier = Modifier.padding(horizontal = 8.dp))
                                Box(Modifier.width(92.dp)) {
                                    PautaTimeField(
                                        value = ends.getOrElse(i) { "" },
                                        onChange = { v -> ends = ends.toMutableList().also { it[i] = v } },
                                        title = tr("Fim"),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (startMs != null && endMs != null && !backwards) {
                                        FocusMath.fmtDuration(endMs - startMs)
                                    } else {
                                        FocusMath.fmtDuration(dur)
                                    },
                                    color = colors.ink3,
                                    style = PautaType.MetaSmall,
                                )
                            }
                            if (backwards) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = tr("O fim não pode ser antes do início."),
                                    color = Color(0xFFA8543D),
                                    style = PautaType.MetaSmall,
                                )
                            }
                        }
                        val editable = seg.endedAt != null && (i < ordered.size - 1 || block.status != "done")
                        if (editable) {
                            Spacer(Modifier.height(4.dp))
                            BasicTextField(
                                value = notes.getOrElse(i) { "" },
                                onValueChange = { v -> notes = notes.toMutableList().also { it[i] = v } },
                                textStyle = TextStyle(
                                    color = colors.ink2, fontFamily = SerifFamily,
                                    fontStyle = FontStyle.Italic, fontSize = 13.5.sp, lineHeight = 19.sp,
                                ),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    Box {
                                        if (notes.getOrElse(i) { "" }.isEmpty()) {
                                            Text(
                                                tr("nota da pausa (opcional)"),
                                                color = colors.ink4, fontFamily = SerifFamily,
                                                fontStyle = FontStyle.Italic, fontSize = 13.5.sp,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        if (seg.endedAt != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = tr("Apagar sessão"),
                                color = Color(0xFFA8543D),
                                style = PautaType.MetaSmall,
                                modifier = Modifier.clickableNoRipple { deleteSession = seg },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // F2 · a reading session's own page span. R5 measures it, BookMath reads
        // it, and until now nothing could correct it — so a session whose *time*
        // you fixed still reported the pace it always had. Shown only in book
        // mode, and asked in the unit F1 established. // PT: o delta de páginas da
        // sessão, na unidade do livro; sem ele, corrigir a hora não corrige o ritmo.
        if (book != null) {
            SectionEyebrow(bookProgressUnit(book))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(110.dp)) {
                    BoxedField(
                        value = pagesDelta,
                        onChange = { raw -> pagesDelta = raw.filter { it.isDigit() }.take(4) },
                        placeholder = tr("por contar"),
                        singleLine = true,
                        fontFamily = MonoFamily,
                        fontSize = 14.sp,
                    )
                }
                Text(bookProgressMark(book), color = colors.ink3, style = PautaType.Meta)
            }
            Spacer(Modifier.height(18.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Guardar"), Modifier.weight(2f)) {
                val changedNotes = ordered.mapIndexedNotNull { i, seg ->
                    val n = notes.getOrElse(i) { "" }
                    if (n != seg.note) seg.rowId to n else null
                }
                // Only the spans that actually moved, and never a backwards one:
                // a half-picked pair leaves the stored span alone rather than
                // writing something the user didn't mean. // PT: só as sessões que
                // mudaram, e nunca com o fim antes do início.
                val changedTimes = ordered.mapIndexedNotNull { i, seg ->
                    val ended = seg.endedAt ?: return@mapIndexedNotNull null
                    val a = DateUtils.withClock(seg.startedAt, starts.getOrElse(i) { "" })
                        ?: return@mapIndexedNotNull null
                    val b = DateUtils.withClock(ended, ends.getOrElse(i) { "" })
                        ?: return@mapIndexedNotNull null
                    if (b < a) return@mapIndexedNotNull null
                    if (a == seg.startedAt && b == ended) null else SessionTimes(seg.rowId, a, b)
                }
                onSave(
                    BlockEdit(
                        title = title,
                        project = project.takeIf { it.isNotBlank() },
                        targetMs = block.targetMs,
                        reflection = reflection,
                        notes = changedNotes,
                        times = changedTimes,
                        pagesDelta = pagesDelta.toIntOrNull(),
                        pagesDeltaChanged = book != null,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tr("Apagar bloco"),
            color = Color(0xFFA8543D),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, colors.rule, RoundedCornerShape(999.dp))
                .clickableNoRipple { confirmDelete = true }
                .padding(vertical = 11.dp),
        )
    }
}

/** The web's `border-bottom: 1.5px solid var(--ink)` title underline. */
private fun Modifier.drawUnderline(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.5.dp.toPx(),
        )
    },
)

// ─── Pauta history sheet ───────────────────────────────────
// tab-pauta.jsx PautaHistorySheet: the 14-day bars, the 3-stat strip, every day
// with blocks, and a per-day drill-down with the read-only timeline.
@Composable
internal fun PautaHistorySheet(
    blocks: List<FocusBlockEntity>,
    allSessions: List<FocusSessionEntity>,
    today: String,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var picked by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val segs = remember(allSessions) { allSessions.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) } }
    val blockById = remember(blocks) { blocks.associateBy { it.id } }

    fun focusOf(day: String): Long = FocusMath.dailyFocusMs(segs, day, now)
    fun countOf(day: String): Int =
        allSessions.filter { DateUtils.dayKeyOf(it.startedAt) == day }.map { it.blockId }.toSet().size

    PautaSheet(title = tr("Pautas anteriores"), onClose = onClose) {
        val pickedKey = picked
        if (pickedKey != null) {
            // Back to the overview.
            Text(
                text = "‹ " + tr("histórico").uppercase(),
                color = colors.ink2,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 0.96.sp, // 0.08em of 12sp
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.paper2)
                    .border(1.dp, colors.rule, RoundedCornerShape(999.dp))
                    .clickableNoRipple { picked = null }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            )
            Spacer(Modifier.height(16.dp))
            SectionEyebrow(I18n.fmtDateLong(LocalDate.parse(pickedKey)))
            Spacer(Modifier.height(4.dp))
            val c = countOf(pickedKey)
            Text(
                text = buildAnnotatedString {
                    append("$c ")
                    append(if (c == 1) tr("bloco") else tr("blocos"))
                    append(". ")
                    withStyle(SpanStyle(color = colors.accent, fontStyle = FontStyle.Italic)) {
                        append(FocusMath.fmtDuration(focusOf(pickedKey)))
                    }
                    append(".")
                },
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 26.sp,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(16.dp))
            val dayEvents = remember(blocks, allSessions, pickedKey) {
                buildTimelineEvents(blocks, allSessions, pickedKey)
            }
            if (dayEvents.isEmpty()) {
                Text(
                    text = tr("Sem blocos nesse dia."),
                    color = colors.ink3,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                TimelineList(
                    events = dayEvents,
                    blockById = blockById,
                    onFilterBlock = null,
                    onEditBlock = null,
                    onEditNote = null,
                )
            }
        } else {
            Text(
                text = tr("O ritmo dos seus dias."),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 20.sp,
                lineHeight = 25.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tr("Cada barra é um dia. Toque para ver os blocos desse dia."),
                color = colors.ink3,
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(22.dp))

            // Last 14 days, oldest first.
            val chartDays = (13 downTo 0).map { i -> DateUtils.addDays(today, -i) }
            val focusByDay = chartDays.associateWith { focusOf(it) }
            val maxMs = (focusByDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
            FocusBars(
                days = chartDays,
                focusByDay = focusByDay,
                maxMs = maxMs,
                today = today,
                onPick = { picked = it },
            )

            Spacer(Modifier.height(22.dp))
            val totalMs = focusByDay.values.sum()
            val activeDays = focusByDay.values.count { it > 0 }
            val avgMs = if (activeDays > 0) totalMs / activeDays else 0L
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PautaRadius.Chip))
                    .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip)),
            ) {
                PStat(tr("Total 14d"), FocusMath.fmtDuration(totalMs), Modifier.weight(1f))
                VertRule()
                PStat(tr("Média/dia activo"), if (activeDays > 0) FocusMath.fmtDuration(avgMs) else "—", Modifier.weight(1f))
                VertRule()
                PStat(tr("Dias activos"), "$activeDays/14", Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            SectionEyebrow(tr("todos os dias com blocos"))
            Spacer(Modifier.height(10.dp))
            val allDays = remember(allSessions) {
                allSessions.map { DateUtils.dayKeyOf(it.startedAt) }.toSet().sortedDescending()
            }
            if (allDays.isEmpty()) {
                Text(
                    text = tr("Ainda nenhuma pauta passada."),
                    color = colors.ink3,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allDays.forEach { k ->
                        val isToday = k == today
                        val count = countOf(k)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(PautaRadius.Chip))
                                .background(if (isToday) colors.accent.copy(alpha = 0.03f) else colors.paper)
                                .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip))
                                .clickableNoRipple { picked = k }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = I18n.fmtDateLong(LocalDate.parse(k)),
                                        color = colors.ink,
                                        fontFamily = SerifFamily,
                                        fontSize = 16.sp,
                                    )
                                    if (isToday) {
                                        Spacer(Modifier.padding(start = 8.dp))
                                        Text(
                                            text = "· " + tr("hoje").uppercase(),
                                            color = colors.accent,
                                            style = PautaType.MetaSmall,
                                            letterSpacing = 1.sp,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$count " + (if (count == 1) tr("bloco") else tr("blocos")),
                                    color = colors.ink3,
                                    style = PautaType.MetaSmall,
                                    letterSpacing = 0.6.sp,
                                )
                            }
                            Text(
                                text = FocusMath.fmtDuration(focusOf(k)),
                                color = colors.accent,
                                fontFamily = MonoFamily,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VertRule() {
    Box(
        Modifier
            .padding(vertical = 0.dp)
            .width(1.dp)
            .height(64.dp)
            .background(LocalPautaColors.current.rule),
    )
}

@Composable
private fun PStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    Column(
        modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 8.sp,
            letterSpacing = 1.2.sp, // 0.15em of 8sp
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 18.sp,
            lineHeight = 18.sp,
        )
    }
}

/** The 14-day bar chart: today in accent, empty days as a 3dp baseline stub. */
@Composable
private fun FocusBars(
    days: List<String>,
    focusByDay: Map<String, Long>,
    maxMs: Long,
    today: String,
    onPick: (String) -> Unit,
) {
    val colors = LocalPautaColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { k ->
                val ms = focusByDay[k] ?: 0L
                val isToday = k == today
                val frac = if (ms > 0) (ms.toFloat() / maxMs) else 0f
                val h = if (ms > 0) (frac * 104).coerceAtLeast(3f) else 3f
                Box(
                    Modifier
                        .weight(1f)
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            when {
                                ms == 0L -> colors.rule
                                isToday -> colors.accent
                                else -> colors.ink.copy(alpha = 0.78f)
                            },
                        )
                        .clickableNoRipple { onPick(k) },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            days.forEachIndexed { i, k ->
                val isToday = k == today
                val show = i == 0 || i == days.size - 1 || i == days.size / 2 || isToday
                Text(
                    text = if (show) k.substring(8) else "",
                    color = if (isToday) colors.accent else colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 8.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
