package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily

/**
 * The Marés add/edit forms, to the web's spec: [AddHabitSheet] mirrors
 * tab-mares.jsx NewHabitForm (nome + quando collapsed, "+ mais opções" reveals
 * descrição, hora certa, frequência with the escolho-eu/dia-fixo anchor,
 * dias da semana, meta com quantidade and recorrência) and [EditHabitSheet]
 * mirrors mares-sheets.jsx HabitEditForm, adding the colour swatches.
 * // PT: formulários de criar/editar maré, iguais aos da web.
 */

/** The web's HABIT_COLORS palette (mares-sheets.jsx) — earthy tones readable on
 *  both themes. null = follow the app accent ("automático"). */
internal val HABIT_COLORS = listOf(
    "#B8533A", "#5A6B3E", "#3D5A80", "#8E5A8E", "#C08A2D", "#3F8E7F", "#A8474A", "#52607A",
)

/** What NewHabitForm hands back on submit — the web onSubmit payload. */
data class HabitDraft(
    val name: String,
    val time: String,
    val clock: String,
    val description: String,
    val recurrence: String,
    val endsAt: Long?,
    val cadence: String,
    val anchor: Int?,
    val weekdays: List<Int>,
    val target: Int?,
    val unit: String,
)

// JS weekday order as the web shows it: seg…sáb, dom last (0=Sun … 6=Sat).
private val WEEKDAY_PILLS = listOf(1 to "seg", 2 to "ter", 3 to "qua", 4 to "qui", 5 to "sex", 6 to "sáb", 0 to "dom")

/** Blank is fine (no exact time); otherwise must read as a real HH:MM. */
private fun clockOk(clock: String): Boolean {
    if (clock.isBlank()) return true
    if (!Regex("^\\d{1,2}:\\d{2}$").matches(clock)) return false
    return clock.substringBefore(":").toInt() in 0..23 && clock.substringAfter(":").toInt() in 0..59
}

// ─── ADD ───────────────────────────────────────────────────

