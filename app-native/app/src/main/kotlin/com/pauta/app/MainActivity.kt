package com.pauta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pauta.app.ui.MainScaffold
import com.pauta.app.ui.Tab
import com.pauta.app.ui.theme.PautaTheme

/**
 * The single activity. Draws the Compose UI edge-to-edge under transparent
 * system bars (the theme handles bar icon contrast). A launcher shortcut or the
 * Quick-Settings tile arrives as the SHORTCUT_FOCUS action and opens straight on
 * the Pauta (focus) tab. // PT: atividade única; abre na tab Pauta quando vem do
 * atalho/azulejo de foco.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val startTab = if (intent?.action == "com.pauta.app.SHORTCUT_FOCUS") Tab.PAUTA else Tab.HOJE

        setContent {
            // Phase 0 uses theme/accent defaults; Phase 1 wires these to persisted
            // prefs (theme mode, accent, high-contrast). // PT: por agora usa os
            // valores por omissão; a Fase 1 liga às preferências guardadas.
            PautaTheme {
                MainScaffold(initialTab = startTab)
            }
        }
    }
}
