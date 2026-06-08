package com.pauta.app.i18n

import android.content.Context
import java.util.Locale

/** Portuguese-keyed i18n. Falls back to the key itself when no translation exists. */
object Strings {

    private val en = mapOf(
        // Navigation
        "Hoje" to "Today",
        "Pauta" to "Agenda",
        "Marés" to "Tides",

        // Common actions
        "Adicionar" to "Add",
        "Cancelar" to "Cancel",
        "Guardar" to "Save",
        "Eliminar" to "Delete",
        "Editar" to "Edit",
        "Concluir" to "Done",
        "Fechar" to "Close",
        "Confirmar" to "Confirm",
        "Retomar" to "Resume",
        "Pausar" to "Pause",
        "Trocar" to "Switch",
        "Iniciar" to "Start",

        // Hoje tab
        "O que importa hoje?" to "What matters today?",
        "Nova intenção" to "New intention",
        "Prioridade" to "Priority",
        "Duração (min)" to "Duration (min)",
        "Quando" to "When",
        "Manhã" to "Morning",
        "Tarde" to "Afternoon",
        "Noite" to "Evening",
        "Reflexão do dia" to "Day reflection",
        "O que valeu a pena hoje?" to "What was worth it today?",
        "Hoje" to "Today",
        "Trazer intenções de ontem" to "Carry over from yesterday",
        "Intenções não concluídas" to "Unfinished intentions",
        "Sem intenções hoje" to "No intentions today",
        "Adicionar primeira intenção" to "Add first intention",
        "Marés de hoje" to "Today's tides",
        "Rotinas" to "Routines",
        "Aplicar rotina" to "Apply routine",
        "Guardar como rotina" to "Save as routine",
        "Nome da rotina" to "Routine name",
        "feito" to "done",
        "em foco" to "in focus",
        "marés" to "tides",

        // Pauta tab
        "Blocos de foco" to "Focus blocks",
        "Iniciar foco" to "Start focus",
        "Título do bloco" to "Block title",
        "Ligado a" to "Linked to",
        "Projeto" to "Project",
        "Meta (min)" to "Goal (min)",
        "Em foco" to "In focus",
        "Pausado" to "Paused",
        "✓ meta cumprida" to "✓ goal reached",
        "Nenhum bloco hoje" to "No blocks today",
        "Nota da pausa" to "Pause note",
        "Reflexão do bloco" to "Block reflection",
        "Marcar intenção como concluída" to "Mark intention as done",
        "Adicionar bloco manual" to "Add manual block",
        "Hora de início" to "Start time",
        "Hora de fim" to "End time",
        "blocos" to "blocks",
        "em foco" to "in focus",
        "Linha do tempo" to "Timeline",
        "Sem blocos" to "No blocks",

        // Marés tab
        "Nova maré" to "New tide",
        "Nome do hábito" to "Habit name",
        "Cadência" to "Cadence",
        "Diária" to "Daily",
        "Semanal" to "Weekly",
        "Mensal" to "Monthly",
        "Dia fixo" to "Fixed day",
        "Qualquer dia" to "Any day",
        "Dias da semana" to "Weekdays",
        "Cor" to "Colour",
        "Hábito contável" to "Countable habit",
        "Meta diária" to "Daily target",
        "Unidade" to "Unit",
        "Hora preferida" to "Preferred time",
        "Descrição" to "Description",
        "Recorrência" to "Recurrence",
        "Para sempre" to "Forever",
        "Com prazo" to "With deadline",
        "Até ao fim do mês" to "Until end of month",
        "Sem dados" to "No data",
        "Antes da maré" to "Before tide",
        "Respiro" to "Rest day",
        "Bloqueado" to "Locked",
        "Fora do horário" to "Off schedule",
        "Registar respiro" to "Log rest day",
        "Motivo (opcional)" to "Reason (optional)",
        "Eliminar maré" to "Delete tide",
        "Detalhes da maré" to "Tide details",
        "Marcar como respiro" to "Mark as rest day",

        // Tiers
        "Onda" to "Wave",
        "Maré Baixa" to "Low Tide",
        "Maré Média" to "Mid Tide",
        "Maré Alta" to "High Tide",
        "Maré Viva" to "Spring Tide",
        "Maré Anual" to "Annual Tide",
        "Oceano" to "Ocean",
        "Tsunami" to "Tsunami",

        // Settings
        "Definições" to "Settings",
        "Idioma" to "Language",
        "Tema" to "Theme",
        "Automático" to "Auto",
        "Claro" to "Light",
        "Escuro" to "Dark",
        "Vibração" to "Haptics",
        "Notificações" to "Notifications",
        "Lembretes" to "Reminders",
        "Planeador" to "Planner",
        "Hábitos" to "Habits",
        "Reflexão" to "Reflection",
        "Acessibilidade" to "Accessibility",
        "Escala do texto" to "Text scale",
        "Movimento reduzido" to "Reduced motion",
        "Alto contraste" to "High contrast",
        "Privacidade" to "Privacy",
        "Exportar dados" to "Export data",
        "Importar dados" to "Import data",
        "Repor tudo" to "Reset all",
        "Cópia de segurança automática" to "Auto backup",
        "Restaurar cópia" to "Restore backup",
        "Acerca" to "About",
        "Versão" to "Version",

        // Months
        "Janeiro" to "January",
        "Fevereiro" to "February",
        "Março" to "March",
        "Abril" to "April",
        "Maio" to "May",
        "Junho" to "June",
        "Julho" to "July",
        "Agosto" to "August",
        "Setembro" to "September",
        "Outubro" to "October",
        "Novembro" to "November",
        "Dezembro" to "December",

        // Days of week (short)
        "Seg" to "Mon",
        "Ter" to "Tue",
        "Qua" to "Wed",
        "Qui" to "Thu",
        "Sex" to "Fri",
        "Sáb" to "Sat",
        "Dom" to "Sun",

        // Misc
        "dia" to "day",
        "dias" to "days",
        "semana" to "week",
        "semanas" to "weeks",
        "mês" to "month",
        "meses" to "months",
        "sequência" to "streak",
    )

    fun tr(pt: String, lang: String): String =
        if (lang == "pt") pt else en[pt] ?: pt

    fun monthName(month: Int, lang: String): String {
        val pt = listOf("Janeiro","Fevereiro","Março","Abril","Maio","Junho",
                        "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro")
        return if (lang == "pt") pt[month - 1] else tr(pt[month - 1], lang)
    }
}
