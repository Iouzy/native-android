package com.pauta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

            // Accessibility text size: scale the font density like the web's
            // html{font-size: textScale}. // PT: tamanho do texto.
            val density = LocalDensity.current
            val scaled = Density(density.density, density.fontScale * prefs.textScale)

            // Fullscreen + keep-awake follow the live preferences.
            val activeBlock by vm.activeBlock.collectAsStateWithLifecycle()
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (prefs.immersive) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                // Don't let the phone sleep while a block is running (web keepAwake).
                if (prefs.keepAwake && activeBlock != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            CompositionLocalProvider(LocalDensity provides scaled) {
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
}
