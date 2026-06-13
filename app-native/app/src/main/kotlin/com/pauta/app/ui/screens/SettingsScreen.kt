package com.pauta.app.ui.screens

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pauta.app.BuildConfig
import com.pauta.app.MainActivity
import com.pauta.app.R
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.service.ReminderScheduler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.i18n.trf
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel

/** Accent palette matching the web app (first entry = build-default terracota). */
private val ACCENT_PRESETS = listOf(
    null      to "#B8533A",  // Terracota (padrão)
    "#5A6B3E" to "#5A6B3E",  // Salva
    "#3D5A80" to "#3D5A80",  // Índigo
    "#2E6E6A" to "#2E6E6A",  // Oceano
    "#8E5A8E" to "#8E5A8E",  // Ameixa
    "#A6792E" to "#A6792E",  // Âmbar
    "#1A1815" to "#1A1815",  // Tinta
)

/**
 * App settings. A8: reached as a navigation destination, with the three
 * full-surface analysis screens (goals, year review, tide guide) navigated to as
 * their own destinations — so each peels back predictively — rather than swapped
 * in with `if (show…)` overlays. // PT: definições — um destino de navegação; os
 * ecrãs de análise são destinos próprios (recuam com o gesto preditivo).
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenYearReview: () -> Unit,
    onOpenTierGuide: () -> Unit,
) {
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
    // B1: pick a folder for the auto-backup (SAF). Persist read+write permission
    // so the WorkManager job can keep writing there with the app closed.
    // // PT: escolher pasta para a cópia automática, com permissão persistente.
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setBackupFolder(uri.toString())
        }
    }

    var showInsights by remember { mutableStateOf(false) }
    var showPinSet by remember { mutableStateOf(false) }
    var showPinDisable by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showReseedConfirm by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var testNotifMsg by remember { mutableStateOf<String?>(null) }

    // A7: archived tides — surfaced in Settings → Dados for restore (or a guarded
    // permanent delete). // PT: marés arquivadas, para restaurar nos Dados.
    val archivedHabits by vm.archivedHabits.collectAsStateWithLifecycle()

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

    if (showReseedConfirm) {
        AlertDialog(
            onDismissRequest = { showReseedConfirm = false },
            title = { Text(tr("Recarregar exemplo"), color = colors.ink) },
            text = { Text(tr("Recarregar o exemplo? Os dados actuais serão substituídos."), color = colors.ink2) },
            confirmButton = {
                TextButton(onClick = { vm.reseed(); showReseedConfirm = false }) {
                    Text(tr("Recarregar"), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReseedConfirm = false }) {
                    Text(tr("Cancelar"), color = colors.ink3)
                }
            },
            containerColor = colors.paper2,
        )
    }

    // A8: this destination's own back gesture is popped predictively by the
    // NavHost (no BackHandler intercepting it). But the PIN flows below render as
    // full-surface early-returns and PinScreen has no back handling of its own —
    // so guard *those*: back cancels the PIN screen and returns to the settings
    // list, instead of popping the whole destination to home. Disabled otherwise,
    // so it never steals the predictive pop. // PT: o NavHost trata do recuo da
    // rota; só intercetamos o back quando um ecrã de PIN aninhado está aberto.
    BackHandler(enabled = showPinSet || showPinDisable) {
        showPinSet = false
        showPinDisable = false
    }

    if (showInsights) {
        InsightsSheet(onClose = { showInsights = false })
        return
    }

    if (showPinSet) {
        PinScreen(PinMode.SET, onSuccess = { showPinSet = false }, onCancel = { showPinSet = false })
        return
    }

    if (showPinDisable) {
        PinScreen(PinMode.DISABLE, onSuccess = { showPinDisable = false }, onCancel = { showPinDisable = false })
        return
    }

    // Archived tides manager — a modal sheet over the settings (not a full-screen
    // sub-screen), so it overlays rather than replacing. // PT: gestor de marés
    // arquivadas, em folha modal sobre as definições.
    if (showArchived) {
        ArchivedHabitsSheet(
            habits = archivedHabits,
            onRestore = { vm.setHabitArchived(it.id, false) },
            onDelete = { vm.removeHabit(it.id) },
            onClose = { showArchived = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Navigation header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                color = colors.accent,
                fontSize = 22.sp,
                modifier = Modifier.clickableNoRipple(onClose),
            )
            Spacer(Modifier.width(14.dp))
            Text(tr("Definições"), color = colors.ink, fontFamily = SerifFamily, fontSize = 26.sp)
        }

        // Hero — app identity, matches web DataSheet hero header
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = 0.08f))
                    .border(1.dp, colors.accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "P",
                    color = colors.accent,
                    fontFamily = SerifFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Pauta", color = colors.ink, fontFamily = SerifFamily, fontSize = 24.sp)
                Text(
                    tr("Hoje · Pauta · Marés"),
                    color = colors.ink3,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 13.sp,
                )
            }
        }
        HorizontalDivider(color = colors.rule)

        // ── ANÁLISE ──────────────────────────────────────────────────────
        Section(tr("Análise"))
        SectionCard {
            ActionRow(
                label = tr("Revisão semanal"),
                subtitle = tr("Foco, hábitos e padrões dos últimos 7 dias."),
            ) { showInsights = true }
            CardDivider()
            ActionRow(
                label = tr("Retrospetiva do ano"),
                subtitle = tr("Resumo anual de foco, hábitos e intenções."),
            ) { onOpenYearReview() }
            CardDivider()
            ActionRow(
                label = tr("Como funcionam as marés"),
                subtitle = tr("Streaks, níveis e respiros explicados."),
            ) { onOpenTierGuide() }
        }

        // ── PRIVACIDADE ──────────────────────────────────────────────────
        Section(tr("Privacidade"))
        SectionCard {
            if (prefs.pinHash == null) {
                ActionRow(
                    label = tr("Bloqueio por PIN"),
                    subtitle = tr("Protege a app com um código de 4+ dígitos."),
                ) { showPinSet = true }
            } else {
                ActionRow(
                    label = tr("Desativar bloqueio por PIN"),
                    subtitle = tr("Introduz o PIN atual para remover o bloqueio."),
                ) { showPinDisable = true }
            }
        }

        // ── APARÊNCIA ────────────────────────────────────────────────────
        Section(tr("Aparência"))
        SectionCard {
            SegmentedRow(
                label = tr("Língua"),
                options = listOf("pt" to "Português", "en" to "English"),
                selected = prefs.lang,
                onSelect = { vm.setLang(it) },
            )
            CardDivider()
            SegmentedRow(
                label = tr("Tema"),
                options = listOf("auto" to tr("Auto"), "light" to tr("Claro"), "dark" to tr("Escuro")),
                selected = prefs.theme,
                onSelect = { vm.setTheme(it) },
            )
            CardDivider()
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
            }
            CardDivider()
            ToggleRow(
                label = tr("Vibração"),
                checked = prefs.haptics,
                subtitle = tr("Pequeno toque ao concluir."),
            ) { vm.setHaptics(it) }
            CardDivider()
            ToggleRow(
                label = tr("Papagaio ajudante"),
                checked = prefs.parrot,
                subtitle = tr("O Pip aparece com dicas e piadas. Toca-lhe para mais."),
            ) { vm.setParrot(it) }
            CardDivider()
            ToggleRow(
                label = tr("Ecrã inteiro"),
                checked = prefs.immersive,
                subtitle = tr("Esconde as barras do sistema. Deslize da margem para as ver."),
            ) { vm.setImmersive(it) }
        }

        // ── ACESSIBILIDADE ───────────────────────────────────────────────
        Section(tr("Acessibilidade"))
        SectionCard {
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
            CardDivider()
            ToggleRow(
                label = tr("Alto contraste"),
                checked = prefs.highContrast,
                subtitle = tr("Reforça o texto e as linhas. Segue o sistema por omissão."),
            ) { vm.setHighContrast(it) }
            CardDivider()
            ToggleRow(
                label = tr("Reduzir movimento"),
                checked = prefs.reducedMotion,
                subtitle = tr("Desliga animações. Segue o sistema por omissão."),
            ) { vm.setReducedMotion(it) }
        }

        // ── FOCO ─────────────────────────────────────────────────────────
        Section(tr("Foco"))
        SectionCard {
            ToggleRow(
                label = tr("Manter ecrã ligado"),
                checked = prefs.keepAwake,
                subtitle = tr("Não deixa o telemóvel adormecer durante um bloco."),
            ) { vm.setKeepAwake(it) }
            CardDivider()
            ToggleRow(
                label = tr("Som ao concluir"),
                checked = prefs.sound,
                subtitle = tr("Um sino suave ao terminar um bloco ou atingir a meta."),
            ) { vm.setSound(it) }
        }

        // ── LEMBRETES ────────────────────────────────────────────────────
        Section(tr("Lembretes"))
        SectionCard {
            ToggleRow(
                label = tr("Notificações"),
                checked = prefs.remindersEnabled,
                subtitle = tr("Avisos locais enquanto a app está aberta."),
                onChange = { enabled ->
                    vm.setRemindersEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (prefs.remindersEnabled) {
                CardDivider()
                TimeRow(tr("Plano do dia"), prefs.plannerTime) { vm.setPlannerTime(it) }
                TimeRow(tr("Hábitos pendentes"), prefs.habitsTime) { vm.setHabitsTime(it) }
                TimeRow(tr("Reflexão noturna"), prefs.reflectionTime) { vm.setReflectionTime(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Sem servidor: os avisos só chegam com a app aberta no telemóvel."),
                    color = colors.ink3,
                    fontFamily = SerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    tr("Testar notificação"),
                    color = colors.ink2,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.rule, RoundedCornerShape(8.dp))
                        .clickableNoRipple {
                            testNotifMsg = null
                            val ok = sendTestReminder(context)
                            testNotifMsg = if (ok)
                                tr("Notificação de teste enviada.")
                            else
                                tr("Não foi possível enviar a notificação de teste.")
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (testNotifMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(testNotifMsg!!, color = colors.ink3, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // ── OBJETIVOS (native-only) ───────────────────────────────────────
        Section(tr("Objetivos"))
        SectionCard {
            ActionRow(tr("Objetivos trimestrais")) { onOpenGoals() }
        }

        // ── DADOS ────────────────────────────────────────────────────────
        Section(tr("Dados"))
        SectionCard {
            ToggleRow(
                label = tr("Cópia automática"),
                checked = prefs.autoBackup != "off",
                subtitle = tr("Guarda em segundo plano, mesmo com a app fechada."),
            ) { enabled -> vm.setAutoBackupCadence(if (enabled) "daily" else "off") }
            if (prefs.autoBackup != "off") {
                CardDivider()
                SegmentedRow(
                    label = tr("Frequência"),
                    options = listOf(
                        "daily" to tr("Diária"),
                        "weekly" to tr("Semanal"),
                        "hourly" to tr("Por hora"),
                    ),
                    selected = prefs.autoBackup,
                    onSelect = { vm.setAutoBackupCadence(it) },
                )
                CardDivider()
                // B1: pick a real folder (Drive, device storage…) so the copy
                // survives an uninstall — the filesDir copy is only a fallback.
                // // PT: pasta real para a cópia sobreviver à desinstalação.
                val folder = prefs.backupFolderUri
                ActionRow(
                    label = if (folder == null) tr("Escolher pasta…") else tr("Pasta de cópia"),
                    subtitle = if (folder == null)
                        tr("Guarda também numa pasta tua (Drive, dispositivo…).")
                    else
                        folderLabel(folder),
                ) { folderLauncher.launch(null) }
                if (folder != null) {
                    CardDivider()
                    ActionRow(
                        label = tr("Remover pasta"),
                        subtitle = tr("Volta a guardar só dentro da app."),
                    ) { vm.setBackupFolder(null) }
                }
            }
            CardDivider()
            ActionRow(
                label = tr("Exportar dados"),
                subtitle = tr("Transfere um ficheiro .json com tudo."),
            ) { vm.exportBackup { json -> shareBackup(context, json) } }
            CardDivider()
            ActionRow(
                label = tr("Enviar para a nuvem"),
                subtitle = tr("Partilha a cópia para o Drive, Dropbox, Ficheiros…"),
            ) { vm.exportBackup { json -> shareBackup(context, json) } }
            CardDivider()
            ActionRow(
                label = tr("Importar dados"),
                subtitle = tr("Restaura a partir de um ficheiro .json."),
            ) { importLauncher.launch("application/json") }
            // Only surfaced once there's something archived — keeps the section
            // quiet for everyone else. // PT: só aparece quando há marés arquivadas.
            if (archivedHabits.isNotEmpty()) {
                CardDivider()
                ActionRow(
                    label = tr("Marés arquivadas"),
                    subtitle = if (archivedHabits.size == 1) tr("1 maré escondida da grelha.")
                        else trf("{n} marés escondidas da grelha.", "n" to archivedHabits.size),
                ) { showArchived = true }
            }
        }

        // ── ATUALIZAÇÕES ─────────────────────────────────────────────────
        Section(tr("Atualizações"))
        SectionCard {
            val versionLabel = if (BuildConfig.BUILD_TS > 0L) {
                val d = java.time.Instant.ofEpochSecond(BuildConfig.BUILD_TS)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                trf("Versão de {date}", "date" to d.toString())
            } else {
                "build #${BuildConfig.BUILD_RUN}"
            }
            Text(
                versionLabel,
                color = colors.ink4,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
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
                updChecking -> Text(
                    tr("A verificar…"),
                    color = colors.ink3,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                updAvailable != null -> Column {
                    ActionRow(tr("Transferir nova versão")) { vm.installUpdate(context) }
                    if (updNeedsPerm) {
                        Text(
                            text = tr("Permite instalar apps desta origem e toca outra vez."),
                            color = colors.accent,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = tr("Se a instalação falhar com «conflito com um pacote existente»: exporta uma cópia de segurança, desinstala a app e instala de novo. Só é preciso uma vez — daí em diante as atualizações mantêm os teus dados."),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                updChecked -> Text(
                    tr("Está atualizado."),
                    color = colors.ink3,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                else -> ActionRow(tr("Verificar atualizações")) { vm.checkForUpdate() }
            }
        }

        // ── ZONA PERIGOSA ────────────────────────────────────────────────
        Section(tr("Zona perigosa"))
        SectionCard {
            ActionRow(
                label = tr("Recarregar exemplo"),
                subtitle = tr("Repõe os dados de exemplo para explorar a app."),
            ) { showReseedConfirm = true }
            CardDivider()
            ActionRow(
                label = tr("Apagar tudo"),
                subtitle = tr("Remove permanentemente todos os dados."),
                danger = true,
            ) { showResetConfirm = true }
        }

        // Footer — source code link, matches web DataSheet footer
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = colors.rule)
        Spacer(Modifier.height(16.dp))
        Text(
            tr("Código-fonte e instruções:"),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "github.com/Iouzy/psychic-guide ↗",
            color = colors.accent,
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickableNoRipple {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Iouzy/psychic-guide"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
        )

        Spacer(Modifier.height(48.dp))
    }
}

/** Fires a single test notification through the reminder channel. Returns false if permission is missing. */
private fun sendTestReminder(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false

    ReminderScheduler.ensureChannel(context)
    val lang = ReminderScheduler.savedLang(context)
    val title = if (lang == "en") "Pauta · test" else "Pauta · teste"
    val body = if (lang == "en") "Notifications are working." else "As notificações estão a funcionar."

    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
    val open = PendingIntent.getActivity(
        context, 998,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        flags,
    )
    val notif = NotificationCompat.Builder(context, ReminderScheduler.channelId())
        .setSmallIcon(R.drawable.ic_stat_focus)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .build()

    return runCatching { NotificationManagerCompat.from(context).notify(998, notif) }.isSuccess
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
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        // Tap the time to pick it on a clock — no free-typed HH:MM.
        // // PT: toca-se na hora para a escolher num relógio.
        Box(Modifier.width(116.dp)) {
            PautaTimeField(value = value, onChange = onCommit, title = label)
        }
    }
}

