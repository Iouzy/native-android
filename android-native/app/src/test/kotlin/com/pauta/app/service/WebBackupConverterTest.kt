package com.pauta.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the web (store.jsx) → native [BackupData] migration converter. */
class WebBackupConverterTest {

    private val webJson = """
        {"app":"pauta","version":4,"exportedAt":1,"data":{
          "today":{"dayKey":"2025-05-20","reflection":"bom dia","intentions":[
             {"id":"i1","text":"Estudar","done":true,"createdAt":100,"priority":1,"when":"manha"}
          ]},
          "days":{"2025-05-19":{"reflection":"ontem","intentions":[
             {"id":"i2","text":"Ler","done":false,"createdAt":90}
          ]}},
          "blocks":[
             {"id":"b1","title":"Foco","linkedToId":"i1","project":"Escola","targetMs":1500000,
              "status":"done","reflection":"ok","createdAt":50,
              "sessions":[{"startedAt":50,"endedAt":1550,"note":"n1"},{"startedAt":2000,"endedAt":2500,"note":""}]}
          ],
          "habits":[
             {"id":"h1","name":"Água","cadence":"weekly","anchor":null,"weekdays":[0,1],"target":0,"createdAt":10,
              "log":{"2025-05-14":1},"respiros":{"2025-05-07":{"reason":"viagem","at":5}},"counts":{}}
          ],
          "goals":[{"id":"g1","text":"ignored","quarter":"2025-Q2"}],
          "prefs":{"lang":"en","theme":"dark","accent":"#B8533A","textScale":1.15,
                   "reminders":{"enabled":true,"habitsTime":"09:30"}}
        }}
    """.trimIndent()

    @Test
    fun convertsWebBackupToNativeSnapshot() {
        val data = WebBackupConverter.convert(webJson)

        // Days: today + one archived day, with reflections carried over.
        assertEquals(2, data.days.size)
        assertEquals("bom dia", data.days.first { it.dayKey == "2025-05-20" }.reflection)

        // Intentions land on the right day, with priority + time-of-day mapped.
        assertEquals(2, data.intentions.size)
        val i1 = data.intentions.first { it.id == "i1" }
        assertEquals("2025-05-20", i1.dayKey)
        assertTrue(i1.done)
        assertEquals(1, i1.priority)
        assertEquals("morning", i1.timeOfDay)

        // Block + its sessions (synthetic ids), link + target preserved.
        assertEquals(1, data.blocks.size)
        assertEquals("i1", data.blocks[0].linkedIntentionId)
        assertEquals(1_500_000L, data.blocks[0].targetMs)
        assertEquals(2, data.sessions.size)
        assertEquals("b1_s0", data.sessions[0].id)
        assertEquals("b1", data.sessions[0].blockId)

        // Habit: anchor null → -1; weekday schedule re-based Sun/Mon → [Mon(0), Sun(6)].
        assertEquals(1, data.habits.size)
        val h = data.habits[0]
        assertEquals("weekly", h.cadence)
        assertEquals(-1, h.anchor)
        assertEquals("[0,6]", h.weekdays)
        assertEquals(1, data.logs.size)
        assertEquals("2025-05-14", data.logs[0].dayKey)
        assertEquals(1, data.respiros.size)
        assertEquals("viagem", data.respiros[0].reason)

        // Prefs carried; goals are intentionally not migrated (not in BackupData).
        assertEquals("en", data.prefs.lang)
        assertEquals("dark", data.prefs.theme)
        assertEquals("#B8533A", data.prefs.accent)
        assertEquals(1.15f, data.prefs.textScale, 0.0001f)
        assertTrue(data.prefs.remindersEnabled)
        assertEquals("09:30", data.prefs.remindersHabitsTime)
    }

    @Test
    fun handlesRawStateAndLegacyStringPriority() {
        // Unwrapped state (no { app, data } envelope) + legacy string priority.
        val raw = """
            {"today":{"dayKey":"2025-05-20","reflection":"",
              "intentions":[{"id":"x","text":"t","priority":"principal","createdAt":1}]},
             "blocks":[],"habits":[]}
        """.trimIndent()
        val data = WebBackupConverter.convert(raw)
        assertEquals(1, data.intentions.size)
        assertEquals(1, data.intentions[0].priority) // "principal" → 1
        assertNull(data.intentions[0].timeOfDay)
    }
}
