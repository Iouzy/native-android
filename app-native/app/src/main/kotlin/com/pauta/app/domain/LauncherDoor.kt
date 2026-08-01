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
}
