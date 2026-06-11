package com.pauta.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pauta.app.BuildConfig
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/** Accent presets offered in Settings (null = the build default terracotta). */
private val ACCENT_PRESETS = listOf(
    null to "#B8533A",   // padrão (terracotta)
    "#2563EB" to "#2563EB",
    "#0E7C66" to "#0E7C66",
    "#7C5CBF" to "#7C5CBF",
    "#C2410C" to "#C2410C",
    "#9333EA" to "#9333EA",
)

/**
 * Settings as a full-surface overlay: appearance (theme / accent / contrast /
 * reduced motion), language, haptics and Pip. Reminders, export/import and the
 * updater layer on here next. // PT: definições — aspeto, idioma, hápticos, Pip.
 */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val colors = LocalPautaColors.current
    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val updChecking by vm.updateChecking.collectAsStateWithLifecycle()
    val updChecked by vm.updateChecked.collectAsStateWithLifecycle()
    val updAvailable by vm.updateAvailable.collectAsStateWithLifecycle()
    val updDownloading by vm.updateDownloading.collectAsStateWithLifecycle()
    val updDownloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
    val updDownloadError by vm.updateDownloadError.collectAsStateWithLifecycle()
    val updNeedsPerm by vm.updateNeedsPerm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank()) vm.importBackup(text) {}
        }
    }

    var showGoals by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(tr("Apagar tudo"), color = colors.ink) },
            text = {
                Text(
                    tr("Apagar tudo e recomeçar? Isto não pode ser desfeito."),
                    color = colors.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.resetAll(); showResetConfirm = false }) {
                    Text(tr("Apagar"), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(tr("Cancelar"), color = colors.ink3)
                }
            },
            containerColor = colors.paper2,
        )
    }

    if (showGoals) {
        GoalsScreen(onClose = { showGoals = false })
        return
    }

    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = colors.accent, fontSize = 22.sp, modifier = Modifier.clickableNoRipple(onClose))
            Spacer(Modifier.width(14.dp))
            Text(tr("Definições"), color = colors.ink, fontFamily = SerifFamily, fontSize = 26.sp)
        }

        Section(tr("Aparência"))
        SegmentedRow(
            label = tr("Tema"),
            options = listOf("auto" to tr("Auto"), "light" to tr("Claro"), "dark" to tr("Escuro")),
            selected = prefs.theme,
            onSelect = { vm.setTheme(it) },
        )
        Spacer(Modifier.height(14.dp))
        Text(tr("Cor de destaque"), color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENT_PRESETS.forEach { (value, hex) ->
                val selected = prefs.accent == value
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(parseHex(hex))
                        .clickableNoRipple { vm.setAccent(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow(tr("Ecrã inteiro"), prefs.immersive) { vm.setImmersive(it) }

        Section(tr("Idioma"))
        SegmentedRow(
            label = tr("Língua"),
            options = listOf("pt" to "Português", "en" to "English"),
            selected = prefs.lang,
            onSelect = { vm.setLang(it) },
        )

        Section(tr("Acessibilidade"))
        SegmentedRow(
            label = tr("Tamanho do texto"),
            options = listOf("1.0" to tr("Normal"), "1.15" to tr("Grande"), "1.3" to tr("Maior")),
            selected = when {
                prefs.textScale >= 1.3f -> "1.3"
                prefs.textScale >= 1.15f -> "1.15"
                else -> "1.0"
            },
            onSelect = { vm.setTextScale(it.toFloat()) },
        )
        Spacer(Modifier.height(6.dp))
        ToggleRow(tr("Alto contraste"), prefs.highContrast) { vm.setHighContrast(it) }
        ToggleRow(tr("Reduzir movimento"), prefs.reducedMotion) { vm.setReducedMotion(it) }

        Section(tr("Foco"))
        ToggleRow(tr("Manter ecrã ligado"), prefs.keepAwake) { vm.setKeepAwake(it) }
        ToggleRow(tr("Som ao concluir"), prefs.sound) { vm.setSound(it) }

        Section(tr("Companhia"))
        ToggleRow(tr("Vibração"), prefs.haptics) { vm.setHaptics(it) }
        ToggleRow(tr("Papagaio ajudante"), prefs.parrot) { vm.setParrot(it) }

        Section(tr("Lembretes"))
        ToggleRow(tr("Lembretes diários"), prefs.remindersEnabled) { enabled ->
            vm.setRemindersEnabled(enabled)
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (prefs.remindersEnabled) {
            TimeRow(tr("Plano do dia"), prefs.plannerTime) { vm.setPlannerTime(it) }
            TimeRow(tr("Hábitos pendentes"), prefs.habitsTime) { vm.setHabitsTime(it) }
            TimeRow(tr("Reflexão noturna"), prefs.reflectionTime) { vm.setReflectionTime(it) }
        }

        Section(tr("Objetivos"))
        ActionRow(tr("Objetivos trimestrais")) { showGoals = true }

        Section(tr("Dados"))
        ActionRow(tr("Exportar dados")) { vm.exportBackup { json -> shareBackup(context, json) } }
        ActionRow(tr("Importar dados")) { importLauncher.launch("application/json") }

        Section(tr("Atualizações"))
        // Show a date-based version label when the build timestamp is stamped by
        // CI (same pattern as the web app's "Versão de YYYY-MM-DD"). Fall back to
        // the run number for local/unstamped builds. // PT: data da versão como na web.
        val versionLabel = if (BuildConfig.BUILD_TS > 0L) {
            val d = java.time.Instant.ofEpochSecond(BuildConfig.BUILD_TS)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            trf("Versão de {date}", "date" to d.toString())
        } else {
            "build #${BuildConfig.BUILD_RUN}"
        }
        Text(versionLabel, color = colors.ink4, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        when {
            updDownloading -> {
                val label = if (updDownloadProgress != null)
                    trf("A transferir atualização… {n}%", "n" to updDownloadProgress!!)
                else tr("A transferir atualização…")
                Text(label, color = colors.ink3, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            updDownloadError -> {
                Text(
                    tr("Não foi possível transferir a atualização."),
                    color = colors.accent,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                ActionRow(tr("Tentar outra vez")) { vm.installUpdate(context) }
            }
            updChecking -> Text(tr("A verificar…"), color = colors.ink3, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            updAvailable != null -> Column {
                ActionRow(tr("Transferir nova versão")) { vm.installUpdate(context) }
                if (updNeedsPerm) {
                    // We just opened the "install unknown apps" toggle — ask the
                    // user to allow it and tap again. // PT: permitir e tocar de novo.
                    Text(
                        text = tr("Permite instalar apps desta origem e toca outra vez."),
                        color = colors.accent,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                // One-time gotcha for builds signed before the project settled on a
                // fixed key — same italic hint the web shows under the button.
                Text(
                    text = tr("Se a instalação falhar com «conflito com um pacote existente»: exporta uma cópia de segurança, desinstala a app e instala de novo. Só é preciso uma vez — daí em diante as atualizações mantêm os teus dados."),
                    color = colors.ink3,
                    fontFamily = SerifFamily,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            updChecked -> Text(tr("Está atualizado."), color = colors.ink3, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
            else -> ActionRow(tr("Verificar atualizações")) { vm.checkForUpdate() }
        }

        Section(tr("Zona perigosa"))
        ActionRow(tr("Apagar tudo"), danger = true) { showResetConfirm = true }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ActionRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    Text(
        text = label,
        color = if (danger) Color(0xFFE53935) else colors.accent,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 10.dp),
    )
}

/** Write the backup JSON to a cache file and fire a share sheet via FileProvider. */
private fun shareBackup(context: android.content.Context, json: String) {
    val dir = File(context.cacheDir, "backups").apply { mkdirs() }
    val file = File(dir, "pauta-backup.json")
    file.writeText(json)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Pauta"))
}

@Composable
private fun TimeRow(label: String, value: String, onCommit: (String) -> Unit) {
    val colors = LocalPautaColors.current
    var text by remember(value) { mutableStateOf(value) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextField(
            value = text,
            onValueChange = { raw ->
                // Keep digits + a single colon, max HH:MM.
                text = raw.filter { it.isDigit() || it == ':' }.take(5)
                if (Regex("^\\d{1,2}:\\d{2}$").matches(text)) onCommit(text)
            },
            singleLine = true,
            modifier = Modifier.width(96.dp),
            textStyle = LocalTextStyle.current.copy(color = colors.ink, fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedIndicatorColor = colors.accent,
                unfocusedIndicatorColor = colors.rule,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
            ),
        )
    }
}

@Composable
private fun Section(title: String) {
    val colors = LocalPautaColors.current
    Spacer(Modifier.height(26.dp))
    Text(title.uppercase(), color = colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onDark,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.paper,
                uncheckedTrackColor = colors.rule,
            ),
        )
    }
}

@Composable
private fun SegmentedRow(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalPautaColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) colors.accent.copy(alpha = 0.16f) else colors.paper2)
                        .clickableNoRipple { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text,
                        color = if (isSel) colors.accent else colors.ink3,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun parseHex(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (e: IllegalArgumentException) { Color(0xFFB8533A) }
