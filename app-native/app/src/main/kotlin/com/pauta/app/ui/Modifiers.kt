package com.pauta.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.ui.theme.PautaMotion
import kotlinx.coroutines.delay

/**
 * A click with no ripple/indication — the app surface is meant to feel native
 * and quiet, like the web app which suppresses the tap-highlight everywhere
 * (`-webkit-tap-highlight-color: transparent`). Use this for the bespoke tappable
 * rows/cards; reserve Material ripple for stock buttons. // PT: clique sem
 * ripple, para a interface ficar silenciosa como na web.
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** [clickableNoRipple] with a long-press, for quiet rows that hide a second
 *  action (e.g. long-press a tide's name to remove it). Pass
 *  [onLongClickLabel] to name that hidden action for TalkBack, which otherwise
 *  has no way to reach it. // PT: o rótulo dá nome ao toque longo para o
 *  TalkBack — sem ele a ação é só um gesto. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableNoRipple(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLongClickLabel: String? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    combinedClickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
    )
}

// ─── P10 · The micro-interaction layer ────────────────────────────────────────
// Three primitives the delight pass is built from: the press dip that stands in
// for the ripple the app refuses, the one-line haptic tick, and the first-entry
// list stagger. All three are *additive* — with haptics off and motion reduced
// every one of them disappears and nothing behaves differently.
// // PT: a camada de micro-interações — o afundar ao toque (em vez do ripple), o
// toque háptico numa linha e a entrada escalonada das listas; com vibração
// desligada e movimento reduzido, todas desaparecem sem mudar comportamento.

/** How far a pressed surface dips. // PT: quanto a superfície afunda ao toque. */
private const val PressScaleTarget = 0.97f

/**
 * The quiet alternative to a ripple: the surface dips to [scale] while a finger
 * is down and springs back on release. It only *observes* the pointer stream
 * (`requireUnconsumed = false`, nothing consumed), so the `clickableNoRipple`
 * further along the same chain still owns the click; and a gesture taken over by
 * a parent — a LazyColumn starting to scroll — cancels the dip instead of
 * leaving the surface stuck small. Place it before the `clip`/`background` so the
 * whole card scales, and pass `enabled = rememberMotionEnabled()` so reduced
 * motion drops the layer entirely. // PT: o toque afunda ligeiramente e volta —
 * a alternativa silenciosa ao ripple. Só observa os eventos (o clique continua a
 * ser de quem o trata) e desiste se a lista começar a deslizar; com movimento
 * reduzido não existe.
 */
fun Modifier.pressScale(enabled: Boolean = true, scale: Float = PressScaleTarget): Modifier =
    if (!enabled) this else composed {
        var pressed by remember { mutableStateOf(false) }
        val current by animateFloatAsState(
            targetValue = if (pressed) scale else 1f,
            animationSpec = PautaMotion.Spring,
            label = "pressScale",
        )
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                // null = the gesture was consumed elsewhere (a parent scroll);
                // either way the finger is gone. // PT: subiu ou foi cancelado.
                waitForUpOrCancellation()
                pressed = false
            }
        }.graphicsLayer {
            scaleX = current
            scaleY = current
        }
    }

/**
 * One quiet haptic tick, honouring the vibration preference — the single entry
 * point for the app's haptic map (tab settle, intention checked, block started or
 * concluded, a tide's day filled, a two-step delete armed) so call sites stay one
 * line. `LongPress` is the subtlest type this Compose version exposes (there is
 * no `SegmentTick` in UI 1.7). // PT: o único toque háptico da app, sujeito à
 * preferência de vibração; `LongPress` é o tipo mais discreto disponível.
 */
fun HapticFeedback.tick(prefs: PrefsEntity) {
    if (prefs.haptics) performHapticFeedback(HapticFeedbackType.LongPress)
}

/** Between two neighbouring items' entrances. // PT: desfasamento entre itens. */
private const val EntranceStepMs = 30

/** The stagger stops growing here, so a long list has no slow tail. // PT: o
 *  escalonamento pára aqui — listas longas não arrastam. */
private const val EntranceMaxSteps = 8

/** How long the whole stagger stays armed: the last step plus its tween, with a
 *  margin. After this the modifier drops out of the chain. // PT: quanto tempo a
 *  entrada fica armada — depois disso o modificador sai da cadeia. */
private const val EntranceWindowMs = EntranceMaxSteps * EntranceStepMs + PautaMotion.Fast + 120L

/** How far an entering item rises into place. // PT: quanto o item sobe. */
private val EntranceRise: Dp = 12.dp

/**
 * Which lists have already played their entrance in this process. Deliberately
 * *not* a `remember`: leaving HOME for Settings and coming back re-composes the
 * screens, and the stagger is a first-impression, not a transition.
 * // PT: que listas já fizeram a entrada nesta sessão — fora da composição, para
 * sobreviver a uma ida às definições.
 */
private val entrancesPlayed = mutableSetOf<String>()

/**
 * Whether the list named [id] should play its entrance now. True only on the
 * first composition of that list in the session, and only for the length of the
 * stagger window — so items composed later *by scrolling* arrive plainly, which
 * is the "per session, not per scroll" rule. Pass `enabled =
 * rememberMotionEnabled()`; reduced motion never plays.
 *
 * Note the pager keeps all three tabs composed (P1), so "first composition" is
 * app start for all of them: the stagger is seen on the tab the app opens into,
 * and swiping to the others is left alone — which is the right trade, since
 * animating a list *during* a swipe is exactly the hitch P1 removed.
 * // PT: a lista só faz a entrada na primeira composição da sessão e só durante
 * a janela do escalonamento (itens compostos ao deslizar entram sem animação).
 * Como as três tabs ficam compostas desde o arranque, a entrada vê-se na tab de
 * abertura — animar durante um swipe seria o soluço que o P1 removeu.
 */
@Composable
fun rememberEntrancePlay(id: String, enabled: Boolean = true): Boolean {
    var armed by remember(id) { mutableStateOf(enabled && id !in entrancesPlayed) }
    LaunchedEffect(id) {
        if (!armed) return@LaunchedEffect
        entrancesPlayed += id
        delay(EntranceWindowMs)
        armed = false
    }
    return armed
}

/**
 * The entrance itself: fade in and rise [EntranceRise] into place over
 * [PautaMotion.Fast], delayed by [index] steps. [play] comes from
 * [rememberEntrancePlay]; when it is false the modifier is not composed at all,
 * and the graphics layer is shed as soon as the item has settled.
 * // PT: a entrada — aparece e sobe até ao lugar, com atraso conforme a posição;
 * quando não há entrada a fazer, o modificador nem chega a existir.
 */
fun Modifier.entranceStagger(index: Int, play: Boolean): Modifier =
    if (!play) this else composed {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val progress by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(
                durationMillis = PautaMotion.Fast,
                delayMillis = index.coerceIn(0, EntranceMaxSteps) * EntranceStepMs,
                easing = PautaMotion.Ease,
            ),
            label = "entranceStagger",
        )
        if (progress >= 1f) {
            this
        } else {
            graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * EntranceRise.toPx()
            }
        }
    }
