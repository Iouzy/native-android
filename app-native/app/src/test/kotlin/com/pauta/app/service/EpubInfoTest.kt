package com.pauta.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L4: the chapter names cross the `:reader` boundary now instead of being dropped
 * at it. What is testable on the JVM is the shape they arrive in — the wire
 * itself needs a Bundle and therefore a device. // PT: os nomes atravessam a
 * fronteira do processo; aqui testa-se a forma com que chegam.
 */
class EpubInfoTest {

    private fun info(titles: List<String>) = EpubInfo(
        chapterWords = List(titles.size) { 100 },
        chapterHrefs = List(titles.size) { "ch$it.xhtml" },
        chapterTitles = titles,
    )

    @Test fun aChapterWithANameReportsIt() {
        assertEquals("As Cidades e os Mortos", info(listOf("As Cidades e os Mortos")).titleOf(0))
    }

    @Test fun aChapterWithoutOneReportsNothingRatherThanBlank() {
        // The caller falls back to "Capítulo {n}"; an empty string would render as
        // an empty row. // PT: vazio devolve nada, para o chamador usar o número.
        assertNull(info(listOf("")).titleOf(0))
        assertNull(info(listOf("   ")).titleOf(0))
    }

    @Test fun anIndexOutsideTheBookIsNotACrash() {
        assertNull(info(listOf("um")).titleOf(1))
        assertNull(info(listOf("um")).titleOf(-1))
    }

    @Test fun aBookThatSentNoTitlesAtAllStillWorks() {
        // An older reply carries no titles; every chapter falls back.
        // // PT: uma resposta sem títulos continua a funcionar.
        val none = EpubInfo(chapterWords = listOf(10, 20), chapterHrefs = listOf("a", "b"))
        assertNull(none.titleOf(0))
        assertEquals(2, none.chapterCount)
    }

    @Test fun aNameIsTrimmedBeforeItIsShown() {
        assertEquals("Prefácio", info(listOf("  Prefácio  ")).titleOf(0))
    }
}
