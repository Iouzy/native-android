package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.FocusMath
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.TideToday
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SansFamily
import com.pauta.app.ui.theme.SerifFamily

// The Pauta tab's modal sheets, ported 1:1 from src/sheets.jsx: StartSheet
// (title + link-to-intention + resume-template + project + duration), the
// optimistic PauseSheet (timer already stopped; cancel = resume) and the
// ConcludeSheet (reflection + plan-vs-actual + intention tick + today's tides).
// // PT: os "sheets" da Pauta, copiados da web um a um.

// ─── START SHEET ──────────────────────────────────────────
@Composable
fun StartSheet(
    intentions: List<IntentionEntity>,
    projects: List<String>,
    recentBlocks: List<FocusBlockEntity>,
    hasActive: Boolean,
    activeTitle: String,
    onStart: (title: String, linkedToId: String?, project: String?, targetMin: Int?) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var title by remember { mutableStateOf("") }
    var selectedIntention by remember { mutableStateOf<String?>(null) }
    var project by remember { mutableStateOf("") }
    var targetMin by remember { mutableStateOf(0) } // 0 = no target
    // A6: the title is focused on open (keyboard up, no tap) and the first empty
    // submit flips [triedSubmit], turning the underline + hint danger instead of
    // leaving a silently-disabled button. // PT: foca o título ao abrir; validação
    // inline em vez de botão desligado.
    val titleFocus = rememberAutoFocusRequester()
    var triedSubmit by remember { mutableStateOf(false) }

    fun submit() {
        if (title.isBlank()) { triedSubmit = true; return }
        onStart(title.trim(), selectedIntention, project.trim().ifEmpty { null }, targetMin.takeIf { it > 0 })
    }

    PautaSheet(title = tr("Novo bloco"), onClose = onClose) {
        if (hasActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.paper2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = trf("\"{t}\" será automaticamente pausado.", "t" to activeTitle),
                    color = colors.ink2,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        Text(tr("Em que vais focar?"), color = colors.ink, fontFamily = SerifFamily, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        UnderlineField(
            value = title,
            onChange = { title = it; selectedIntention = null },
            placeholder = tr("ex.: escrever capítulo 3"),
            modifier = Modifier.focusRequester(titleFocus),
            isError = triedSubmit && title.isBlank(),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (triedSubmit && title.isBlank()) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Escreve em que te vais focar."))
        }

        val open = intentions.filter { !it.done }
        if (open.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SheetEyebrow(tr("ou continue com…"))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                open.forEach { i ->
                    val sel = selectedIntention == i.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.paper2 else Color.Transparent)
                            .border(1.dp, if (sel) colors.accent else colors.rule, RoundedCornerShape(10.dp))
                            .clickableNoRipple {
                                selectedIntention = i.id
                                title = i.text
                                // Carry the intention's planned duration into the timer target.
                                i.targetMin?.let { if (it > 0) targetMin = it }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (sel) colors.accent else Color.Transparent)
                                .border(1.5.dp, if (sel) colors.accent else colors.ink3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sel) Box(Modifier.size(6.dp).clip(CircleShape).background(colors.paper))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(i.text, color = colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (recentBlocks.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SheetEyebrow(tr("retomar de antes"))
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recentBlocks.forEach { b ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                            .clickableNoRipple {
                                title = b.title
                                selectedIntention = null
                                project = b.project.orEmpty()
                                targetMin = b.targetMs?.let { (it / 60_000L).toInt() } ?: 0
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(b.title, color = colors.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        b.project?.let { Text(it, color = colors.ink3, fontFamily = MonoFamily, fontSize = 10.sp) }
                        b.targetMs?.takeIf { it > 0 }?.let {
                            Text(FocusMath.fmtDuration(it), color = colors.accent, fontFamily = MonoFamily, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SheetEyebrow(tr("projecto (opcional)"))
        Spacer(Modifier.height(10.dp))
        BoxedField(
            value = project,
            onChange = { project = it },
            placeholder = tr("ex.: Livro, Cliente X, Casa"),
            singleLine = true,
            fontFamily = SansFamily,
            fontSize = 14.sp,
        )
        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                projects.forEach { p ->
                    SelectPill(label = p, selected = project == p, accent = colors.accent) { project = p }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SheetEyebrow(tr("duração (opcional)"))
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            listOf(0 to tr("Sem limite"), 25 to "25 min", 50 to "50 min", 90 to "90 min").forEach { (m, label) ->
                SelectPill(label = label, selected = targetMin == m, accent = colors.accent, large = true) { targetMin = m }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Iniciar agora"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}

// ─── PAUSE SHEET ──────────────────────────────────────────
/** Optimistic: the block is ALREADY paused when this opens (no seconds lost
 *  while typing). Cancel/× = mis-click → [onResume]; Confirmar saves the note. */
@Composable
fun PauseSheet(
    block: FocusBlockEntity,
    onResume: () -> Unit,
    onConfirm: (note: String) -> Unit,
) {
    val colors = LocalPautaColors.current
    var note by remember { mutableStateOf("") }
    PautaSheet(title = tr("Pausar bloco"), onClose = onResume) {
        Text(tr("pausado"), color = colors.ink3, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(block.title, color = colors.ink, fontFamily = SerifFamily, fontSize = 22.sp, lineHeight = 26.sp)
        Spacer(Modifier.height(18.dp))

        SheetEyebrow(tr("O que ficou em mente? (opcional)"))
        Spacer(Modifier.height(8.dp))
        BoxedField(
            value = note,
            onChange = { note = it },
            placeholder = tr("ex.: travei no segundo parágrafo"),
            minHeight = 64.dp,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Retomar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onResume() }
            PautaButton(tr("Confirmar"), Modifier.weight(2f), PautaButtonVariant.InkPrimary) { onConfirm(note) }
        }
    }
}

// ─── CONCLUDE SHEET ───────────────────────────────────────
/** For active blocks the conclude is optimistic too ([wasActive]): cancel
 *  resumes the timer. Confirm saves reflection, optionally ticks the linked
 *  intention, and marks any picked tides done. */
@Composable
fun ConcludeSheet(
    block: FocusBlockEntity,
    totalMs: Long,
    intention: IntentionEntity?,
    todayTides: List<TideToday>,
    wasActive: Boolean,
    onConfirm: (reflection: String, markIntentionDone: Boolean, tideIds: List<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var reflection by remember { mutableStateOf(block.reflection) }
    var markDone by remember { mutableStateOf(true) }
    var tideIds by remember { mutableStateOf(listOf<String>()) }
    val planned = block.targetMs ?: 0L
    val deltaMs = totalMs - planned

    PautaSheet(title = tr("Concluir bloco"), onClose = onCancel) {
        Text(
            text = trf("✓ {d} em foco", "d" to FocusMath.fmtDuration(totalMs)).uppercase(),
            color = colors.accent,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(block.title, color = colors.ink, fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 29.sp)
        Spacer(Modifier.height(if (planned > 0) 8.dp else 18.dp))

        // Plan vs. actual — only when the block carried a planned duration.
        if (planned > 0) {
            Text(
                text = buildAnnotatedString {
                    append(trf("Planeado {p} · real {a}", "p" to FocusMath.fmtDuration(planned), "a" to FocusMath.fmtDuration(totalMs)))
                    append(" ")
                    withStyle(SpanStyle(color = if (deltaMs >= 0) colors.accent else colors.ink4)) {
                        append((if (deltaMs >= 0) "+" else "−") + FocusMath.fmtDuration(kotlin.math.abs(deltaMs)))
                    }
                },
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.5.sp,
                letterSpacing = 0.21.sp,
            )
            Spacer(Modifier.height(18.dp))
        }

        SheetEyebrow(tr("O que aconteceu?"))
        Spacer(Modifier.height(8.dp))
        BoxedField(
            value = reflection,
            onChange = { reflection = it },
            placeholder = tr("ex.: terminei o esqueleto. ficou faltando a conclusão."),
            minHeight = 84.dp,
        )

        if (intention != null && !intention.done) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                    .clickableNoRipple { markDone = !markDone }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (markDone) colors.accent else Color.Transparent)
                        .border(1.5.dp, if (markDone) colors.accent else colors.ink3, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (markDone) {
                        Icon(PautaIcons.Check, contentDescription = null, tint = colors.paper, modifier = Modifier.size(10.dp))
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        append(tr("marcar") + " ")
                        withStyle(SpanStyle(fontFamily = SerifFamily, fontStyle = FontStyle.Italic)) {
                            append("\"" + intention.text + "\"")
                        }
                        append(" " + tr("como concluído no Hoje"))
                    },
                    color = colors.ink2,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        // Mark a today's tide as done — a focus block often IS a tide (reading,
        // studying, exercising), so closing the loop here avoids logging twice.
        if (todayTides.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SheetEyebrow(tr("Marés de hoje"))
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                todayTides.forEach { t ->
                    val on = t.habit.id in tideIds
                    val col = t.habit.color
                        ?.takeIf { it.isNotBlank() }
                        ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                        ?: colors.accent
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) col.copy(alpha = 0.08f) else Color.Transparent)
                            .border(1.dp, if (on) col else colors.rule, RoundedCornerShape(999.dp))
                            .clickableNoRipple {
                                tideIds = if (on) tideIds - t.habit.id else tideIds + t.habit.id
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(
                            Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(if (on) col else Color.Transparent)
                                .border(1.5.dp, if (on) col else colors.ink3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(PautaIcons.Check, contentDescription = null, tint = colors.paper, modifier = Modifier.size(9.dp))
                        }
                        Text(t.habit.name, color = if (on) col else colors.ink2, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(
                if (wasActive) tr("Retomar") else tr("Cancelar"),
                Modifier.weight(1f),
                PautaButtonVariant.Ghost,
            ) { onCancel() }
            PautaButton(tr("Concluir"), Modifier.weight(2f), PautaButtonVariant.Primary) {
                onConfirm(reflection.trim(), markDone, tideIds)
            }
        }
    }
}

// ─── shared field atoms (also used by the Marés forms) ────

/** The start sheet's headline input: borderless with a 1.5dp ink underline (the
 *  underline + caret turn danger when [isError]). [imeAction]/[keyboardActions]
 *  let the field submit from the keyboard. */
@Composable
internal fun UnderlineField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 18.sp),
            cursorBrush = SolidColor(if (isError) DangerRed else colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = keyboardActions,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.ink4, fontFamily = SansFamily, fontSize = 18.sp)
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (isError) DangerRed else colors.ink))
    }
}

/** The sheets' boxed input: paper-2 surface, rule border, radius 10 — serif and
 *  multiline by default (the pause/conclude notes), sans single-line for the
 *  project field. The border + caret turn danger when [isError];
 *  [keyboardType]/[imeAction]/[keyboardActions] drive keyboard-only submission. */
@Composable
internal fun BoxedField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    fontFamily: androidx.compose.ui.text.font.FontFamily = SerifFamily,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalPautaColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = colors.ink, fontFamily = fontFamily, fontSize = fontSize, lineHeight = fontSize * 1.45),
        cursorBrush = SolidColor(if (isError) DangerRed else colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.paper2)
                    .border(1.dp, if (isError) DangerRed else colors.rule, RoundedCornerShape(10.dp))
                    .padding(horizontal = if (singleLine) 12.dp else 14.dp, vertical = if (singleLine) 10.dp else 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.ink4, fontFamily = fontFamily, fontSize = fontSize)
                }
                inner()
            }
        },
    )
}

// A6 (keyboard + validation pass): the shared error red for an invalid field's
// underline/border + hint — the web's --danger, a deliberate literal (not a theme
// token, like the rest of the danger copy). // PT: vermelho de erro da validação
// inline; literal deliberado, como o resto do texto destrutivo.
internal val DangerRed = Color(0xFFA8474A)

/** A [FocusRequester] that grabs focus once, just after the sheet's entrance — a
 *  form opens with its first field focused and the keyboard up, no tap needed.
 *  The short beat lets the bottom-sheet animation attach the node before we
 *  request (requesting mid-attach is silently dropped). // PT: foca o primeiro
 *  campo ao abrir, sem toque; a pausa breve deixa o nó montar primeiro. */
@Composable
internal fun rememberAutoFocusRequester(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    return fr
}

/** A small danger-tinted line under a field whose inline validation failed —
 *  shown instead of silently disabling the submit button. // PT: dica de erro por
 *  baixo do campo, em vez de desligar o botão. */
@Composable
internal fun FieldError(text: String) {
    Text(
        text = text,
        color = DangerRed,
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

/** A wrap-flow of selectable pills (projects, durations, tides). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

/** The web's outline pill toggle: rule border, accent border + accent-tinted
 *  background when selected. */
@Composable
internal fun SelectPill(label: String, selected: Boolean, accent: Color, large: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = 0.07f) else Color.Transparent)
            .border(1.dp, if (selected) accent else colors.rule, RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 7.dp else 4.dp),
    ) {
        Text(
            text = label,
            color = if (selected) accent else colors.ink2,
            fontSize = if (large) 13.sp else 12.sp,
        )
    }
}

// ─── SWITCH SHEET ──────────────────────────────────────────
/** "Trocar foco": pause the running block and jump to an open intention or to
 *  something new; or conclude the current block first. */
@Composable
fun SwitchSheet(
    currentBlock: FocusBlockEntity,
    intentions: List<IntentionEntity>,
    onPick: (linkedToId: String?, title: String) -> Unit,
    onConcludeFirst: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var adding by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    val available = intentions.filter { !it.done && it.id != currentBlock.linkedToId }
    // A6: revealing the inline "algo novo" field focuses it at once so it's
    // typeable without a second tap. // PT: ao abrir o campo novo, foca-o logo.
    val addFocus = remember { FocusRequester() }
    LaunchedEffect(adding) {
        if (adding) { delay(60); runCatching { addFocus.requestFocus() } }
    }

    PautaSheet(title = tr("Trocar foco"), onClose = onClose) {
        Text(
            text = tr("em curso →"),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = currentBlock.title,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 20.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(18.dp))

        SheetEyebrow(tr("Pausar e ir para…"))
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { i ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.paper)
                        .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                        .clickableNoRipple { onPick(i.id, i.text) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("›", color = colors.ink3, fontSize = 15.sp)
                    Text(i.text, color = colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
            if (adding) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.paper)
                        .border(1.dp, colors.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 15.sp),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (newTitle.isNotBlank()) onPick(null, newTitle.trim()) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(addFocus),
                            decorationBox = { inner ->
                                Box {
                                    if (newTitle.isEmpty()) {
                                        Text(tr("ex.: revisar PRs"), color = colors.ink4, fontSize = 15.sp)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    Text(
                        text = tr("OK"),
                        color = colors.paper,
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.88.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent.copy(alpha = if (newTitle.isBlank()) 0.4f else 1f))
                            .clickableNoRipple { if (newTitle.isNotBlank()) onPick(null, newTitle.trim()) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                        .clickableNoRipple { adding = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("+", color = colors.ink3, fontSize = 15.sp)
                    Text(tr("algo novo (não está no Hoje)"), color = colors.ink3, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple(onConcludeFirst)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(PautaIcons.Check, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(12.dp))
            Text(
                text = buildAnnotatedString {
                    append(tr("ou concluir") + " ")
                    withStyle(SpanStyle(fontFamily = SerifFamily)) { append("\"" + currentBlock.title + "\"") }
                    append(" " + tr("primeiro"))
                },
                color = colors.ink2,
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
        PautaButton(tr("Cancelar"), Modifier.fillMaxWidth(), PautaButtonVariant.Ghost) { onClose() }
    }
}

// ─── MANUAL BLOCK SHEET ────────────────────────────────────
/** "Registar tempo": log a past focus block by hand (forgot the timer). */
@Composable
fun ManualBlockSheet(
    today: String,
    onAdd: (title: String, startMs: Long, endMs: Long) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    // Default the start to the current time so the common "just now" case needs
    // no typing at all. // PT: arranca na hora actual para registo rápido.
    var start by remember {
        mutableStateOf(java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) })
    }
    var dur by remember { mutableStateOf("") }
    // A6: focus the "o quê" on open; Done there hops to the duration, Done on the
    // duration submits. Blank title / out-of-range duration show inline hints
    // rather than a silently-disabled button. // PT: foca o "o quê"; Seguir salta
    // para a duração; validação inline em vez de botão desligado.
    val titleFocus = rememberAutoFocusRequester()
    val durFocus = remember { FocusRequester() }
    var triedSubmit by remember { mutableStateOf(false) }

    val minutes = dur.toIntOrNull() ?: 0
    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) &&
        runCatching { java.time.LocalDate.parse(date) }.isSuccess
    val startOk = Regex("^\\d{1,2}:\\d{2}$").matches(start)
    val titleBad = title.isBlank()
    val durBad = minutes !in 1..1440
    val ready = !titleBad && dateOk && startOk && !durBad

    fun submit() {
        if (!ready) { triedSubmit = true; return }
        val (hh, mm) = start.split(":").map { it.toInt() }
        val startMs = java.time.LocalDate.parse(date)
            .atTime(hh.coerceIn(0, 23), mm.coerceIn(0, 59))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        onAdd(title.trim(), startMs, startMs + minutes * 60_000L)
        onClose()
    }

    PautaSheet(title = tr("Registar tempo"), onClose = onClose) {
        Text(
            text = tr("Esqueceu-se de iniciar o cronómetro? Registe o bloco à mão."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))

        SheetEyebrow(tr("O quê"))
        Spacer(Modifier.height(6.dp))
        BoxedField(
            title, { title = it }, tr("ex.: leitura"),
            modifier = Modifier.focusRequester(titleFocus),
            singleLine = true, fontFamily = SansFamily, fontSize = 15.sp,
            isError = triedSubmit && titleBad,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { durFocus.requestFocus() }),
        )
        if (triedSubmit && titleBad) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Escreva o que fez."))
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                SheetEyebrow(tr("Data"))
                Spacer(Modifier.height(6.dp))
                // Pick on a calendar — no free-typed YYYY-MM-DD.
                // // PT: escolhe-se num calendário; a data não se escreve à mão.
                PautaDateField(date) { date = it }
            }
            Column(Modifier.width(132.dp)) {
                SheetEyebrow(tr("Início"))
                Spacer(Modifier.height(6.dp))
                // Pick on a clock — no free-typed HH:MM.
                // // PT: escolhe-se num relógio; a hora não se escreve à mão.
                PautaTimeField(start, { start = it }, title = tr("Início"))
            }
        }

        Spacer(Modifier.height(14.dp))
        SheetEyebrow(tr("Duração (min)"))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(120.dp)) {
            BoxedField(
                dur,
                { raw -> dur = raw.filter { it.isDigit() }.take(4) },
                "45",
                modifier = Modifier.focusRequester(durFocus),
                singleLine = true, fontFamily = SansFamily, fontSize = 15.sp,
                isError = triedSubmit && durBad,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        if (triedSubmit && durBad) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Duração entre 1 e 1440 min."))
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Adicionar"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}

