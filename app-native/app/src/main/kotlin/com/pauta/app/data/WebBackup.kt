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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