@Composable
private fun Section(title: String) {
    val colors = LocalPautaColors.current
    Spacer(Modifier.height(26.dp))
    Text(
        title.uppercase(),
        color = colors.ink3,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
}

/** Rounded card grouping rows — matches the web DataGroup visual. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.paper2)
            .border(1.dp, colors.rule, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        content = content,
    )
}

/** Thin rule between card items. */
@Composable
private fun CardDivider() {
    val colors = LocalPautaColors.current
    HorizontalDivider(color = colors.rule.copy(alpha = 0.6f))
}

@Composable
private fun ActionRow(
    label: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (danger) Color(0xFFE53935) else colors.accent,
            fontSize = 16.sp,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.ink3,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * A7: the archived-tides manager (Settings → Dados). Lists every archived tide
 * with a one-tap "restaurar" (un-archive) and a two-step "remover" — the only way
 * to permanently delete an archived tide, guarded so it's never a single tap.
 * Closes itself once the list empties. // PT: gestor de marés arquivadas —
 * restaurar (um toque) ou remover (com confirmação).
 */
@Composable
private fun ArchivedHabitsSheet(
    habits: List<HabitEntity>,
    onRestore: (HabitEntity) -> Unit,
    onDelete: (HabitEntity) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    // Restoring/removing the last tide empties the live list — close then, so the
    // sheet never lingers empty. // PT: fecha quando a lista esvazia.
    LaunchedEffect(habits.isEmpty()) { if (habits.isEmpty()) onClose() }
    PautaSheet(title = tr("Marés arquivadas"), onClose = onClose) {
        Text(
            text = tr("As marés arquivadas saem da grelha e do dia, mas guardam todo o histórico."),
            color = colors.ink3,
            fontFamily = SerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        habits.forEachIndexed { i, h ->
            if (i > 0) CardDivider()
            ArchivedHabitRow(habit = h, onRestore = { onRestore(h) }, onDelete = { onDelete(h) })
        }
    }
}

@Composable
private fun ArchivedHabitRow(habit: HabitEntity, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPautaColors.current
    var confirming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(habit.name, color = colors.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        if (!confirming) {
            Text(
                text = tr("restaurar"),
                color = colors.accent,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple(onRestore),
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = tr("remover"),
                color = DangerRed,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple { confirming = true },
            )
        } else {
            // The second tap confirms — an archived tide is never deleted in one
            // tap. // PT: segundo toque confirma — nunca apaga num só toque.
            Text(
                text = tr("Cancelar"),
                color = colors.ink3,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple { confirming = false },
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = tr("Apagar"),
                color = DangerRed,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple(onDelete),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    val colors = LocalPautaColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.ink, fontSize = 16.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = colors.ink3, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
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
private fun SegmentedRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
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
                        .background(if (isSel) colors.accent.copy(alpha = 0.16f) else colors.paper)
                        .border(1.dp, if (isSel) colors.accent.copy(alpha = 0.3f) else colors.rule, RoundedCornerShape(8.dp))
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

/** A human-readable name for a SAF tree URI, e.g. "primary:Documents/Pauta" →
 *  "Pauta". Falls back to the decoded path tail when there's no folder name.
 *  // PT: nome legível da pasta SAF escolhida. */
private fun folderLabel(treeUri: String): String {
    // lastPathSegment is already URL-decoded, e.g. "primary:Documents/Pauta".
    val docId = Uri.parse(treeUri).lastPathSegment ?: return treeUri
    val path = docId.substringAfter(':', docId)
    return path.trim('/').substringAfterLast('/').ifBlank { path.ifBlank { docId } }
}
