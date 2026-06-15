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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.pauta.app.data.entity.RoutineEntity
import com.pauta.app.data.entity.RoutineItemEntity
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaButton
import com.pauta.app.ui.PautaButtonVariant
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaColors
import com.pauta.app.ui.theme.SansFamily
import com.pauta.app.ui.theme.SerifFamily

/**
 * D1 · Routines UI — the manager for `RoutineEntity`/`RoutineItemEntity`, which
 * existed in Room (and round-trip in the v4 backup) but had no surface. Reached
 * from a "Rotinas" chip in the Hoje header, so creating, editing and — above all
 * — *applying* a routine all happen next to today's intentions.
 *
 * A routine is a reusable template of intentions; its items carry only the
 * planning fields (text + optional priority/targetMin). "Aplicar" seeds today
 * with fresh intentions (preserving priority/targetMin, appended after existing
 * ones — the carry-over path); "guardar como rotina" snapshots the current day.
 * // PT: gestor de rotinas — modelos de intenções; aplicar semeia o dia.
 */
@Composable
fun RoutinesSheet(
    routines: List<RoutineEntity>,
    items: List<RoutineItemEntity>,
    todayHasIntentions: Boolean,
    onApply: (routineId: String) -> Unit,
    onCreate: (name: String) -> Unit,
    onSaveFromToday: (name: String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onAddItem: (routineId: String, text: String) -> Unit,
    onUpdateItem: (rowId: Long, text: String, priority: Int?, targetMin: Int?) -> Unit,
    onRemoveItem: (rowId: Long) -> Unit,
    onReorderItems: (routineId: String, orderedRowIds: List<Long>) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val itemsByRoutine = remember(items) { items.groupBy { it.routineId } }
    // Capture emptiness at open so the create field only auto-focuses for a
    // first-time user (an existing user usually opens to apply, not type).
    // // PT: foca o campo de criar só quando ainda não há rotinas.
    val startedEmpty = remember { routines.isEmpty() }

    PautaSheet(title = tr("Rotinas"), onClose = onClose) {
        Text(
            text = tr("Modelos de intenções. Aplique uma para semear o dia ou guarde o dia atual como rotina."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))

        CreateRoutineSection(
            todayHasIntentions = todayHasIntentions,
            autoFocus = startedEmpty,
            onCreate = onCreate,
            onSaveFromToday = onSaveFromToday,
        )

        Spacer(Modifier.height(20.dp))

        if (routines.isEmpty()) {
            Text(tr("Sem rotinas ainda."), color = colors.ink4, fontSize = 14.sp)
        } else {
            routines.forEachIndexed { index, routine ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                RoutineCard(
                    routine = routine,
                    items = itemsByRoutine[routine.id].orEmpty().sortedBy { it.position },
                    onApply = { onApply(routine.id) },
                    onRename = { onRename(routine.id, it) },
                    onDelete = { onDelete(routine.id) },
                    onAddItem = { onAddItem(routine.id, it) },
                    onUpdateItem = onUpdateItem,
                    onRemoveItem = onRemoveItem,
                    onReorder = { orderedIds -> onReorderItems(routine.id, orderedIds) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One name field feeding two actions: "criar" (empty routine) and, when today
 *  has intentions, "guardar como rotina" (snapshot the day). // PT: um campo de
 *  nome, duas acções — criar vazia ou guardar o dia. */
@Composable
private fun CreateRoutineSection(
    todayHasIntentions: Boolean,
    autoFocus: Boolean,
    onCreate: (String) -> Unit,
    onSaveFromToday: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // The short beat lets the sheet's entrance attach the node before we request
    // (mirrors rememberAutoFocusRequester). // PT: pausa breve antes de focar.
    LaunchedEffect(Unit) { if (autoFocus) { delay(120); runCatching { focus.requestFocus() } } }
    val canSubmit = name.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        DashedInput(
            value = name,
            onValue = { name = it },
            placeholder = tr("nome da rotina…"),
            focusRequester = focus,
            onDone = { if (canSubmit) { onCreate(name.trim()); name = "" } },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PautaButton(
                label = tr("criar"),
                variant = PautaButtonVariant.Primary,
                enabled = canSubmit,
            ) { onCreate(name.trim()); name = "" }
            if (todayHasIntentions) {
                PautaButton(
                    label = tr("guardar como rotina"),
                    variant = PautaButtonVariant.Ghost,
                    enabled = canSubmit,
                ) { onSaveFromToday(name.trim()); name = "" }
            }
        }
    }
}

/** A routine: editable name, item count, "aplicar", its items (each editable +
 *  reorderable), an add-item field, and a two-step delete. */
@Composable
private fun RoutineCard(
    routine: RoutineEntity,
    items: List<RoutineItemEntity>,
    onApply: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddItem: (String) -> Unit,
    onUpdateItem: (rowId: Long, text: String, priority: Int?, targetMin: Int?) -> Unit,
    onRemoveItem: (rowId: Long) -> Unit,
    onReorder: (orderedRowIds: List<Long>) -> Unit,
) {
    val colors = LocalPautaColors.current
    // A brief "aplicada ✓" after a tap, so the apply registers even though the
    // new intentions land on Hoje behind the sheet. // PT: confirmação breve.
    var applied by remember { mutableStateOf(false) }
    LaunchedEffect(applied) { if (applied) { delay(1500); applied = false } }
    var confirmingDelete by remember { mutableStateOf(false) }
    // Hoisted above the items loop so its remember slot is stable as items are
    // added/removed (positional memoization). // PT: estado do campo, acima do ciclo.
    var draft by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.rule, RoundedCornerShape(14.dp))
            .background(colors.paper2)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoutineNameField(
                name = routine.name,
                onCommit = onRename,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = items.size.toString(),
                color = colors.ink4,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(14.dp))
            if (applied) {
                Text(
                    text = tr("aplicada") + " ✓",
                    color = colors.accent,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    text = tr("aplicar"),
                    color = if (items.isEmpty()) colors.ink4 else colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickableNoRipple {
                        if (items.isNotEmpty()) { onApply(); applied = true }
                    },
                )
            }
        }

        if (items.isNotEmpty()) Spacer(Modifier.height(8.dp))
        items.forEachIndexed { i, item ->
            // Keyed by rowId so each row keeps its edit state across reorders.
            // // PT: chave por rowId — o estado segue a linha ao reordenar.
            key(item.rowId) {
                RoutineItemRow(
                    item = item,
                    first = i == 0,
                    last = i == items.lastIndex,
                    onUpdate = { text, priority, targetMin -> onUpdateItem(item.rowId, text, priority, targetMin) },
                    onRemove = { onRemoveItem(item.rowId) },
                    onUp = { onReorder(moved(items, i, i - 1)) },
                    onDown = { onReorder(moved(items, i, i + 1)) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        DashedInput(
            value = draft,
            onValue = { draft = it },
            placeholder = tr("adicionar intenção…"),
            onDone = { if (draft.isNotBlank()) { onAddItem(draft.trim()); draft = "" } },
        )

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule.copy(alpha = 0.6f)))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!confirmingDelete) {
                Text(
                    text = tr("remover"),
                    color = DangerRed,
                    fontSize = 13.sp,
                    modifier = Modifier.clickableNoRipple { confirmingDelete = true },
                )
            } else {
                // The second tap confirms — a routine is never deleted in one tap
                // (A7's no-single-tap-destruction ethos). // PT: segundo toque confirma.
                Text(
                    text = tr("Cancelar"),
                    color = colors.ink3,
                    fontSize = 13.sp,
                    modifier = Modifier.clickableNoRipple { confirmingDelete = false },
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    text = tr("Apagar"),
                    color = DangerRed,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.clickableNoRipple(onDelete),
                )
            }
        }
    }
}

/** An item row: reorder arrows, editable text, a priority cycle dot, an optional
 *  target-minutes field and a remove ×. Text/target commit on Done or focus-loss
 *  (not per keystroke); priority commits on each tap. // PT: linha de item. */
@Composable
private fun RoutineItemRow(
    item: RoutineItemEntity,
    first: Boolean,
    last: Boolean,
    onUpdate: (text: String, priority: Int?, targetMin: Int?) -> Unit,
    onRemove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val colors = LocalPautaColors.current
    // Local edit state, re-seeded only when the underlying row changes identity
    // (rowId). // PT: estado local; só re-semeia quando muda a linha.
    var text by remember(item.rowId) { mutableStateOf(item.text) }
    var priority by remember(item.rowId) { mutableStateOf(item.priority) }
    var target by remember(item.rowId) { mutableStateOf(item.targetMin?.toString() ?: "") }

    fun commit() = onUpdate(text, priority, target.toIntOrNull()?.takeIf { it > 0 })

    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Reorder ↑/↓ stacked to save width; dimmed + inert at the ends.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ReorderArrow("↑", enabled = !first, cd = tr("subir"), onClick = onUp)
            ReorderArrow("↓", enabled = !last, cd = tr("descer"), onClick = onDown)
        }
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .weight(1f)
                .commitOnFocusLost { commit() },
        )
        Spacer(Modifier.width(6.dp))
        // Priority dot — tap cycles 1 → 2 → 3 → none (matching the Hoje row).
        Box(
            Modifier.size(26.dp).clickableNoRipple { priority = nextPriority(priority); commit() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = priority?.toString() ?: "·",
                color = priorityColor(priority, colors),
                fontWeight = if (priority != null) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
            )
        }
        // Target minutes — compact; placeholder doubles as the "min" hint.
        Box(Modifier.width(40.dp)) {
            BasicTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() }.take(3) },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontFamily = MonoFamily, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.commitOnFocusLost { commit() },
                decorationBox = { inner ->
                    if (target.isEmpty()) {
                        Text(tr("min"), color = colors.ink4, fontFamily = MonoFamily, fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "×",
            color = colors.ink4,
            fontFamily = MonoFamily,
            fontSize = 15.sp,
            modifier = Modifier.clickableNoRipple(onRemove).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ReorderArrow(glyph: String, enabled: Boolean, cd: String, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Text(
        text = glyph,
        color = if (enabled) colors.ink3 else colors.ink4.copy(alpha = 0.35f),
        fontFamily = MonoFamily,
        fontSize = 12.sp,
        modifier = Modifier
            .size(width = 18.dp, height = 16.dp)
            .semantics { contentDescription = cd }
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
    )
}

/** The routine title as an inline field — commits a non-blank rename on Done or
 *  focus-loss. // PT: nome da rotina editável; grava ao sair do campo. */
@Composable
private fun RoutineNameField(name: String, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPautaColors.current
    var text by remember(name) { mutableStateOf(name) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = colors.ink, fontFamily = SerifFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onCommit(text.trim()) }),
        modifier = modifier.commitOnFocusLost { if (text.isNotBlank()) onCommit(text.trim()) },
        decorationBox = { inner ->
            if (text.isEmpty()) Text(tr("nome da rotina…"), color = colors.ink4, fontFamily = SerifFamily, fontSize = 17.sp)
            inner()
        },
    )
}

/** A dashed-bordered single-line input (the add-field idiom from WeekAheadSheet),
 *  committing on the IME Done action. // PT: campo tracejado, grava no "concluir". */
@Composable
private fun DashedInput(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit,
) {
    val colors = LocalPautaColors.current
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            color = colors.rule,
                            cornerRadius = CornerRadius(10.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                            ),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = colors.ink4, fontSize = 14.sp)
                inner()
            }
        },
    )
}

/** Run [onLost] when the field loses focus after having held it — the lightweight
 *  "commit on blur" used by the editable fields. // PT: grava ao perder o foco. */
private fun Modifier.commitOnFocusLost(onLost: () -> Unit): Modifier = composed {
    var hadFocus by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        if (hadFocus && !state.isFocused) onLost()
        hadFocus = state.isFocused
    }
}

private fun nextPriority(current: Int?): Int? = when (current) {
    null -> 1; 1 -> 2; 2 -> 3; else -> null
}

private fun priorityColor(priority: Int?, colors: PautaColors): Color = when (priority) {
    1 -> colors.accent
    2 -> colors.ink2
    3 -> colors.ink3
    else -> colors.ink4
}

/** A new rowId order with the element at [from] moved to [to] (no-op if [to] is
 *  out of bounds — the end arrows are inert anyway). // PT: nova ordem dos itens. */
private fun moved(items: List<RoutineItemEntity>, from: Int, to: Int): List<Long> {
    val ids = items.map { it.rowId }.toMutableList()
    if (to !in ids.indices) return ids
    ids.add(to, ids.removeAt(from))
    return ids
}
