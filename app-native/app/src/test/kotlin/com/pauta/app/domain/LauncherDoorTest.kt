package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R8: the two launcher icons map to the one `bookMode` boolean — and nothing else
 *  is allowed to. // PT: os dois ícones mapeiam para a única preferência de modo. */
class LauncherDoorTest {

    @Test fun theBookIconAsksForBookMode() {
        assertEquals(true, LauncherDoor.bookModeFor(LauncherDoor.BOOK_ALIAS_CLASS))
    }

    @Test fun theMainIconAsksForThePlanner() {
        assertEquals(false, LauncherDoor.bookModeFor(LauncherDoor.MAIN_CLASS))
    }

    @Test fun anythingElseLeavesTheModeAlone() {
        // A component we don't recognise, or none at all, is not a door — the
        // preference keeps whatever the user last chose.
        assertNull(LauncherDoor.bookModeFor(null))
        assertNull(LauncherDoor.bookModeFor(""))
        assertNull(LauncherDoor.bookModeFor("com.pauta.app.MainActivity2"))
        assertNull(LauncherDoor.bookModeFor("com.example.other.BookLauncher"))
    }

    // ── F6 · when a launch actually opens a door ──────────────

    private fun opens(
        action: String? = LauncherDoor.ACTION_MAIN,
        launcher: Boolean = true,
        fromHistory: Boolean = false,
        consumed: Boolean = false,
        className: String? = LauncherDoor.BOOK_ALIAS_CLASS,
    ) = LauncherDoor.opensADoor(action, launcher, fromHistory, consumed, className)

    @Test fun aLauncherTapOpensItsDoor() {
        assertEquals(true, opens())
        assertEquals(true, opens(className = LauncherDoor.MAIN_CLASS))
    }

    @Test fun aReturnThroughRecentsIsNotAChoiceOfIcon() {
        // FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY: the user swiped back into the app.
        // That says nothing about which icon they want, and flipping the mode there
        // would be the app changing itself behind them.
        // // PT: voltar pelos recentes não é escolher um ícone.
        assertEquals(false, opens(fromHistory = true))
    }

    @Test fun theSameLaunchCannotOpenTheDoorTwice() {
        // A launch intent outlives its launch — it is still getIntent() after a
        // configuration change — so a door read twice would be applied twice.
        // // PT: o mesmo intent não abre a porta duas vezes.
        assertEquals(false, opens(consumed = true))
    }

    @Test fun onlyAGenuineLauncherTapCounts() {
        assertEquals(false, opens(action = null))
        assertEquals(false, opens(action = "android.intent.action.SEND"))
        assertEquals(false, opens(action = "com.pauta.app.SHORTCUT_FOCUS"))
        assertEquals(false, opens(launcher = false))
    }

    @Test fun anIntentThatNamesNoDoorOpensNone() {
        assertEquals(false, opens(className = null))
        assertEquals(false, opens(className = "com.example.other.BookLauncher"))
    }

    @Test fun theActionConstantMatchesAndroids() {
        // Spelled out here so this file stays free of Android types; a drift would
        // silently stop every door. // PT: a constante tem de bater certo.
        assertEquals("android.intent.action.MAIN", LauncherDoor.ACTION_MAIN)
    }

    @Test fun theAliasNamesMatchTheManifest() {
        // The manifest declares `.BookLauncher` on `.MainActivity` in the app's own
        // package; a rename on one side without the other silently stops both doors
        // from doing anything. // PT: os nomes têm de bater certo com o manifesto.
        assertEquals("com.pauta.app.BookLauncher", LauncherDoor.BOOK_ALIAS_CLASS)
        assertEquals("com.pauta.app.MainActivity", LauncherDoor.MAIN_CLASS)
    }
}
