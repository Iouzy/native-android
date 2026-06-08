package com.pauta.app.domain

import com.pauta.app.data.entity.HabitLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsTest {

    @Test
    fun navigatorLevel_thresholds() {
        assertEquals("Aprendiz", Insights.navigatorLevel(0).name)
        assertEquals("Aprendiz", Insights.navigatorLevel(29).name)
        assertEquals("Grumete", Insights.navigatorLevel(30).name)
        assertEquals("Marujo", Insights.navigatorLevel(100).name)
        assertEquals("Timoneiro", Insights.navigatorLevel(300).name)
        assertEquals("Almirante", Insights.navigatorLevel(5000).name)
        assertEquals("Almirante", Insights.navigatorLevel(99_999).name)
    }

    @Test
    fun totalDoneDays_countsLogEntries() {
        val logs = listOf(
            HabitLogEntity("h1", "2025-05-01"),
            HabitLogEntity("h1", "2025-05-02"),
            HabitLogEntity("h2", "2025-05-01"),
        )
        assertEquals(3, Insights.totalDoneDays(logs))
        assertEquals("Aprendiz", Insights.navigatorLevel(Insights.totalDoneDays(logs)).name)
    }
}
