package com.pauta.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pauta.app.i18n.tr
import com.pauta.app.ui.screens.GoalsScreen
import com.pauta.app.ui.screens.HistoryView
import com.pauta.app.ui.screens.HojeScreen
import com.pauta.app.ui.screens.MaresScreen
import com.pauta.app.ui.screens.PautaScreen
import com.pauta.app.ui.screens.PinMode
import com.pauta.app.ui.screens.PinScreen
import com.pauta.app.ui.screens.SettingsScreen
import com.pauta.app.ui.screens.TierGuideScreen
import com.pauta.app.ui.screens.YearReviewScreen
import com.pauta.app.ui.theme.LocalPautaColors
import com.pauta.app.ui.theme.bookPautaColors
import com.pauta.app.ui.theme.MonoFamily
import com.pauta.app.ui.theme.SerifFamily
import com.pauta.app.ui.viewmodel.AppViewModel
import com.pauta.app.ui.viewmodel.PendingUndo
import kotlinx.coroutines.launch

/** The three tabs, in order. Their labels are the Portuguese source strings
 *  (Hoje / Pauta / Marés), translated through the i18n layer. */
enum class Tab(val ptLabel: String) {
    HOJE("Hoje"),
    PAUTA("Pauta"),
    MARES("Marés"),
}

/** C4: an external way into the app, parsed by [MainActivity] from the launch (or
 *  new) intent and handed to [MainScaffold]: a launcher shortcut/QS tile that
 *  opens a tab, or a shared text that becomes a new intention. // PT: uma entrada
 *  externa — atalho que abre uma tab, ou texto partilhado que vira intenção. */
sealed interface AppEntry {
    data class OpenTab(val tab: Tab) : AppEntry
    data class ShareText(val text: String) : AppEntry
}

/** A8: the full-surface screens reached from the shell are real navigation
 *  destinations now, not hand-rolled `if (show…)` overlays — so each one peels
 *  back predictively (Android 14+) and lives on a back stack. // PT: rotas de
 *  navegação para os ecrãs de página inteira (em vez de sobreposições à mão). */
private object Route {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    const val YEAR_REVIEW = "yearReview"
    const val TIER_GUIDE = "tierGuide"
    const val HISTORY = "history"
}

/**
 * The app shell. A single [NavHost] hosts the tab shell ([HomeShell]) plus the
 * full-surface screens (settings, goals, year review, tide guide, history) as
 * sibling destinations, so the system back gesture peels each one predictively
 * and state lives on a back stack. The PIN lock and the first-run onboarding sit
 * *above* the NavHost — they must cover every destination. // PT: casca da app —
 * um NavHost com a shell das tabs e os ecrãs de página inteira como destinos;
 * bloqueio por PIN e onboarding ficam por cima de tudo.
 *
 * @param entry the latest external entry (launcher shortcut or shared text), or
 *   null. Drives the initial tab plus, while running, tab switches and the
 *   share-confirm sheet.
 * @param onEntryConsumed called once an [entry] has been acted on, so the next
 *   one (delivered via onNewIntent) is seen as a fresh change.
 */
