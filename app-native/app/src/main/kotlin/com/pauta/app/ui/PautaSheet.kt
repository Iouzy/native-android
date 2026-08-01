package com.pauta.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pauta.app.i18n.tr
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaMotion
import com.pauta.app.ui.theme.rememberMotionEnabled

// P9 · Sheet anatomy — the four gaps every sheet body is built from, so the
// ~dozen forms stop mixing 6/10/14/16/18/20/22/24 for the same job. Anything
// tighter than these (a title and the field right under it, a hint under a
// control group) stays a local literal — these are the *structural* gaps.
// // PT: a anatomia das folhas — os quatro espaçamentos de que todos os
// formulários são feitos; os espaços mais curtos continuam locais.

/** The gutter of every sheet body — header and content share it. // PT: a margem lateral. */
val SheetGutter: Dp = 24.dp

/** Between two field groups (an eyebrow + its control and the next). // PT: entre grupos. */
val SheetFieldGap: Dp = 18.dp

/** Between an eyebrow/label and the control it names. // PT: entre etiqueta e campo. */
val SheetLabelGap: Dp = 8.dp

/** Above a sheet's action row (Cancelar / Confirmar). // PT: antes dos botões. */
val SheetActionGap: Dp = 22.dp

/**
 * U1 · Has the surrounding sheet finished arriving? `null` when the composable
 * asking isn't inside a [PautaSheet] at all (an inline editor, a full screen), so
 * callers can fall back. This is the signal an autofocused field waits on instead
 * of guessing a duration: raising the keyboard while the sheet is still sliding
 * makes `imePadding()` re-lay-out a half-drawn sheet, which is the visible jump
 * U1 exists to kill. // PT: diz se a folha já assentou; `null` fora de uma folha.
 * O campo com foco automático espera por este sinal em vez de adivinhar um tempo
 * — abrir o teclado a meio do deslize faz saltar a folha.
 */
val LocalSheetSettled: ProvidableCompositionLocal<State<Boolean>?> =
    staticCompositionLocalOf { null }

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
    // Read the pref out here, not inside the popup/dialog subcomposition — one
    // read, and neither surface needs its own ViewModel lookup. // PT: lê a
    // preferência uma vez, fora da subcomposição da folha.
    val entrance = rememberSheetEntrance(rememberMotionEnabled())
    // U1 · The reverse direction: put the keyboard away *before* the sheet starts
    // leaving, so the sheet doesn't drop through the gap the IME leaves behind.
    // Covers every dismiss the sheet itself owns — scrim, back, drag handle, ×.
    // // PT: esconde o teclado antes de a folha sair, para não cair no vazio que
    // o teclado deixa.
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss: () -> Unit = { keyboard?.hide(); onClose() }
    if (isPhone) {
        PautaBottomSheet(title, dismiss, entrance, content)
    } else {
        PautaCenteredSheet(title, dismiss, entrance, content)
    }
}

/**
 * P9: the one sheet entrance, 0 → 1 over [PautaMotion.Slow] on the house easing.
 * Material3 owns the bottom sheet's slide and exposes no spec to retune, so what
 * both faces *share* — and what makes them read as one gesture — is the content
 * fade; the centred card, which M3 gives no motion at all, adds a short rise on
 * top of it. Under reduced motion the value starts (and stays) at 1, so a sheet
 * simply appears. // PT: a entrada única das folhas — o mesmo fade nas duas
 * faces (o cartão centrado sobe um pouco, por não ter animação própria); com
 * movimento reduzido aparece já assente.
 */
@Composable
private fun rememberSheetEntrance(motion: Boolean): Float {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown || !motion) 1f else 0f,
        animationSpec = if (motion) PautaMotion.tween(PautaMotion.Slow) else snap(),
        label = "sheetEntrance",
    )
    return progress
}

/** The fade half of the entrance. Skipped entirely once settled, so a resting
 *  sheet carries no extra graphics layer. // PT: o fade da entrada; sem camada
 *  extra depois de assentar. */
private fun Modifier.entranceFade(progress: Float): Modifier =
    if (progress < 1f) this.alpha(progress) else this

