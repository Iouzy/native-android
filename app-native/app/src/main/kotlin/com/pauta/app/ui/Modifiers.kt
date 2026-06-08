package com.pauta.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

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