@Composable
fun MainScaffold(entry: AppEntry?, onEntryConsumed: () -> Unit) {
    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val needsUnlock by vm.needsUnlock.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()

    // C4: the tab the pager opens on at first composition — captured once from the
    // launch entry (a focus shortcut/QS tile → Pauta) so a cold start lands there
    // with no animation. Later entries animate via [tabRequest]. // PT: tab inicial
    // do pager, fixada na primeira entrada (sem animação no arranque a frio).
    val initialTab = remember { (entry as? AppEntry.OpenTab)?.tab ?: Tab.HOJE }

    // C4: entries that arrive while the app runs. A shortcut sets [tabRequest] (the
    // pager animates to it); a shared text sets [pendingShare] (a confirm sheet
    // adds it to today). Both pop back to Home first, so they work from any
    // destination. // PT: entradas com a app aberta — atalho muda de tab; texto
    // partilhado abre a folha de confirmação.
    var tabRequest by remember { mutableStateOf<Tab?>(null) }
    var pendingShare by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry) {
        when (val e = entry) {
            is AppEntry.OpenTab -> {
                navController.popBackStack(Route.HOME, inclusive = false)
                tabRequest = e.tab
            }
            is AppEntry.ShareText -> {
                navController.popBackStack(Route.HOME, inclusive = false)
                tabRequest = Tab.HOJE
                pendingShare = e.text
            }
            null -> Unit
        }
        if (entry != null) onEntryConsumed()
    }

    // The app keeps all state in one Activity-scoped AppViewModel. NavHost would
    // otherwise give every destination its own back-stack-entry ViewModelStore,
    // so a screen's `viewModel()` would build a *separate* AppViewModel per route.
    // Capture the Activity's owner here (we're above the NavHost) and re-provide
    // it inside each destination. // PT: fixa o AppViewModel único (da Activity)
    // em cada destino, senão cada rota teria o seu.
    val appOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner above MainScaffold"
    }

    // A8: screen transitions are animations, so they honour reduced motion —
    // instant (no slide/fade) when it's on, which also makes the predictive-back
    // gesture a plain snap. // PT: transições respeitam "movimento reduzido".
    val animate = !prefs.reducedMotion

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

    // K4: sepia/parchment colours wrap the whole NavHost; overlays (PIN, onboarding)
    // remain outside so they always use the base palette.
    val baseColors = LocalPautaColors.current
    val effectiveColors = if (prefs.bookMode)
        bookPautaColors(dark = baseColors.isDark)
    else
        baseColors

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPautaColors provides effectiveColors) {
            NavHost(
                navController = navController,
                startDestination = Route.HOME,
                enterTransition = { enterPush(animate) },
                exitTransition = { exitPush(animate) },
                popEnterTransition = { enterPop(animate) },
                popExitTransition = { exitPop(animate) },
            ) {
                composable(Route.HOME) {
                    AppScoped(appOwner) {
                        HomeShell(
                            initialTab = initialTab,
                            requestedTab = tabRequest,
                            onTabConsumed = { tabRequest = null },
                            onOpenSettings = { navController.open(Route.SETTINGS) },
                            onOpenHistory = { navController.open(Route.HISTORY) },
                        )
                    }
                }
                composable(Route.SETTINGS) {
                    AppScoped(appOwner) {
                        SettingsScreen(
                            onClose = { navController.popBackStack() },
                            onOpenGoals = { navController.open(Route.GOALS) },
                            onOpenYearReview = { navController.open(Route.YEAR_REVIEW) },
                            onOpenTierGuide = { navController.open(Route.TIER_GUIDE) },
                        )
                    }
                }
                composable(Route.GOALS) {
                    AppScoped(appOwner) { GoalsScreen(onClose = { navController.popBackStack() }) }
                }
                composable(Route.YEAR_REVIEW) {
                    AppScoped(appOwner) { YearReviewScreen(onClose = { navController.popBackStack() }) }
                }
                composable(Route.TIER_GUIDE) {
                    AppScoped(appOwner) { TierGuideScreen(onClose = { navController.popBackStack() }) }
                }
                composable(Route.HISTORY) {
                    AppScoped(appOwner) {
                        // E1: HistoryView now owns its data (history list + search +
                        // per-day memory) via the app-scoped ViewModel, like the other
                        // promoted screens. // PT: o Histórico vai buscar os dados ao VM.
                        HistoryView(onClose = { navController.popBackStack() })
                    }
                }
            }
        }

        // PIN lock overlay — shown after cold-start when a PIN is configured.
        // Sits above the NavHost so it covers whatever destination is showing.
        // // PT: ecrã de bloqueio por PIN ao arrancar a app.
        if (needsUnlock && prefs.pinHash != null) {
            PinScreen(mode = PinMode.LOCK, onSuccess = { vm.unlockApp() })
        }

        // First-run welcome carousel — drawn last so it covers everything; gated
        // on prefsReady so it never flashes for existing users.
        val prefsReady by vm.prefsReady.collectAsStateWithLifecycle()
        if (prefsReady && !prefs.onboardingSeen) {
            OnboardingOverlay(onDone = { vm.setOnboardingSeen() })
        }

        // Post-update "what's new" — shown once after an in-place update, above the
        // NavHost like onboarding, but only for an already-onboarded user and after
        // any PIN unlock (so the lock screen still comes first). // PT: ecrã de
        // novidades pós-atualização, mostrado uma vez (depois do desbloqueio).
        val whatsNew by vm.whatsNew.collectAsStateWithLifecycle()
        whatsNew?.let { state ->
            val locked = needsUnlock && prefs.pinHash != null
            if (prefsReady && prefs.onboardingSeen && !locked) {
                WhatsNewOverlay(state = state, onDone = { vm.dismissWhatsNew() })
            }
        }

        // C4: a shared text (ACTION_SEND) waits for a confirm before it is added
        // as today's intention. The sheet is a real modal window, so it's gated to
        // never sit above the lock/onboarding overlays — a share that arrives while
        // locked simply waits there until unlock. // PT: texto partilhado espera
        // confirmação antes de virar intenção (nunca por cima do bloqueio).
        pendingShare?.let { text ->
            val locked = needsUnlock && prefs.pinHash != null
            if (prefsReady && prefs.onboardingSeen && !locked) {
                ShareIntentionSheet(
                    text = text,
                    onConfirm = { vm.addIntention(text); pendingShare = null },
                    onDismiss = { pendingShare = null },
                )
            }
        }
    }
}

