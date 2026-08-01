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

    @Test fun theAliasNamesMatchTheManifest() {
        // The manifest declares `.BookLauncher` on `.MainActivity` in the app's own
        // package; a rename on one side without the other silently stops both doors
        // from doing anything. // PT: os nomes têm de bater certo com o manifesto.
        assertEquals("com.pauta.app.BookLauncher", LauncherDoor.BOOK_ALIAS_CLASS)
        assertEquals("com.pauta.app.MainActivity", LauncherDoor.MAIN_CLASS)
    }
}
