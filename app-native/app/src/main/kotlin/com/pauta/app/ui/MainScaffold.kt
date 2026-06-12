package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.tr
import com.pauta.app.ui.screens.HojeScreen
import com.pauta.app.ui.screens.MaresScreen
import com.pauta.app.ui.screens.PautaScreen
import com.pauta.app.ui.screens.PinMode
import com.pauta.app.ui.screens.PinScreen
import com.pauta.app.ui.screens.SettingsScreen
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/** The three tabs, in order. Their labels are the Portuguese source strings
 *  (Hoje / Pauta / Marés), translated through the i18n layer. */
enum class Tab(val ptLabel: String) {
    HOJE("Hoje"),
    PAUTA("Pauta"),
    MARES("Marés"),
}

/**
 * The app shell, mirroring the web layout: a status row with the settings gear
 * at the right, a full-bleed pager over the three tabs, and the bottom tab bar
 * (icon + uppercase mono label). Swipe moves between tabs; tapping a tab
 * animates to it; and a hardware keyboard's 1 / 2 / 3 jump straight to Hoje /
 * Pauta / Marés — matching the web app's shortcuts. // PT: casca da app como na
 * web — barra de estado com engrenagem, pager com swipe, barra de tabs com
 * ícone + etiqueta, e atalhos físicos 1/2/3.
 *
 * @param initialTab which tab to open on (a focus shortcut/QS tile opens PAUTA).
 */
@Composable
fun MainScaffold(initialTab: Tab = Tab.HOJE) {
    val colors = LocalPautaColors.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialTab.ordinal, pageCount = { Tab.entries.size })
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val needsUnlock by vm.needsUnlock.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Coming back to the app re-checks the day at once (the in-process ticker
    // covers the foreground case) and triggers the auto-backup if due.
    // // PT: ao voltar à app, vira logo o dia e faz cópia automática se for altura.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.maybeRollover()
                vm.maybeAutoBackup(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.paper)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val target = when (ev.key) {
                        Key.One, Key.NumPad1 -> Tab.HOJE
                        Key.Two, Key.NumPad2 -> Tab.PAUTA
                        Key.Three, Key.NumPad3 -> Tab.MARES
                        else -> null
                    }
                    if (target != null) {
                        scope.launch { pager.animateScrollToPage(target.ordinal) }
                        true
                    } else false
                },
        ) {
            StatusRow(onMenu = { showSettings = true })
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.HOJE -> HojeScreen()
                    Tab.PAUTA -> PautaScreen()
                    Tab.MARES -> MaresScreen()
                }
            }
            TabBar(
                current = Tab.entries[pager.currentPage],
                onSelect = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal) } },
            )
        }

        // Pip lives just above the tab bar in the bottom-right corner.
        if (prefs.parrot) {
            ParrotCompanion(
                tab = Tab.entries[pager.currentPage],
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 10.dp, bottom = 80.dp),
            )
        }

        if (showSettings) {
            SettingsScreen(onClose = { showSettings = false })
        }

        // PIN lock overlay — shown after cold-start when a PIN is configured.
        // // PT: ecrã de bloqueio por PIN ao arrancar a app.
        if (needsUnlock && prefs.pinHash != null) {
            PinScreen(mode = PinMode.LOCK, onSuccess = { vm.unlockApp() })
        }

        // First-run welcome carousel — drawn last so it covers tabs, Pip and
        // settings; gated on prefsReady so it never flashes for existing users.
        val prefsReady by vm.prefsReady.collectAsStateWithLifecycle()
        if (prefsReady && !prefs.onboardingSeen) {
            OnboardingOverlay(onDone = { vm.setOnboardingSeen() })
        }
    }
}

/** The web's `.statusbar` row: just the quiet settings affordance, pushed to
 *  the right, under the (transparent) system status bar. */
@Composable
private fun StatusRow(onMenu: () -> Unit) {
    val colors = LocalPautaColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = PautaIcons.Gear,
            contentDescription = tr("Definições"),
            tint = colors.ink2,
            modifier = Modifier
                .size(16.dp)
                .clickableNoRipple(onMenu),
        )
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val colors = LocalPautaColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.tabbarBg),
    ) {
        // border-top: 1px solid var(--rule)
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.rule),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val tint = if (selected) colors.accent else colors.ink3
                Column(
                    Modifier
                        .weight(1f)
                        .clickableNoRipple { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = when (tab) {
                            Tab.HOJE -> PautaIcons.Hoje
                            Tab.PAUTA -> PautaIcons.Pauta
                            Tab.MARES -> PautaIcons.Mares
                        },
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tr(tab.ptLabel).uppercase(),
                        color = tint,
                        fontFamily = MonoFamily,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp, // 0.08em of 10sp
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
