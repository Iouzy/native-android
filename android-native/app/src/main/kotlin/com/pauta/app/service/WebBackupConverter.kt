package com.pauta.app.service

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Converts a backup exported by the WEB/Capacitor build (the store.jsx shape,
 * localStorage key "pauta.v4") into the native [BackupData] snapshot, so a user
 * coming from the web app can bring their data into the Room database.
 *
 * Pure + unit-tested. Mapping highlights (web → native):
 *  - weekday schedule is re-based: web `0=Sun..6=Sat` → native `0=Mon..6=Sun`
 *  - habit `anchor`: web `null` (manual) → native `-1`
 *  - intention `when` (manha/tarde/noite) → `timeOfDay` (morning/afternoon/night)
 *  - intention `priority`: numeric, with legacy strings (principal/importante) mapped
 *  - block sessions get synthetic ids; today's reflection becomes a [DayEntity]
 *
 * Scope: covers everything [BackupData] holds (days, intentions, focus blocks +
 * sessions, habits + logs/respiros/counts, prefs). Goals, routines and week-plans
 * are NOT in [BackupData] yet, so they are not migrated here — see the PR notes.
 */
object WebBackupConverter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var seq = 0
    private fun uid(prefix: String) = prefix + System.currentTimeMillis() + (seq++)

    fun convert(text: String): BackupData {
        val root = json.parseToJsonElement(text).jsonObject
        // Unwrap { app, version, data } if present; otherwise root *is* the state.
        val state = (root["data"] as? JsonObject) ?: root
        return toBackupData(json.decodeFromJsonElement(WebState.serializer(), state))
    }

    private fun toBackupData(s: WebState): BackupData {
        val days = LinkedHashMap<String, DayEntity>()
        val intentions = mutableListOf<IntentionEntity>()

        fun addDay(day: WebDay?, keyOverride: String? = null) {
            if (day == null) return
            val dk = keyOverride ?: day.dayKey ?: return
            days[dk] = DayEntity(dayKey = dk, reflection = day.reflection)
            day.intentions.forEach { intentions += it.toEntity(dk) }
        }
        addDay(s.today)
        s.days.forEach { (k, d) -> addDay(d, k) }

        val blocks = mutableListOf<FocusBlockEntity>()
        val sessions = mutableListOf<FocusSessionEntity>()
        s.blocks.forEach { b ->
            val id = b.id ?: uid("b_")
            blocks += FocusBlockEntity(
                id = id,
                title = b.title,
                linkedIntentionId = b.linkedToId,
                project = b.project,
                status = if (b.status in BLOCK_STATUSES) b.status else "done",
                reflection = b.reflection,
                targetMs = b.targetMs ?: 0L,
                createdAt = b.createdAt,
            )
            b.sessions.forEachIndexed { i, seg ->
                sessions += FocusSessionEntity(
                    id = "${id}_s$i",
                    blockId = id,
                    startedAt = seg.startedAt,
                    endedAt = seg.endedAt,
                    note = seg.note,
                    sessionIndex = i,
                )
            }
        }

        val habits = mutableListOf<HabitEntity>()
        val logs = mutableListOf<HabitLogEntity>()
        val respiros = mutableListOf<HabitRespiroEntity>()
        val counts = mutableListOf<HabitCountEntity>()
        s.habits.forEachIndexed { idx, h ->
            val id = h.id ?: uid("h_")
            // Re-base the weekday schedule: web getDay() (0=Sun..6=Sat) → native (0=Mon..6=Sun).
            val nativeWeekdays = h.weekdays.map { (it + 6) % 7 }.toSortedSet()
            habits += HabitEntity(
                id = id,
                name = h.name,
                description = h.description,
                cadence = if (h.cadence in CADENCES) h.cadence else "daily",
                anchor = h.anchor ?: -1,
                weekdays = nativeWeekdays.joinToString(separator = ",", prefix = "[", postfix = "]"),
                target = h.target ?: 0,
                unit = h.unit,
                clock = h.clock,
                color = h.color ?: "",
                recurrence = if (h.recurrence in RECURRENCES) h.recurrence else "forever",
                endsAt = h.endsAt,
                createdAt = h.createdAt,
                sortOrder = idx,
            )
            h.log.keys.forEach { dk -> logs += HabitLogEntity(habitId = id, dayKey = dk) }
            h.respiros.forEach { (dk, r) -> respiros += HabitRespiroEntity(habitId = id, dayKey = dk, reason = r.reason, at = r.at) }
            h.counts.forEach { (dk, c) -> if (c > 0) counts += HabitCountEntity(habitId = id, dayKey = dk, count = c) }
        }

        return BackupData(
            days = days.values.toList(),
            intentions = intentions,
            blocks = blocks,
            sessions = sessions,
            habits = habits,
            logs = logs,
            respiros = respiros,
            counts = counts,
            prefs = s.prefs.toEntity(),
        )
    }

    private fun WebIntention.toEntity(dayKey: String) = IntentionEntity(
        id = id ?: uid("i_"),
        dayKey = dayKey,
        text = text,
        done = done,
        priority = coercePriority(priority),
        targetMin = targetMin?.takeIf { it > 0 },
        timeOfDay = mapWhen(`when`),
        linkedBlockId = null,
        createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
    )

    private fun WebPrefs?.toEntity(): PrefsEntity {
        val p = this ?: WebPrefs()
        return PrefsEntity(
            id = "prefs",
            lang = p.lang,
            theme = p.theme,
            accent = p.accent ?: "",
            haptics = p.haptics,
            sound = p.sound,
            keepAwake = p.keepAwake,
            immersive = p.immersive,
            textScale = p.textScale.toFloat(),
            reducedMotion = p.reducedMotion,
            highContrast = p.highContrast,
            parrot = p.parrot,
            onboardingSeen = p.onboardingSeen,
            autoBackup = p.autoBackup,
            remindersEnabled = p.reminders.enabled,
            remindersPlannerTime = p.reminders.plannerTime,
            remindersHabitsTime = p.reminders.habitsTime,
            remindersReflectionTime = p.reminders.reflectionTime,
        )
    }

    // Numeric priority (1=high..3=low), with legacy string values mapped (matches
    // sanitizeIntention in store.jsx). Native default is 2 (normal).
    private fun coercePriority(e: JsonElement?): Int {
        val prim = e as? JsonPrimitive ?: return 2
        prim.intOrNull?.let { if (it in 1..3) return it }
        return when (prim.contentOrNull) {
            "principal" -> 1
            "importante" -> 2
            else -> 2
        }
    }

    private fun mapWhen(w: String?): String? = when (w) {
        "manha" -> "morning"
        "tarde" -> "afternoon"
        "noite" -> "night"
        else -> null
    }

    private val BLOCK_STATUSES = setOf("active", "paused", "done")
    private val CADENCES = setOf("daily", "weekly", "monthly")
    private val RECURRENCES = setOf("forever", "period", "month")
}

