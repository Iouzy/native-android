package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F12: the three period chips are a way of *writing* `HabitEntity.time`, which is
 * `pauta.v4` data. Everything here is about not losing what is already in that
 * string. // PT: as pílulas escrevem o campo de texto que já existe — sem perder
 * nada do que lá está.
 */
class TimeOfDayTest {

    // ── reading ───────────────────────────────────────────────

    @Test fun anEmptyFieldSelectsNothing() {
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn(""))
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn("   "))
    }

    @Test fun theOwnersOwnTideReadsAsTwoPeriods() {
        // "manhã e tarde" is a real tide in the owner's list, and prose the app
        // could not previously use. // PT: uma maré real da lista do dono.
        assertEquals(setOf("manhã", "tarde"), TimeOfDay.periodsIn("manhã e tarde"))
    }

    @Test fun accentsAndCaseDoNotMatter() {
        assertEquals(setOf("manhã"), TimeOfDay.periodsIn("Manha"))
        assertEquals(setOf("manhã"), TimeOfDay.periodsIn("MANHÃ"))
        assertEquals(setOf("noite"), TimeOfDay.periodsIn("Noite"))
    }

    @Test fun englishSpellingsAreRecognisedToo() {
        // Stored text is Portuguese, but a tide someone typed in English still
        // lights its chip. // PT: guarda-se em português, lê-se nas duas línguas.
        assertEquals(setOf("manhã"), TimeOfDay.periodsIn("morning"))
        assertEquals(setOf("noite"), TimeOfDay.periodsIn("evening"))
        assertEquals(setOf("tarde", "noite"), TimeOfDay.periodsIn("afternoon and night"))
    }

    @Test fun aLongerWordIsNotAPeriod() {
        // "amanhã" contains "manhã" and is not it. // PT: "amanhã" não é "manhã".
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn("amanhã"))
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn("tardeza"))
    }

    @Test fun arbitraryTextSelectsNoChipAndIsNotChanged() {
        // The whole promise of "the field stays". // PT: o campo continua livre.
        val prose = "depois do almoço, antes do treino"
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn(prose))
        assertEquals(prose, TimeOfDay.write(prose, emptySet()))
    }

    // ── writing ───────────────────────────────────────────────

    @Test fun periodsAreWrittenInDayOrder() {
        assertEquals("manhã, noite", TimeOfDay.write("", setOf("noite", "manhã")))
        assertEquals("manhã, tarde, noite", TimeOfDay.write("", TimeOfDay.PERIODS.toSet()))
    }

    @Test fun aTapKeepsWhatTheChipsHaveNoOpinionAbout() {
        // Tapping a chip must not wipe the prose beside it — data loss on an
        // ordinary tap is the whole subject of this round.
        // // PT: tocar numa pílula não pode apagar o que estava escrito.
        val out = TimeOfDay.toggle("depois do almoço", "manhã")
        assertEquals(setOf("manhã"), TimeOfDay.periodsIn(out))
        assertEquals("manhã, depois do almoço", out)
    }

    @Test fun togglingOffLeavesTheRestBehind() {
        val on = TimeOfDay.toggle("antes do treino", "tarde")
        val off = TimeOfDay.toggle(on, "tarde")
        assertEquals(emptySet<String>(), TimeOfDay.periodsIn(off))
        assertEquals("antes do treino", off)
    }

    @Test fun togglingOffTheLastPeriodEmptiesTheField() {
        assertEquals("", TimeOfDay.toggle("manhã", "manhã"))
    }

    @Test fun theOwnersTideCanBeReducedToOnePeriod() {
        // …and the "e" left holding nothing goes with the period it joined.
        // // PT: e o "e" que ficou sem par vai com o período que ligava.
        assertEquals("manhã", TimeOfDay.toggle("manhã e tarde", "tarde"))
    }

    @Test fun aConjunctionThatIsSomebodysOwnWordIsLeftAlone() {
        // "café e treino" has no period in it, so nothing is ours to tidy.
        // // PT: sem período no texto, não se mexe em nada.
        assertEquals("café e treino", TimeOfDay.write("café e treino", emptySet()))
        assertEquals("manhã, café e treino", TimeOfDay.toggle("café e treino", "manhã"))
    }

    @Test fun aRoundTripThroughTheChipsIsStable() {
        // Selecting the same set twice must not accumulate separators.
        // // PT: escolher duas vezes não pode acumular vírgulas.
        val once = TimeOfDay.write("", setOf("manhã", "noite"))
        val twice = TimeOfDay.write(once, setOf("manhã", "noite"))
        assertEquals(once, twice)
    }

    @Test fun anImportedWebStringIsNotDamagedByBeingRead() {
        // pauta.v4 round-trips must stay lossless: reading a tide's `when` and
        // writing back the same selection has to return the same string.
        // // PT: ler e voltar a escrever não pode alterar o texto importado.
        for (prose in listOf("manhã", "tarde", "manhã, tarde, noite", "sempre que der")) {
            assertEquals(prose, TimeOfDay.write(prose, TimeOfDay.periodsIn(prose)))
        }
    }
}
