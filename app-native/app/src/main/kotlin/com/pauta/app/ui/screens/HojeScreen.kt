package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Hoje (Today) tab — first interactive slice: the date + the question, a day
 * pulse, the priority-sorted intention list with add / toggle / cycle-priority /
 * delete, and the nightly reflection. Carry-over, the tide strip, week planner,
 * history and time-of-day grouping land in later increments toward full parity
 * with tab-hoje.jsx. // PT: primeira fatia interativa da tab Hoje.
 */
@Composable
fun HojeScreen() {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val intentions by vm.intentions.collectAsStateWithLifecycle()
    val reflection by vm.reflection.collectAsStateWithLifecycle()
    val carry by vm.carry.collectAsStateWithLifecycle()

    // Auto-sort by priority level (1 highest; unset sinks to 4), stable within a
    // level via stored position — matching the web list.
    val sorted = remember(intentions) {
        intentions.sortedWith(compareBy({ it.priority ?: 4 }, { it.position }))
    }
    val done = intentions.count { it.done }
    val total = intentions.size

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = localizedDate().replaceFirstChar { it.uppercase() },
            color = colors.ink3,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tr("O que importa hoje?"),
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 30.sp,
            lineHeight = 34.sp,
        )

        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = trf("{d}/{t} intenções", "d" to done, "t" to total),
                color = colors.ink3,
                fontSize = 13.sp,
            )
        }

        carry?.let { source ->
            Spacer(Modifier.height(16.dp))
            CarryBanner(source = source, onCarry = { vm.carryOver() })
        }

        Spacer(Modifier.height(18.dp))
        AddIntentionField(accent = colors.accent, onAdd = { vm.addIntention(it) })

        Spacer(Modifier.height(8.dp))
        sorted.forEach { item ->
            IntentionRow(
                item = item,
                onToggle = { vm.toggleIntention(item.id) },
                onDelete = { vm.removeIntention(item.id) },
                onCyclePriority = { vm.setIntentionPriority(item.id, nextPriority(item.priority)) },
            )
        }
        if (total == 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = tr("Ainda sem intenções para hoje."),
                color = colors.ink4,
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(36.dp))
        Text(
            text = tr("Reflexão da noite"),
            color = colors.ink3,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        ReflectionField(
            value = reflection,
            accent = colors.accent,
            onChange = { vm.setReflection(it) },
        )
        Spacer(Modifier.height(48.dp))
    }
}

private fun nextPriority(current: Int?): Int? = when (current) {
    null -> 1; 1 -> 2; 2 -> 3; else -> null
}

/** One-tap "bring forward" of the most recent past day's unfinished intentions. */
@Composable
private fun CarryBanner(source: CarrySource, onCarry: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.accentBg)
            .clickableNoRipple(onCarry)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = trf("Trazer {n} de {d}", "n" to source.items.size, "d" to shortDate(source.dayKey)),
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(text = "↓", color = colors.accent, fontSize = 16.sp)
    }
}

private fun shortDate(dayKey: String): String {
    val locale = if (I18n.lang == Lang.EN) Locale.ENGLISH else Locale("pt", "PT")
    return LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("d MMM", locale))
}

@Composable
private fun IntentionRow(
    item: IntentionEntity,
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
            modifier = Modifier
                .size(22.dp)
                .clickableNoRipple(onToggle),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text,
            color = if (item.done) colors.ink4 else colors.ink,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
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

@Composable
private fun AddIntentionField(accent: Color, onAdd: (String) -> Unit) {
    val colors = LocalPautaColors.current
    var text by remember { mutableStateOf("") }
    fun commit() {
        if (text.isNotBlank()) { onAdd(text); text = "" }
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(tr("Nova intenção…"), color = colors.ink4) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        colors = transparentFieldColors(accent, colors.ink),
    )
}

@Composable
private fun ReflectionField(value: String, accent: Color, onChange: (String) -> Unit) {
    val colors = LocalPautaColors.current
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(tr("O que valeu a pena hoje?"), color = colors.ink4) },
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        textStyle = TextStyle(color = colors.ink, fontSize = 16.sp, lineHeight = 24.sp),
        colors = transparentFieldColors(accent, colors.ink),
    )
}

/** A TextField with a transparent paper-coloured container and an accent caret/
 *  underline, so inputs read as part of the paper surface rather than Material
 *  boxes. */
@Composable
private fun transparentFieldColors(accent: Color, ink: Color) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    cursorColor = accent,
    focusedIndicatorColor = accent,
    unfocusedIndicatorColor = LocalPautaColors.current.rule,
    focusedTextColor = ink,
    unfocusedTextColor = ink,
)

private fun localizedDate(): String {
    val locale = if (I18n.lang == Lang.EN) Locale.ENGLISH else Locale("pt", "PT")
    return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale))
}
