package com.pauta.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.viewmodel.AppViewModel

/** Summary sheet: navigator rank, lifetime done-days, this-month consistency. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val tr = { pt: String -> Strings.tr(pt, prefs.lang) }
    val insights by vm.insights.collectAsState()

    LaunchedEffect(Unit) { vm.loadInsights() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
        ) {
            Text(tr("Resumo"), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))

            val i = insights
            if (i == null) {
                Text(
                    tr("A calcular…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(tr(i.level.name), style = MaterialTheme.typography.titleLarge)
                Text(
                    tr(i.level.subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                StatRow(tr("Dias cumpridos"), i.level.totalDays.toString())
                HorizontalDivider()
                StatRow(tr("Consistência este mês"), i.overallPct?.let { "$it%" } ?: "—")
                HorizontalDivider()
                StatRow(tr("Marés ativas"), i.activeHabits.toString())
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
