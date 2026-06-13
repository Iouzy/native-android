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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.pauta.app.data.entity.PlannedIntentionEntity
import com.pauta.app.domain.DateUtils
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SansFamily
import com.pauta.app.ui.theme.SerifFamily
import java.time.LocalDate

/**
 * "A semana" — the web's WeekAheadSheet (tab-hoje.jsx): one row per day for the
 * next seven, each with its planned intentions and a dashed add field. Plans
 * become that day's intentions when it arrives (runRollover). // PT: planear a
 * semana — cada plano vira as intenções do dia quando ele chegar.
 */
@Composable
fun WeekAheadSheet(
    today: String,
    plans: List<PlannedIntentionEntity>,
    onAdd: (dayKey: String, text: String) -> Unit,
    onRemove: (id: String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val days = remember(today) { (1..7).map { DateUtils.addDays(today, it) } }
    val byDay = plans.groupBy { it.dayKey }

    PautaSheet(title = tr("A semana"), onClose = onClose) {
        Text(
            text = tr("Deixe preparado o que importa nos próximos dias. Cada plano vira as intenções desse dia quando ele chegar."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(16.dp))

        days.forEachIndexed { index, dayKey ->
            PlanDayRow(
                dayKey = dayKey,
                items = byDay[dayKey].orEmpty(),
                onAdd = onAdd,
                onRemove = onRemove,
                // A6: the first day's field grabs focus on open so planning starts
                // on the keyboard. // PT: o primeiro dia foca-se ao abrir.
                autoFocus = index == 0,
            )
        }
    }
}

@Composable
private fun PlanDayRow(
    dayKey: String,
    items: List<PlannedIntentionEntity>,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    autoFocus: Boolean = false,
) {
    val colors = LocalPautaColors.current
    var text by remember(dayKey) { mutableStateOf("") }
    // Done commits and clears but keeps focus, so several plans can be typed in a
    // row without leaving the keyboard. // PT: Enter regista e limpa, mantendo o
    // foco para escrever vários planos seguidos.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) { delay(120); runCatching { focusRequester.requestFocus() } }
    }

    fun commit() {
        val t = text.trim()
        if (t.isNotEmpty()) {
            onAdd(dayKey, t)
            text = ""
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            text = I18n.fmtDateLong(LocalDate.parse(dayKey)).uppercase(),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 1.44.sp, // 0.16em of 9sp
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.paper2)
                        .border(1.dp, colors.rule, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.text,
                        color = colors.ink,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "×",
                        color = colors.ink4,
                        fontFamily = MonoFamily,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickableNoRipple { onRemove(item.id) }
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            // Dashed add field, committing on Done — the web also commits on blur.
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontFamily = SansFamily, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                        if (text.isEmpty()) {
                            Text(tr("planear intenção…"), color = colors.ink4, fontSize = 14.sp)
                        }
                        inner()
                    }
                },
            )
        }
    }
}
