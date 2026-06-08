package com.pauta.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light palette (matches CSS --paper / --ink / --accent defaults)
val PautaPaper      = Color(0xFFF8F5F0)
val PautaInk        = Color(0xFF1A1A1A)
val PautaAccentDef  = Color(0xFF2563EB)   // default blue
val PautaMuted      = Color(0xFF888888)
val PautaBorder     = Color(0xFFE0DDD8)
val PautaCardBg     = Color(0xFFFFFFFF)
val PautaSuccess    = Color(0xFF059669)
val PautaWarning    = Color(0xFFF59E0B)
val PautaDanger     = Color(0xFFDC2626)

// Dark palette
val PautaPaperDark  = Color(0xFF1A1918)
val PautaInkDark    = Color(0xFFF0EDE8)
val PautaMutedDark  = Color(0xFF888888)
val PautaBorderDark = Color(0xFF333333)
val PautaCardDark   = Color(0xFF252422)

// Habit tier colours
val TierWave        = Color(0xFF93C5FD)   // sky-300
val TierLow         = Color(0xFF60A5FA)   // blue-400
val TierMid         = Color(0xFF3B82F6)   // blue-500
val TierHigh        = Color(0xFF2563EB)   // blue-600
val TierSpring      = Color(0xFF1D4ED8)   // blue-700
val TierAnnual      = Color(0xFF1E40AF)   // blue-800
val TierOcean       = Color(0xFF1E3A8A)   // blue-900
val TierTsunami     = Color(0xFF172554)   // blue-950

fun tierColor(streakDays: Int): Color = when {
    streakDays >= 720 -> TierTsunami
    streakDays >= 360 -> TierOcean
    streakDays >= 240 -> TierAnnual
    streakDays >= 120 -> TierSpring
    streakDays >= 60  -> TierHigh
    streakDays >= 30  -> TierMid
    streakDays >= 7   -> TierLow
    streakDays >= 1   -> TierWave
    else              -> Color(0xFFCCCCCC)
}