/**
 * C4: the confirm-and-add sheet for a shared text. It shows the shared text as a
 * would-be intention and, on confirm, adds it to today — using the app's modal
 * surface (a bottom sheet on phones, drag-to-dismiss). // PT: folha de confirmação
 * do texto partilhado; adiciona-o às intenções de hoje.
 */
@Composable
private fun ShareIntentionSheet(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalPautaColors.current
    PautaSheet(title = tr("Nova intenção"), onClose = onDismiss) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = colors.ink,
            fontFamily = SerifFamily,
            fontSize = 18.sp,
            lineHeight = 25.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = tr("Adicionar como intenção de hoje?"),
            color = colors.ink3,
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.22.sp, // 0.02em of 11sp
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PautaButton(tr("Cancelar"), variant = PautaButtonVariant.Ghost, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            PautaButton(tr("Adicionar"), onClick = onConfirm)
        }
    }
}

/**
 * The tab shell: a status row with the settings gear, a full-bleed pager over the
 * three tabs, and the bottom tab bar (icon + uppercase mono label). Swipe moves
 * between tabs; tapping a tab animates to it; a hardware keyboard's 1 / 2 / 3 jump
 * straight to Hoje / Pauta / Marés — matching the web app's shortcuts. // PT: casca
 * das tabs — barra de estado, pager com swipe, barra de tabs e atalhos 1/2/3.
 */
@Composable
private fun HomeShell(
    initialTab: Tab,
    requestedTab: Tab?,
    onTabConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val colors = LocalPautaColors.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = initialTab.ordinal, pageCount = { Tab.entries.size })
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // C4: a launcher shortcut tapped while the app is open asks for a tab here —
    // animate to it, then clear the request so the same shortcut can fire again.
    // (Cold starts already open on it via initialTab, so this just no-ops.) // PT:
    // um atalho com a app aberta pede uma tab — anima até lá e limpa o pedido.
    LaunchedEffect(requestedTab) {
        val target = requestedTab ?: return@LaunchedEffect
        pager.animateScrollToPage(target.ordinal)
        onTabConsumed()
    }

    val vm: AppViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    // F2: real state for Pip's context moods — a running focus block (calm
    // watching) and today's intentions (a flutter when the last one completes).
    // // PT: estado real para os humores do Pip.
    val activeBlock by vm.activeBlock.collectAsStateWithLifecycle()
    val intentions by vm.intentions.collectAsStateWithLifecycle()

    // A7: a single app-wide snackbar offers "Anular" after a destructive single
    // delete (an intention or a focus block). The ViewModel emits the deleted
    // data; tapping Anular puts it back. Each event waits its turn, so two quick
    // deletes stay independently undoable. // PT: snackbar único com "Anular"
    // depois de apagar uma intenção/bloco; cada remoção fica anulável.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.undoRequests.collect { pending ->
            val message = when (pending) {
                is PendingUndo.Intention -> tr("Intenção removida")
                is PendingUndo.Block -> tr("Bloco removido")
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = tr("Anular"),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(pending)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.paper)
                .focusRequester(focusRequester)
                .focusable()
                // onKeyEvent (não onPreviewKeyEvent): propaga de baixo para cima, então
                // um campo de texto focado consome o dígito antes de chegar aqui — caso
                // contrário digitar "1"/"2"/"3" num input (ex.: minutos no Hoje) trocava de tab.
                // onKeyEvent (not onPreviewKeyEvent): bubbles up, so a focused text field
                // consumes the digit before it reaches this tab-switch shortcut.
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
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
            StatusRow(onMenu = onOpenSettings)
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (Tab.entries[page]) {
                    Tab.HOJE -> HojeScreen(onOpenHistory = onOpenHistory)
                    Tab.PAUTA -> PautaScreen()
                    Tab.MARES -> MaresScreen()
                }
            }
            TabBar(
                current = Tab.entries[pager.currentPage],
                onSelect = { tab -> scope.launch { pager.animateScrollToPage(tab.ordinal) } },
                bookMode = prefs.bookMode,
            )
        }

        // Pip lives just above the tab bar in the bottom-right corner.
        if (prefs.parrot) {
            ParrotCompanion(
                tab = Tab.entries[pager.currentPage],
                activeFocus = activeBlock != null,
                allIntentionsDone = intentions.isNotEmpty() && intentions.all { it.done },
                reflectionTime = prefs.reflectionTime,
                reducedMotion = prefs.reducedMotion,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 10.dp, bottom = 80.dp),
            )
        }

        // The undo snackbar floats just above the tab bar (and the system nav
        // bar), styled as the app's dark ink surface with an accent action — not
        // the stock Material colours. // PT: snackbar sobre a barra de tabs, com
        // as cores da app (superfície escura, ação em destaque).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = colors.surfaceDark,
                contentColor = colors.onDark,
                actionColor = colors.accent,
            )
        }
    }
}