@Composable
fun AddHabitSheet(onSubmit: (HabitDraft) -> Unit, onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("forever") }
    var periodDays by remember { mutableStateOf("30") }
    var cadence by remember { mutableStateOf("daily") }
    var dayMode by remember { mutableStateOf("manual") }
    var weekday by remember { mutableIntStateOf(1) }
    var monthday by remember { mutableStateOf("1") }
    var countable by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("3") }
    var unit by remember { mutableStateOf("") }
    var weekdays by remember { mutableStateOf(listOf<Int>()) }
    var expanded by remember { mutableStateOf(false) }
    // A6: name is focused on open and Done on it submits; the first blank submit
    // flips [triedSubmit] so the underline + hint turn danger rather than leaving
    // a dead button. // PT: foca o nome ao abrir; validação inline.
    val nameFocus = rememberAutoFocusRequester()
    var triedSubmit by remember { mutableStateOf(false) }

    fun submit() {
        if (name.isBlank() || !clockOk(clock.trim())) { triedSubmit = true; return }
        val days = (periodDays.toIntOrNull() ?: 1).coerceAtLeast(1)
        val mday = (monthday.toIntOrNull() ?: 1).coerceIn(1, 31)
        val endsAt = if (recurrence == "period") System.currentTimeMillis() + (days - 1) * 86_400_000L else null
        val anchor = if (dayMode == "fixed") {
            when (cadence) { "weekly" -> weekday; "monthly" -> mday; else -> null }
        } else {
            null
        }
        val isCountable = countable && cadence == "daily"
        onSubmit(
            HabitDraft(
                name = name.trim(), time = time.trim(), clock = clock.trim(),
                description = description.trim(), recurrence = recurrence, endsAt = endsAt,
                cadence = cadence, anchor = anchor,
                weekdays = if (cadence == "daily") weekdays.sorted() else emptyList(),
                target = if (isCountable) (target.toIntOrNull() ?: 2).coerceAtLeast(2) else null,
                unit = if (isCountable) unit.trim() else "",
            ),
        )
    }

    PautaSheet(title = tr("Nova maré"), onClose = onClose) {
        UnderlineField(
            name, { name = it }, tr("Nome da maré (ex.: meditar)"),
            modifier = Modifier.focusRequester(nameFocus),
            isError = triedSubmit && name.isBlank(),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (triedSubmit && name.isBlank()) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Dá um nome à maré."))
        }
        Spacer(Modifier.height(14.dp))
        BoxedField(time, { time = it }, tr("Quando? (opcional, ex.: manhã)"), singleLine = true)

        if (!expanded) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = tr("+ mais opções (descrição, recorrência)"),
                color = colors.ink3,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                modifier = Modifier.clickableNoRipple { expanded = true },
            )
        } else {
            Spacer(Modifier.height(14.dp))
            BoxedField(description, { description = it }, tr("Porquê esta maré? (opcional)"), minHeight = 64.dp)

            Spacer(Modifier.height(18.dp))
            SheetEyebrow(tr("hora certa") + " · " + tr("opcional"))
            Spacer(Modifier.height(8.dp))
            ClockField(clock) { clock = it }

            Spacer(Modifier.height(18.dp))
            SheetEyebrow(tr("frequência"))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectPill(tr("diária"), cadence == "daily", colors.accent, large = true) { cadence = "daily" }
                SelectPill(tr("semanal"), cadence == "weekly", colors.accent, large = true) { cadence = "weekly" }
                SelectPill(tr("mensal"), cadence == "monthly", colors.accent, large = true) { cadence = "monthly" }
            }

            if (cadence == "daily") {
                Spacer(Modifier.height(16.dp))
                SheetEyebrow(tr("dias da semana"))
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    WEEKDAY_PILLS.forEach { (wd, label) ->
                        SelectPill(tr(label), wd in weekdays, colors.accent) {
                            weekdays = if (wd in weekdays) weekdays - wd else weekdays + wd
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FormHint(if (weekdays.isEmpty()) tr("todos os dias") else tr("só nos dias escolhidos"))

                Spacer(Modifier.height(16.dp))
                CheckRow(countable, tr("uma meta com quantidade (ex.: 2L de água, 3 treinos)")) {
                    countable = !countable
                }
                if (countable) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(72.dp)) { NumberField(target, max = 2) { target = it } }
                        Text(tr("×"), color = colors.ink3, fontFamily = MonoFamily, fontSize = 13.sp)
                        Box(Modifier.weight(1f)) {
                            BoxedField(unit, { unit = it }, tr("unidade (copos, treinos…)"), singleLine = true)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectPill(tr("escolho eu"), dayMode == "manual", colors.accent, large = true) { dayMode = "manual" }
                    SelectPill(tr("dia fixo"), dayMode == "fixed", colors.accent, large = true) { dayMode = "fixed" }
                }
                if (dayMode == "fixed") {
                    Spacer(Modifier.height(10.dp))
                    if (cadence == "weekly") {
                        ChipFlow {
                            WEEKDAY_PILLS.forEach { (wd, label) ->
                                SelectPill(tr(label), weekday == wd, colors.accent) { weekday = wd }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("dia"), color = colors.ink2, fontSize = 13.sp)
                            Box(Modifier.width(72.dp)) { NumberField(monthday, max = 2) { monthday = it } }
                            Text(tr("do mês"), color = colors.ink2, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FormHint(
                    if (dayMode == "fixed") {
                        tr("Repete sempre no mesmo dia. Os outros dias do período ficam bloqueados.")
                    } else {
                        tr("Marca um dia qualquer do período. Depois disso, os restantes ficam bloqueados.")
                    },
                )
            }

            Spacer(Modifier.height(18.dp))
            SheetEyebrow(tr("recorrência"))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RecurrenceOption(tr("permanente"), tr("todos os dias, sem fim"), recurrence == "forever") { recurrence = "forever" }
                RecurrenceOption(tr("por um período"), tr("durante X dias"), recurrence == "period") { recurrence = "period" }
                RecurrenceOption(tr("só este mês"), tr("termina no fim do mês"), recurrence == "month") { recurrence = "month" }
            }
            if (recurrence == "period") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("durante"), color = colors.ink2, fontSize = 13.sp)
                    Box(Modifier.width(72.dp)) { NumberField(periodDays, max = 3) { periodDays = it } }
                    Text(tr("dias"), color = colors.ink2, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Adicionar"), Modifier.weight(2f), PautaButtonVariant.Primary) { submit() }
        }
    }
}

// ─── EDIT ──────────────────────────────────────────────────

@Composable
fun EditHabitSheet(
    habit: HabitEntity,
    onSave: (HabitEntity) -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    var name by remember { mutableStateOf(habit.name) }
    var time by remember { mutableStateOf(habit.time) }
    var clock by remember { mutableStateOf(habit.clock) }
    var description by remember { mutableStateOf(habit.description) }
    var color by remember { mutableStateOf(habit.color) }
    var recurrence by remember { mutableStateOf(habit.recurrence) }
    var countable by remember { mutableStateOf(habit.target != null) }
    var target by remember { mutableStateOf((habit.target ?: 3).toString()) }
    var unit by remember { mutableStateOf(habit.unit) }
    // Web: rebuilt from endsAt so the "durante N dias" field round-trips.
    var periodDays by remember {
        mutableStateOf(
            (habit.endsAt?.let { maxOf(1, Math.round((it - habit.createdAt) / 86_400_000.0).toInt() + 1) } ?: 30).toString(),
        )
    }
    // A6: inline validation instead of a silently-disabled "Guardar" — a blank
    // name flips this and turns the underline + hint danger. (No autofocus: this
    // is an edit of a pre-filled form.) // PT: validação inline, sem auto-foco.
    var triedSubmit by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank() || !clockOk(clock.trim())) { triedSubmit = true; return }
        val days = (periodDays.toIntOrNull() ?: 1).coerceAtLeast(1)
        // Web: period re-anchors on createdAt; forever and month clear endsAt
        // (month ends via habitEndKey, not a timestamp).
        val endsAt = if (recurrence == "period") habit.createdAt + (days - 1) * 86_400_000L else null
        val isCountable = countable && habit.cadence == "daily"
        onSave(
            habit.copy(
                name = name.trim(), time = time.trim(), clock = clock.trim(),
                description = description.trim(), recurrence = recurrence, endsAt = endsAt,
                color = color,
                target = if (isCountable) (target.toIntOrNull() ?: 2).coerceAtLeast(2) else null,
                unit = if (isCountable) unit.trim() else "",
            ),
        )
    }

    PautaSheet(title = tr("Editar maré"), onClose = onClose) {
        SheetEyebrow(tr("nome"))
        Spacer(Modifier.height(8.dp))
        UnderlineField(
            name, { name = it }, tr("Nome da maré (ex.: meditar)"),
            isError = triedSubmit && name.isBlank(),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        if (triedSubmit && name.isBlank()) {
            Spacer(Modifier.height(6.dp))
            FieldError(tr("Dá um nome à maré."))
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("Cor"))
        Spacer(Modifier.height(8.dp))
        ColorSwatchRow(selected = color, onPick = { color = it })

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("quando"))
        Spacer(Modifier.height(8.dp))
        BoxedField(time, { time = it }, tr("ex.: manhã, antes de dormir"), singleLine = true)

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("hora certa") + " · " + tr("opcional"))
        Spacer(Modifier.height(8.dp))
        ClockField(clock) { clock = it }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("descrição · porquê esta maré?"))
        Spacer(Modifier.height(8.dp))
        BoxedField(description, { description = it }, tr("A intenção, o motivo, o sentimento que quer cultivar."), minHeight = 64.dp)

        if (habit.cadence == "daily") {
            Spacer(Modifier.height(16.dp))
            CheckRow(countable, tr("meta com quantidade (ex.: 2L de água, 3 treinos)")) { countable = !countable }
            if (countable) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(72.dp)) { NumberField(target, max = 2) { target = it } }
                    Text(tr("×"), color = colors.ink3, fontFamily = MonoFamily, fontSize = 13.sp)
                    Box(Modifier.weight(1f)) {
                        BoxedField(unit, { unit = it }, tr("unidade (copos, treinos…)"), singleLine = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SheetEyebrow(tr("recorrência"))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RecurrenceOption(tr("permanente"), tr("todos os dias, sem fim"), recurrence == "forever") { recurrence = "forever" }
            RecurrenceOption(tr("por um período"), tr("durante X dias"), recurrence == "period") { recurrence = "period" }
            RecurrenceOption(tr("só este mês"), tr("termina no fim do mês"), recurrence == "month") { recurrence = "month" }
        }
        if (recurrence == "period") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("durante"), color = colors.ink2, fontSize = 13.sp)
                Box(Modifier.width(72.dp)) { NumberField(periodDays, max = 3) { periodDays = it } }
                Text(tr("dias"), color = colors.ink2, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onClose() }
            PautaButton(tr("Guardar"), Modifier.weight(2f), PautaButtonVariant.Primary) { save() }
        }
        // A7: the safe alternative to delete sits first — archiving hides the tide
        // but keeps every mark; "remover" below is the destructive path. // PT:
        // arquivar (guarda o histórico) antes de remover (apaga tudo).
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (habit.archived) tr("restaurar maré") else tr("arquivar maré"),
            color = colors.ink2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple(onArchive)
                .padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tr("Arquivar esconde a maré da grelha sem perder o histórico."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = tr("remover esta maré"),
            color = DangerRed,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple(onRemove)
                .padding(vertical = 4.dp),
        )
    }
}

