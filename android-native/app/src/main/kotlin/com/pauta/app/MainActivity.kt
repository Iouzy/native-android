package com.pauta.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.pauta.app.service.FocusActionBus
import com.pauta.app.ui.MainScaffold
import com.pauta.app.ui.theme.PautaTheme
import com.pauta.app.ui.viewmodel.AppViewModel
import com.pauta.app.ui.viewmodel.AppViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels { AppViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Edge-to-edge with transparent system bars so the themed background sits
        // behind the status/navigation bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // Route notification-button actions (pause/resume/conclude) back into the
        // store. FocusActionReceiver posts them onto a shared flow.
        lifecycleScope.launch {
            FocusActionBus.actions.collect { kind -> viewModel.onFocusAction(kind) }
        }

        setContent {
            val prefs by viewModel.prefs.collectAsState()
            PautaTheme(theme = prefs.theme, accent = prefs.accent) {
                MainScaffold(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Detect a day rollover while the app was backgrounded.
        viewModel.refreshDay()
        handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    private fun handleShortcut(intent: Intent?) {
        if (intent?.action == SHORTCUT_FOCUS) {
            FocusActionBus.requestOpenFocus()
        }
    }

    companion object {
        const val SHORTCUT_FOCUS = "com.pauta.app.SHORTCUT_FOCUS"
    }
}
