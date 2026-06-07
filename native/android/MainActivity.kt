package com.pauta.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(FocusActivityPlugin::class.java)
        registerPlugin(AppUpdaterPlugin::class.java)
        super.onCreate(savedInstanceState)

        // Draw edge-to-edge with transparent system bars so the app's own (themed)
        // background sits behind the status/navigation bars — instead of the
        // default light window background showing as an ugly grey/white strip at
        // the bottom. The web layer reserves room for the bars via
        // env(safe-area-inset-*) (viewport-fit=cover is set in index.html).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        registerShortcuts()
        // Cold start via a launcher shortcut: stash the action so the web layer can
        // consume it once it has mounted (FocusActivity.consumePendingShortcut()).
        handleShortcutIntent(intent, cold = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Warm start (app already running): the JS "action" listener is live, so
        // emit straight away rather than stashing.
        handleShortcutIntent(intent, cold = false)
    }

    private fun handleShortcutIntent(intent: Intent?, cold: Boolean) {
        try {
            if (intent?.action == SHORTCUT_FOCUS) {
                if (cold) pendingShortcutAction = "start-focus"
                else FocusActivityPlugin.onAction("start-focus")
            }
        } catch (e: Exception) { /* never let a shortcut break the launch path */ }
    }

    // Dynamic launcher shortcut: long-press the app icon → "Iniciar foco".
    // Dynamic (not static res/xml) keeps it self-contained here — no manifest
    // meta-data or string-resource injection — and ShortcutManagerCompat no-ops
    // cleanly below API 25. Best-effort: a failure must not affect launch.
    private fun registerShortcuts() {
        try {
            val launch = Intent(this, MainActivity::class.java)
                .setAction(SHORTCUT_FOCUS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val sc = ShortcutInfoCompat.Builder(this, "start-focus")
                .setShortLabel("Iniciar foco")
                .setLongLabel("Iniciar bloco de foco")
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_stat_focus))
                .setIntent(launch)
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(this, listOf(sc))
        } catch (e: Exception) { /* shortcuts are best-effort */ }
    }

    companion object {
        const val SHORTCUT_FOCUS = "com.pauta.app.SHORTCUT_FOCUS"
        // Set on a cold-start shortcut launch; drained by the web layer via the
        // FocusActivity.consumePendingShortcut() bridge once it's listening.
        @Volatile
        var pendingShortcutAction: String? = null
    }
}