/**
 * Re-provide the Activity-scoped [ViewModelStoreOwner] inside a NavHost
 * destination, so every `viewModel()` call within resolves to the app's single
 * [AppViewModel] instead of a per-route one. // PT: fixa o dono do ViewModel da
 * Activity dentro de cada destino.
 */
@Composable
private fun AppScoped(owner: ViewModelStoreOwner, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

/** Navigate to [route], reusing the top entry if it's already the current one —
 *  so a quick double-tap can't stack the same screen twice. // PT: navega sem
 *  empilhar a mesma rota em toques repetidos. */
private fun NavController.open(route: String) {
    navigate(route) { launchSingleTop = true }
}

// A8: navigation transitions — a calm horizontal push (new screen slides in from
// the right; the one behind drifts a quarter-width with a fade for depth). The
// pop reverses, and because NavHost drives popExit seekably, that right-ward
// slide *is* the predictive-back peel under the finger. All instant when reduced
// motion is on. // PT: transições de navegação — empurrão horizontal suave; o pop
// é o "descascar" do gesto preditivo. Instantâneas com movimento reduzido.
private const val NavTransitionMs = 300

private fun enterPush(animate: Boolean): EnterTransition =
    if (!animate) EnterTransition.None
    else slideInHorizontally(tween(NavTransitionMs)) { it } + fadeIn(tween(NavTransitionMs))

private fun exitPush(animate: Boolean): ExitTransition =
    if (!animate) ExitTransition.None
    else slideOutHorizontally(tween(NavTransitionMs)) { -it / 4 } + fadeOut(tween(NavTransitionMs))

private fun enterPop(animate: Boolean): EnterTransition =
    if (!animate) EnterTransition.None
    else slideInHorizontally(tween(NavTransitionMs)) { -it / 4 } + fadeIn(tween(NavTransitionMs))

private fun exitPop(animate: Boolean): ExitTransition =
    if (!animate) ExitTransition.None
    else slideOutHorizontally(tween(NavTransitionMs)) { it } + fadeOut(tween(NavTransitionMs))

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
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit, bookMode: Boolean = false) {
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
                    // K3: in book mode, substitute the three tab labels with their
                    // reading-companion equivalents (Estante / Sessão / Hábitos).
                    val label = if (bookMode) when (tab) {
                        Tab.HOJE -> tr("Estante")
                        Tab.PAUTA -> tr("Sessão")
                        Tab.MARES -> tr("Hábitos")
                    } else tr(tab.ptLabel)
                    Text(
                        text = label.uppercase(),
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
