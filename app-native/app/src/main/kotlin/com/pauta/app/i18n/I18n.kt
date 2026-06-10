package com.pauta.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

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
    /** Backed by Compose snapshot state: every composable that calls [tr] during
     *  composition subscribes to it, so flipping the language in Settings
     *  re-renders the whole UI live — the same behaviour as the web app, where
     *  components re-render and tr() re-reads PAUTA_LANG. Plain (non-compose)
     *  readers like the notification builders just see the current value.
     *  // PT: estado de snapshot — mudar a língua recompõe a UI toda em direto. */
    var lang: Lang by mutableStateOf(Lang.PT)

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

    // ── Dates ─────────────────────────────────────────────────
    // Hand-tuned day/month names ported from store.jsx DATE_NAMES: both languages
    // keep the same compact European day-month layout so the typography matches
    // the web app exactly (locale formatters would say "quarta-feira"; the app
    // says "quarta"). // PT: nomes de datas calibrados à mão, como na web.
    private val DAYS_PT = listOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
    private val DAYS_EN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val MONTHS_SHORT_PT = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val MONTHS_SHORT_EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_LONG_PT = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    private val MONTHS_LONG_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    private fun dayName(date: LocalDate): String {
        val sundayFirst = date.dayOfWeek.value % 7 // ISO Mon=1..Sun=7 → Sun=0..Sat=6, like JS getDay()
        return (if (lang == Lang.EN) DAYS_EN else DAYS_PT)[sundayFirst]
    }

    /** "quarta, 10 jun" / "Wednesday, 10 Jun" — web fmtDateLong. */
    fun fmtDateLong(date: LocalDate): String {
        val months = if (lang == Lang.EN) MONTHS_SHORT_EN else MONTHS_SHORT_PT
        return "${dayName(date)}, ${date.dayOfMonth} ${months[date.monthValue - 1]}"
    }

    /** "10 jun" / "10 Jun" — web fmtDateShort. */
    fun fmtDateShort(date: LocalDate): String {
        val months = if (lang == Lang.EN) MONTHS_SHORT_EN else MONTHS_SHORT_PT
        return "${date.dayOfMonth} ${months[date.monthValue - 1]}"
    }

    /** "Junho · 2026" / "June · 2026" — web fmtMonthYear. */
    fun fmtMonthYear(year: Int, month: Int): String {
        val months = if (lang == Lang.EN) MONTHS_LONG_EN else MONTHS_LONG_PT
        return "${months[month - 1]} · $year"
    }

    /**
     * English dictionary. Wherever the same Portuguese key exists in the web
     * app's I18N_EN (src/i18n.jsx), the value here is copied verbatim so both
     * builds speak identical English. Native-only keys are marked as such.
     * // PT: valores copiados do dicionário da web sempre que a chave existe lá.
     */
    val EN: Map<String, String> = mapOf(
        // ── Shell / navigation ──
        "Hoje" to "Today",
        "Pauta" to "Pauta",
        "Marés" to "Tides",
        "Definições" to "Settings",

        // ── Hoje ──
        "Histórico" to "History",
        "O que importa" to "What matters",
        "hoje" to "today",
        "{d}/{t} intenções" to "{d}/{t} intentions",
        "{d} em foco" to "{d} in focus",
        "{d}/{t} marés" to "{d}/{t} tides",
        "Trazer {n} de {d}" to "Bring {n} from {d}", // native-only
        "Nova intenção…" to "New intention…",
        "manhã" to "morning",
        "tarde" to "afternoon",
        "noite" to "night",
        "sem hora" to "no time",
        "min" to "min", // native-only
        "Adicionar" to "Add",
        "Ainda sem intenções para hoje." to "No intentions for today yet.", // native-only
        "Reflexão da noite" to "Evening reflection",
        "O que valeu hoje?" to "What was worth it today?",
        "Marés de hoje" to "Today's tides",
        "respiro" to "breath",
        "Escreva quando quiser. Não precisa de ser longo." to "Write whenever you like. It needn't be long.",
        "amanhã, recomeça." to "tomorrow, begin again.",
        "Apagar" to "Delete", // native-only
        "Ainda sem dias anteriores." to "No previous days yet.", // native-only
        "Reflexão" to "Reflection",

        // ── Pauta ──
        "{n} blocos · {t} de foco hoje" to "{n} blocks · {t} of focus today", // native-only
        "Em pausa" to "Paused",
        "Retomar" to "Resume",
        "Concluir" to "Finish",
        "Concluídos" to "Finished", // native-only
        "Sem blocos de foco ainda. Toca em + para começar." to "No focus blocks yet. Tap + to start.", // native-only
        "Começar bloco" to "Start a block", // native-only
        "Novo bloco" to "New block",
        "Em que vais focar?" to "What will you focus on?",
        "Iniciar agora" to "Start now",
        "Cancelar" to "Cancel",
        "Concluir bloco" to "Finish block",
        "O que aconteceu?" to "What happened?",
        "alvo {t}" to "target {t}", // native-only
        "Pausar" to "Pause",
        "Foco" to "Focus",

        // Pauta sheets (sheets.jsx, web-identical).
        "\"{t}\" será automaticamente pausado." to "\"{t}\" will be automatically paused.",
        "ex.: escrever capítulo 3" to "e.g.: write chapter 3",
        "ou continue com…" to "or continue with…",
        "retomar de antes" to "resume from before",
        "projecto (opcional)" to "project (optional)",
        "ex.: Livro, Cliente X, Casa" to "e.g.: Book, Client X, House",
        "duração (opcional)" to "duration (optional)",
        "Sem limite" to "No limit",
        "Pausar bloco" to "Pause block",
        "pausado" to "paused",
        "O que ficou em mente? (opcional)" to "What's on your mind? (optional)",
        "ex.: travei no segundo parágrafo" to "e.g.: got stuck on the second paragraph",
        "Confirmar" to "Confirm",
        "✓ {d} em foco" to "✓ {d} in focus",
        "Planeado {p} · real {a}" to "Planned {p} · actual {a}",
        "ex.: terminei o esqueleto. ficou faltando a conclusão." to "e.g.: finished the outline. still missing the conclusion.",
        "marcar" to "mark",
        "como concluído no Hoje" to "as finished in Today",
        "fechar" to "close",

        // ── Marés ──
        "mês anterior" to "previous month", // native-only
        "mês seguinte" to "next month", // native-only
        "{p}% de constância" to "{p}% consistency", // native-only
        "Sem marés ainda. Toca em + para criar a primeira." to "No tides yet. Tap + to create the first one.", // native-only
        "Nova maré" to "New tide", // native-only
        "Nome do hábito" to "Habit name", // native-only
        "Diária" to "Daily", // native-only
        "Semanal" to "Weekly", // native-only
        "Mensal" to "Monthly", // native-only
        "meta" to "target", // native-only
        "unidade (ex: L, km)" to "unit (e.g. L, km)", // native-only
        "Criar" to "Create", // native-only

        // Tide tiers (names + subtitles, web-identical).
        "Tsunami" to "Tsunami",
        "Oceano" to "Ocean",
        "Maré anual" to "Yearly tide",
        "Maré viva" to "Spring tide",
        "Maré alta" to "High tide",
        "Maré média" to "Mid tide",
        "Maré baixa" to "Low tide",
        "Onda" to "Wave",
        "uma força que ninguém trava" to "a force nobody can stop",
        "já não é uma maré" to "it's no longer a tide",
        "quase um ano de constância" to "almost a year of constancy",
        "a mais forte do mês" to "the strongest of the month",
        "a corrente é forte" to "the current is strong",
        "a corrente assentou" to "the current has settled",
        "a água começou a subir" to "the water has started to rise",
        "primeira agitação" to "first stirring",

        // Navigator levels (names + subtitles, web-identical).
        "Almirante" to "Admiral",
        "Navegador" to "Navigator",
        "Capitão" to "Captain",
        "Piloto" to "Pilot",
        "Timoneiro" to "Helmsman",
        "Marujo" to "Sailor",
        "Grumete" to "Deckhand",
        "Aprendiz" to "Apprentice",
        "mestre dos mares" to "master of the seas",
        "com N maiúsculo" to "with a capital N",
        "comanda a sua embarcação" to "commands their vessel",
        "lê a água e o tempo" to "reads the water and the weather",
        "leme firme" to "steady at the helm",
        "já é da tripulação" to "is part of the crew now",
        "aprendeu as cordas" to "learned the ropes",
        "pés ainda em terra firme" to "feet still on solid ground",

        // ── Objetivos ──
        "Objetivos" to "Goals", // native-only
        "Objetivos trimestrais" to "Quarterly goals", // native-only
        "Novo objetivo…" to "New goal…", // native-only
        "Sem objetivos neste trimestre." to "No goals this quarter.", // native-only
        "Novo marco…" to "New milestone…", // native-only

        // ── Definições ──
        "Aparência" to "Appearance",
        "Tema" to "Theme",
        "Auto" to "Auto",
        "Claro" to "Light",
        "Escuro" to "Dark",
        "Cor de destaque" to "Accent colour",
        "Idioma" to "Language",
        "Língua" to "Language", // native-only
        "Acessibilidade" to "Accessibility",
        "Alto contraste" to "High contrast",
        "Reduzir movimento" to "Reduce motion",
        "Companhia" to "Company", // native-only
        "Vibração" to "Haptics",
        "Papagaio ajudante" to "Parrot helper",
        "Lembretes" to "Reminders",
        "Lembretes diários" to "Daily reminders", // native-only
        "Plano do dia" to "Today's plan",
        "Hábitos pendentes" to "Pending habits",
        "Reflexão noturna" to "Nightly reflection",
        "Dados" to "Data",
        "Exportar dados" to "Export data",
        "Importar dados" to "Import data",
        "Atualizações" to "Updates", // native-only
        "A verificar…" to "Checking…",
        "Transferir nova versão" to "Download new version",
        "Está atualizado." to "Up to date.",
        "Verificar atualizações" to "Check for updates",

        // ── Widget ──
        "Foco {m}m" to "Focus {m}m",
    )
}

/** Convenience top-level aliases so call sites read like the web (`tr(...)`). */
fun tr(pt: String): String = I18n.tr(pt)
fun trf(pt: String, vararg args: Pair<String, Any?>): String = I18n.trf(pt, *args)
