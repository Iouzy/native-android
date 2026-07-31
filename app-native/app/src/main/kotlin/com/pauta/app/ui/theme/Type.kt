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

// P5: the small role set the screens actually use — one headline size for all
// tab faces (the fix for the 38/44/34sp jump when swiping), plus the recurring
// body/label/meta styles. Sizes are sp, so the textScale pref keeps scaling.
// // PT: os papéis tipográficos dos ecrãs — um só tamanho de título para todas
// as tabs, mais corpo/etiqueta/meta; em sp, para a pref de tamanho continuar
// a funcionar.
object PautaType {
    val ScreenTitle = TextStyle(fontFamily = SerifFamily, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val CardTitle = TextStyle(fontFamily = SerifFamily, fontSize = 20.sp, lineHeight = 25.sp)
    val Body = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 21.sp)
    val Label = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 18.sp)
    val Meta = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp)
    val MetaSmall = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp)

    // P7 (the leftover P5 parked): the focus timer's digits. Tabular figures so a
    // rolling second never nudges the line sideways — Geist Mono is fixed-pitch
    // already, but "tnum" also holds the shape if the face ever falls back. Zen's
    // 64sp variant is a `.copy()` of this, so the feature setting travels with it.
    // // PT: os dígitos do cronómetro — algarismos tabulares, sem saltos laterais.
    val Timer = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 42.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.84).sp, // -0.02em of 42sp
        fontFeatureSettings = "tnum",
    )
}

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
