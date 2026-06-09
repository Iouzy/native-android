package com.pauta.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.ui.viewmodel.AppViewModel
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
import com.pauta.app.i18n.tr
import com.pauta.app.ui.screens.HojeScreen
import com.pauta.app.ui.screens.MaresScreen
import com.pauta.app.ui.screens.PautaScreen
import com.pauta.app.ui.theme.LocalPautaColors
import kotlinx.coroutines.launch

/** The three tabs, in order. Their labels are the Portuguese source strings
 *  (Hoje / Pauta / Marés), translated through the i18n layer. */
enum class Tab(val ptLabel: String) {
    HOJE("Hoje"),
    PAUTA("Pauta"),
    MARES("Marés"),
}

/**
 * The app shell: a full-bleed pager over the three tabs with a bottom tab bar.
 * Swipe moves between tabs; tapping a tab animates to it; and a hardware
 * keyboard's 1 / 2 / 3 jump straight to Hoje / Pauta / Marés — matching the web
 * app's shortcuts. // PT: casca da app — pager com swipe entre as três tabs,
 * barra inferior, e atalhos físicos 1/2/3 como na web.
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
            }
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            // Real screens replace the placeholders tab by tab (Hoje → Phase 2,
            // Pauta → 3, Marés → 4). // PT: ecrãs reais entram tab a tab.
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
                    .padding(end = 10.dp, bottom = 64.dp),
            )
        }
    }
}

@Composable
private fun TabPlaceholder(tab: Tab) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "${tr(tab.ptLabel)} · ${tr("Em construção")}",
            color = colors.ink3,
        )
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val colors = LocalPautaColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.tabbarBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickableNoRipple { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = tr(tab.ptLabel),
                        color = if (selected) colors.accent else colors.ink3,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
