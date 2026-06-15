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

    /** "quarta, 10" / "Wednesday, 10" — the dim weekday+day prefix of the
     *  period eyebrow (the accent month token is rendered separately). */
    fun fmtWeekdayDay(date: LocalDate): String = "${dayName(date)}, ${date.dayOfMonth}"

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

    /** "Junho" / "June" — web fmtMonthLong (the Marés header). */
    fun fmtMonthLong(month: Int): String =
        (if (lang == Lang.EN) MONTHS_LONG_EN else MONTHS_LONG_PT)[month - 1]

    /** "jun" / "Jun" — web fmtMonthShort (chart ticks). */
    fun fmtMonthShort(month: Int): String =
        (if (lang == Lang.EN) MONTHS_SHORT_EN else MONTHS_SHORT_PT)[month - 1]

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
        "Nova intenção" to "New intention", // native-only
        "Adicionar como intenção de hoje?" to "Add as today's intention?", // native-only
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
        "dia anterior" to "previous day", // native-only
        "dia seguinte" to "next day", // native-only
        "Sem conteúdo neste dia." to "No content for this day.", // native-only
        "Reflexão" to "Reflection",
        // E1 · search in the History view
        "Procurar…" to "Search…", // native-only
        "Sem resultados." to "No results.", // native-only
        "Limpar" to "Clear", // native-only
        "intenção" to "intention", // native-only
        "dias anteriores" to "previous days",
        "a semana" to "the week",
        "A semana" to "The week",
        "Deixe preparado o que importa nos próximos dias. Cada plano vira as intenções desse dia quando ele chegar." to "Set up what matters over the next days. Each plan becomes that day's intentions when it arrives.",
        "planear intenção…" to "plan an intention…",
        "Marca um dia qualquer do período. Depois disso, os restantes ficam bloqueados." to "Mark any day of the period. After that, the rest are locked.",
        "Repete sempre no mesmo dia. Os outros dias do período ficam bloqueados." to "Always repeats on the same day. The other days of the period are locked.",

        // ── Rotinas (D1: intention templates) ──
        // Ported from the web app (original EN values kept verbatim).
        "Rotinas" to "Routines",
        "guardar como rotina" to "save as routine",
        "nome da rotina…" to "routine name…",
        // The manager sheet itself is native-only.
        "Modelos de intenções. Aplique uma para semear o dia ou guarde o dia atual como rotina." to "Templates of intentions. Apply one to seed the day, or save the current day as a routine.", // native-only
        "criar" to "create", // native-only
        "aplicar" to "apply", // native-only
        "aplicada" to "applied", // native-only
        "adicionar intenção…" to "add an intention…", // native-only
        "Sem rotinas ainda." to "No routines yet.", // native-only
        "subir" to "move up", // native-only
        "descer" to "move down", // native-only

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
        "Trocar foco" to "Switch focus",
        "Trocar" to "Switch",
        "em curso →" to "in progress →",
        "Pausar e ir para…" to "Pause and go to…",
        "ex.: revisar PRs" to "e.g.: review PRs",
        "OK" to "OK",
        "algo novo (não está no Hoje)" to "something new (not in Today)",
        "ou concluir" to "or finish",
        "primeiro" to "first",
        "Registar tempo" to "Log time",
        "Esqueceu-se de iniciar o cronómetro? Registe o bloco à mão." to "Forgot to start the timer? Log the block by hand.",
        "O quê" to "What",
        "Data" to "Date",
        "Início" to "Start",
        "Escolher hora" to "Pick time", // native-only
        "Duração (min)" to "Duration (min)",
        "ex.: leitura" to "e.g. reading",
        "registar" to "log",
        "limpar" to "clear",

        // ── Marés ──
        "mês anterior" to "previous month", // native-only
        "mês seguinte" to "next month", // native-only
        "Maré actual" to "Current tide",
        "Maré passada" to "Past tide",
        "marés passadas" to "past tides",
        "toque" to "tap",
        "marca feito" to "marks done",
        "pressão longa" to "long press",
        "num dia vazio marca respiro" to "on an empty day marks a breath",
        "dias passados são editáveis — a honestidade é o melhor amigo da maré." to "past days are editable — honesty is the tide's best friend.",
        "semana {obs}/{total}" to "week {obs}/{total}",
        "mês {obs}/{total}" to "month {obs}/{total}",
        "dia {obs}/{total}" to "day {obs}/{total}",
        "melhor: {n} dias" to "best: {n} days",
        "remover" to "remove",
        "Remover esta maré? Todo o histórico será perdido." to "Remove this tide? All history will be lost.",
        // A7 — undo + habit archive (native-only).
        "Anular" to "Undo", // native-only
        "Intenção removida" to "Intention removed", // native-only
        "Bloco removido" to "Block removed", // native-only
        "arquivar maré" to "archive tide", // native-only
        "restaurar maré" to "restore tide", // native-only
        "Arquivar esconde a maré da grelha sem perder o histórico." to "Archiving hides the tide from the grid without losing its history.", // native-only
        "Marés arquivadas" to "Archived tides", // native-only
        "As marés arquivadas saem da grelha e do dia, mas guardam todo o histórico." to "Archived tides leave the grid and today, but keep all their history.", // native-only
        "1 maré escondida da grelha." to "1 tide hidden from the grid.", // native-only
        "{n} marés escondidas da grelha." to "{n} tides hidden from the grid.", // native-only
        "restaurar" to "restore", // native-only
        "semanal" to "weekly",
        "mensal" to "monthly",
        "dia {n}" to "day {n}",
        "resp." to "br.",
        "×" to "×",
        "em {month}." to "in {month}.",
        "maré ainda não existia" to "tide didn't exist yet",
        "marés ainda não existiam" to "tides didn't exist yet",
        "domingo" to "sunday",
        "segunda" to "monday",
        "terça" to "tuesday",
        "quarta" to "wednesday",
        "quinta" to "thursday",
        "sexta" to "friday",
        "sábado" to "saturday",

        // Marés Passadas (TrendSheet, web-identical).
        "Marés Passadas" to "Past Tides",
        "As suas marés ao longo do ano." to "Your tides through the year.",
        "Sobem e descem. Cada onda é um mês. Toque para ver as marés desse mês." to "They rise and fall. Each wave is a month. Tap to see that month's tides.",
        "Ainda não há marés suficientes para mostrar uma onda." to "Not enough tides yet to show a wave.",
        "Volte aqui dentro de alguns meses." to "Come back in a few months.",
        "o seu posto" to "your rank",
        "dias feitos" to "days done",
        "faltam {n} dias para" to "{n} days left until",
        "abrir este mês" to "open this month", // native-only

        // Histórico da maré (HabitDetailSheet, web-identical).
        "Histórico da maré" to "Tide history",
        "desde {date} · {n} {unit}" to "since {date} · {n} {unit}",
        "dia" to "day",
        "dias" to "days",
        "maré actual" to "current tide",
        "respiros" to "breaths",
        "Total" to "Total",
        "Actual" to "Current",
        "Melhor" to "Best",
        "Respiros" to "Breaths",
        "→ {name} em {n}d" to "→ {name} in {n}d",
        "→ {name} em {n} {u}" to "→ {name} in {n} {u}",
        "Toque num dia para marcar como feito." to "Tap a day to mark it as done.",
        "Pressão longa num dia falhado para marcar respiro." to "Long press a missed day to mark a breath.",
        "Vista de sempre — para marcar ou corrigir um dia, usa a grelha do mês no separador Marés." to "All-time view — to mark or fix a day, use the month grid in the Marés tab.", // native-only
        "{p}% de constância" to "{p}% consistency", // native-only
        "Sem marés ainda. Toca em + para criar a primeira." to "No tides yet. Tap + to create the first one.", // native-only
        "Nova maré" to "New tide", // native-only
        "Sem marés para hoje" to "No tides today", // native-only (Glance widget empty state)

        // Marés add/edit form (NewHabitForm + HabitEditForm, web-identical).
        "Nome da maré (ex.: meditar)" to "Tide name (e.g.: meditate)",
        "Quando? (opcional, ex.: manhã)" to "When? (optional, e.g.: morning)",
        "+ mais opções (descrição, recorrência)" to "+ more options (description, recurrence)",
        "Porquê esta maré? (opcional)" to "Why this tide? (optional)",
        "hora certa" to "exact time",
        "opcional" to "optional",
        "frequência" to "frequency",
        "diária" to "daily",
        "escolho eu" to "i choose",
        "dia fixo" to "fixed day",
        "seg" to "mon",
        "ter" to "tue",
        "qua" to "wed",
        "qui" to "thu",
        "sex" to "fri",
        "sáb" to "sat",
        "dom" to "sun",
        "dias da semana" to "weekdays",
        "todos os dias" to "every day",
        "só nos dias escolhidos" to "only on chosen days",
        "Repete sempre no mesmo dia. Os outros dias do período ficam bloqueados." to
            "Always repeats on the same day. The other days of the period are locked.",
        "Marca um dia qualquer do período. Depois disso, os restantes ficam bloqueados." to
            "Mark any day of the period. After that, the rest are locked.",
        "uma meta com quantidade (ex.: 2L de água, 3 treinos)" to "a goal with a quantity (e.g.: 2L of water, 3 workouts)",
        "meta com quantidade (ex.: 2L de água, 3 treinos)" to "goal with quantity (e.g.: 2L of water, 3 workouts)",
        "unidade (copos, treinos…)" to "unit (glasses, workouts…)",
        "do mês" to "of the month",
        "recorrência" to "recurrence",
        "permanente" to "permanent",
        "por um período" to "for a period",
        "só este mês" to "this month only",
        "todos os dias, sem fim" to "every day, no end",
        "durante X dias" to "for X days",
        "termina no fim do mês" to "ends at the end of the month",
        "durante" to "for",
        "nome" to "name",
        "Cor" to "Colour",
        "automático" to "automatic",
        "quando" to "when",
        "ex.: manhã, antes de dormir" to "e.g.: morning, before bed",
        "descrição · porquê esta maré?" to "description · why this tide?",
        "A intenção, o motivo, o sentimento que quer cultivar." to "The intention, the reason, the feeling you want to cultivate.",
        "Guardar" to "Save",
        "editar" to "edit",
        "remover esta maré" to "remove this tide",
        "Editar maré" to "Edit tide", // native-only

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

        // ── Revisão (InsightsSheet, web-identical) ──
        "Revisão" to "Review",
        "revisão" to "review",
        "A sua semana, de relance." to "Your week, at a glance.",
        "Sem julgamento. Só o que aconteceu, para reparar no padrão." to "No judgement. Just what happened, to notice the pattern.",
        "Mês" to "Month",
        "Padrões" to "Patterns",
        "Melhor hora do dia" to "Best hour of the day",
        "Hábitos × foco" to "Habits × focus",
        "Calendário de foco" to "Focus calendar",
        "foco" to "focus",
        "foco · {n} {label}" to "focus · {n} {label}",
        "dias activos" to "active days",
        "dia activo" to "active day",
        "intenções feitas" to "intentions done",
        "hábitos · {n} feitos" to "habits · {n} done",
        "↑ +{pct}% foco vs. semana anterior ({prev})" to "↑ +{pct}% focus vs. previous week ({prev})",
        "↓ {pct}% foco vs. semana anterior ({prev})" to "↓ {pct}% focus vs. previous week ({prev})",
        "= mesmo foco da semana anterior ({prev})" to "= same focus as previous week ({prev})",
        "Pico: {d} com {t} em foco." to "Peak: {d} with {t} in focus.", // native-only composite
        "Escreveu {n} {label}." to "You wrote {n} {label}.",
        "reflexão" to "reflection",
        "reflexões" to "reflections",
        "Marés esta semana" to "Tides this week",
        "1 respiro esta semana. Honesto." to "1 breath this week. Honest.",
        "{n} respiros esta semana. Honesto." to "{n} breaths this week. Honest.",
        "Ainda sem blocos suficientes para encontrar a sua melhor hora." to "Not enough blocks yet to find your best hour.",
        "a sua hora mais focada" to "your most focused hour",
        "Sem padrões suficientes ainda. Continue a registar hábitos e blocos — em poucas semanas aparecem aqui ligações." to
            "Not enough patterns yet. Keep logging habits and blocks — in a few weeks connections will appear here.",
        "Nos dias com" to "On days with",
        ", foca" to ", focus",
        "Ainda a juntar padrões. Continue a usar a app — em poucos dias aparecem aqui." to
            "Still gathering patterns. Keep using the app — they'll show up in a few days.",
        "A tua maré mais constante é {name} — {n} dias seguidos." to "Your most constant tide is {name} — {n} days running.",
        "Focas mais por volta das {h}h." to "You focus most around {h}h.",
        "Concluíste {pct}% das intenções de prioridade alta." to "You completed {pct}% of your high-priority intentions.",
        "d,s,t,q,q,s,s" to "s,m,t,w,t,f,s",
        "{focus} em foco · {days} {label}" to "{focus} in focus · {days} {label}",

        // ── Definições ──
        "Análise" to "Analysis",
        "Revisão semanal" to "Weekly review",
        "Foco, hábitos e padrões dos últimos 7 dias." to "Focus, habits and patterns from the last 7 days.",
        "Hoje · Pauta · Marés" to "Today · Pauta · Tides",
        "Aparência" to "Appearance",
        "Tema" to "Theme",
        "Auto" to "Auto",
        "Claro" to "Light",
        "Escuro" to "Dark",
        "Cor de destaque" to "Accent colour",
        "Idioma" to "Language",
        "Língua" to "Language", // native-only
        "Pequeno toque ao concluir." to "A small tap when done.",
        "O Pip aparece com dicas e piadas. Toca-lhe para mais." to "Pip shows tips and jokes. Tap it for more.",
        "Esconde as barras do sistema. Deslize da margem para as ver." to "Hides system bars. Swipe from the edge to see them.",
        "Acessibilidade" to "Accessibility",
        "Alto contraste" to "High contrast",
        "Reforça o texto e as linhas. Segue o sistema por omissão." to "Strengthens text and lines. Follows the system by default.",
        "Reduzir movimento" to "Reduce motion",
        "Desliga animações. Segue o sistema por omissão." to "Turns off animations. Follows the system by default.",
        "Tamanho do texto" to "Text size",
        "Normal" to "Normal",
        "Grande" to "Large",
        "Maior" to "Larger",
        "Ecrã inteiro" to "Fullscreen",
        "Manter ecrã ligado" to "Keep screen on",
        "Não deixa o telemóvel adormecer durante um bloco." to "Keeps the phone awake during a block.",
        "Companhia" to "Company", // native-only
        "Vibração" to "Haptics",
        "Papagaio ajudante" to "Parrot helper",
        "Som ao concluir" to "Completion sound",
        "Um sino suave ao terminar um bloco ou atingir a meta." to "A soft chime when finishing a block or hitting the goal.",
        "Lembretes" to "Reminders",
        "Notificações" to "Notifications",
        "Avisos locais enquanto a app está aberta." to "Local alerts while the app is open.",
        "Sem servidor: os avisos só chegam com a app aberta no telemóvel." to "No server: alerts only arrive with the app open on your phone.",
        "Testar notificação" to "Test notification",
        "Notificação de teste enviada." to "Test notification sent.",
        "Não foi possível enviar a notificação de teste." to "Couldn't send the test notification.",
        "Lembretes diários" to "Daily reminders", // native-only
        "Plano do dia" to "Today's plan",
        "Hábitos pendentes" to "Pending habits",
        "Reflexão noturna" to "Nightly reflection",
        // C2 · actionable notifications
        "Responder" to "Reply", // native-only
        "Escreve a tua reflexão…" to "Write your reflection…", // native-only
        "Reflexão guardada" to "Reflection saved", // native-only
        "1 maré por completar" to "1 tide to complete", // native-only
        "{n} marés por completar" to "{n} tides to complete", // native-only
        "Alvo · {t}" to "Target · {t}", // native-only
        "Alvo de foco" to "Focus target", // native-only
        "Alvo de foco alcançado" to "Focus target reached", // native-only
        // D2 · per-habit reminders
        "Está na hora desta maré." to "Time for this tide.", // native-only
        "Feito" to "Done", // native-only
        "Dados" to "Data",
        "Exportar dados" to "Export data",
        "Transfere um ficheiro .json com tudo." to "Downloads a .json file with everything.",
        "Enviar para a nuvem" to "Send to cloud",
        "Partilha a cópia para o Drive, Dropbox, Ficheiros…" to "Share the backup to Drive, Dropbox, Files…",
        "Importar dados" to "Import data",
        "Restaura a partir de um ficheiro .json." to "Restores from a .json file.",
        "Zona perigosa" to "Danger zone", // native-only
        "Apagar tudo" to "Delete everything", // native-only
        "Remove permanentemente todos os dados." to "Permanently removes all data.",
        "Apagar tudo e recomeçar? Isto não pode ser desfeito." to "Delete everything and start over? This can't be undone.",
        "Código-fonte e instruções:" to "Source code and instructions:",

        // ── Análise extra rows ──
        "Retrospetiva do ano" to "Year in review", // native-only
        "Resumo anual de foco, hábitos e intenções." to "Annual summary of focus, habits and intentions.", // native-only
        "Como funcionam as marés" to "How tides work", // native-only
        "Streaks, níveis e respiros explicados." to "Streaks, levels and breaths explained.", // native-only

        // ── Privacidade / PIN ──
        "Privacidade" to "Privacy", // native-only
        "Bloqueio por PIN" to "PIN lock", // native-only
        "Protege a app com um código de 4+ dígitos." to "Protects the app with a 4+ digit code.", // native-only
        "Desativar bloqueio por PIN" to "Disable PIN lock", // native-only
        "Introduz o PIN atual para remover o bloqueio." to "Enter your current PIN to remove the lock.", // native-only
        "Introduz o teu PIN" to "Enter your PIN", // native-only
        "Confirma o PIN para desativar" to "Confirm PIN to disable", // native-only
        "Confirma o novo PIN" to "Confirm new PIN", // native-only
        "Cria um novo PIN" to "Create a new PIN", // native-only
        "Mínimo 4 dígitos" to "Minimum 4 digits", // native-only
        "PIN demasiado curto" to "PIN too short", // native-only
        "PIN incorrecto" to "Incorrect PIN", // native-only
        "Os PINs não coincidem" to "PINs don't match", // native-only
        "Desbloqueio biométrico" to "Biometric unlock", // native-only
        "Desbloqueia com impressão digital ou rosto; o PIN fica como alternativa." to "Unlock with fingerprint or face; PIN stays as a fallback.", // native-only
        "Desbloquear" to "Unlock", // native-only
        "Usar PIN" to "Use PIN", // native-only
        "Usar biometria" to "Use biometrics", // native-only

        // ── Cópia automática ──
        "Cópia automática" to "Auto backup", // native-only
        "Guarda em segundo plano, mesmo com a app fechada." to "Saves in the background, even when the app is closed.", // native-only
        "Frequência" to "Frequency", // native-only
        "Diária" to "Daily", // native-only
        "Semanal" to "Weekly", // native-only
        "Por hora" to "Hourly", // native-only
        "Escolher pasta…" to "Choose a folder…", // native-only
        "Pasta de cópia" to "Backup folder", // native-only
        "Guarda também numa pasta tua (Drive, dispositivo…)." to "Also saves to a folder of yours (Drive, device…).", // native-only
        "Remover pasta" to "Remove folder", // native-only
        "Volta a guardar só dentro da app." to "Goes back to saving only inside the app.", // native-only

        // ── Retrospetiva do ano ──
        "horas de foco" to "hours of focus", // native-only
        "intenções" to "intentions", // native-only
        "dias com marés" to "days with tides", // native-only
        "Melhor streak" to "Best streak", // native-only
        "{n} dias — {name}" to "{n} days — {name}", // native-only
        "Reflexões" to "Reflections", // native-only
        "{n} reflexões escritas" to "{n} reflections written", // native-only
        "Maré mais alta" to "Highest tide", // native-only (year review label; distinct from tier name)
        "Sem dados para este ano." to "No data for this year.", // native-only

        // ── Como funcionam as marés ──
        "Cada hábito tem uma maré — uma forma de ver até onde chegaste." to "Each habit has a tide — a way to see how far you've come.", // native-only
        "Nível do navegador" to "Navigator level", // native-only
        "Soma de todos os dias marcados em todos os hábitos." to "Sum of all days marked across all habits.", // native-only
        "Um dia de respiro conta para a streak mas não entra no cálculo de conclusão. Usa-o quando precisas de uma pausa honesta — a tua maré não quebra." to "A breath day counts toward the streak but not the completion calculation. Use it when you need an honest break — your tide won't break.", // native-only

        // ── Recarregar exemplo ──
        "Recarregar exemplo" to "Reload sample", // native-only
        "Repõe os dados de exemplo para explorar a app." to "Restores the sample data to explore the app.", // native-only
        "Recarregar o exemplo? Os dados actuais serão substituídos." to "Reload the sample? Your current data will be replaced.", // native-only
        "Recarregar" to "Reload", // native-only

        "Atualizações" to "Updates", // native-only
        "A verificar…" to "Checking…",
        "Transferir nova versão" to "Download new version",
        "Está atualizado." to "Up to date.",
        "Verificar atualizações" to "Check for updates",
        "Versão de {date}" to "Version {date}",
        "A transferir atualização…" to "Downloading update…",
        "A transferir atualização… {n}%" to "Downloading update… {n}%",
        "Não foi possível transferir a atualização." to "Couldn't download the update.",
        "Tentar outra vez" to "Try again",
        "Não foi possível verificar. Confirma a ligação à internet." to "Couldn't check for updates. Check your internet connection.", // native-only
        "Novidades" to "What's new", // native-only
        "Pauta foi atualizada." to "Pauta has been updated.", // native-only
        "Pequenas melhorias e correções." to "Small improvements and fixes.", // native-only
        "Permite instalar apps desta origem e toca outra vez." to "Allow installing apps from this source, then tap again.",
        "Se a instalação falhar com «conflito com um pacote existente»: exporta uma cópia de segurança, desinstala a app e instala de novo. Só é preciso uma vez — daí em diante as atualizações mantêm os teus dados." to "If the install fails with “conflicts with an existing package”: export a backup, uninstall the app, and install again. You only need to do this once — after that, updates keep your data.",

        // ── Widget ──
        "Foco {m}m" to "Focus {m}m",

        // ── Pauta tab parity (web values copied from src/i18n.jsx) ──
        "(sem projecto)" to "(no project)",
        "Adicione comportamentos que quer praticar regularmente. Cada mês tem o seu grid." to "Add behaviours you want to practise regularly. Each month has its own grid.",
        "Ainda nenhum bloco hoje. Comece quando quiser." to "No blocks yet today. Start whenever you like.",
        "Ainda nenhuma pauta passada." to "No past Pauta yet.",
        "Apagar bloco" to "Delete block",
        "Apagar este bloco? Não dá pra desfazer." to "Delete this block? This can't be undone.",
        "As marés têm história. Sobem e descem ao longo do ano." to "Tides have a history. They rise and fall through the year.",
        "Beber água" to "Drink water",
        "Cada barra é um dia. Toque para ver os blocos desse dia." to "Each bar is a day. Tap to see that day's blocks.",
        "Comece pelo que importa." to "Start with what matters.",
        "Começar em branco" to "Start blank",
        "Começar um bloco de foco" to "Start a focus block",
        "Continuar" to "Continue",
        "Cultive hábitos como marés." to "Cultivate habits like tides.",
        "Cumpriu a meta planeada." to "You hit your planned goal.",
        "Cumpriu os {n} min planeados." to "You hit your planned {n} min.",
        "Descartar este bloco? Não fica guardado." to "Discard this block? It won't be saved.",
        "Dias activos" to "Active days",
        "Dormir cedo" to "Sleep early",
        "Editar bloco" to "Edit block",
        "Esta é a sua" to "This is your",
        "Exercício" to "Exercise",
        "Filtrado" to "Filtered",
        "Ler" to "Read",
        "Marés comuns" to "Common tides",
        "Meditar" to "Meditate",
        "Média/dia activo" to "Avg/active day",
        "Na Pauta, inicie um bloco de foco e o tempo conta-se sozinho. Pause, retome e conclua quando quiser." to "On Pauta, start a focus block and time counts itself. Pause, resume and finish whenever you like.",
        "Na tab Hoje, liste 1 a 4 intenções — as coisas que movem o seu dia — e reflita à noite." to "On the Hoje tab, list 1–4 intentions — the things that move your day — and reflect at night.",
        "Nada por aqui ainda." to "Nothing here yet.",
        "Nas Marés, marque hábitos dia a dia. A constância faz a maré subir — e os dias de descanso são honestos." to "On Marés, mark habits day by day. Consistency makes the tide rise — and rest days are honest.",
        "O ritmo dos seus dias." to "The rhythm of your days.",
        "Pauta · {d}" to "Pauta · {d}",
        "Pautas anteriores" to "Previous Pautas",
        "Pressão longa num dia não feito para marcar respiro." to "Long press an undone day to mark a breath.",
        "Projecto" to "Project",
        "Pés na água. O resto vem com o tempo." to "Feet in the water. The rest comes with time.",
        "Seguir" to "Next",
        "Sem blocos nesse dia." to "No blocks that day.",
        "Sessões ({n})" to "Sessions ({n})",
        "Toda onda começa pequena." to "Every wave starts small.",
        "Total 14d" to "Total 14d",
        "Trabalhe em blocos." to "Work in blocks.",
        "Tudo" to "All",
        "Um lugar calmo, privado e offline para o que importa. Sem conta, sem servidor — tudo fica no seu telemóvel." to "A calm, private, offline place for what matters. No account, no server — everything stays on your phone.",
        "Um novo bloco" to "A new block",
        "adicionar maré" to "add tide",
        "adicionar nota…" to "add a note…",
        "ainda não chegou" to "not here yet",
        "ainda não concluído" to "not finished yet",
        "antes da maré" to "before the tide",
        "bem-vindo" to "welcome",
        "bloco" to "block",
        "blocos" to "blocks",
        "começar" to "start",
        "concluído" to "finished",
        "em curso" to "in progress",
        "em foco" to "in focus",
        "em foco." to "in focus.",
        "feito" to "done",
        "feito hoje" to "done today",
        "fora do horário" to "off-schedule",
        "histórico" to "history",
        "hoje (por fazer)" to "today (to do)",
        "iniciado" to "started",
        "intenção do dia →" to "intention of the day →",
        "início {t}" to "started {t}",
        "legenda" to "legend",
        "marés" to "tides",
        "meta cumprida" to "target reached",
        "meta {n} min" to "{n} min target",
        "nota da pausa (opcional)" to "pause note (optional)",
        "não feito" to "not done",
        "o que aconteceu?" to "what happened?",
        "pausa" to "paused",
        "pausado às {t} · {d} acumulado" to "paused at {t} · {d} accumulated",
        "pauta" to "pauta",
        "pronto" to "ready",
        "retomado" to "resumed",
        "retomado · em curso" to "resumed · in progress",
        "saltar" to "skip",
        "seu" to "yours",
        "todos os dias com blocos" to "all days with blocks",
        "toque para sair" to "tap to exit",
        "título do bloco" to "block title",
        "{a} · em curso" to "{a} · in progress",
        "{a} → {b}" to "{a} → {b}",
        "{d} no total" to "{d} total",
        // native-only keys
        "Comece com uma pauta em branco. Muda tudo depois nas Definições." to "Start with a blank pauta. Change everything later in Settings.", // native-only
        "Descartar" to "Discard", // native-only

        // ── A6 · keyboard + validation pass ──
        "guardado" to "saved", // native-only
        "Escreve em que te vais focar." to "Write what you'll focus on.", // native-only
        "Escreva o que fez." to "Write what you did.", // native-only
        "Duração entre 1 e 1440 min." to "Duration between 1 and 1440 min.", // native-only
        "Dá um nome à maré." to "Name this tide.", // native-only
    )
}

/** Convenience top-level aliases so call sites read like the web (`tr(...)`). */
fun tr(pt: String): String = I18n.tr(pt)
fun trf(pt: String, vararg args: Pair<String, Any?>): String = I18n.trf(pt, *args)
