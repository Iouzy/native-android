package com.pauta.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** App language. Portuguese (pt-PT) is the SOURCE language — the Portuguese
 *  string is itself the lookup key, exactly like the web app's tr(). */
enum class Lang { PT, EN }

/**
 * In-code i18n, mirroring src/i18n.jsx: write every user-facing string in
 * Portuguese and wrap it in [tr] (or [trf] for interpolation). The PT string IS
 * the key; English values live in [EN]. Missing keys fall back to the Portuguese
 * text, so untranslated strings degrade gracefully. // PT: PT é a língua-fonte;
 * a própria string PT é a chave; o inglês vem do dicionário [EN].
 *
 * This deliberately avoids Android's strings.xml for app copy so the PT-as-key
 * model and graceful fallback match the web one-to-one. strings.xml only holds
 * the launcher label and other system-surface text.
 */
object I18n {
    @Volatile
    var lang: Lang = Lang.PT

    /** Simple lookup: returns the EN value when in English and present, else the
     *  Portuguese source string. */
    fun tr(pt: String): String =
        if (lang == Lang.EN) EN[pt] ?: pt else pt

    /** Interpolating lookup. Placeholders are `{name}`; pass them in [args].
     *  e.g. trf("{d}/{t} intenções", "d" to 2, "t" to 5). */
    fun trf(pt: String, vararg args: Pair<String, Any?>): String {
        var s = tr(pt)
        for ((k, v) in args) s = s.replace("{$k}", v.toString())
        return s
    }

    /** English dictionary. Grows alongside the UI; seeded here with the nav +
     *  the handful of strings used by the Phase-0 shell. */
    val EN: Map<String, String> = mapOf(
        "Hoje" to "Today",
        "Pauta" to "Focus",
        "Marés" to "Tides",
        "O que importa hoje?" to "What matters today?",
        "Em construção" to "Under construction",
    )
}

/** Convenience top-level aliases so call sites read like the web (`tr(...)`). */
fun tr(pt: String): String = I18n.tr(pt)
fun trf(pt: String, vararg args: Pair<String, Any?>): String = I18n.trf(pt, *args)

/** Lets Composables re-read when the language flips at runtime. */
val LocalLang = staticCompositionLocalOf { Lang.PT }