// ── Web (store.jsx) backup shapes — only the fields the native BackupData needs. ──
// Unknown keys (activeId, goals, routines, plans, …) are ignored by the parser.

@Serializable
private data class WebState(
    val today: WebDay? = null,
    val days: Map<String, WebDay> = emptyMap(),
    val blocks: List<WebBlock> = emptyList(),
    val habits: List<WebHabit> = emptyList(),
    val prefs: WebPrefs? = null,
)

@Serializable
private data class WebDay(
    val dayKey: String? = null,
    val intentions: List<WebIntention> = emptyList(),
    val reflection: String = "",
)

@Serializable
private data class WebIntention(
    val id: String? = null,
    val text: String = "",
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val priority: JsonElement? = null,
    val targetMin: Int? = null,
    val `when`: String? = null,
)

@Serializable
private data class WebSession(
    val startedAt: Long = 0L,
    val endedAt: Long? = null,
    val note: String = "",
)

@Serializable
private data class WebBlock(
    val id: String? = null,
    val title: String = "",
    val linkedToId: String? = null,
    val project: String? = null,
    val targetMs: Long? = null,
    val sessions: List<WebSession> = emptyList(),
    val status: String = "done",
    val reflection: String = "",
    val createdAt: Long = 0L,
)

@Serializable
private data class WebRespiro(val reason: String = "", val at: Long = 0L)

@Serializable
private data class WebHabit(
    val id: String? = null,
    val name: String = "",
    val time: String = "",
    val description: String = "",
    val recurrence: String = "forever",
    val endsAt: Long? = null,
    val log: Map<String, Int> = emptyMap(),
    val respiros: Map<String, WebRespiro> = emptyMap(),
    val counts: Map<String, Int> = emptyMap(),
    val target: Int? = null,
    val unit: String = "",
    val clock: String = "",
    val cadence: String = "daily",
    val anchor: Int? = null,
    val weekdays: List<Int> = emptyList(),
    val color: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
private data class WebReminders(
    val enabled: Boolean = false,
    val plannerTime: String = "08:00",
    val habitsTime: String = "09:00",
    val reflectionTime: String = "21:30",
)

@Serializable
private data class WebPrefs(
    val lang: String = "pt",
    val theme: String = "auto",
    val accent: String? = null,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val keepAwake: Boolean = true,
    val immersive: Boolean = false,
    val textScale: Double = 1.0,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val parrot: Boolean = true,
    val onboardingSeen: Boolean = false,
    val autoBackup: String = "off",
    val reminders: WebReminders = WebReminders(),
)
