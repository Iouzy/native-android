package com.pauta.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pauta.app.BuildConfig
import com.pauta.app.i18n.Strings
import com.pauta.app.service.BackupManager
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val tr = { pt: String -> Strings.tr(pt, prefs.lang) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(tr("Definições"), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            // Language
            SettingSection(tr("Idioma"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pt" to "Português", "en" to "English").forEach { (code, label) ->
                    FilterChip(selected = prefs.lang == code, onClick = { vm.setLang(code) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))

            // Theme
            SettingSection(tr("Tema"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to tr("Automático"), "light" to tr("Claro"), "dark" to tr("Escuro"))
                    .forEach { (code, label) ->
                        FilterChip(selected = prefs.theme == code, onClick = { vm.setTheme(code) }, label = { Text(label) })
                    }
            }
            Spacer(Modifier.height(16.dp))

            // Accent colour
            SettingSection(tr("Cor"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("", "#2563EB", "#059669", "#DC2626", "#7C3AED", "#F59E0B").forEach { hex ->
                    AssistChip(
                        onClick = { vm.setAccent(hex) },
                        label = { Text(if (hex.isBlank()) "—" else " ") },
                        leadingIcon = if (hex.isNotBlank()) {
                            { Box(Modifier.size(16.dp)) {} }
                        } else null,
                        colors = if (prefs.accent == hex)
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else AssistChipDefaults.assistChipColors()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Haptics toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Vibração"), modifier = Modifier.weight(1f))
                Switch(checked = prefs.haptics, onCheckedChange = { vm.setHaptics(it) })
            }
            Spacer(Modifier.height(8.dp))

            // Reminders toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Lembretes"), modifier = Modifier.weight(1f))
                Switch(
                    checked = prefs.remindersEnabled,
                    onCheckedChange = { vm.setReminders(it) }
                )
            }
            Spacer(Modifier.height(16.dp))

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Privacy / data
            SettingSection(tr("Privacidade"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = BackupManager.exportToDownloads(context, vm)
                            statusMsg = if (ok) "✓ ${tr("Exportar dados")}" else "✗"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Exportar dados"))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val ok = BackupManager.exportCsvToDownloads(context)
                            statusMsg = if (ok) "✓ ${tr("Exportar CSV")}" else "✗"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Exportar CSV"))
                }
            }
            statusMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // About
            SettingSection(tr("Acerca"))
            Text("Pauta", style = MaterialTheme.typography.titleMedium)
            Text(
                "${tr("Versão")} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}
