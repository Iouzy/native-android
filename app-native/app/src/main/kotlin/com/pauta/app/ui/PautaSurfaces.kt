package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily

// P4 · Surface primitives — the one radius scale, the one paper card and the one
// mono eyebrow, replacing the ad-hoc per-screen copies (radii 4…20, three private
// eyebrow composables). Pills keep their 999dp; tiny decorative radii (2–4dp
// marks, tags, progress bars) stay literal on purpose. // PT: primitivas de
// superfície — a escala de raios, o cartão de papel e o eyebrow mono únicos.

/** The app's corner-radius scale. Anything card-like uses one of these four
 *  steps; pills stay 999dp. // PT: a escala de raios de canto da app. */
object PautaRadius {
    val Chip = 8.dp
    val Field = 10.dp
    val Card = 14.dp
    val Sheet = 20.dp
}

/**
 * The recurring paper card: clipped corners, a 1dp `rule` hairline, `paper2`
 * fill and inner padding — the pattern previously copy-pasted per screen with
 * drifting radii/paddings. [onClick] keeps the app's quiet no-ripple press.
 * // PT: o cartão de papel recorrente — canto arredondado, contorno `rule`,
 * fundo `paper2`; o toque continua sem ripple.
 */
@Composable
fun PautaCard(
    modifier: Modifier = Modifier,
    radius: Dp = PautaRadius.Card,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPautaColors.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .border(1.dp, colors.rule, shape)
            .background(colors.paper2)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The one mono uppercase section eyebrow (previously `SectionLabel`,
 * `MonoSectionLabel`, `SheetEyebrow` and inline copies drifting 9–10sp /
 * 0.9–2 letterSpacing). [color] covers the rare accent/ink4 variants.
 * // PT: o único eyebrow de secção — mono, maiúsculas, 10sp.
 */
@Composable
fun SectionEyebrow(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPautaColors.current.ink3,
) {
    Text(
        text = label.uppercase(),
        color = color,
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        modifier = modifier,
    )
}
