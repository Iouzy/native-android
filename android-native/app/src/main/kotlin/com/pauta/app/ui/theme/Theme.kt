package com.pauta.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary          = PautaAccentDef,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    background       = PautaPaper,
    onBackground     = PautaInk,
    surface          = PautaCardBg,
    onSurface        = PautaInk,
    surfaceVariant   = Color(0xFFF0EDE8),
    onSurfaceVariant = PautaMuted,
    outline          = PautaBorder,
    error            = PautaDanger,
    onError          = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF60A5FA),
    onPrimary        = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E3A8A),
    background       = PautaPaperDark,
    onBackground     = PautaInkDark,
    surface          = PautaCardDark,
    onSurface        = PautaInkDark,
    surfaceVariant   = Color(0xFF2A2826),
    onSurfaceVariant = PautaMutedDark,
    outline          = PautaBorderDark,
    error            = Color(0xFFEF4444),
    onError          = Color.White,
)

@Composable
fun PautaTheme(
    theme: String = "auto",     // "auto" | "light" | "dark"
    accent: String = "",        // hex colour or ""
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (theme) {
        "dark"  -> true
        "light" -> false
        else    -> systemDark
    }

    // Override the primary accent colour if the user has set one
    var colors = if (useDark) DarkColorScheme else LightColorScheme
    if (accent.isNotBlank()) {
        val parsed = runCatching {
            Color(android.graphics.Color.parseColor(
                if (accent.startsWith("#")) accent else "#$accent"
            ))
        }.getOrNull()
        if (parsed != null) {
            colors = if (useDark) colors.copy(primary = parsed, primaryContainer = parsed.copy(alpha = 0.3f))
                     else colors.copy(primary = parsed, primaryContainer = parsed.copy(alpha = 0.15f))
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography   = PautaTypography,
        content      = content
    )
}
