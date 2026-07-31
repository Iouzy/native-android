package com.pauta.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * P3: the app's motion vocabulary — three durations, one easing, one spring,
 * shared by every animated surface so transitions feel like one hand drew them.
 * Off-scale durations (e.g. the Hoje pulse count-up) still go through [tween]
 * to pick up the house easing. // PT: o vocabulário de movimento da app — três
 * durações, um easing e uma mola partilhados por todas as animações; durações
 * fora da escala continuam a usar [tween] para herdar o easing comum.
 */
object PautaMotion {
    /** Tint changes, small fades. // PT: mudanças de cor, pequenos fades. */
    const val Fast = 140

    /** Most transitions. // PT: a maioria das transições. */
    const val Base = 240

    /** Nav pushes, sheet entrances. // PT: navegação e entradas de folhas. */
    const val Slow = 380

    /** Decisive out — leaves quickly, lands softly. // PT: parte rápido, assenta suave. */
    val Ease: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** The house spring — barely bouncy. // PT: a mola da casa, quase sem ressalto. */
    val Spring: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** The default tween: [Base] ms on [Ease]. // PT: o tween por omissão. */
    fun <T> tween(ms: Int = Base): TweenSpec<T> = tween(ms, easing = Ease)
}

/**
 * Whether animations should run — the inverse of the reduced-motion pref, read
 * once from the app-scoped [AppViewModel] so call sites stop re-deriving
 * `val animate = !prefs.reducedMotion` by hand. When false, call sites fall back
 * to `snap()` / `EnterTransition.None` as before. // PT: lê a preferência de
 * "movimento reduzido" num só sítio; quando falso, as animações saltam.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    return !prefs.reducedMotion
}
