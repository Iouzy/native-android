package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaIcons
import com.pauta.app.ui.SheetEyebrow
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SansFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Native date/time entry for the sheets (task A4). Material3 `DatePicker` /
 * `TimePicker`, skinned with [LocalPautaColors], sit behind tappable boxed
 * fields so the user never free-types a `YYYY-MM-DD` or an `HH:MM`. The fields
 * read and emit those exact string formats, so the repository and the pauta.v4
 * backup layers are untouched. // PT: selectores nativos de data/hora — nunca se
 * escreve a data nem a hora à mão; os formatos guardados ficam idênticos.
 */

// ─── tappable fields ──────────────────────────────────────

/** A boxed field that opens a calendar. [value]/[onChange] speak the stored
 *  `YYYY-MM-DD` day key; the face shows it in the app's own date voice. */
@Composable
internal fun PautaDateField(value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PickerFace(text = displayDate(value), placeholder = "AAAA-MM-DD", fontFamily = SansFamily) { show = true }
    if (show) {
        val initial = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        PautaDatePickerDialog(
            initial = initial,
            onConfirm = { onChange(it.toString()); show = false },
            onDismiss = { show = false },
        )
    }
}

/** A boxed field that opens a clock. [value]/[onChange] speak the stored `HH:MM`
 *  (24h); when [optional] the clock offers "limpar" to clear it back to blank.
 *  [title] is the picker's mono eyebrow (e.g. "Início", "hora certa"). */
@Composable
internal fun PautaTimeField(
    value: String,
    onChange: (String) -> Unit,
    title: String,
    optional: Boolean = false,
    placeholder: String = "08:30",
) {
    var show by remember { mutableStateOf(false) }
    PickerFace(text = value, placeholder = placeholder, fontFamily = MonoFamily, icon = PautaIcons.Pauta) { show = true }
    if (show) {
        val (h, m) = parseHhmm(value) ?: (8 to 0)
        PautaTimePickerDialog(
            title = title,
            initialHour = h,
            initialMinute = m,
            showClear = optional && value.isNotBlank(),
            onConfirm = { hh, mm -> onChange("%02d:%02d".format(hh, mm)); show = false },
            onClear = { onChange(""); show = false },
            onDismiss = { show = false },
        )
    }
}

/** The shared face: a paper-2 boxed field (matching [BoxedField]) that is tapped
 *  to open a picker rather than typed into. Empty value shows the placeholder. */
@Composable
private fun PickerFace(
    text: String,
    placeholder: String,
    fontFamily: FontFamily,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.paper2)
            .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text.ifEmpty { placeholder },
            color = if (text.isEmpty()) colors.ink4 else colors.ink,
            fontFamily = fontFamily,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── dialogs ──────────────────────────────────────────────

/** Material3 calendar in a paper dialog. `showModeToggle = false` keeps the
 *  keyboard-entry path off, so there is no free-text date route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PautaDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The DatePicker selection is UTC-midnight millis; convert through UTC on
    // both sides so the chosen calendar day round-trips whatever the device
    // zone. // PT: o DatePicker fala em millis UTC — convertemos em UTC nos dois
    // sentidos para a data não escorregar um dia.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val pickerColors = pautaDatePickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        tonalElevation = 0.dp,
        colors = pickerColors,
        confirmButton = {
            PautaButton(tr("Confirmar"), variant = PautaButtonVariant.Primary) {
                val ms = state.selectedDateMillis
                onConfirm(if (ms != null) Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate() else initial)
            }
        },
        dismissButton = {
            PautaButton(tr("Cancelar"), variant = PautaButtonVariant.Ghost) { onDismiss() }
        },
    ) {
        DatePicker(
            state = state,
            title = null,            // drop the generic "select date" line
            showModeToggle = false,  // no keyboard toggle → no free-text path
            colors = pickerColors,
        )
    }
}

/** Material3 24h clock in a paper-tinted dialog. [showClear] adds a "limpar"
 *  action for the optional habit time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PautaTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    showClear: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = colors.paper) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SheetEyebrow(title)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state, colors = pautaTimePickerColors())
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PautaButton(tr("Cancelar"), Modifier.weight(1f), PautaButtonVariant.Ghost) { onDismiss() }
                    PautaButton(tr("Confirmar"), Modifier.weight(1f), PautaButtonVariant.Primary) {
                        onConfirm(state.hour, state.minute)
                    }
                }
                // Optional times (a habit's "hora certa") can be unset — a quiet
                // mono link below the pair, like the sheets' destructive links.
                // // PT: horas opcionais podem ficar vazias — ligação discreta.
                if (showClear) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tr("limpar"),
                        color = colors.ink3,
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.88.sp,
                        modifier = Modifier
                            .clickableNoRipple { onClear() }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// ─── token skins ──────────────────────────────────────────
// MaterialTheme only maps a handful of roles, so the pickers' *container* tints
// (year/time-selector boxes) would otherwise fall back to stock M3 colours.
// Skin the visible surfaces explicitly with Pauta tokens. // PT: pinta-se cada
// superfície visível com os tokens da Pauta.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pautaDatePickerColors(): DatePickerColors {
    val c = LocalPautaColors.current
    return DatePickerDefaults.colors(
        containerColor = c.paper,
        headlineContentColor = c.ink,
        weekdayContentColor = c.ink3,
        subheadContentColor = c.ink3,
        navigationContentColor = c.ink2,
        yearContentColor = c.ink2,
        currentYearContentColor = c.accent,
        selectedYearContentColor = c.onDark,
        selectedYearContainerColor = c.accent,
        dayContentColor = c.ink,
        selectedDayContentColor = c.onDark,
        selectedDayContainerColor = c.accent,
        todayContentColor = c.accent,
        todayDateBorderColor = c.accent,
        dividerColor = c.rule,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pautaTimePickerColors(): TimePickerColors {
    val c = LocalPautaColors.current
    return TimePickerDefaults.colors(
        clockDialColor = c.paper2,
        clockDialSelectedContentColor = c.onDark,
        clockDialUnselectedContentColor = c.ink,
        selectorColor = c.accent,
        containerColor = c.paper,
        timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.12f),
        timeSelectorUnselectedContainerColor = c.paper2,
        timeSelectorSelectedContentColor = c.accent,
        timeSelectorUnselectedContentColor = c.ink,
    )
}

// ─── helpers ──────────────────────────────────────────────

/** "quinta, 12 jun" (+ year when it isn't the current one), or the raw key if it
 *  doesn't parse. Display only — the stored value stays `YYYY-MM-DD`. */
private fun displayDate(key: String): String {
    val d = runCatching { LocalDate.parse(key) }.getOrNull() ?: return key
    val base = I18n.fmtDateLong(d)
    return if (d.year == LocalDate.now().year) base else "$base ${d.year}"
}

/** Parse "H:MM"/"HH:MM" → hour/minute to seed the clock, or null when blank or
 *  out of range (then the clock opens at a neutral default). */
private fun parseHhmm(s: String): Pair<Int, Int>? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h to min else null
}
