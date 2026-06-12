package com.pauta.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Thin wrapper over Compose's HapticFeedback, gated on the user's haptics pref
 * so every call site stays clean.
 * // PT: invólucro sobre HapticFeedback do Compose, controlado pela preferência.
 */
class PautaHaptics(
    private val haptic: HapticFeedback,
    private val enabled: Boolean,
) {
    /** Light tick — toggles, keys, tab changes. */
    fun tick() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Heavy press — long-press respiro and destructive confirms. */
    fun longPress() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * Returns a [PautaHaptics] bound to the composition's haptic provider and gated
 * on [enabled]. Stable across recompositions for the same [enabled] value.
 */
@Composable
fun rememberHaptics(enabled: Boolean): PautaHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) { PautaHaptics(haptic, enabled) }
}
