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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.pauta.app.ui.EmptyState
import com.pauta.app.ui.PautaCard
import com.pauta.app.ui.PautaRadius
import com.pauta.app.ui.PautaSheet
import com.pauta.app.ui.SectionEyebrow
import com.pauta.app.ui.SheetFieldGap
import com.pauta.app.ui.canUseBiometric
import com.pauta.app.ui.clickableNoRipple
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.PautaMotion
import com.pauta.app.ui.theme.rememberMotionEnabled
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import java.text.Normalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Where this app's code actually lives. The footer used to point at
 *  `Iouzy/psychic-guide`, which is a different repository. // PT: o repositório
 *  certo — o rodapé apontava para outro. */
private const val SOURCE_REPO = "https://github.com/Iouzy/native-android"

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
    val updCheckFailed by vm.updateCheckFailed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // C3: whether the device has usable biometrics — gates the unlock toggle below
    // (only shown alongside a set PIN). // PT: há biometria utilizável?
    val canBiometric = remember { context.canUseBiometric() }

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
    var showGoalSheet by remember { mutableStateOf(false) }
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

    // U4: the annual reading goal is reachable from the Modo section too — the
    // very sheet the Hábitos tab opens, not a second copy of it. // PT: o mesmo
    // sheet do objetivo anual que a tab Hábitos abre.
    if (showGoalSheet) {
        AnnualGoalSheet(current = prefs.bookAnnualGoal, onClose = { showGoalSheet = false })
    }

    // ── U5 · the searchable index ────────────────────────────────────────
    // Thirty rows across seven sections is past the point where scanning works.
    // So every row is *declared* once below — label, subtitle, its search-only
    // keywords and its composable — and the list further down renders either all
    // of them, grouped, or only the ones the query matches. One definition, two
    // renderings; the alternative (a second, filtered copy of the tree) would rot
    // the day someone edits one and not the other. // PT: cada linha declara-se
    // uma vez; a lista mostra-as todas ou só as que a procura encontra.
    var query by remember { mutableStateOf("") }
    val folded = searchFold(query.trim())
    val searching = folded.isNotEmpty()
    val scroll = rememberScrollState()
    // Filtering shortens the content, which clamps the scroll offset — so the
    // resting position is captured the moment a query starts (before the list
    // collapses) and put back when it ends. // PT: guarda-se a posição antes de
    // filtrar, para a repor ao limpar.
    var restingScroll by remember { mutableStateOf(0) }
    LaunchedEffect(searching) {
        if (!searching && restingScroll > 0) {
            val target = restingScroll
            restingScroll = 0
            // The full list has to re-measure before that offset exists again.
            // // PT: esperar que a lista volte a ter altura para lá chegar.
            withTimeoutOrNull(500) { snapshotFlow { scroll.maxValue }.first { it >= target } }
            scroll.scrollTo(target)
        }
    }

    // Show "v1.<run> · YYYY-MM-DD" when built in CI; just "v1.0" locally.
    // PT: versão + data juntos — run e timestamp ao mesmo tempo.
    val versionLabel = buildString {
        append("v${BuildConfig.VERSION_NAME}")
        if (BuildConfig.BUILD_TS > 0L) {
            val d = java.time.Instant.ofEpochSecond(BuildConfig.BUILD_TS)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            append(" · $d")
        }
    }
    val backupFolder = prefs.backupFolderUri

    // U4 · The information architecture: seven sections ordered by what you
    // reach for, not by the order the app grew. "Aparência" used to be a junk
    // drawer (língua and tema next to vibração, papagaio and o modo livro);
    // its companions moved out to [Companhia], the lens to [Modo], and
    // Acessibilidade folded in — to a user, text size *is* appearance.
    // // PT: sete secções por uso; a Aparência deixa de ser gaveta de tudo.
    // Built as plain, explicitly typed statements rather than one nested
    // `buildList { add(SettingsSection(…, buildList { … })) }` tree: every row
    // here carries a lambda and a fistful of default arguments, and the nested
    // form hands all of them to the inference engine as a single problem. One
    // statement per row keeps each one typed on its own, and reads better while
    // it's at it. // PT: instruções soltas e tipadas, uma por linha — mais
    // legível e sem uma árvore de inferência única.
    val sections = mutableListOf<SettingsSection>()
    // ── MODO ─────────────────────────────────────────────────────────
    // U4 put the lens first; U7 moved the control itself into the header, so
    // what stays here is the state and the two ways to change it — a section
    // whose subject is now operated from somewhere else still has to say
    // where. // PT: a lente à cabeça; o controlo mudou-se para o cabeçalho e
    // esta linha diz onde está.
    val modoRows = mutableListOf<SettingsRow>()
    modoRows.add(infoRow(
        label = tr("Lente"),
        subtitle = tr("Troque no cabeçalho, ou mantenha premido o ícone das definições."),
        value = if (prefs.bookMode) tr("Livro") else "Pauta",
        keywords = "modo livro book mode lens",
    ))
    // Only reading has an annual goal, so the row only exists in the lens
    // that uses it. // PT: só o modo livro tem objetivo anual.
    if (prefs.bookMode) modoRows.add(actionRow(
        label = tr("Objetivo anual"),
        subtitle = tr("Livros a ler este ano."),
        value = if (prefs.bookAnnualGoal > 0) "${prefs.bookAnnualGoal}" else tr("Definir objetivo"),
        keywords = "livro book goal objetivo",
    ) { showGoalSheet = true })
    sections += SettingsSection(tr("Modo"), modoRows)

    // ── APARÊNCIA ────────────────────────────────────────────────────
    val aparenciaRows = mutableListOf<SettingsRow>()
    aparenciaRows.add(segmentedRow(
        label = tr("Língua"),
        options = listOf("pt" to "Português", "en" to "English"),
        selected = prefs.lang,
        keywords = "language idioma língua português english",
    ) { vm.setLang(it) })
    aparenciaRows.add(segmentedRow(
        label = tr("Tema"),
        options = listOf("auto" to tr("Auto"), "light" to tr("Claro"), "dark" to tr("Escuro")),
        selected = prefs.theme,
        keywords = "theme tema escuro claro dark light",
    ) { vm.setTheme(it) })
    aparenciaRows.add(SettingsRow(tr("Cor de destaque"), keywords = "accent colour color cor") {
        Column(
            Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = RowVPadding),
            verticalArrangement = Arrangement.Center,
        ) {
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
    })
    // Three sizes, changed roughly once ever: not worth a permanent row
    // of pills. // PT: três tamanhos, escolhidos uma vez — folha, não
    // pílulas.
    aparenciaRows.add(pickerRow(
        label = tr("Tamanho do texto"),
        options = listOf("1.0" to tr("Normal"), "1.15" to tr("Grande"), "1.3" to tr("Maior")),
        selected = when {
            prefs.textScale >= 1.3f -> "1.3"
            prefs.textScale >= 1.15f -> "1.15"
            else -> "1.0"
        },
        keywords = "text size texto tamanho letra font",
    ) { vm.setTextScale(it.toFloat()) })
    aparenciaRows.add(toggleRow(
        label = tr("Alto contraste"),
        checked = prefs.highContrast,
        subtitle = tr("Reforça o texto e as linhas. Segue o sistema por omissão."),
        keywords = "contrast contraste acessibilidade accessibility",
    ) { vm.setHighContrast(it) })
    aparenciaRows.add(toggleRow(
        label = tr("Reduzir movimento"),
        checked = prefs.reducedMotion,
        subtitle = tr("Desliga animações. Segue o sistema por omissão."),
        keywords = "motion movimento animação animation acessibilidade accessibility",
    ) { vm.setReducedMotion(it) })
    sections += SettingsSection(tr("Aparência"), aparenciaRows)

    // ── FOCO E LEMBRETES ─────────────────────────────────────────────
    // A block and the notification that nudges you into one are the same
    // errand. // PT: o bloco e o aviso que o lembra são o mesmo assunto.
    val focoRows = mutableListOf<SettingsRow>()
    focoRows.add(toggleRow(
        label = tr("Manter ecrã ligado"),
        checked = prefs.keepAwake,
        subtitle = tr("Não deixa o telemóvel adormecer durante um bloco."),
        keywords = "screen awake ecrã",
    ) { vm.setKeepAwake(it) })
    focoRows.add(toggleRow(
        label = tr("Som ao concluir"),
        checked = prefs.sound,
        subtitle = tr("Um sino suave ao terminar um bloco ou atingir a meta."),
        keywords = "sound som sino bell",
    ) { vm.setSound(it) })
    // U2: which durations every timer offers. Unset reads as Pomodoro here
    // — that's the app-wide default — while a reading session quietly uses
    // the simpler set until this is chosen. Both sets always end in
    // "Outro…", so a custom time is one tap away either way. // PT: os
    // tempos oferecidos pelo temporizador; por escolher = Pomodoro.
    focoRows.add(segmentedRow(
        label = tr("Tempos do temporizador"),
        options = listOf(
            TimerPresets.Pomodoro to tr("Pomodoro"),
            TimerPresets.Simples to tr("Simples"),
        ),
        selected = prefs.timerPresets ?: TimerPresets.Pomodoro,
        keywords = "timer temporizador minutos minutes pomodoro foco focus",
    ) { vm.setTimerPresets(it) })
    focoRows.add(toggleRow(
        label = tr("Notificações"),
        checked = prefs.remindersEnabled,
        subtitle = tr("Avisos locais enquanto a app está aberta."),
        keywords = "notifications notificações lembretes reminders avisos",
        onChange = { enabled ->
            vm.setRemindersEnabled(enabled)
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    ))
    if (prefs.remindersEnabled) {
        val reminderKeys = "notificações notifications lembretes reminders hora time"
        focoRows.add(timeRow(tr("Plano do dia"), prefs.plannerTime, reminderKeys) { vm.setPlannerTime(it) })
        focoRows.add(timeRow(tr("Hábitos pendentes"), prefs.habitsTime, reminderKeys, divider = false) { vm.setHabitsTime(it) })
        focoRows.add(timeRow(tr("Reflexão noturna"), prefs.reflectionTime, reminderKeys, divider = false) { vm.setReflectionTime(it) })
        focoRows.add(SettingsRow(
            label = tr("Testar notificação"),
            keywords = "$reminderKeys teste test",
            divider = false,
        ) {
            Column(Modifier.fillMaxWidth()) {
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
                        .clip(RoundedCornerShape(PautaRadius.Chip))
                        .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip))
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
        })
    }
    sections += SettingsSection(tr("Foco e lembretes"), focoRows)

    // ── COMPANHIA ────────────────────────────────────────────────────
    // native-only: how the app keeps you company — none of it is appearance,
    // which is where all three used to sit. // PT: a companhia da app; nada
    // disto era aparência.
    val companhiaRows = mutableListOf<SettingsRow>()
    companhiaRows.add(toggleRow(
        label = tr("Vibração"),
        checked = prefs.haptics,
        subtitle = tr("Pequeno toque ao concluir."),
        keywords = "haptics vibration vibração",
    ) { vm.setHaptics(it) })
    companhiaRows.add(toggleRow(
        label = tr("Papagaio ajudante"),
        checked = prefs.parrot,
        subtitle = tr("O Pip aparece com dicas e piadas. Toca-lhe para mais."),
        keywords = "parrot papagaio pip",
    ) { vm.setParrot(it) })
    companhiaRows.add(toggleRow(
        label = tr("Ecrã inteiro"),
        checked = prefs.immersive,
        subtitle = tr("Esconde as barras do sistema. Deslize da margem para as ver."),
        keywords = "fullscreen immersive ecrã inteiro",
    ) { vm.setImmersive(it) })
    sections += SettingsSection(tr("Companhia"), companhiaRows)

    // ── ANÁLISE E OBJETIVOS ──────────────────────────────────────────
    // Looking back and aiming forward — four full-surface screens, one card.
    // // PT: olhar para trás e apontar em frente, no mesmo cartão.
    val analiseRows = mutableListOf<SettingsRow>()
    analiseRows.add(actionRow(
        label = tr("Revisão semanal"),
        subtitle = tr("Foco, hábitos e padrões dos últimos 7 dias."),
        keywords = "insights review revisão semana week",
    ) { showInsights = true })
    analiseRows.add(actionRow(
        label = tr("Retrospetiva do ano"),
        subtitle = tr("Resumo anual de foco, hábitos e intenções."),
        keywords = "year ano retrospetiva review",
    ) { onOpenYearReview() })
    analiseRows.add(actionRow(
        label = tr("Como funcionam as marés"),
        subtitle = tr("Streaks, níveis e respiros explicados."),
        keywords = "marés tides habits hábitos ajuda help",
    ) { onOpenTierGuide() })
    analiseRows.add(actionRow(
        label = tr("Objetivos trimestrais"),
        keywords = "goals objetivos trimestre quarter",
    ) { onOpenGoals() })
    sections += SettingsSection(tr("Análise e objetivos"), analiseRows)

    // ── DADOS E PRIVACIDADE ──────────────────────────────────────────
    // The lock, the copies and the export are one question — "who can reach
    // my data, and where does it go" — so they answer it together. // PT: o
    // bloqueio, as cópias e a exportação respondem à mesma pergunta.
    val dadosRows = mutableListOf<SettingsRow>()
    if (prefs.pinHash == null) {
        dadosRows.add(actionRow(
            label = tr("Bloqueio por PIN"),
            subtitle = tr("Protege a app com um código de 4+ dígitos."),
            keywords = "pin lock bloqueio código privacidade privacy",
        ) { showPinSet = true })
    } else {
        dadosRows.add(actionRow(
            label = tr("Desativar bloqueio por PIN"),
            subtitle = tr("Introduz o PIN atual para remover o bloqueio."),
            keywords = "pin lock bloqueio privacidade privacy",
        ) { showPinDisable = true })
        // C3: biometric unlock — only with a PIN set and usable biometrics
        // (hardware + something enrolled). No biometrics → this row never
        // appears, so the lock stays exactly PIN-only. // PT: biometria só
        // com PIN definido e biometria disponível.
        if (canBiometric) dadosRows.add(toggleRow(
            label = tr("Desbloqueio biométrico"),
            subtitle = tr("Desbloqueia com impressão digital ou rosto; o PIN fica como alternativa."),
            checked = prefs.biometricEnabled,
            keywords = "pin biometric biometria impressão digital fingerprint",
        ) { vm.setBiometricEnabled(it) })
    }
    dadosRows.add(toggleRow(
        label = tr("Cópia automática"),
        checked = prefs.autoBackup != "off",
        subtitle = tr("Guarda em segundo plano, mesmo com a app fechada."),
        keywords = "backup cópia copia automática",
    ) { enabled -> vm.setAutoBackupCadence(if (enabled) "daily" else "off") })
    if (prefs.autoBackup != "off") {
        dadosRows.add(pickerRow(
            label = tr("Frequência"),
            options = listOf(
                "daily" to tr("Diária"),
                "weekly" to tr("Semanal"),
                "hourly" to tr("Por hora"),
            ),
            selected = prefs.autoBackup,
            keywords = "backup cópia copia frequency frequência",
        ) { vm.setAutoBackupCadence(it) })
        // B1: pick a real folder (Drive, device storage…) so the copy
        // survives an uninstall — the filesDir copy is only a fallback.
        // // PT: pasta real para a cópia sobreviver à desinstalação.
        dadosRows.add(actionRow(
            label = if (backupFolder == null) tr("Escolher pasta…") else tr("Pasta de cópia"),
            subtitle = if (backupFolder == null)
                tr("Guarda também numa pasta tua (Drive, dispositivo…).")
            else null,
            // U4: the chosen folder is the row's *value* — the one thing
            // in a settings row worth the accent. // PT: a pasta escolhida
            // é o valor da linha, e é o que leva o destaque.
            value = backupFolder?.let { folderLabel(it) },
            keywords = "backup cópia copia pasta folder drive",
        ) { folderLauncher.launch(null) })
        if (backupFolder != null) dadosRows.add(actionRow(
            label = tr("Remover pasta"),
            subtitle = tr("Volta a guardar só dentro da app."),
            keywords = "backup cópia copia pasta folder",
            chevron = null,
        ) { vm.setBackupFolder(null) })
    }
    dadosRows.add(actionRow(
        label = tr("Exportar dados"),
        subtitle = tr("Transfere um ficheiro .json com tudo."),
        keywords = "backup export exportar cópia copia json",
    ) { vm.exportBackup { json -> shareBackup(context, json) } })
    dadosRows.add(actionRow(
        label = tr("Enviar para a nuvem"),
        subtitle = tr("Partilha a cópia para o Drive, Dropbox, Ficheiros…"),
        keywords = "backup cloud nuvem cópia copia drive dropbox",
    ) { vm.exportBackup { json -> shareBackup(context, json) } })
    dadosRows.add(actionRow(
        label = tr("Importar dados"),
        subtitle = tr("Restaura a partir de um ficheiro .json."),
        keywords = "backup import importar restore restaurar json",
    ) { importLauncher.launch("application/json") })
    // Only surfaced once there's something archived — keeps the section
    // quiet for everyone else. // PT: só aparece quando há marés arquivadas.
    if (archivedHabits.isNotEmpty()) dadosRows.add(actionRow(
        label = tr("Marés arquivadas"),
        subtitle = if (archivedHabits.size == 1) tr("1 maré escondida da grelha.")
            else trf("{n} marés escondidas da grelha.", "n" to archivedHabits.size),
        keywords = "archived arquivadas marés tides hábitos habits",
    ) { showArchived = true })
    sections += SettingsSection(tr("Dados e privacidade"), dadosRows)

    // ── SOBRE ────────────────────────────────────────────────────────
    // What build this is, whether there's a newer one, and where the code
    // lives — the three things you come here to read. The update state below
    // is still the inline seven-branch `when`; U6 moves it into a sheet.
    // // PT: a versão, a atualização e o código-fonte, juntos.
    val sobreRows = mutableListOf<SettingsRow>()
    sobreRows.add(SettingsRow(versionLabel, keywords = "${tr("Versão")} version build") {
        Text(
            versionLabel,
            color = colors.ink4,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    })
    // The update block has no visible label of its own until U6 gives it
    // one, so its search key is the word people actually type.
    // // PT: o bloco das atualizações ainda não tem rótulo visível; a
    // chave de procura é a palavra que se escreve.
    sobreRows.add(SettingsRow(
        label = tr("Atualizações"),
        keywords = "update updates atualizar versão version nova",
        divider = false,
    ) {
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
                ActionRow(tr("Tentar outra vez"), onClick = { vm.installUpdate(context) })
            }
            updChecking -> Text(
                tr("A verificar…"),
                color = colors.ink3,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            // Offline / transient failure after backoff — say so, don't lie
            // "up to date" (B2). // PT: falha de rede, não "atualizado".
            updCheckFailed -> {
                Text(
                    tr("Não foi possível verificar. Confirma a ligação à internet."),
                    color = colors.accent,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                ActionRow(tr("Tentar outra vez"), onClick = { vm.checkForUpdate() })
            }
            updAvailable != null -> Column {
                ActionRow(tr("Transferir nova versão"), onClick = { vm.installUpdate(context) })
                if (updNeedsPerm) {
                    Text(
                        text = tr("Permite instalar apps desta origem e toca outra vez."),
                        color = colors.accent,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                // Release notes (the GitHub release body), shown plainly — the
                // JSON always carried them but nothing ever displayed them (B2).
                // PT: notas da versão, mostradas como texto simples.
                val notes = updAvailable!!.notes
                if (notes.isNotBlank()) {
                    SectionEyebrow(
                        tr("Novidades"),
                        color = colors.ink4,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        text = notes,
                        color = colors.ink3,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
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
            else -> ActionRow(tr("Verificar atualizações"), onClick = { vm.checkForUpdate() })
        }
    })
    // U4: the source link leaves the centred footer for a row of Sobre,
    // where it belongs — and finally points at *this* repository. It read
    // `Iouzy/psychic-guide`, a different repo entirely. // PT: o link do
    // código-fonte passa a linha da secção Sobre — e aponta para o
    // repositório certo.
    sobreRows.add(actionRow(
        label = tr("Código-fonte"),
        subtitle = SOURCE_REPO.removePrefix("https://"),
        keywords = "source code github repositório",
        chevron = "↗",
    ) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_REPO))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    })
    sections += SettingsSection(tr("Sobre"), sobreRows)

    // ── ZONA PERIGOSA ────────────────────────────────────────────────
    // The one section you should never reach by accident: an extra gap and a
    // full-width rule cut it off from the list above, and its header carries
    // the danger red the rows use. It is searchable like any other section,
    // but being last in this list it can never float to the top of a result
    // — the two destructive rows keep their moat. // PT: a zona perigosa fica
    // separada e, por ser a última, nunca sobe ao topo dos resultados.
    val perigosaRows = mutableListOf<SettingsRow>()
    perigosaRows.add(actionRow(
        label = tr("Recarregar exemplo"),
        subtitle = tr("Repõe os dados de exemplo para explorar a app."),
        keywords = "reset sample exemplo reseed",
    ) { showReseedConfirm = true })
    perigosaRows.add(actionRow(
        label = tr("Apagar tudo"),
        subtitle = tr("Remove permanentemente todos os dados."),
        keywords = "delete reset apagar wipe",
        danger = true,
    ) { showResetConfirm = true })
    sections += SettingsSection(tr("Zona perigosa"), perigosaRows, danger = true)

    val visible: List<SettingsSection> = if (!searching) sections else sections
        .map { SettingsSection(it.title, it.rows.filter { row -> row.matches(folded) }, it.danger) }
        .filter { it.rows.isNotEmpty() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Navigation header. U7: the whole right side used to be empty while the
        // app's biggest state change sat six rows into a card below — the lens
        // switcher lives here now, where a header control belongs. // PT: o
        // seletor de lente ocupa o lado direito do cabeçalho, antes vazio.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←",
                color = colors.accent,
                fontSize = 22.sp,
                modifier = Modifier.clickableNoRipple(onClose),
            )
            Spacer(Modifier.width(14.dp))
            // The title takes the slack, so the pill stays pinned right and a
            // long title (or textScale 1.3) wraps instead of pushing it off the
            // edge. // PT: o título fica com a folga; a pílula não é empurrada.
            Text(
                tr("Definições"),
                color = colors.ink,
                fontFamily = SerifFamily,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            LensSwitch(bookMode = prefs.bookMode, onSelect = { vm.setBookMode(it) })
        }

        // U5: the search field sits directly under the header and is deliberately
        // NOT autofocused — the resting screen has to stay the list, or every
        // visit opens the keyboard for a setting you were about to scroll to.
        // // PT: a procura fica sob o cabeçalho e não rouba o foco.
        Spacer(Modifier.height(14.dp))
        SettingsSearch(
            value = query,
            onChange = { next ->
                // Captured here, not in an effect: by the time the list has
                // filtered, the offset has already been clamped away. // PT: a
                // posição guarda-se antes de a lista encolher.
                if (query.isBlank() && next.isNotBlank()) restingScroll = scroll.value
                query = next
            },
            onClear = { query = "" },
        )

        // Hero — app identity, matches web DataSheet hero header. It stands down
        // during a search so the results start under the field instead of behind
        // 80dp of app identity. // PT: o herói sai de cena durante a procura.
        if (!searching) {
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(PautaRadius.Field))
                        .background(colors.accent.copy(alpha = 0.08f))
                        .border(1.dp, colors.accent.copy(alpha = 0.2f), RoundedCornerShape(PautaRadius.Field)),
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
                    // U4: the subtitle names the three tabs you actually have — the
                    // planner's, or the reading companion's under book mode. It used
                    // to say "Hoje · Pauta · Marés" in both. // PT: o subtítulo segue
                    // o modo; antes mentia no modo livro.
                    Text(
                        if (prefs.bookMode) tr("Estante · Sessão · Hábitos") else tr("Hoje · Pauta · Marés"),
                        color = colors.ink3,
                        fontFamily = SerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    )
                }
            }
            HorizontalDivider(color = colors.rule)
        }

        // The one rendering of the index: grouped when idle, filtered when not.
        // Each row keeps its section card and its eyebrow either way, so a hit
        // arrives with the context that explains it. // PT: a mesma lista,
        // agrupada ou filtrada — o resultado nunca perde a secção.
        visible.forEach { section ->
            // Keyed by identity, not by position: filtering drops whole sections
            // and rows, and an unkeyed slot would hand its `remember`ed state (an
            // open picker sheet, a half-confirmed delete) to whichever row landed
            // in it. // PT: chave por identidade — a filtragem não pode trocar o
            // estado das linhas.
            key(section.title) {
                if (section.danger) {
                    Spacer(Modifier.height(SectionGap))
                    HorizontalDivider(color = colors.rule)
                }
                Section(section.title, color = if (section.danger) DangerRed else colors.ink3)
                SectionCard {
                    section.rows.forEachIndexed { i, row ->
                        if (i > 0 && row.divider) CardDivider()
                        key(row.label) { row.content() }
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            Spacer(Modifier.height(SectionGap))
            EmptyState(tr("Nada encontrado."))
        }

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
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = RowVPadding),
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

// P9 · Settings anatomy — one section header (the app's [SectionEyebrow], not a
// private sans copy), one card (the shared [PautaCard]) and one row height, so
// scrolling the list reads as a single rhythm instead of eight. // PT: a
// anatomia das definições — um só cabeçalho, um só cartão, uma só altura de
// linha.

/** The gap above a section header. // PT: espaço antes de cada secção. */
private val SectionGap = 24.dp

/** Every settings row's vertical padding, and the floor that keeps an action row
 *  the same height as the toggle row above it (a M3 [Switch] is 32dp tall, plain
 *  text isn't). // PT: a altura mínima que alinha linhas de acção e de
 *  interruptor. */
private val RowVPadding = 11.dp
private val RowMinHeight = 56.dp

// ─── U5 · the settings index ──────────────────────────────────────────────
// One declaration per row, so the grouped list and the search results are the
// same list rendered twice — never two lists to keep in step. // PT: uma
// declaração por linha; a lista e os resultados são a mesma coisa.

/**
 * One row of Settings: what it says, what it is called when searched, and how it
 * draws itself.
 *
 * [keywords] are search-only and deliberately *not* wrapped in `tr` — they carry
 * the words from **both** languages plus the ones a user types regardless of the
 * app's language ("backup" for Cópia automática, "pin" for Bloqueio). A string
 * that is never rendered isn't a translatable string; making it one would mean a
 * PT user searching "backup" only finds it in English. [divider] is the rule
 * *above* the row, drawn only when it isn't the first one showing — which is why
 * dividers live here and not inside the content. // PT: as palavras-chave só
 * servem para procurar, por isso valem nas duas línguas.
 */
private class SettingsRow(
    val label: String,
    val subtitle: String? = null,
    val keywords: String? = null,
    val divider: Boolean = true,
    val content: @Composable () -> Unit,
)

/** A card of [SettingsRow]s under one eyebrow. [danger] is Zona perigosa's own
 *  treatment: the extra gap, the full-width rule and the red header. // PT: um
 *  cartão de linhas; [danger] traz o afastamento e o vermelho. */
private class SettingsSection(
    val title: String,
    val rows: List<SettingsRow>,
    val danger: Boolean = false,
)

/** Combining marks left behind by an NFD decomposition. */
private val CombiningMarks = Regex("\\p{Mn}+")

/** Fold a string for matching: accents off, case off — so "acao" finds "Ação"
 *  and "COPIA" finds "Cópia". // PT: sem acentos e sem maiúsculas, para que
 *  "acao" encontre "Ação". */
private fun searchFold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(CombiningMarks, "").lowercase()

/** [query] is already folded by the caller — folding it once per keystroke
 *  instead of once per row. // PT: a consulta já vem normalizada. */
private fun SettingsRow.matches(query: String): Boolean =
    searchFold(label).contains(query) ||
        (subtitle != null && searchFold(subtitle).contains(query)) ||
        (keywords != null && searchFold(keywords).contains(query))

// The factories below exist so a row's label and subtitle are written once and
// then used for *both* the search index and the row itself. // PT: o rótulo
// escreve-se uma vez e serve para procurar e para desenhar.

private fun actionRow(
    label: String,
    subtitle: String? = null,
    value: String? = null,
    keywords: String? = null,
    danger: Boolean = false,
    chevron: String? = "›",
    divider: Boolean = true,
    onClick: () -> Unit,
) = SettingsRow(label, subtitle, keywords, divider) {
    ActionRow(label, subtitle, value, danger, chevron, onClick)
}

/** An [actionRow] that only reports — the same anatomy, no tap target and no
 *  chevron. Modo's "Lente" row states the current lens without pretending to be
 *  the control that changes it. // PT: linha que só informa; o controlo está no
 *  cabeçalho. */
private fun infoRow(
    label: String,
    subtitle: String? = null,
    value: String? = null,
    keywords: String? = null,
    divider: Boolean = true,
) = SettingsRow(label, subtitle, keywords, divider) {
    ActionRow(label, subtitle, value, chevron = null, onClick = null)
}

private fun toggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    keywords: String? = null,
    divider: Boolean = true,
    onChange: (Boolean) -> Unit,
) = SettingsRow(label, subtitle, keywords, divider) {
    ToggleRow(label, checked, subtitle, onChange)
}

private fun segmentedRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    keywords: String? = null,
    divider: Boolean = true,
    onSelect: (String) -> Unit,
) = SettingsRow(label, keywords = keywords, divider = divider) {
    SegmentedRow(label, options, selected, onSelect)
}

