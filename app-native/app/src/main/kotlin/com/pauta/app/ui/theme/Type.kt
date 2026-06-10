package com.pauta.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pauta.app.R

// The real Pauta type, converted from the woff2 files the web vendors:
// Geist and Geist Mono ship as VARIABLE fonts (one file, a wght axis), so each
// weight is the same resource pinned to its axis value; Instrument Serif is a
// static regular + italic pair. The OFL licence texts ship in assets/licenses.
// // PT: as fontes verdadeiras da Pauta — Geist (variável), Geist Mono
// (variável) e Instrument Serif (regular + itálico), como na web.

@OptIn(ExperimentalTextApi::class)
val SansFamily: FontFamily = FontFamily(
    Font(R.font.geist, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.geist, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.geist, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

@OptIn(ExperimentalTextApi::class)
val MonoFamily: FontFamily = FontFamily(
    Font(R.font.geist_mono, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.geist_mono, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.geist_mono, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

val SerifFamily: FontFamily = FontFamily(
    Font(R.font.instrument_serif, weight = FontWeight.Normal),
    Font(R.font.instrument_serif_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)

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
