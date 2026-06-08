package com.pauta.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.DayHistoryItem
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/** Read-only view of past days (intentions done/total + reflection). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val tr = { pt: String -> Strings.tr(pt, prefs.lang) }
    val history by vm.history.collectAsState()

    LaunchedEffect(Unit) { vm.loadHistory() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
        ) {
            Text(tr("Histórico"), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                Text(
                    tr("Ainda não há dias anteriores."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(history, key = { it.dayKey }) { item -> HistoryRow(item, prefs.lang) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HistoryRow(item: DayHistoryItem, lang: String) {
    val label = remember(item.dayKey, lang) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale(if (lang == "en") "en" else "pt"))
            .format(DateUtils.calFromKey(item.dayKey).time)
            .replaceFirstChar { it.uppercase() }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (item.totalCount > 0) {
                Text(
                    "${item.doneCount}/${item.totalCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.reflection.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                item.reflection,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
    }
}