private fun pickerRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    keywords: String? = null,
    divider: Boolean = true,
    onSelect: (String) -> Unit,
) = SettingsRow(label, keywords = keywords, divider = divider) {
    PickerRow(label, options, selected, onSelect)
}

private fun timeRow(
    label: String,
    value: String,
    keywords: String? = null,
    divider: Boolean = true,
    onCommit: (String) -> Unit,
) = SettingsRow(label, keywords = keywords, divider = divider) {
    TimeRow(label, value, onCommit)
}

/**
 * U5 · The settings search field. The app's own [UnderlineField] rather than the
 * History view's boxed one — this sits under a header, not inside a card, and the
 * underline is what the sheets use. `ImeAction.Search` dismisses the keyboard so
 * the results the query just produced are actually visible. // PT: o campo de
 * procura das definições — sublinhado, como nas folhas.
 */
@Composable
private fun SettingsSearch(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    val colors = LocalPautaColors.current
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            UnderlineField(
                value = value,
                onChange = onChange,
                placeholder = tr("Procurar definições…"),
                fontSize = 15.sp,
                imeAction = ImeAction.Search,
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = tr("Limpar"),
                tint = colors.ink4,
                modifier = Modifier
                    .size(18.dp)
                    .clickableNoRipple { focus.clearFocus(); onClear() },
            )
        }
    }
}

