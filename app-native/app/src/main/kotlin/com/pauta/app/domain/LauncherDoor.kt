package com.pauta.app.domain

/**
 * native-only (R8): which door the app was opened through.
 *
 * There are two launcher icons and one app. The book icon is an `activity-alias`
 * onto `MainActivity`, so the only thing separating the doors is the component
 * name the OS launched — and all a door does is set the `bookMode` preference the
 * in-app toggle already owns. It is a shortcut to that toggle, not a second mode
 * system: there is still exactly one boolean, and the last action (icon or
 * toggle) wins.
 *
 * Kept here, free of Android types, so the mapping is testable on the JVM;
 * `MainActivity` supplies the class name and owns the "is this actually a cold
 * launcher start?" question. // PT: que porta abriu a app — só o nome do
 * componente; o resto é a preferência de sempre.
 */
object LauncherDoor {

    /** The book alias declared in the manifest (`.BookLauncher`). */
    const val BOOK_ALIAS_CLASS = "com.pauta.app.BookLauncher"

    /** The main icon, which resolves straight to the activity. */
    const val MAIN_CLASS = "com.pauta.app.MainActivity"

    /**
     * The mode a launch through [className] asks for: true from the book icon,
     * false from the main one, and **null for anything else** — an unrecognised
     * (or absent) component says nothing about the mode, and leaving the
     * preference alone is the only safe reading of silence. // PT: o modo que a
     * porta pede; null quando não é porta nenhuma e a preferência fica como está.
     */
    fun bookModeFor(className: String?): Boolean? = when (className) {
        BOOK_ALIAS_CLASS -> true
        MAIN_CLASS -> false
        else -> null
    }

    /** `Intent.ACTION_MAIN`, spelled out so this file stays free of Android types
     *  and testable on the JVM. // PT: a acção MAIN, sem importar Android. */
    const val ACTION_MAIN = "android.intent.action.MAIN"

    /**
     * F6 · whether a launch should apply its door.
     *
     * R8 answered this with "only on a cold start", and on a device that turned out
     * to mean *only* on a cold start: killing the app and tapping the book icon
     * opened book mode, but tapping it while the app sat in recents left whatever
     * mode was already showing. `onNewIntent` — the path a live app actually
     * receives a launcher tap through — passed `coldStart = false` and dropped the
     * door on purpose.
     *
     * The thing R8 was guarding against is real and is still guarded, just more
     * precisely. Two conditions replace "cold start":
     *
     * - **[fromHistory]** — `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` marks a return
     *   through the recents screen, which is not a statement about which icon you
     *   want. Without this, swiping back into the app would flip the mode.
     * - **[alreadyConsumed]** — the launch intent outlives the launch: it is still
     *   `getIntent()` after a configuration change or a process restore, so a door
     *   read twice is a door applied twice. Applying it once and marking it spent
     *   is what makes re-reading safe.
     *
     * // PT: substitui o "só no arranque a frio" por duas condições exactas — não
     * vir dos recentes, e ainda não ter sido usada.
     */
    fun opensADoor(
        action: String?,
        hasLauncherCategory: Boolean,
        fromHistory: Boolean,
        alreadyConsumed: Boolean,
        className: String?,
    ): Boolean =
        action == ACTION_MAIN &&
            hasLauncherCategory &&
            !fromHistory &&
            !alreadyConsumed &&
            bookModeFor(className) != null
}
