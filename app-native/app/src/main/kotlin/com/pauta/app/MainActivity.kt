package com.pauta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.ui.MainScaffold
import com.pauta.app.ui.Tab
import com.pauta.app.ui.theme.PautaTheme
import com.pauta.app.ui.theme.ThemeMode
import com.pauta.app.ui.theme.parseAccentOrDefault
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * The single activity. Draws the Compose UI edge-to-edge under transparent
 * system bars, themed from the user's persisted preferences (theme mode, accent,
 * high-contrast, language). A launcher shortcut or the Quick-Settings tile
 * arrives as the SHORTCUT_FOCUS action and opens straight on the Pauta (focus)
 * tab. // PT: atividade única; tema vem das preferências guardadas; abre na tab
 * Pauta quando vem do atalho/azulejo de foco.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val startTab = if (intent?.action == "com.pauta.app.SHORTCUT_FOCUS") Tab.PAUTA else Tab.HOJE

        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsStateWithLifecycle()

            // Keep the i18n source language in step with the stored preference.
            // Set during composition (idempotent) so tr() reads the right value
            // when the tree recomposes after a change. // PT: língua do i18n
            // alinhada com a preferência guardada.
            I18n.lang = if (prefs.lang == "en") Lang.EN else Lang.PT

            val mode = when (prefs.theme) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.AUTO
            }

            PautaTheme(
                mode = mode,
                accent = parseAccentOrDefault(prefs.accent),
                highContrast = prefs.highContrast,
            ) {
                MainScaffold(initialTab = startTab)
            }
        }
    }
}
