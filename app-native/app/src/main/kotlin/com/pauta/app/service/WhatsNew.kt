package com.pauta.app.service

import android.content.Context
import com.pauta.app.BuildConfig

/** The release notes to surface once, after an in-place update. [run] is the CI
 *  build run of the freshly-installed APK. // PT: as notas a mostrar uma vez. */
data class WhatsNewState(val run: Int, val notes: String)

/**
 * Tracks which build the user has already seen the "what's new" screen for, in a
 * tiny private SharedPreferences — device-local, never part of the `pauta.v4`
 * backup, readable at launch before Room loads. After the in-app updater installs
 * a newer APK, the next launch (whose [BuildConfig.BUILD_RUN] has advanced) shows
 * the stashed GitHub release notes once. A fresh install records a baseline
 * silently and shows nothing — we can't show notes for an update we never
 * tracked. // PT: regista a build já vista no ecrã de novidades e mostra as notas
 * da versão uma vez após uma atualização; uma instalação nova só semeia a base.
 */
object WhatsNew {
    private const val PREFS = "pauta_whatsnew"
    private const val KEY_LAST_RUN = "lastSeenRun"
    private const val KEY_NOTES = "pendingNotes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stash the release `body` just before handing the APK to the installer, so
     *  it can be shown on the next launch (once the run number has bumped). The
     *  updater is the only place a new build arrives, so this is the only writer. */
    fun stashNotes(context: Context, notes: String) {
        prefs(context).edit().putString(KEY_NOTES, notes.trim()).apply()
    }

    /** The notes to show on this launch, or null when there's nothing to show.
     *  Records a baseline on a fresh install so it never fires retroactively.
     *  // PT: as notas a mostrar agora, ou null; semeia a base na 1ª execução. */
    fun pending(context: Context): WhatsNewState? {
        val sp = prefs(context)
        val last = sp.getInt(KEY_LAST_RUN, 0)
        val run = BuildConfig.BUILD_RUN
        // Fresh install / first run with this feature: record where we are and stay
        // quiet. A local (run 0) build never trips this. // PT: 1ª execução → só base.
        if (last == 0) {
            if (run > 0) sp.edit().putInt(KEY_LAST_RUN, run).apply()
            return null
        }
        if (run <= last) return null
        return WhatsNewState(run = run, notes = sp.getString(KEY_NOTES, "").orEmpty())
    }

    /** Mark the current run seen (and drop the stashed notes) — called when the
     *  user dismisses the screen, so it shows only once. */
    fun markSeen(context: Context) {
        prefs(context).edit().putInt(KEY_LAST_RUN, BuildConfig.BUILD_RUN).remove(KEY_NOTES).apply()
    }
}
