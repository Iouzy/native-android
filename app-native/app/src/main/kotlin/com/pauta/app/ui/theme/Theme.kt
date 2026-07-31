package com.pauta.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Theme mode mirrors prefs.theme on the web: auto follows the OS. */
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Root theme. Builds the bespoke [PautaColors] for the active mode + accent and
 * publishes them through [LocalPautaColors]; it also maps the core tokens onto a
 * Material3 [androidx.compose.material3.ColorScheme] so stock components inherit
 * the paper/ink/accent look. System bars are made to match the surface (light
 * icons on dark, dark icons on light), edge-to-edge. // PT: tema raiz — constrói
 * os tokens, expõe-os, e alinha as barras de sistema com a superfície.
 */
@Composable
fun PautaTheme(
    mode: ThemeMode = ThemeMode.AUTO,
    accent: Color = DefaultAccent,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkPautaColors(accent, highContrast)
    else lightPautaColors(accent, highContrast)

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onDark,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.paper2,
            onSurfaceVariant = colors.ink2,
            outline = colors.rule,
            error = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onDark,
            background = colors.paper,
            onBackground = colors.ink,
            surface = colors.paper,
            onSurface = colors.ink,
            surfaceVariant = colors.paper2,
            onSurfaceVariant = colors.ink2,
            outline = colors.rule,
            error = colors.accent,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // The window background is `@color/paper`, a DayNight resource — it
            // follows the *system* theme, while the app follows prefs.theme. Once
            // the palette is resolved, repaint it so the two can't disagree: an
            // app set to dark on a light phone no longer flashes pale wherever
            // the window shows (cold start behind the first frame, an activity
            // recreate on a language change, and — before the backdrop in
            // MainScaffold — through the nav cross-fade). // PT: o fundo da
            // janela passa a seguir o tema da app, não o do sistema.
            window.setBackgroundDrawable(ColorDrawable(colors.paper.toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalPautaColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PautaTypography,
            content = content,
        )
    }
}
