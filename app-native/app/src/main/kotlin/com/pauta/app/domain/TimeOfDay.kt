package com.pauta.app.domain

import java.text.Normalizer

/**
 * native-only (F12): a tide's *when*, as three chips over the free-text field
 * that already exists.
 *
 * `HabitEntity.time` is `pauta.v4` data: the web app wrote prose into it, a
 * round-trip must stay lossless, and one tide in the owner's own list reads
 * "manhã e tarde". So this is **a way of writing that string, not a new model** —
 * no column, no migration, and anything the chips don't cover is left exactly as
 * the user typed it.
 *
 * Two decisions worth not re-deriving:
 *
 * - **The stored words are Portuguese**, always. PT is the source language here
 *   and the PT string is the key everywhere else; storing the interface language
 *   would mean the text changed meaning when someone switched languages, and a
 *   tide written in one language would stop matching in the other. The chips
 *   *display* through `tr()`; what lands in the column does not.
 * - **Reading recognises both languages**, so a tide someone typed "morning" into
 *   still lights its chip. Recognition is accent- and case-insensitive for the
 *   same reason the settings search is.
 *
 * // PT: três períodos como forma de escrever o campo de texto que já existe —
 * sem coluna nova e sem perder o que lá estiver escrito. Guarda-se em português;
 * lê-se nas duas línguas.
 */
object TimeOfDay {

    /** The three periods, in the order they happen — which is also the order they
     *  are written back in, so "noite, manhã" comes out "manhã, noite".
     *  // PT: os três períodos, pela ordem do dia. */
    val PERIODS: List<String> = listOf("manhã", "tarde", "noite")

    /** What each period may be spelled as when reading an existing string. The PT
     *  spelling is what gets written; the rest are recognised only.
     *  // PT: as grafias reconhecidas; escreve-se sempre a portuguesa. */
    private val SPELLINGS: Map<String, List<String>> = mapOf(
        "manhã" to listOf("manha", "morning"),
        "tarde" to listOf("tarde", "afternoon"),
        "noite" to listOf("noite", "night", "evening"),
    )

    private val CombiningMarks = Regex("\\p{Mn}+")

    /** Accents off, case off — "Manhã" and "manha" are the same word.
     *  // PT: sem acentos nem maiúsculas. */
    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(CombiningMarks, "").lowercase()

    /** A word boundary that doesn't fire inside a longer word: "amanhã" is not
     *  "manhã", and "tardeza" is not "tarde". // PT: fronteira de palavra, para
     *  "amanhã" não contar como "manhã". */
    private fun mentions(foldedText: String, foldedWord: String): Boolean =
        Regex("(^|[^\\p{L}])" + Regex.escape(foldedWord) + "($|[^\\p{L}])").containsMatchIn(foldedText)

    /** Which periods [time] already names. // PT: os períodos que o texto refere. */
    fun periodsIn(time: String): Set<String> {
        if (time.isBlank()) return emptySet()
        val folded = fold(time)
        return PERIODS.filterTo(LinkedHashSet()) { period ->
            SPELLINGS.getValue(period).any { mentions(folded, it) }
        }
    }

    /**
     * Whatever [time] says *besides* the periods — the part the chips have no
     * opinion about, kept verbatim so "manhã, depois do almoço" doesn't lose the
     * lunch. Separators left stranded by the removal go with it.
     * // PT: o que sobra do texto sem os períodos, para não se perder nada.
     */
    fun remainder(time: String): String {
        if (time.isBlank()) return ""
        // Nothing to remove means nothing to tidy: a field with no period in it
        // comes back exactly as it was typed, which is what "the field stays"
        // has to mean. // PT: sem períodos, o texto volta intacto.
        if (periodsIn(time).isEmpty()) return time.trim()
        var out = time
        for (period in PERIODS) {
            for (spelling in SPELLINGS.getValue(period)) {
                out = Regex(
                    "(^|[^\\p{L}])" + Regex.escape(spelling) + "($|[^\\p{L}])",
                    setOf(RegexOption.IGNORE_CASE),
                ).replace(out) { m -> m.groupValues[1] + m.groupValues[2] }
                // The accented spelling, which `fold` handles on the read side but
                // a literal replace does not. // PT: também a grafia com acento.
                out = Regex(
                    "(^|[^\\p{L}])" + Regex.escape(period) + "($|[^\\p{L}])",
                    setOf(RegexOption.IGNORE_CASE),
                ).replace(out) { m -> m.groupValues[1] + m.groupValues[2] }
            }
        }
        // A conjunction left holding nothing is ours to drop — but only here,
        // after a period was actually removed. Run unconditionally it would eat
        // the "e" out of "café e treino", which is somebody's tide and none of our
        // business. // PT: só se removemos mesmo um período é que a conjunção
        // pendurada é nossa para apagar.
        out = Regex("(^|[^\\p{L}])(e|and)($|[^\\p{L}])", setOf(RegexOption.IGNORE_CASE))
            .replace(out) { m -> m.groupValues[1] + m.groupValues[3] }
        // Collapse the punctuation the removal left behind: ", ,", a leading or
        // trailing comma. // PT: limpa a pontuação que ficou pendurada.
        return out
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(^|\\s)[,;]+"), "$1")
            .replace(Regex("[,;]\\s*[,;]+"), ",")
            .replace(Regex("^[\\s,;]+|[\\s,;]+$"), "")
            .trim()
    }

    /**
     * The string to store for [periods] on top of [time], keeping anything the
     * chips don't cover. Periods come out in day order and comma-separated, which
     * is what the web app's own prose looked like and what reads naturally when a
     * later screen prints it back.
     * // PT: o texto a guardar — períodos por ordem do dia, mais o que já lá estava.
     */
    fun write(time: String, periods: Set<String>): String {
        val chosen = PERIODS.filter { it in periods }
        val rest = remainder(time)
        return when {
            chosen.isEmpty() -> rest
            rest.isEmpty() -> chosen.joinToString(", ")
            else -> chosen.joinToString(", ") + ", " + rest
        }
    }

    /** Turn one period on or off in [time]. // PT: liga ou desliga um período. */
    fun toggle(time: String, period: String): String {
        val current = periodsIn(time)
        val next = if (period in current) current - period else current + period
        return write(time, next)
    }
}