// ─── form atoms ────────────────────────────────────────────

// The web's --danger (removal copy, invalid-field hints) lives as [DangerRed] in
// PautaSheets.kt now, shared by the validation pass. // PT: o vermelho de erro é
// partilhado a partir de PautaSheets.kt.

/** Tap to pick the exact time on a clock; clearable since the time is optional.
 *  The stored value stays "HH:MM" (or blank). // PT: escolhe-se a hora no
 *  relógio; pode ficar vazia. */
@Composable
private fun ClockField(value: String, onChange: (String) -> Unit) {
    PautaTimeField(value = value, onChange = onChange, title = tr("hora certa"), optional = true)
}

/** Digits-only boxed input ([max] = digit count, e.g. 2 → "31"). */
@Composable
private fun NumberField(value: String, max: Int, onChange: (String) -> Unit) {
    BoxedField(
        value = value,
        onChange = { raw -> onChange(raw.filter { it.isDigit() }.take(max)) },
        placeholder = "",
        singleLine = true,
        fontFamily = MonoFamily,
    )
}

/** The serif-italic helper line under a control group. */
@Composable
private fun FormHint(text: String) {
    Text(
        text = text,
        color = LocalPautaColors.current.ink3,
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/** The web's square checkbox + label ("uma meta com quantidade…"). */
@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickableNoRipple(onToggle),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) colors.accent else Color.Transparent)
                .border(1.5.dp, if (checked) colors.accent else colors.ink3, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(PautaIcons.Check, contentDescription = null, tint = colors.onDark, modifier = Modifier.size(10.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.ink2, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

/** One recurrence choice: label + serif hint, accent-bordered when selected. */
@Composable
private fun RecurrenceOption(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.07f) else Color.Transparent)
            .border(1.dp, if (selected) colors.accent else colors.rule, RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (selected) colors.accent else colors.ink,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.height(1.dp))
        Text(hint, color = colors.ink3, fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontSize = 11.sp)
    }
}

/** "automático" (dashed, follows the accent) + the eight fixed swatches. */
@Composable
private fun ColorSwatchRow(selected: String?, onPick: (String?) -> Unit) {
    val colors = LocalPautaColors.current
    ChipFlow {
        Swatch(col = colors.accent, selected = selected == null, dashed = true) { onPick(null) }
        HABIT_COLORS.forEach { hex ->
            val col = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
            Swatch(col = col, selected = selected == hex) { onPick(hex) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = if (selected == null) tr("automático") else selected.lowercase(),
        color = colors.ink3,
        fontFamily = MonoFamily,
        fontSize = 9.sp,
        letterSpacing = 0.72.sp,
    )
}

@Composable
private fun Swatch(col: Color, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .size(28.dp)
            // The selection halo sits just outside the circle, like the web's
            // box-shadow ring.
            .drawBehind {
                if (selected) {
                    drawCircle(color = col.copy(alpha = 0.35f), radius = size.minDimension / 2 + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .background(col)
            .drawBehind {
                if (dashed) {
                    drawCircle(
                        color = colors.paper,
                        radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                }
            }
            .then(if (!dashed) Modifier.border(1.dp, colors.rule, CircleShape) else Modifier)
            .clickableNoRipple(onClick),
    )
}
