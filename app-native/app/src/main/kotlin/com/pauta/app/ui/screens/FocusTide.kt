package com.pauta.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.exp

/**
 * F1 — the "tide-rise" fill behind the active focus surface, unifying the app's
 * two metaphors: a focus block fills like a rising tide. A barely-visible accent
 * water level climbs bottom-up with the target progress; the first time the
 * target is reached a single quiet wave-crest sweeps across once (never looping).
 *
 * Battery-honest by design: the water level moves only with the caller's existing
 * 1s tick (no per-frame loop while idle) and the crest is a one-shot animation.
 * Reduced motion settles the fill statically at the right level with no sweep.
 * // PT: o bloco de foco enche como uma maré que sobe — nível ao ritmo do tique
 * de 1s, crista única ao cumprir a meta, estático com movimento reduzido.
 */

/**
 * Drives the one-shot crest. The returned [Animatable] runs 0→1 once, the first
 * time [reached] flips true; while idle it rests at 0 (before) or 1 (after),
 * which parks the crest off-screen, so [drawTide] can read it unconditionally.
 * [key] (the block id) re-seeds it per block so reopening one already over its
 * target never replays the sweep. Reduced motion settles to the resting "full"
 * state without animating. // PT: a crista atravessa uma só vez ao cumprir.
 */
@Composable
internal fun rememberTideCrest(
    key: Any?,
    reached: Boolean,
    reducedMotion: Boolean,
): Animatable<Float, AnimationVector1D> {
    val crest = remember(key) { Animatable(0f) }
    var seeded by remember(key) { mutableStateOf(false) }
    LaunchedEffect(reached) {
        if (!seeded) {
            seeded = true
            // Opening a block that is already over target: jump to the resting
            // full state without a sweep. // PT: já cumprido ao abrir, sem crista.
            if (reached) {
                crest.snapTo(1f)
                return@LaunchedEffect
            }
        }
        when {
            reached && !reducedMotion -> {
                crest.snapTo(0f)
                crest.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
            }
            reached -> crest.snapTo(1f) // reduced motion: settle full, no sweep
            else -> crest.snapTo(0f)
        }
    }
    return crest
}

/**
 * Paints the tide. [progress] is the 0..1 target fraction (the water height) and
 * [crest] is the sweep phase from [rememberTideCrest]. Pure draw — call from a
 * `Modifier.drawBehind {}` placed AFTER the surface's dark background so the
 * water sits between the surface and its content, clipped by the surface shape.
 */
internal fun DrawScope.drawTide(progress: Float, crest: Float, accent: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val p = progress.coerceIn(0f, 1f)
    val sweeping = crest > 0f && crest < 1f
    if (p <= 0f && !sweeping) return // empty block, nothing to paint

    // Leave a little headroom at the very top so the crest still has room to
    // break at 100%. // PT: folga no topo para a crista quebrar mesmo cheio.
    val headroom = 10.dp.toPx()
    val level = h - p * (h - headroom)

    // The crest: a soft bump travelling left→right exactly once. Its centre runs
    // from off the left edge to off the right edge, so at crest 0/1 the surface
    // reads as flat. // PT: a crista viaja e esconde-se nas pontas.
    val crestX = crest * (w * 1.4f) - w * 0.2f
    val crestHalf = w * 0.22f
    val crestAmp = if (sweeping) 7.dp.toPx() else 0f

    fun surfaceY(x: Float): Float {
        if (!sweeping) return level
        val d = (x - crestX) / crestHalf
        return level - crestAmp * exp(-(d * d).toDouble()).toFloat()
    }

    val steps = 40
    val fill = Path().apply {
        moveTo(0f, h)
        lineTo(0f, surfaceY(0f))
        for (i in 1..steps) {
            val x = w * i / steps
            lineTo(x, surfaceY(x))
        }
        lineTo(w, h)
        close()
    }
    drawPath(fill, accent.copy(alpha = 0.12f))

    // The waterline edge — a thin brighter rim that brightens as the crest
    // passes. // PT: linha de água, mais clara quando a crista atravessa.
    val line = Path().apply {
        moveTo(0f, surfaceY(0f))
        for (i in 1..steps) {
            val x = w * i / steps
            lineTo(x, surfaceY(x))
        }
    }
    drawPath(
        line,
        accent.copy(alpha = if (sweeping) 0.40f else 0.24f),
        style = Stroke(width = 1.5.dp.toPx()),
    )
}
