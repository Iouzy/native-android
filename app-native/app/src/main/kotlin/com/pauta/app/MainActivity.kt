package com.pauta.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.ui.AppEntry
import com.pauta.app.ui.MainScaffold
import com.pauta.app.ui.Tab
import com.pauta.app.ui.theme.PautaTheme
import com.pauta.app.ui.theme.ThemeMode
import com.pauta.app.ui.theme.parseAccentOrDefault
import com.pauta.app.ui.viewmodel.AppViewModel

/**
 * The single activity. Draws the Compose UI edge-to-edge under transparent
 * system bars, themed from the user's persisted preferences (theme mode, accent,
 * high-contrast, language).
 *
 * It is also the app's front door for the OS: a launcher shortcut or the
 * Quick-Settings tile arrives as one of the SHORTCUT_* actions and opens straight
 * on the matching tab, and a text share (ACTION_SEND) arrives as a candidate
 * intention. Both are parsed into an [AppEntry] and handed to the Compose tree.
 * // PT: porta de entrada do SO — atalhos/azulejo abrem a tab certa; partilha de
 * texto vira uma intenção; tudo via [AppEntry].
 *
 * C3: a [FragmentActivity] (still an androidx ComponentActivity, so Compose's
 * setContent/edge-to-edge keep working) because the biometric-unlock prompt
 * requires one. // PT: é uma FragmentActivity — exigida pela folha biométrica.
 */
class MainActivity : FragmentActivity() {

    // C4: the latest external entry — a launcher shortcut (open a tab) or a shared
    // text (add as an intention), or null for a plain launch. Compose reads this
    // snapshot state, so updating it recomposes the tree. Set on first create and
    // on every new intent, because the activity is singleTop: an entry arriving
    // while the app is already open comes through onNewIntent, not onCreate. // PT:
    // entrada externa (atalho/partilha); atualizada também em onNewIntent.
    private val entry = mutableStateOf<AppEntry?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only the genuine first creation reads the launch intent. A later
        // recreation (e.g. a per-app language change recreates us) restores its
        // state and must NOT re-fire the original shortcut/share. // PT: só a
        // criação inicial lê o intent — a recriação (ex.: mudança de idioma) não
        // pode repetir o atalho/partilha.
        if (savedInstanceState == null) entry.value = parseEntry(intent)

        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsStateWithLifecycle()

            // Keep the i18n source language in step with the stored preference.
            // Set during composition (idempotent) so tr() reads the right value
            // when the tree recomposes after a change. // PT: língua do i18n
            // alinhada com a preferência guardada.
            I18n.lang = if (prefs.lang == "en") Lang.EN else Lang.PT

            // C4: on Android 13+, also keep the OS per-app locale (the system "App
            // language" setting) in step with the in-app choice — and adopt an
            // OS-side change back — so the two stay in sync both ways. The SDK gate
            // is a compile-time constant, so this conditional composable call is
            // stable. // PT: no Android 13+, sincroniza a língua com o idioma
            // por-app do sistema, nos dois sentidos.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val prefsReady by vm.prefsReady.collectAsStateWithLifecycle()
                SyncAppLocale(prefsReady = prefsReady, lang = prefs.lang, onAdopt = vm::setLang)
            }

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
                    MainScaffold(
                        entry = entry.value,
                        onEntryConsumed = { entry.value = null },
                    )
                }
            }
        }
    }

    /** C4: re-deliver an entry that arrives while the app is already open — the
     *  activity is singleTop, so a shortcut/share tapped now comes here rather than
     *  through a fresh onCreate. // PT: entrega uma entrada com a app já aberta. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entry.value = parseEntry(intent)
    }

    /** C4: map a launch/new intent to an [AppEntry] — the focus/quick-capture/tides
     *  shortcuts open a tab (the SHORTCUT_FOCUS one mirrors the QS tile), and an
     *  ACTION_SEND `text/plain` share becomes a candidate intention. Anything else
     *  (a plain MAIN launch) is null. // PT: traduz o intent numa entrada. */
    private fun parseEntry(intent: Intent?): AppEntry? = when (intent?.action) {
        ACTION_SHORTCUT_FOCUS -> AppEntry.OpenTab(Tab.PAUTA)
        ACTION_SHORTCUT_NEW_INTENTION -> AppEntry.OpenTab(Tab.HOJE)
        ACTION_SHORTCUT_MARES -> AppEntry.OpenTab(Tab.MARES)
        Intent.ACTION_SEND -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            if (intent.type == "text/plain" && !text.isNullOrBlank()) AppEntry.ShareText(text) else null
        }
        else -> null
    }

    private companion object {
        // Launcher-shortcut actions. SHORTCUT_FOCUS is shared with the QS tile and
        // the focus service; the other two are C4-only. // PT: ações dos atalhos.
        const val ACTION_SHORTCUT_FOCUS = "com.pauta.app.SHORTCUT_FOCUS"
        const val ACTION_SHORTCUT_NEW_INTENTION = "com.pauta.app.SHORTCUT_NEW_INTENTION"
        const val ACTION_SHORTCUT_MARES = "com.pauta.app.SHORTCUT_MARES"
    }
}

/**
 * C4: two-way sync between the in-app language toggle (`prefs.lang` — the source of
 * truth that drives I18n and the notification text) and the OS per-app locale (the
 * Android 13+ "App language" system setting).
 *
 * The OS side is a store we can read synchronously, while `prefs.lang` loads a
 * moment later from Room — so a fresh activity first ADOPTS an explicit, differing
 * OS-side language into prefs (the user changed it in system Settings, which
 * recreated us); from then on the in-app choice is authoritative and is MIRRORED to
 * the OS. The first pass never pushes, so a default install keeps the OS side at
 * "system default" with no needless recreate. // PT: sincroniza nos dois sentidos —
 * ao arrancar adota uma mudança feita no sistema; depois espelha a escolha da app.
 */
@Composable
private fun SyncAppLocale(prefsReady: Boolean, lang: String, onAdopt: (String) -> Unit) {
    // Plain remember (NOT Saveable): a locale change recreates the activity, and we
    // WANT the adopt pass to run again on that fresh composition. // PT: remember
    // simples — a recriação por mudança de locale deve correr a adoção de novo.
    var reconciled by remember { mutableStateOf(false) }
    LaunchedEffect(prefsReady, lang) {
        if (!prefsReady) return@LaunchedEffect
        val os = AppCompatDelegate.getApplicationLocales()
        val osLang = os.toLanguageTags()
            .takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("en", ignoreCase = true)) "en" else "pt" }
        if (!reconciled) {
            reconciled = true
            // First pass: adopt an explicit, differing OS language (the prefs change
            // re-runs this effect, now in sync); otherwise just record the baseline
            // and leave the OS side untouched. // PT: adota uma mudança explícita do
            // sistema; senão fica só a base, sem mexer no sistema.
            if (osLang != null && osLang != lang) onAdopt(osLang)
            return@LaunchedEffect
        }
        // A later in-app change is authoritative → mirror it to the OS per-app
        // locale. // PT: uma mudança posterior na app espelha-se no sistema.
        val want = LocaleListCompat.forLanguageTags(if (lang == "en") "en" else "pt-PT")
        if (os != want) AppCompatDelegate.setApplicationLocales(want)
    }
}
