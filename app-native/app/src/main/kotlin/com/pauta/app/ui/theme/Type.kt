package com.pauta.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Type families. The web vendors Geist / Geist Mono / Instrument Serif as woff2.
// Android can't load woff2 as a resource font, so Phase 1 will vendor ttf/otf
// versions into res/font and point these FontFamily values at them. Until then
// we fall back to the platform sans / serif / monospace so the layout and scale
// are already correct. // PT: por agora caímos para as fontes do sistema; as
// fontes próprias (Geist / Instrument Serif) entram em res/font na Fase 1.
val SansFamily: FontFamily = FontFamily.SansSerif
val SerifFamily: FontFamily = FontFamily.Serif
val MonoFamily: FontFamily = FontFamily.Monospace

val PautaTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifFamily, fontSize = 40.sp, lineHeight = 44.sp),
    displayMedium = TextStyle(fontFamily = SerifFamily, fontSize = 32.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = SansFamily, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SansFamily, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp),
)
