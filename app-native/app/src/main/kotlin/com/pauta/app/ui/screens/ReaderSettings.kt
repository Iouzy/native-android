package com.pauta.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.i18n.tr
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SheetFieldGap
import com.pauta.app.ui.SheetLabelGap
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaType
import com.pauta.app.ui.theme.ReaderThemes

/**
 * L5 · the reader's own type and colour, as the reader sees them.
 *
 * The most-expected feature of any reader and the one this one had least of: body
 * size came from the app-wide `textScale` and nothing else was adjustable at all.
 * Someone reading at night in a bright room had to leave the book, open Settings,
 * change a preference that also resized the whole planner, and come back.
 *
 * A small value type rather than four parameters, so adding a fifth later doesn't
 * touch every signature between the sheet and the stylesheet.
 * // PT: as definições do leitor num só valor, em vez de quatro parâmetros.
 */
internal data class ReaderSettings(
    val textScale: Float,
    val lineHeight: Float,
    val margin: Int,
    val theme: String,
) {
    companion object {
        fun of(prefs: PrefsEntity) = ReaderSettings(
            textScale = prefs.readerTextScale,
            lineHeight = prefs.readerLineHeight,
            margin = prefs.readerMargin,
            theme = prefs.readerTheme,
        )
    }
}

/**
 * The `Aa` sheet: four rows, each a label and a stepper.
 *
 * Steppers rather than sliders — a slider is imprecise and Material, and these
 * are values you nudge rather than sweep. Changes apply **live behind the sheet**,
 * which is the entire reason they live here instead of in Settings: you can see
 * the page you are changing.
 * // PT: quatro linhas com um passo a passo (não sliders); mudam a página por
 * baixo, que é o motivo de estarem aqui e não nas definições.
 */
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onTextScale: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMargin: (Int) -> Unit,
    onTheme: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    PautaSheet(title = tr("Leitura"), onClose = onClose) {
        StepperRow(
            label = tr("Tamanho do texto"),
            value = "${(settings.textScale * 100).toInt()}%",
            onLess = { onTextScale(settings.textScale - 0.1f) },
            onMore = { onTextScale(settings.textScale + 0.1f) },
        )
        Spacer(Modifier.height(SheetFieldGap))
        StepperRow(
            label = tr("Entrelinha"),
            value = String.format("%.2f", settings.lineHeight),
            onLess = { onLineHeight(settings.lineHeight - 0.06f) },
            onMore = { onLineHeight(settings.lineHeight + 0.06f) },
        )
        Spacer(Modifier.height(SheetFieldGap))
        StepperRow(
            label = tr("Margens"),
            value = "${settings.margin}",
            onLess = { onMargin(settings.margin - 2) },
            onMore = { onMargin(settings.margin + 2) },
        )
        Spacer(Modifier.height(SheetFieldGap))
        Text(tr("Tema de leitura"), color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(SheetLabelGap))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderThemes.ALL.forEach { theme ->
                SelectPill(
                    label = readerThemeLabel(theme),
                    selected = settings.theme == theme,
                    accent = colors.accent,
                    large = true,
                ) { onTheme(theme) }
            }
        }
        Spacer(Modifier.height(SheetFieldGap))
    }
}

/** The four themes' names. `app` says what it does rather than naming a colour.
 *  // PT: os nomes dos temas; "app" diz o que faz. */
@Composable
private fun readerThemeLabel(theme: String): String = when (theme) {
    ReaderThemes.Paper -> tr("Papel")
    ReaderThemes.Sepia -> tr("Sépia")
    ReaderThemes.Night -> tr("Noite")
    else -> tr("Como a app")
}

/** `label   −  value  +` in the app's mono meta treatment. // PT: uma linha de
 *  ajuste, no tratamento mono da app. */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.ink2, fontSize = 14.sp, modifier = Modifier.weight(1f))
        StepButton("−", label) { onLess() }
        Text(
            text = value,
            color = colors.ink,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepButton("+", label) { onMore() }
    }
}

@Composable
private fun StepButton(glyph: String, of: String, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(PautaRadius.Chip))
            .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip))
            .clickableNoRipple(onClick)
            .semantics { contentDescription = "$of $glyph"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = colors.ink2, style = PautaType.Meta)
    }
}
