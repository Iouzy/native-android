package com.pauta.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * native-only (R3): the two window flags a full-surface screen may claim for as
 * long as it is on screen, over and above the user's preferences.
 *
 * The reader needs both: nothing but the page while reading (whatever the
 * `immersive` pref says), and no screen timeout mid-page (still gated on the
 * `keepAwake` pref, which stays the user's call). `MainActivity` reads these
 * during composition, so setting one recomposes and re-applies the window flags;
 * clearing it on dispose restores exactly what the preferences ask for.
 *
 * Snapshot state rather than a pref: this is a property of what is on screen, not
 * a setting, and it must never survive the screen that set it. // PT: bandeiras de
 * janela pedidas por um ecrã enquanto está aberto — não são preferências e não
 * sobrevivem ao ecrã.
 */
object ScreenMode {

    /** Hide the system bars regardless of the `immersive` preference. */
    var immersive: Boolean by mutableStateOf(false)

    /** Ask for FLAG_KEEP_SCREEN_ON — still subject to the `keepAwake` preference. */
    var keepAwake: Boolean by mutableStateOf(false)
}
