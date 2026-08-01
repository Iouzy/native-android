package com.pauta.app.domain

import com.pauta.app.data.entity.BookEntity
import com.pauta.app.domain.BookMath.SessionSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMathTest {

    private val hour = 3_600_000L

    private fun book(
        format: String = "physical",
        fileKind: String? = null,
        wordCount: Int = 0,
    ) = BookEntity(
        id = "bk_1",
        title = "Livro",
        format = format,
        filePath = fileKind?.let { "/data/files/books/bk_1.$it" },
        fileKind = fileKind,
        wordCount = wordCount,
        createdAt = 0L,
    )

    // ── pagesPerHour ──────────────────────────────────────────

    @Test fun paceNullWithNoSessions() {
        assertNull(BookMath.pagesPerHour(emptyList()))
    }

    @Test fun paceNullWithOneSession() {
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(30, hour))))
    }

    @Test fun paceIsOverallRateNotPerSessionAverage() {
        // 30 pages in 1h + 30 pages in 3h = 60 pages / 4h = 15/h (a naive
        // per-session average would say 20/h). // PT: ritmo global, não média.
        val pace = BookMath.pagesPerHour(listOf(SessionSpan(30, hour), SessionSpan(30, 3 * hour)))!!
        assertEquals(15f, pace, 0.001f)
    }

    @Test fun paceMixedDurationsAndDeltas() {
        // 10 in 30min + 25 in 90min = 35 pages / 2h = 17.5/h.
        val pace = BookMath.pagesPerHour(listOf(SessionSpan(10, hour / 2), SessionSpan(25, hour * 3 / 2)))!!
        assertEquals(17.5f, pace, 0.001f)
    }

    @Test fun paceIgnoresZeroDurationSessionsWhenCounting() {
        // The zero-duration span isn't a usable data point, so only one remains.
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(30, hour), SessionSpan(10, 0))))
    }

    @Test fun paceNullWhenNothingWasRead() {
        assertNull(BookMath.pagesPerHour(listOf(SessionSpan(0, hour), SessionSpan(0, hour))))
    }

    // ── etaDays ───────────────────────────────────────────────

    @Test fun etaZeroWhenNothingRemains() {
        assertEquals(0, BookMath.etaDays(remaining = 0, pagesPerHour = 20f))
        assertEquals(0, BookMath.etaDays(remaining = -5, pagesPerHour = 20f))
    }

    @Test fun etaNullWithoutAPace() {
        assertNull(BookMath.etaDays(remaining = 100, pagesPerHour = 0f))
        assertNull(BookMath.etaDays(remaining = 100, pagesPerHour = -1f))
    }

    @Test fun etaRoundsUpToWholeDays() {
        // 100 pages at 20/h × 1h/day = 5 days exactly; 101 pages tips to 6.
        assertEquals(5, BookMath.etaDays(remaining = 100, pagesPerHour = 20f))
        assertEquals(6, BookMath.etaDays(remaining = 101, pagesPerHour = 20f))
    }

    @Test fun etaScalesWithDailyReadingBudget() {
        // Half an hour a day halves the daily pages, doubling the days.
        assertEquals(10, BookMath.etaDays(remaining = 100, pagesPerHour = 20f, dailyReadingMinutes = 30f))
    }

    // ── R6 · wordsPerUnit ─────────────────────────────────────

    @Test fun audiobooksHaveNoWordsPerUnit() {
        // A listened minute is time, not words — there is no honest WPM here.
        assertNull(BookMath.wordsPerUnit(book(format = "audiobook")))
        // Even if one somehow carried a word count.
        assertNull(BookMath.wordsPerUnit(book(format = "audiobook", wordCount = 90_000)))
    }

    @Test fun pagesAreWorthTheEstimate() {
        assertEquals(280f, BookMath.wordsPerUnit(book())!!, 0.001f)
        assertEquals(280f, BookMath.wordsPerUnit(book(format = "ebook", fileKind = "pdf"))!!, 0.001f)
    }

    @Test fun countedEpubUnitsAreHundredthsOfTheText() {
        // An EPUB shows percent, so one unit of progress is 1% of 84 000 words.
        assertEquals(840f, BookMath.wordsPerUnit(book(fileKind = "epub", wordCount = 84_000))!!, 0.001f)
    }

    @Test fun uncountedEpubFallsBackToThePageEstimate() {
        // Attached but never read through: still tracked by hand, in pages.
        assertEquals(280f, BookMath.wordsPerUnit(book(fileKind = "epub"))!!, 0.001f)
    }

    @Test fun onlyACountedEpubDropsTheApproximationSign() {
        assertTrue(BookMath.hasCountedWords(book(fileKind = "epub", wordCount = 84_000)))
        assertFalse(BookMath.hasCountedWords(book(fileKind = "epub")))
        assertFalse(BookMath.hasCountedWords(book(fileKind = "pdf", wordCount = 84_000)))
        assertFalse(BookMath.hasCountedWords(book()))
    }

    // ── R6 · wordsPerMinute ───────────────────────────────────

    @Test fun wpmNullWithNoSessions() {
        assertNull(BookMath.wordsPerMinute(emptyList(), 280f))
    }

    @Test fun wpmNullWithOneSession() {
        assertNull(BookMath.wordsPerMinute(listOf(SessionSpan(30, hour)), 280f))
    }

    @Test fun wpmNullWhenAZeroDurationSpanLeavesOnlyOne() {
        assertNull(BookMath.wordsPerMinute(listOf(SessionSpan(30, hour), SessionSpan(10, 0)), 280f))
    }

    @Test fun wpmNullWhenNothingWasRead() {
        assertNull(BookMath.wordsPerMinute(listOf(SessionSpan(0, hour), SessionSpan(0, hour)), 280f))
    }

    @Test fun wpmNullWithoutAWordsPerUnit() {
        val spans = listOf(SessionSpan(30, hour), SessionSpan(30, hour))
        assertNull(BookMath.wordsPerMinute(spans, 0f))
        assertNull(BookMath.wordsPerMinute(spans, -280f))
    }

    @Test fun wpmDropsBackwardsSpans() {
        // R5's deltas are signed; reading backwards is not reading, so that span
        // is dropped and the one that remains is too few to call a pace.
        assertNull(BookMath.wordsPerMinute(listOf(SessionSpan(30, hour), SessionSpan(-10, hour)), 280f))
    }

    @Test fun wpmEstimatedFromPages() {
        // 30 pages in 1h + 30 in 3h = 60 pages / 4h = 15 pages/h;
        // × 280 words = 4 200 words/h = 70 words/min.
        val wpm = BookMath.wordsPerMinute(listOf(SessionSpan(30, hour), SessionSpan(30, 3 * hour)), 280f)!!
        assertEquals(70f, wpm, 0.001f)
    }

    @Test fun wpmFromARealEpubWordCount() {
        // 84 000 words over 100 percentage points = 840 words each; 20 points in
        // 2h = 16 800 words / 120 min = 140 words/min.
        val perUnit = BookMath.wordsPerUnit(book(fileKind = "epub", wordCount = 84_000))!!
        val wpm = BookMath.wordsPerMinute(listOf(SessionSpan(12, hour), SessionSpan(8, hour)), perUnit)!!
        assertEquals(140f, wpm, 0.001f)
    }

    @Test fun wpmAgreesWithPagesPerHourOnTheSameSpans() {
        // The two figures are the same rate in different clothes; they must never
        // disagree about the same sessions.
        val spans = listOf(SessionSpan(10, hour / 2), SessionSpan(25, hour * 3 / 2))
        val pace = BookMath.pagesPerHour(spans)!!
        val wpm = BookMath.wordsPerMinute(spans, BookMath.WORDS_PER_PAGE.toFloat())!!
        assertEquals(pace * BookMath.WORDS_PER_PAGE / 60f, wpm, 0.001f)
    }
}
