package com.pauta.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pauta.app.domain.HistoryDay
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.i18n.tr
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only history of past days — date, the day's done/total intentions, and
 * the nightly reflection. A full-surface overlay closed with the back arrow.
 * // PT: histórico só-leitura dos dias anteriores.
 */
@Composable
fun HistoryView(days: List<HistoryDay>, onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    // A8: a full-surface navigation destination now (it used to swap in inside the
    // Hoje pager, below the shell's status row) — so it owns the status-bar inset
    // itself. // PT: agora é um destino de página inteira; consome a margem da
    // barra de estado.
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "←",
                color = colors.accent,
                fontSize = 22.sp,
                modifier = Modifier.clickableNoRipple(onClose),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = tr("Histórico"),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 26.sp,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (days.isEmpty()) {
            Text(tr("Ainda sem dias anteriores."), color = colors.ink4, fontSize = 14.sp)
        } else {
            days.forEach { day -> HistoryRow(day); Divider() }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HistoryRow(day: HistoryDay) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDay(day.dayKey).replaceFirstChar { it.uppercase() },
                color = colors.ink2,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (day.totalCount > 0) {
                Text(
                    text = "${day.doneCount}/${day.totalCount}",
                    color = colors.ink3,
                    fontSize = 13.sp,
                )
            }
        }
        if (day.reflection.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = day.reflection, color = colors.ink3, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun Divider() {
    val colors = LocalPautaColors.current
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.rule),
    )
}

private fun formatDay(dayKey: String): String {
    val locale = if (I18n.lang == Lang.EN) Locale.ENGLISH else Locale("pt", "PT")
    return LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale))
}
