package com.pauta.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.pauta.app.i18n.Strings
import com.pauta.app.ui.screens.*
import com.pauta.app.ui.viewmodel.AppViewModel

private enum class Tab(val labelPt: String, val icon: ImageVector) {
    HOJE("Hoje", Icons.Outlined.CheckCircle),
    PAUTA("Pauta", Icons.Outlined.Timer),
    MARES("Marés", Icons.Outlined.Waves),
}

@Composable
fun MainScaffold(vm: AppViewModel) {
    val prefs by vm.prefs.collectAsState()
    val lang = prefs.lang
    var current by rememberSaveable { mutableStateOf(Tab.HOJE) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // Launcher shortcut / Quick-Settings tile → jump to the focus tab.
    LaunchedEffect(Unit) {
        com.pauta.app.service.FocusActionBus.openFocus.collect { current = Tab.PAUTA }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab,
                        onClick = { current = tab },
                        icon = { Icon(tab.icon, contentDescription = Strings.tr(tab.labelPt, lang)) },
                        label = { Text(Strings.tr(tab.labelPt, lang)) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            val openSettings = { showSettings = true }
            when (current) {
                Tab.HOJE  -> HojeScreen(vm, onOpenSettings = openSettings, onOpenHistory = { showHistory = true })
                Tab.PAUTA -> PautaScreen(vm, onOpenSettings = openSettings)
                Tab.MARES -> MaresScreen(vm, onOpenSettings = openSettings)
            }
        }
    }

    if (showSettings) {
        SettingsSheet(vm = vm, onDismiss = { showSettings = false })
    }
    if (showHistory) {
        HistorySheet(vm = vm, onDismiss = { showHistory = false })
    }
}
