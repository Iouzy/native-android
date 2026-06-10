package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pauta.app.i18n.tr
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily

/**
 * The web app's Sheet (ui-primitives.jsx): a centered modal card on a dimmed
 * backdrop — width min(440, screen−28), radius 18, a sticky header with a mono
 * uppercase eyebrow title and a circular × — with the body scrolling beneath.
 * Tapping outside or the × closes it. // PT: o "Sheet" da web — cartão modal
 * centrado com cabeçalho mono e fecho redondo.
 */
@Composable
fun PautaSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPautaColors.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 14.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(colors.paper),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    color = colors.ink3,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.8.sp, // 0.18em of 10sp
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.rule, CircleShape)
                        .clickableNoRipple(onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "×", color = colors.ink3, fontFamily = MonoFamily, fontSize = 16.sp)
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
                content = content,
            )
        }
    }
}

/** The web Button pill (ui-primitives.jsx): primary (accent), inkPrimary (the
 *  dark inverse surface) and ghost variants; disabled fades to 0.4. */
enum class PautaButtonVariant { Primary, InkPrimary, Ghost }

@Composable
fun PautaButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: PautaButtonVariant = PautaButtonVariant.Primary,
    enabled: Boolean = true,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val bg = when (variant) {
        PautaButtonVariant.Primary -> accent ?: colors.accent
        PautaButtonVariant.InkPrimary -> colors.surfaceDark
        PautaButtonVariant.Ghost -> Color.Transparent
    }
    val fg = when (variant) {
        PautaButtonVariant.Primary, PautaButtonVariant.InkPrimary -> colors.onDark
        PautaButtonVariant.Ghost -> colors.ink2
    }
    val vPad = if (variant == PautaButtonVariant.Ghost) 11.dp else 13.dp
    val hPad = if (variant == PautaButtonVariant.Ghost) 14.dp else 20.dp
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = hPad, vertical = vPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.07).sp, // -0.005em of 14sp
        )
    }
}

/** The mono uppercase section eyebrow used throughout the sheets. */
@Composable
fun SheetEyebrow(label: String, color: Color = LocalPautaColors.current.ink3) {
    Text(
        text = label.uppercase(),
        color = color,
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
    )
}

/** Accessibility label for the sheet's close affordance (kept for parity with
 *  the web's `title={tr("fechar")}`). */
fun sheetCloseLabel(): String = tr("fechar")
