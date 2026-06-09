package com.pauta.app.data

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.GoalEntity
import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.MilestoneEntity
import com.pauta.app.data.entity.PlannedIntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.data.entity.RoutineEntity
import com.pauta.app.data.entity.RoutineItemEntity
import com.pauta.app.domain.DateUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

/**
 * Converts between the Room data and the web app's `pauta.v4` backup JSON, so
 * exports round-trip losslessly across web ⇄ native. This file is the EXPORT
 * side (Room snapshot → v4 JSON); the import side parses the same shape back.
 *
 * The v4 envelope is `{ app:"pauta", version:4, exportedAt, data:{ today, days,
 * blocks, habits, goals, routines, plans, prefs, activeId } }`. Internal storage
 * already mirrors the web's conventions (local YYYY-MM-DD keys, ms-epoch times,
 * JS-weekday numbers, sparse log/respiro/count maps), so the mapping is direct.
 * // PT: converte os dados Room para o JSON `pauta.v4` da web, sem perdas.
 */
object WebBackup {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Everything needed to render a backup (gathered from the DAOs). */
    data class Snapshot(
        val todayKey: String,
        val days: List<DayEntity>,
        val intentions: List<IntentionEntity>,
        val blocks: List<FocusBlockEntity>,
        val sessions: List<FocusSessionEntity>,
        val habits: List<HabitEntity>,
        val logs: List<HabitLogEntity>,
        val respiros: List<HabitRespiroEntity>,
        val counts: List<HabitCountEntity>,
        val goals: List<GoalEntity>,
        val milestones: List<MilestoneEntity>,
        val routines: List<RoutineEntity>,
        val routineItems: List<RoutineItemEntity>,
        val plans: List<PlannedIntentionEntity>,
        val prefs: PrefsEntity,
    )

