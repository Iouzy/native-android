package com.pauta.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The full Pauta design-token palette, ported 1:1 from the CSS custom properties
 * in index.html (:root, html[data-theme="dark"], html[data-contrast="high"]).
 *
 * Material3's [androidx.compose.material3.ColorScheme] only carries a handful of
 * roles, but Pauta's look leans on many bespoke tokens (paper-2/3, ink-2/3/4,
 * rule, the inverse "dark surface" used by the focus card, etc.). So we keep the
 * whole token set here and expose it through [LocalPautaColors], exactly as the
 * web reads `var(--paper-2)` everywhere. // PT: paleta completa de tokens, igual
 * às custom properties do index.html; o Material3 só guarda alguns papéis, por
 * isso mantemos o conjunto todo e expomo-lo via CompositionLocal.
 */
@Immutable
data class PautaColors(
    val paper: Color,
    val paper2: Color,
    val paper3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val rule: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentBg: Color,
    val good: Color,
    // Inverse surfaces (the active focus card, primary buttons) stay dark in both
    // themes. // PT: superfícies invertidas mantêm-se escuras nos dois temas.
    val surfaceDark: Color,
    val onDark: Color,
    val onDark2: Color,
    val tabbarBg: Color,
    val pageBg: Color,
    val isDark: Boolean,
)

private val Accent = Color(0xFFB8533A)
private val AccentSoft = Color(0xFFE8C3B5)
private val AccentBg = Color(0xFFF4E2DA)

/** Light palette — index.html :root. */
fun lightPautaColors(accent: Color = Accent, highContrast: Boolean = false) = PautaColors(
    paper = Color(0xFFF5F1EA),
    paper2 = Color(0xFFEDE7DC),
    paper3 = Color(0xFFE4DDCE),
    ink = Color(0xFF1A1815),
    ink2 = Color(0xFF4A453C),
    ink3 = if (highContrast) Color(0xFF44403A) else Color(0xFF6D665A),
    ink4 = if (highContrast) Color(0xFF5C564C) else Color(0xFFB5AC9C),
    rule = if (highContrast) Color(0xFFA79E8E) else Color(0xFFD9D2C5),
    accent = accent,
    accentSoft = AccentSoft,
    accentBg = AccentBg,
    good = Color(0xFF5A6B3E),
    surfaceDark = Color(0xFF1A1815),
    onDark = Color(0xFFF5F1EA),
    onDark2 = Color(0xFFB5AC9C),
    tabbarBg = Color(0xEBF5F1EA), // rgba(245,241,234,0.92)
    pageBg = Color(0xFFD6CFC2),
    isDark = false,
)

/** Dark palette — index.html html[data-theme="dark"]. Accent tokens are NOT
 *  overridden by the dark block in CSS, so they inherit the light values. */
fun darkPautaColors(accent: Color = Accent, highContrast: Boolean = false) = PautaColors(
    paper = Color(0xFF1B1A17),
    paper2 = Color(0xFF232220),
    paper3 = Color(0xFF2C2A26),
    ink = Color(0xFFECE6DA),
    ink2 = Color(0xFFC3BCAD),
    ink3 = if (highContrast) Color(0xFFD6D0C3) else Color(0xFF8A8275),
    ink4 = if (highContrast) Color(0xFFB3AC9E) else Color(0xFF5F5A50),
    rule = if (highContrast) Color(0xFF5A554C) else Color(0xFF322F2A),
    accent = accent,
    accentSoft = AccentSoft,
    accentBg = AccentBg,
    good = Color(0xFF8DA068),
    surfaceDark = Color(0xFF2A2824),
    onDark = Color(0xFFF2ECE0),
    onDark2 = Color(0xFFB5AC9C),
    tabbarBg = Color(0xEB141310), // rgba(20,19,16,0.92)
    pageBg = Color(0xFF0F0E0C),
    isDark = true,
)

/** Default accent, used before prefs load. */
val DefaultAccent: Color = Accent

val LocalPautaColors = staticCompositionLocalOf { lightPautaColors() }

// native-only: sepia/parchment palette for book mode; all accent + inverse
// tokens are identical to the base palette so the accent colour stays consistent.
fun bookPautaColors(dark: Boolean): PautaColors = if (dark) PautaColors(
    paper = Color(0xFF28190F),
    paper2 = Color(0xFF332010),
    paper3 = Color(0xFF3D2818),
    ink = Color(0xFFEBD9C0),
    ink2 = Color(0xFFC8A882),
    ink3 = Color(0xFF9A7855),
    ink4 = Color(0xFF6B5038),
    rule = Color(0xFF4A3020),
    accent = Accent,
    accentSoft = AccentSoft,
    accentBg = AccentBg,
    good = Color(0xFF8DA068),
    surfaceDark = Color(0xFF2A2824),
    onDark = Color(0xFFF2ECE0),
    onDark2 = Color(0xFFB5AC9C),
    tabbarBg = Color(0xFF28190F),
    pageBg = Color(0xFF28190F),
    isDark = true,
) else PautaColors(
    paper = Color(0xFFF2E8D5),
    paper2 = Color(0xFFE8D9BC),
    paper3 = Color(0xFFD9C9A8),
    ink = Color(0xFF2C1A0E),
    ink2 = Color(0xFF5C3A1E),
    ink3 = Color(0xFF8C6540),
    ink4 = Color(0xFFB89870),
    rule = Color(0xFFD4B896),
    accent = Accent,
    accentSoft = AccentSoft,
    accentBg = AccentBg,
    good = Color(0xFF5A6B3E),
    surfaceDark = Color(0xFF1A1815),
    onDark = Color(0xFFF5F1EA),
    onDark2 = Color(0xFFB5AC9C),
    tabbarBg = Color(0xFFF2E8D5),
    pageBg = Color(0xFFF2E8D5),
    isDark = false,
)
