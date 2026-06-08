package com.pauta.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Parse a stored accent hex (e.g. "#B8533A") into a Compose [Color], falling
 *  back to [DefaultAccent] when null/blank/malformed — mirroring the web, where
 *  `prefs.accent == null` means "use the build default". // PT: converte o hex
 *  guardado em cor; null/ inválido → cor de destaque por omissão. */
fun parseAccentOrDefault(hex: String?): Color {
    if (hex.isNullOrBlank()) return DefaultAccent
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        DefaultAccent
    }
}