    fun export(s: Snapshot): String {
        val intentionsByDay = s.intentions.groupBy { it.dayKey }
        val reflectionByDay = s.days.associate { it.dayKey to it.reflection }
        val sessionsByBlock = s.sessions.groupBy { it.blockId }
        val logsByHabit = s.logs.groupBy { it.habitId }
        val respByHabit = s.respiros.groupBy { it.habitId }
        val countsByHabit = s.counts.groupBy { it.habitId }
        val milestonesByGoal = s.milestones.groupBy { it.goalId }
        val itemsByRoutine = s.routineItems.groupBy { it.routineId }
        val plansByDay = s.plans.groupBy { it.dayKey }

        fun intentionObj(i: IntentionEntity) = buildJsonObject {
            put("id", i.id); put("text", i.text); put("done", i.done); put("createdAt", i.createdAt)
            i.priority?.let { put("priority", it) }
            i.targetMin?.let { put("targetMin", it) }
            i.timeOfDay?.let { put("when", it) }
        }

        val dayKeys = (intentionsByDay.keys + reflectionByDay.keys)

        val data = buildJsonObject {
            putJsonObject("today") {
                put("dayKey", s.todayKey)
                putJsonArray("intentions") {
                    intentionsByDay[s.todayKey].orEmpty().sortedBy { it.position }.forEach { add(intentionObj(it)) }
                }
                put("reflection", reflectionByDay[s.todayKey] ?: "")
            }
            putJsonObject("days") {
                dayKeys.filter { it != s.todayKey }.forEach { key ->
                    putJsonObject(key) {
                        putJsonArray("intentions") {
                            intentionsByDay[key].orEmpty().sortedBy { it.position }.forEach { add(intentionObj(it)) }
                        }
                        put("reflection", reflectionByDay[key] ?: "")
                    }
                }
            }
            putJsonArray("blocks") {
                s.blocks.forEach { b ->
                    addJsonObject {
                        put("id", b.id); put("title", b.title)
                        b.linkedToId?.let { put("linkedToId", it) }
                        b.project?.let { put("project", it) }
                        b.targetMs?.let { put("targetMs", it) }
                        put("status", b.status); put("reflection", b.reflection); put("createdAt", b.createdAt)
                        putJsonArray("sessions") {
                            sessionsByBlock[b.id].orEmpty().sortedBy { it.position }.forEach { sg ->
                                addJsonObject {
                                    put("startedAt", sg.startedAt)
                                    if (sg.endedAt != null) put("endedAt", sg.endedAt) else put("endedAt", null as String?)
                                    put("note", sg.note)
                                }
                            }
                        }
                    }
                }
            }
            putJsonArray("habits") {
                s.habits.forEach { h ->
                    addJsonObject {
                        put("id", h.id); put("name", h.name); put("time", h.time); put("description", h.description)
                        put("recurrence", h.recurrence)
                        h.endsAt?.let { put("endsAt", it) }
                        put("cadence", h.cadence)
                        h.anchor?.let { put("anchor", it) }
                        putJsonArray("weekdays") { h.weekdays.forEach { add(it) } }
                        h.target?.let { put("target", it) }
                        put("unit", h.unit); put("clock", h.clock)
                        h.color?.let { put("color", it) }
                        put("createdAt", h.createdAt)
                        putJsonObject("log") { logsByHabit[h.id].orEmpty().forEach { put(it.dayKey, 1) } }
                        putJsonObject("respiros") {
                            respByHabit[h.id].orEmpty().forEach { r ->
                                putJsonObject(r.dayKey) { put("reason", r.reason); put("at", r.at) }
                            }
                        }
                        putJsonObject("counts") { countsByHabit[h.id].orEmpty().forEach { put(it.dayKey, it.count) } }
                    }
                }
            }
            putJsonArray("goals") {
                s.goals.forEach { g ->
                    addJsonObject {
                        put("id", g.id); put("text", g.text); put("done", g.done); put("quarter", g.quarter)
                        g.habitId?.let { put("habitId", it) }
                        put("createdAt", g.createdAt)
                        putJsonArray("milestones") {
                            milestonesByGoal[g.id].orEmpty().sortedBy { it.position }.forEach { m ->
                                addJsonObject { put("id", m.id); put("text", m.text); put("done", m.done) }
                            }
                        }
                    }
                }
            }
            putJsonArray("routines") {
                s.routines.forEach { r ->
                    addJsonObject {
                        put("id", r.id); put("name", r.name)
                        putJsonArray("items") {
                            itemsByRoutine[r.id].orEmpty().sortedBy { it.position }.forEach { it2 ->
                                addJsonObject {
                                    put("text", it2.text)
                                    it2.priority?.let { put("priority", it) }
                                    it2.targetMin?.let { put("targetMin", it) }
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("plans") {
                plansByDay.forEach { (dayKey, items) ->
                    putJsonObject(dayKey) {
                        putJsonArray("intentions") {
                            items.sortedBy { it.position }.forEach { p ->
                                addJsonObject {
                                    put("id", p.id); put("text", p.text); put("done", false); put("createdAt", p.createdAt)
                                    p.priority?.let { put("priority", it) }
                                    p.targetMin?.let { put("targetMin", it) }
                                }
                            }
                        }
                    }
                }
            }
            put("prefs", prefsObj(s.prefs))
            put("activeId", s.blocks.firstOrNull { it.status == "active" }?.id?.let {
                kotlinx.serialization.json.JsonPrimitive(it)
            } ?: kotlinx.serialization.json.JsonNull)
        }

        val root = buildJsonObject {
            put("app", "pauta")
            put("version", 4)
            put("exportedAt", Instant.now().toString())
            put("data", data)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    // ── import (web JSON → Room snapshot) ─────────────────────
    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement?.str(): String? = prim()?.contentOrNull
    private fun JsonElement?.long(): Long? = prim()?.longOrNull ?: prim()?.contentOrNull?.toLongOrNull()
    private fun JsonElement?.int(): Int? = prim()?.intOrNull ?: prim()?.contentOrNull?.toIntOrNull()
    private fun JsonElement?.bool(): Boolean? = prim()?.booleanOrNull
    private fun JsonElement?.float(): Float? = prim()?.floatOrNull
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.array(): JsonArray? = this as? JsonArray

    private val LEGACY_PRIORITY = mapOf("principal" to 1, "importante" to 2)
    private fun parsePriority(el: JsonElement?): Int? {
        val n = el.int(); if (n == 1 || n == 2 || n == 3) return n
        return LEGACY_PRIORITY[el.str()]
    }
    private fun normalizeWhen(s: String?): String? = if (s in setOf("manha", "tarde", "noite")) s else null

    /**
     * Parse a `pauta.v4` backup (envelope or raw state) into a [Snapshot].
     * Handles the web's shapes: today + days object maps, legacy string
     * priorities, the `when` bucket, sparse habit-mark maps, and auto-pausing any
     * block left "active" (so an import never resurrects a phantom timer).
     */
    fun import(text: String): Snapshot {
        val root = json.parseToJsonElement(text).obj() ?: throw IllegalArgumentException("not a JSON object")
        val data = root["data"].obj() ?: root

        val days = mutableListOf<DayEntity>()
        val intentions = mutableListOf<IntentionEntity>()
        fun parseIntentions(el: JsonElement?, dayKey: String) {
            el.array()?.forEachIndexed { idx, e ->
                val i = e.obj() ?: return@forEachIndexed
                val id = i["id"].str() ?: return@forEachIndexed
                intentions.add(
                    IntentionEntity(
                        id = id, dayKey = dayKey, text = i["text"].str() ?: "",
                        done = i["done"].bool() ?: false, priority = parsePriority(i["priority"]),
                        targetMin = i["targetMin"].int(), timeOfDay = normalizeWhen(i["when"].str()),
                        createdAt = i["createdAt"].long() ?: 0L, position = idx,
                    ),
                )
            }
        }

        val today = data["today"].obj()
        val todayKey = today?.get("dayKey").str() ?: DateUtils.todayKey()
        if (today != null) {
            days.add(DayEntity(todayKey, today["reflection"].str() ?: ""))
            parseIntentions(today["intentions"], todayKey)
        }
        data["days"].obj()?.forEach { (key, v) ->
            val d = v.obj() ?: return@forEach
            days.add(DayEntity(key, d["reflection"].str() ?: ""))
            parseIntentions(d["intentions"], key)
        }

        val blocks = mutableListOf<FocusBlockEntity>()
        val sessions = mutableListOf<FocusSessionEntity>()
        data["blocks"].array()?.forEach { be ->
            val b = be.obj() ?: return@forEach
            val id = b["id"].str() ?: return@forEach
            var status = b["status"].str() ?: "done"
            val wasActive = status == "active"
            if (wasActive) status = "paused" // auto-pause on import
            blocks.add(
                FocusBlockEntity(
                    id = id, title = b["title"].str() ?: "", linkedToId = b["linkedToId"].str(),
                    project = b["project"].str(), targetMs = b["targetMs"].long(), status = status,
                    reflection = b["reflection"].str() ?: "", createdAt = b["createdAt"].long() ?: 0L,
                ),
            )
            b["sessions"].array()?.forEachIndexed { idx, se ->
                val s = se.obj() ?: return@forEachIndexed
                var ended = s["endedAt"].long()
                if (wasActive && ended == null) ended = System.currentTimeMillis() // close the running span
                sessions.add(
                    FocusSessionEntity(
                        blockId = id, startedAt = s["startedAt"].long() ?: 0L, endedAt = ended,
                        note = s["note"].str() ?: "", position = idx,
                    ),
                )
            }
        }

        val habits = mutableListOf<HabitEntity>()
        val logs = mutableListOf<HabitLogEntity>()
        val respiros = mutableListOf<HabitRespiroEntity>()
        val counts = mutableListOf<HabitCountEntity>()
        data["habits"].array()?.forEachIndexed { idx, he ->
            val h = he.obj() ?: return@forEachIndexed
            val id = h["id"].str() ?: return@forEachIndexed
            val weekdays = h["weekdays"].array()?.mapNotNull { it.int() } ?: emptyList()
            habits.add(
                HabitEntity(
                    id = id, name = h["name"].str() ?: "", time = h["time"].str() ?: "",
                    description = h["description"].str() ?: "", createdAt = h["createdAt"].long() ?: 0L,
                    recurrence = h["recurrence"].str() ?: "forever", endsAt = h["endsAt"].long(),
                    cadence = h["cadence"].str() ?: "daily", anchor = h["anchor"].int(), weekdays = weekdays,
                    target = h["target"].int(), unit = h["unit"].str() ?: "", clock = h["clock"].str() ?: "",
                    color = h["color"].str(), position = idx,
                ),
            )
            h["log"].obj()?.keys?.forEach { dk -> logs.add(HabitLogEntity(id, dk)) }
            h["respiros"].obj()?.forEach { (dk, v) ->
                val r = v.obj()
                respiros.add(HabitRespiroEntity(id, dk, r?.get("reason").str() ?: "", r?.get("at").long() ?: 0L))
            }
            h["counts"].obj()?.forEach { (dk, v) -> counts.add(HabitCountEntity(id, dk, v.int() ?: 0)) }
        }

        val goals = mutableListOf<GoalEntity>()
        val milestones = mutableListOf<MilestoneEntity>()
        data["goals"].array()?.forEachIndexed { gi, ge ->
            val g = ge.obj() ?: return@forEachIndexed
            val id = g["id"].str() ?: return@forEachIndexed
            goals.add(
                GoalEntity(
                    id = id, text = g["text"].str() ?: "", done = g["done"].bool() ?: false,
                    quarter = g["quarter"].str() ?: "", habitId = g["habitId"].str(),
                    createdAt = g["createdAt"].long() ?: 0L, position = gi,
                ),
            )
            g["milestones"].array()?.forEachIndexed { mi, me ->
                val m = me.obj() ?: return@forEachIndexed
                val mid = m["id"].str() ?: return@forEachIndexed
                milestones.add(MilestoneEntity(mid, id, m["text"].str() ?: "", m["done"].bool() ?: false, mi))
            }
        }

        val routines = mutableListOf<RoutineEntity>()
        val routineItems = mutableListOf<RoutineItemEntity>()
        data["routines"].array()?.forEachIndexed { ri, re ->
            val r = re.obj() ?: return@forEachIndexed
            val id = r["id"].str() ?: return@forEachIndexed
            routines.add(RoutineEntity(id, r["name"].str() ?: "", ri))
            r["items"].array()?.forEachIndexed { ii, ie ->
                val it = ie.obj() ?: return@forEachIndexed
                routineItems.add(
                    RoutineItemEntity(
                        routineId = id, text = it["text"].str() ?: "",
                        priority = parsePriority(it["priority"]), targetMin = it["targetMin"].int(), position = ii,
                    ),
                )
            }
        }

        val plans = mutableListOf<PlannedIntentionEntity>()
        data["plans"].obj()?.forEach { (dayKey, v) ->
            v.obj()?.get("intentions").array()?.forEachIndexed { idx, e ->
                val p = e.obj() ?: return@forEachIndexed
                val id = p["id"].str() ?: return@forEachIndexed
                plans.add(
                    PlannedIntentionEntity(
                        id = id, dayKey = dayKey, text = p["text"].str() ?: "",
                        priority = parsePriority(p["priority"]), targetMin = p["targetMin"].int(),
                        createdAt = p["createdAt"].long() ?: 0L, position = idx,
                    ),
                )
            }
        }

        return Snapshot(
            todayKey = todayKey, days = days, intentions = intentions, blocks = blocks, sessions = sessions,
            habits = habits, logs = logs, respiros = respiros, counts = counts, goals = goals,
            milestones = milestones, routines = routines, routineItems = routineItems, plans = plans,
            prefs = parsePrefs(data["prefs"].obj()),
        )
    }

    private fun parsePrefs(o: JsonObject?): PrefsEntity {
        if (o == null) return PrefsEntity()
        val rem = o["reminders"].obj()
        val def = PrefsEntity()
        return PrefsEntity(
            lang = o["lang"].str() ?: def.lang,
            theme = o["theme"].str() ?: def.theme,
            accent = o["accent"].str(),
            reducedMotion = o["reducedMotion"].bool() ?: def.reducedMotion,
            highContrast = o["highContrast"].bool() ?: def.highContrast,
            textScale = o["textScale"].float() ?: def.textScale,
            haptics = o["haptics"].bool() ?: def.haptics,
            sound = o["sound"].bool() ?: def.sound,
            keepAwake = o["keepAwake"].bool() ?: def.keepAwake,
            immersive = o["immersive"].bool() ?: def.immersive,
            autoBackup = o["autoBackup"].str() ?: def.autoBackup,
            parrot = o["parrot"].bool() ?: def.parrot,
            onboardingSeen = o["onboardingSeen"].bool() ?: def.onboardingSeen,
            remindersEnabled = rem?.get("enabled").bool() ?: def.remindersEnabled,
            plannerTime = rem?.get("plannerTime").str() ?: def.plannerTime,
            habitsTime = rem?.get("habitsTime").str() ?: def.habitsTime,
            reflectionTime = rem?.get("reflectionTime").str() ?: def.reflectionTime,
        )
    }

    private fun prefsObj(p: PrefsEntity): JsonObject = buildJsonObject {
        put("lang", p.lang); put("theme", p.theme)
        p.accent?.let { put("accent", it) }
        put("reducedMotion", p.reducedMotion); put("highContrast", p.highContrast)
        put("textScale", p.textScale); put("haptics", p.haptics); put("sound", p.sound)
        put("keepAwake", p.keepAwake); put("immersive", p.immersive)
        put("autoBackup", p.autoBackup); put("parrot", p.parrot); put("onboardingSeen", p.onboardingSeen)
        putJsonObject("reminders") {
            put("enabled", p.remindersEnabled)
            put("plannerTime", p.plannerTime); put("habitsTime", p.habitsTime); put("reflectionTime", p.reflectionTime)
        }
    }
}
