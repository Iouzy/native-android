package com.pauta.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
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
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * native-only (K7): the book-mode header of the Marés tab — the annual reading
 * goal card and the "HÁBITOS DE LEITURA" eyebrow that precede the full, normal
 * Marés content. The habits themselves are the existing habit engine, embedded
 * unchanged: [MaresScreen] renders these as extra leading items of its own
 * LazyColumn when `bookMode` is true (two nested scrollables would clash, so
 * the embed goes this way around). // PT: cartão do objetivo anual + eyebrow do
 * modo livro, por cima das Marés normais.
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
