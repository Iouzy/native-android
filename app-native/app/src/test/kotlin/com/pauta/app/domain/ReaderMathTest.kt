package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3: the one sum the reader can get silently wrong — how large to draw a page.
 *  R5: and what a session is worth when the reader closes. // PT: o tamanho a que
 *  uma página é desenhada, e o que vale uma sessão ao fechar o leitor. */
class ReaderMathTest {

    @Test fun drawsAtTheAskedWidthAndKeepsTheProportions() {
        // An A4-ish page (595×842 points) on a 1080px-wide phone.
        val (w, h) = ReaderMath.fitPage(595, 842, targetWidth = 1080, maxEdge = 2048)
        assertEquals(1080, w)
        assertEquals(1528, h)
        assertEquals(595f / 842f, w.toFloat() / h, 0.002f)
    }

    @Test fun aTallPageIsHeldByItsLongEdge() {
        // 595×842 asked for 2000px wide would be 2830px tall — over the ceiling,
        // so the height sets the scale and the width comes down with it.
        val (w, h) = ReaderMath.fitPage(595, 842, targetWidth = 2000, maxEdge = 2048)
        assertEquals(2048, h)
        assertTrue("width should shrink with the height, was $w", w in 1440..1450)
    }

    @Test fun aWidePageIsHeldByItsWidth() {
        val (w, h) = ReaderMath.fitPage(1600, 900, targetWidth = 4000, maxEdge = 2048)
        assertEquals(2048, w)
        assertEquals(1152, h)
    }

    @Test fun neitherEdgeEverPassesTheCeiling() {
        for (target in listOf(1, 500, 1080, 4096, 100_000)) {
            for ((pw, ph) in listOf(10 to 4000, 4000 to 10, 1 to 1, 3000 to 3000)) {
                val (w, h) = ReaderMath.fitPage(pw, ph, target, maxEdge = 2048)
                assertTrue("$pw×$ph at $target gave $w×$h", w in 1..2048 && h in 1..2048)
            }
        }
    }

    @Test fun nonsenseInputsComeBackAsOnePixelNotAsACrash() {
        assertEquals(1 to 1, ReaderMath.fitPage(0, 842, 1080, 2048))
        assertEquals(1 to 1, ReaderMath.fitPage(595, 0, 1080, 2048))
        assertEquals(1 to 1, ReaderMath.fitPage(595, 842, 0, 2048))
        assertEquals(1 to 1, ReaderMath.fitPage(595, 842, 1080, 0))
        assertEquals(1 to 1, ReaderMath.fitPage(-5, -5, -5, -5))
    }

    // ── R5 · what a closing session is worth ──

    @Test fun aTenSecondPeekAtTheSamePageSavesNothing() {
        val out = ReaderMath.sessionOutcome(durationMs = 10_000, startPage = 80, endPage = 80)
        assertFalse(out.save)
    }

    @Test fun aShortSittingThatTurnedAPageIsStillASession() {
        // The guard is short *and* still — checking a quote is not the same as
        // reading three pages over a coffee. // PT: curta mas com páginas conta.
        val out = ReaderMath.sessionOutcome(durationMs = 20_000, startPage = 80, endPage = 83)
        assertTrue(out.save)
        assertEquals(83, out.page)
        assertEquals(3, out.pagesDelta)
    }

    @Test fun aLongSittingCountsEvenWithNothingTurned() {
        // Forty minutes on one page is a page someone was reading.
        val out = ReaderMath.sessionOutcome(durationMs = 40 * 60_000L, startPage = 12, endPage = 12)
        assertTrue(out.save)
        assertEquals(0, out.pagesDelta)
    }

    @Test fun theMinuteBoundaryIsExactlyAMinute() {
        assertFalse(ReaderMath.sessionOutcome(59_999, 5, 5).save)
        assertTrue(ReaderMath.sessionOutcome(60_000, 5, 5).save)
    }

    @Test fun readingBackwardsKeepsItsSign() {
        // BookMath.pagesPerHour drops negative spans; it can only do that if the
        // delta arrives signed. // PT: o delta chega com sinal, para poder ser
        // descartado em vez de somado.
        val out = ReaderMath.sessionOutcome(30 * 60_000L, startPage = 140, endPage = 5)
        assertTrue(out.save)
        assertEquals(-135, out.pagesDelta)
        assertEquals(5, out.page)
    }

    @Test fun sessionMinutesRoundAndNeverReachZero() {
        assertEquals(38, ReaderMath.sessionMinutes(38 * 60_000L))
        assertEquals(39, ReaderMath.sessionMinutes(38 * 60_000L + 40_000L))
        assertEquals(1, ReaderMath.sessionMinutes(20_000))
        assertEquals(1, ReaderMath.sessionMinutes(0))
    }

    @Test fun aPdfBookmarkIsAZeroBasedIndexAndAPageIsNot() {
        assertEquals(80, ReaderMath.bookmarkPage("79", "pdf"))
        assertEquals(1, ReaderMath.bookmarkPage("0", "pdf"))
        assertEquals(1, ReaderMath.bookmarkPage("-4", "pdf"))
    }

    @Test fun anUnreadableBookmarkPointsAtNoPage() {
        assertNull(ReaderMath.bookmarkPage("", "pdf"))
        assertNull(ReaderMath.bookmarkPage("abc", "pdf"))
        // R4's EPUB position is "spine:percent" — a position, not a page.
        assertNull(ReaderMath.bookmarkPage("12:0.43", "epub"))
        assertNull(ReaderMath.bookmarkPage("79", null))
    }
}
