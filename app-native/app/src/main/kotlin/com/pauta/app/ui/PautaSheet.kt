package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
 * The app's modal surface, responsive to width. On a phone (< 600dp wide) it is
 * a [ModalBottomSheet] — a drag handle, drag-to-dismiss and `imePadding()` so it
 * fits one-handed use and the keyboard never covers a field; on a wide screen
 * (≥ 600dp, tablet/landscape) it stays the centred card the web app used. The
 * [content] slot is identical in both modes, so the ~dozen call sites are
 * unchanged. // PT: superfície modal — bottom sheet no telemóvel (uma mão, pega
 * de arrasto, sem teclado por cima), cartão centrado no ecrã largo; o conteúdo é
 * o mesmo nos dois modos.
 */
@Composable
fun PautaSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // 600dp is Material's compact→medium width breakpoint — the line between
    // "phone, reach the bottom" and "there's room to centre a card". // PT: 600dp
    // é o limite compacto→médio do Material (telemóvel vs. ecrã com espaço).
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    if (isPhone) {
        PautaBottomSheet(title, onClose, content)
    } else {
        PautaCenteredSheet(title, onClose, content)
    }
}

/**
 * Phone path: a bottom sheet anchored to the thumb. The drag handle is the
 * dismiss affordance (so the desktop × is dropped), the mono eyebrow keeps the
 * sheet's identity, and `imePadding()` lifts the scrolling body above the
 * keyboard. // PT: caminho do telemóvel — bottom sheet ao alcance do polegar; a
 * pega fecha (sem ×), o teclado nunca tapa o corpo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PautaBottomSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPautaColors.current
    // skipPartiallyExpanded: form sheets open fully — no half-height stop to
    // fight through. // PT: abre logo em altura cheia, sem paragem a meio.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), // the app's 18dp radius, top-only
        containerColor = colors.paper,
        contentColor = colors.ink,
        tonalElevation = 0.dp, // flat paper, no M3 tonal tint
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.ink4) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
        ) {
            SheetEyebrow(title)
        }
        Column(
            Modifier
                .fillMaxWidth()
                // imePadding BEFORE verticalScroll shrinks the scroll viewport to
                // sit above the keyboard, so the focused field is brought into a
                // visible region (not behind the IME). // PT: encolhe a área de
                // scroll para cima do teclado — o campo focado fica visível.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
            content = content,
        )
    }
}

/**
 * Wide-screen path: the web app's centred card on a dimmed backdrop — width
 * min(440, screen−28), radius 18, a sticky header with a mono uppercase eyebrow
 * title and a circular ×, body scrolling beneath. Tapping outside or the ×
 * closes it. // PT: o cartão centrado da web, para ecrãs largos.
 */
@Composable
private fun PautaCenteredSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
