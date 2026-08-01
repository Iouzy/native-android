package com.pauta.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R3: the one sum the reader can get silently wrong — how large to draw a page.
 *  // PT: o tamanho a que uma página é desenhada. */
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
}