/**
 * Phone path: a bottom sheet anchored to the thumb. The drag handle is the
 * dismiss affordance (so the desktop × is dropped), the mono eyebrow keeps the
 * sheet's identity, and `imePadding()` lifts the scrolling body above the
 * keyboard. // PT: caminho do telemóvel — bottom sheet ao alcance do polegar; a
 * pega fecha (sem ×), o teclado nunca tapa o corpo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PautaBottomSheet(
    title: String,
    onClose: () -> Unit,
    entrance: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPautaColors.current
    // skipPartiallyExpanded: form sheets open fully — no half-height stop to
    // fight through. // PT: abre logo em altura cheia, sem paragem a meio.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // U1 · `currentValue`, not `targetValue`: the former flips when the expand
    // animation *finishes*, the latter the moment it is asked for — which is
    // exactly the race the old 120ms guess kept losing. Nothing here consults
    // `reducedMotion`: M3 owns this slide and exposes no spec to retune, so the
    // honest test is the animation's own end, whatever its duration (instant
    // under a 0× animator scale, five seconds under 5×). // PT: usa `currentValue`
    // — muda quando a animação acaba, não quando começa; serve qualquer duração.
    val settled = remember(sheetState) {
        derivedStateOf { sheetState.currentValue == SheetValue.Expanded }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = PautaRadius.Sheet, topEnd = PautaRadius.Sheet), // the sheet radius, top-only
        containerColor = colors.paper,
        contentColor = colors.ink,
        tonalElevation = 0.dp, // flat paper, no M3 tonal tint
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.ink4) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .entranceFade(entrance)
                .padding(start = SheetGutter, end = SheetGutter, bottom = 10.dp),
        ) {
            SheetEyebrow(title)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .entranceFade(entrance)
                // imePadding BEFORE verticalScroll shrinks the scroll viewport to
                // sit above the keyboard, so the focused field is brought into a
                // visible region (not behind the IME). // PT: encolhe a área de
                // scroll para cima do teclado — o campo focado fica visível.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = SheetGutter, end = SheetGutter, bottom = SheetActionGap),
        ) {
            CompositionLocalProvider(LocalSheetSettled provides settled) { content() }
        }
    }
}

/**
 * Wide-screen path: the web app's centred card on a dimmed backdrop — width
 * min(440, screen−28), the sheet radius, a sticky header with a mono uppercase eyebrow
 * title and a circular ×, body scrolling beneath. Tapping outside or the ×
 * closes it. // PT: o cartão centrado da web, para ecrãs largos.
 */
@Composable
private fun PautaCenteredSheet(
    title: String,
    onClose: () -> Unit,
    entrance: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPautaColors.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    // U1 · A dialog has no slide to wait out — it is laid out where it lands — so
    // "settled" here means "the window has drawn one frame", enough for the focus
    // target to be attached. // PT: o cartão não desliza; assenta ao fim de um
    // frame, o suficiente para o campo já existir.
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos { }; settled.value = true }
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
                // The rise the bottom sheet gets from its own slide. Outside the
                // shadow so the card's drop shadow travels with it. // PT: a
                // subida que o bottom sheet já tem no seu deslize.
                .then(
                    if (entrance < 1f) {
                        Modifier.graphicsLayer {
                            alpha = entrance
                            translationY = (1f - entrance) * 10.dp.toPx()
                        }
                    } else {
                        Modifier
                    },
                )
                .shadow(24.dp, RoundedCornerShape(PautaRadius.Sheet))
                .clip(RoundedCornerShape(PautaRadius.Sheet))
                .background(colors.paper),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = SheetGutter, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same eyebrow the bottom sheet's title row uses — the two
                // faces differ only in the dismiss affordance (handle vs. ×).
                // // PT: o mesmo eyebrow do bottom sheet; só o fecho difere.
                SheetEyebrow(title, modifier = Modifier.weight(1f))
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
                    .padding(start = SheetGutter, end = SheetGutter, bottom = SheetActionGap),
            ) {
                CompositionLocalProvider(LocalSheetSettled provides settled) { content() }
            }
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
    // U3: the same pill at chip height, for a button that commits a form sitting
    // in a list rather than a sheet's action row. // PT: a mesma pílula, à altura
    // de uma chip, para formulários dentro de uma lista.
    compact: Boolean = false,
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
    val vPad = when {
        compact -> 8.dp
        variant == PautaButtonVariant.Ghost -> 11.dp
        else -> 13.dp
    }
    val hPad = if (compact || variant == PautaButtonVariant.Ghost) 14.dp else 20.dp
    // P10: the press dip — only while the button can actually be pressed.
    // // PT: o afundar ao toque, só quando o botão está ativo.
    val press = if (enabled) rememberMotionEnabled() else false
    Box(
        modifier
            .pressScale(press)
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

/** The sheets' section eyebrow — kept for its ~40 call sites, now delegating to
 *  the shared [SectionEyebrow] (P4). // PT: delega no eyebrow único. */
@Composable
fun SheetEyebrow(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPautaColors.current.ink3,
) {
    SectionEyebrow(label, modifier = modifier, color = color)
}

/** Accessibility label for the sheet's close affordance (kept for parity with
 *  the web's `title={tr("fechar")}`). */
fun sheetCloseLabel(): String = tr("fechar")