/**
 * U7 · The lens switcher, in the settings header. Deliberately not a bare
 * [Switch]: a naked toggle in a header says nothing about what it toggles, while
 * two named sides say it in two words. Mono uppercase, accent fill on the active
 * side, `clickableNoRipple` like every other quiet control. The tint animates on
 * [PautaMotion.Fast] so it lands with the app-wide palette crossfade rather than
 * snapping ahead of it. // PT: o seletor de lente — dois lados nomeados, não um
 * interruptor mudo; a cor acompanha o esbatimento da paleta.
 */
@Composable
private fun LensSwitch(bookMode: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(PautaRadius.Chip))
            .border(1.dp, colors.rule, RoundedCornerShape(PautaRadius.Chip)),
    ) {
        LensSide("Pauta", selected = !bookMode) { onSelect(false) }
        LensSide(tr("Livro"), selected = bookMode) { onSelect(true) }
    }
}

@Composable
private fun LensSide(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalPautaColors.current
    val animate = rememberMotionEnabled()
    val spec: AnimationSpec<Color> = if (animate) PautaMotion.tween(PautaMotion.Fast) else snap()
    val bg by animateColorAsState(
        if (selected) colors.accent else Color.Transparent,
        animationSpec = spec,
        label = "lensBg",
    )
    val fg by animateColorAsState(
        if (selected) colors.onDark else colors.ink3,
        animationSpec = spec,
        label = "lensFg",
    )
    Text(
        label.uppercase(),
        color = fg,
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .semantics { this.selected = selected; this.role = Role.Tab }
            .background(bg)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Section(title: String, color: Color = LocalPautaColors.current.ink3) {
    Spacer(Modifier.height(SectionGap))
    SectionEyebrow(title, color = color)
    Spacer(Modifier.height(8.dp))
}

/** Rounded card grouping rows — the shared paper card, padded so its rows keep
 *  the gutter they had. // PT: o cartão partilhado a agrupar as linhas. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    PautaCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 16.dp),
        content = content,
    )
}

/** Thin rule between card items. */
@Composable
private fun CardDivider() {
    val colors = LocalPautaColors.current
    HorizontalDivider(color = colors.rule.copy(alpha = 0.6f))
}

/**
 * U4 · Row hierarchy. This used to paint every label in the accent, so Revisão
 * semanal, Exportar dados, Escolher pasta and Tentar outra vez all shouted in
 * the same terracota — accent everywhere is accent nowhere. Now the label is
 * plain ink (matching [ToggleRow]) and the accent is spent on the one
 * thing worth reading fast: the row's [value] — the chosen folder, the version,
 * "Nova versão". [chevron] shows the row is tappable instead of leaving it to be
 * inferred from colour ("›" for anything that opens in-app, "↗" for a link that
 * leaves it, null for a row that just *does* something). A null [onClick] is a
 * read-only row — same anatomy, no tap target — which is how Modo states the
 * current lens without pretending the row switches it. // PT: o rótulo em
 * tinta, o destaque só no valor, um chevron a dizer que a linha se toca, e
 * [onClick] nulo para uma linha que só informa.
 */
@Composable
private fun ActionRow(
    label: String,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    chevron: String? = "›",
    onClick: (() -> Unit)?,
) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier)
            .heightIn(min = RowMinHeight)
            .padding(vertical = RowVPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                // The danger red is the app's own [DangerRed], not the stray
                // Material 0xFFE53935 this row used to hard-code. // PT: o
                // vermelho da casa, não um vermelho Material solto.
                color = if (danger) DangerRed else colors.ink,
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
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                color = colors.accent,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
        if (chevron != null) {
            Spacer(Modifier.width(8.dp))
            // Decorative: the row's own label carries the semantics, and TalkBack
            // reading "greater-than sign" after every setting helps nobody.
            // // PT: decorativo — o TalkBack já lê o rótulo da linha.
            Text(
                text = chevron,
                color = colors.ink4,
                fontSize = 15.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * U4 · A one-of-N choice collapsed to `label — value ›`, opening a small sheet.
 * For the settings picked once and then forgotten (text size, backup cadence) a
 * permanent row of pills costs more space than the choice is worth; Língua and
 * Tema keep their [SegmentedRow], being two or three options changed often.
 * // PT: escolha rara — linha com o valor e folha pequena, em vez de pílulas
 * sempre visíveis.
 */
@Composable
private fun PickerRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ActionRow(
        label = label,
        value = options.firstOrNull { it.first == selected }?.second ?: selected,
        onClick = { open = true },
    )
    if (open) {
        ChoiceSheet(
            title = label,
            options = options,
            selected = selected,
            onSelect = { open = false; onSelect(it) },
            onClose = { open = false },
        )
    }
}

/** The picker sheet behind [PickerRow] — the same bordered option rows the
 *  "Trocar foco" sheet uses, with the current one in accent. // PT: as opções,
 *  na folha; a atual em destaque. */
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalPautaColors.current
    PautaSheet(title = title, onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(PautaRadius.Field))
                        .background(if (isSel) colors.accent.copy(alpha = 0.07f) else colors.paper)
                        .border(
                            1.dp,
                            if (isSel) colors.accent else colors.rule,
                            RoundedCornerShape(PautaRadius.Field),
                        )
                        .clickableNoRipple { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text,
                        color = if (isSel) colors.accent else colors.ink,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Text("✓", color = colors.accent, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * A7: the archived-tides manager (Settings → Dados e privacidade). Lists every archived tide
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
        Spacer(Modifier.height(SheetFieldGap))
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
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = RowVPadding),
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
    Column(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = RowVPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = colors.ink2, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                val isSel = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(PautaRadius.Chip))
                        .background(if (isSel) colors.accent.copy(alpha = 0.16f) else colors.paper)
                        .border(1.dp, if (isSel) colors.accent.copy(alpha = 0.3f) else colors.rule, RoundedCornerShape(PautaRadius.Chip))
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
