package com.pauta.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

/** [clickableNoRipple] with a long-press, for quiet rows that hide a second
 *  action (e.g. long-press a tide's name to remove it). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableNoRipple(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    combinedClickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}
