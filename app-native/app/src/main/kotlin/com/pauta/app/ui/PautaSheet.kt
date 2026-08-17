package com.pauta.app.ui

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
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
 * F3 · back is a **two-stage** gesture whenever the keyboard is up: the first
 * press puts the keyboard away and keeps the form, the second dismisses the
 * sheet. That is the platform convention everywhere else on Android, and the app
 * broke it — adding a tide, the keyboard covered the lower half of the sheet,
 * there was no gesture that closed it, and back threw away everything typed. The
 * only two outcomes were "keyboard in the way" and "lose your work".
 *
 * `docs/archive/UX_FIXES.md` U1 fixed the keyboard *arriving* mid-animation.
 * Nobody fixed it leaving.
 *
 * The visibility comes from [isImeVisible], not from focus: a field can hold
 * focus with the keyboard down, and the two states are not the same. Enabled
 * only while the keyboard is up, this never eats a back press that should close
 * the sheet.
 *
 * **S3 · which dispatcher hears the press first is the whole of this.** F3
 * registered a plain [BackHandler] and asserted that composing it inside the
 * sheet body put it above Material's own back handling. That is true on API ≤ 32
 * and inside the centred [Dialog] — both route through the AndroidX
 * `OnBackPressedDispatcher`, which invokes the *last* registered enabled callback
 * first, and this one registers after the dialog's. It is false on API 33+ for
 * the bottom sheet: `ModalBottomSheetDialogLayout.onAttachedToWindow` registers
 * its dismiss straight with the **platform** dispatcher at
 * `PRIORITY_OVERLAY`, while everything AndroidX sits at `PRIORITY_DEFAULT`, and
 * the platform calls the highest priority first. Material won every time, the
 * sheet went, and the typed text went with it — exactly the defect F3 shipped to
 * fix (`docs/SHAKEDOWN.md` S3, reproduced on the `pauta_pixel7` AVD in PR #190).
 * So on 33+ we register on that same dispatcher, one rung above Material.
 *
 * // PT: com o teclado aberto, "voltar" fecha o teclado e guarda o formulário; a
 * segunda vez fecha a folha. Só está activo enquanto o teclado está mesmo
 * visível. O que faltava (S3): a partir do Android 13 a folha do Material regista
 * o seu "fechar" directamente no despachante do sistema, com prioridade acima de
 * tudo o que é AndroidX — por isso ganhava sempre. Aqui registamos um degrau
 * acima dela.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetImeBackHandler() {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val putKeyboardAway: () -> Unit = {
        // Clear the focus as well as hiding: a field that keeps focus keeps
        // asking for the IME, and the keyboard comes straight back.
        // // PT: tirar o foco também, senão o teclado volta sozinho.
        focus.clearFocus()
        keyboard?.hide()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ImeBackAboveTheSheet(enabled = WindowInsets.isImeVisible, onBack = putKeyboardAway)
    } else {
        BackHandler(enabled = WindowInsets.isImeVisible, onBack = putKeyboardAway)
    }
}

/**
 * S3 · the API 33+ half: the first back press, taken from the platform's own
 * dispatcher one priority above the sheet's.
 *
 * Registered **only** while [enabled] — the keyboard is up — so the moment it
 * goes down the callback leaves the dispatcher and the very next press is
 * Material's again: the second back closes the sheet, and the predictive-back
 * gesture still peels it, because with the keyboard down nothing of ours is
 * registered at all. Nothing here is a new dependency; `android.window` is the
 * framework.
 *
 * // PT: a metade para Android 13+ — apanha o primeiro "voltar" no despachante do
 * sistema, um grau acima da folha, e só enquanto o teclado está aberto. Com o
 * teclado fechado não há nada nosso registado: o segundo "voltar" fecha a folha e
 * o gesto preditivo continua a descolá-la.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ImeBackAboveTheSheet(enabled: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    // The callback outlives a recomposition, so it must not capture a stale
    // lambda. // PT: o callback sobrevive às recomposições; não pode guardar uma
    // lambda velha.
    val latest by rememberUpdatedState(onBack)
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        // `PRIORITY_OVERLAY + 1`: the sheet registers *at* OVERLAY
        // (`ModalBottomSheet.android.kt`), and higher is called first. The
        // dispatcher is the dialog window's own, the same one the sheet used.
        // // PT: um acima da prioridade da folha, no mesmo despachante.
        val dispatcher = view.findOnBackInvokedDispatcher()
        val callback = OnBackInvokedCallback { latest() }
        dispatcher?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY + 1,
            callback,
        )
        onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
    }
}

/**
 * F3 · the cheap half: a tap on the sheet's own background puts the keyboard
 * away. Children get the pointer event first, so a tap on a field, a chip or a
 * button is unaffected; and a tap is only a tap after the finger lifts without
 * moving, so scrolling the body still scrolls it. // PT: tocar no fundo da folha
 * fecha o teclado; os filhos recebem o toque primeiro e o scroll não é afectado.
 */
private fun Modifier.dismissImeOnBackgroundTap(clearFocus: () -> Unit): Modifier =
    this.pointerInput(Unit) { detectTapGestures(onTap = { clearFocus() }) }

/**
 * S2 · A drag that starts in the sheet's **body** must not take the sheet with it.
 *
 * `ModalBottomSheet` links the body's scroll to the sheet's own drag through
 * nested scroll: whatever the scrolling content leaves unconsumed is handed up to
 * the sheet, which slides down — and with `skipPartiallyExpanded = true` the only
 * anchor below Expanded is Hidden, so it settles *dismissed*. On a short form —
 * `Nova maré` in the state it opens in — there is nothing to scroll, so the whole
 * gesture is leftover from the first pixel and a gentle 290px pull anywhere in the
 * body closes the sheet, taking whatever was typed with it. On an expanded one the
 * body scrolls first and does the same the moment it reaches its top. Both were
 * watched on the `pauta_pixel7` AVD (`docs/SHAKEDOWN.md` S1, PR #190).
 *
 * Swallowing that leftover cuts the link, and only the link. We take what the
 * body's own scroll declined, so scrolling is untouched; and the drag handle sits
 * *outside* this modifier, so the affordance the phone path documents as "this is
 * how you close it" is still the one that closes it.
 *
 * // PT: um arrasto no corpo da folha deixa de a fechar. O que o scroll do corpo
 * não consome era entregue à folha, que — sem paragem a meio — só tem "escondida"
 * para onde ir, e leva com ela o que estava escrito. Engolimos essa sobra: o
 * scroll não muda, e a pega (fora deste modificador) continua a fechar a folha.
 */
private object SheetBodyDragBoundary : NestedScrollConnection {
    // Vertical only, and only what a finger produced: a programmatic scroll — the
    // field `imePadding()` brings back into view when the keyboard opens — is left
    // alone, exactly as Material's own connection leaves it. // PT: só o vertical,
    // e só o que veio de um dedo; o scroll programático fica como está.
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (source == NestedScrollSource.UserInput) Offset(0f, available.y) else Offset.Zero

    // The throw at the end of the drag travels separately, so it needs the same
    // treatment — otherwise a flick still reaches the sheet after the finger has
    // gone. // PT: o impulso final viaja à parte; sem isto, um safanão ainda chega
    // à folha depois de o dedo sair.
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(0f, available.y)
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
    // fight through. S2: that also leaves Hidden as the only anchor a downward
    // drag can settle on, which is why the body's drag had to stop reaching the
    // sheet ([SheetBodyDragBoundary]); adding the half-way stop back would fix the
    // dismissal by putting the fight-through in front of every form again.
    // // PT: abre logo em altura cheia, sem paragem a meio — e por isso um arrasto
    // para baixo só tinha "escondida" onde assentar; ver [SheetBodyDragBoundary].
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
        val focus = LocalFocusManager.current
        Column(
            Modifier
                .fillMaxWidth()
                .entranceFade(entrance)
                // imePadding BEFORE verticalScroll shrinks the scroll viewport to
                // sit above the keyboard, so the focused field is brought into a
                // visible region (not behind the IME). // PT: encolhe a área de
                // scroll para cima do teclado — o campo focado fica visível.
                .imePadding()
                // S2 · outside the scroll, so it is the scroll's nested-scroll
                // parent and gets the leftover before Material's sheet connection
                // does. // PT: fora do scroll, para apanhar a sobra antes da folha.
                .nestedScroll(SheetBodyDragBoundary)
                .verticalScroll(rememberScrollState())
                .dismissImeOnBackgroundTap { focus.clearFocus() }
                .padding(start = SheetGutter, end = SheetGutter, bottom = SheetActionGap),
        ) {
            SheetImeBackHandler()
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
            val focus = LocalFocusManager.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .dismissImeOnBackgroundTap { focus.clearFocus() }
                    .padding(start = SheetGutter, end = SheetGutter, bottom = SheetActionGap),
            ) {
                SheetImeBackHandler()
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
