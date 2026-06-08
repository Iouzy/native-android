package com.pauta.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities mirroring the web app's `pauta.v4` localStorage schema 1:1.
 *
 * Design rules so backups round-trip losslessly:
 * - **Day keys** are `YYYY-MM-DD` strings in LOCAL time (never UTC), exactly as
 *   the web computes them.
 * - **Timestamps** are milliseconds since the Unix epoch (JS `Date.getTime()`).
 * - **Ordered collections** (intentions, habits, goals, milestones, sessions,
 *   routine items, planned intentions) carry an explicit [position] because the
 *   web stores them as ordered arrays.
 * - **Sparse maps** in the web (`habit.log`, `respiros`, `counts`) become their
 *   own rows — a row exists only when the web map had that key.
 * - **Weekdays** keep the web's JS convention (0=Sun … 6=Sat). Monday-first
 *   display re-basing happens in the domain layer, not in storage.
 * - IDs reuse the web's string ids (`i_…`, `b_…`, `h_…`, `g_…`, `m_…`, `r_…`)
 *   so references survive import.
 *
 * // PT: entidades Room que espelham o schema `pauta.v4` da app web, para os
 * backups irem e voltarem sem perdas.
 */

/** today / days[dayKey] — a day's nightly reflection. Intentions live in their
 *  own table keyed by [IntentionEntity.dayKey]. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val dayKey: String,
    val reflection: String = "",
)

/** today.intentions[] and days[dayKey].intentions[] — the daily plan items. */
@Entity(tableName = "intentions")
data class IntentionEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val text: String,
    val done: Boolean = false,
    val priority: Int? = null,          // 1 (highest) … 3 (lowest); null = unset
    val targetMin: Int? = null,         // planned focus minutes
    val timeOfDay: String? = null,      // web `when`: "manha" | "tarde" | "noite"
    val createdAt: Long,
    val position: Int = 0,
)

/** blocks[] — focus blocks (Pauta tab). Sessions are a separate table. */
@Entity(tableName = "focus_blocks")
data class FocusBlockEntity(
    @PrimaryKey val id: String,
    val title: String,
    val linkedToId: String? = null,     // an intention id (may dangle, as on web)
    val project: String? = null,
    val targetMs: Long? = null,         // soft Pomodoro target
    val status: String,                 // "active" | "paused" | "done"
    val reflection: String = "",
    val createdAt: Long,
)

/** blocks[].sessions[] — one start/stop span; pause/resume creates a new row. */
@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val blockId: String,
    val startedAt: Long,
    val endedAt: Long? = null,          // null while running
    val note: String = "",
    val position: Int = 0,
)

/** habits[] — Marés (tides). log/respiros/counts are separate tables. */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val time: String = "",              // free-text time preference
    val description: String = "",
    val createdAt: Long,
    val recurrence: String = "forever", // "forever" | "period" | "month"
    val endsAt: Long? = null,
    val cadence: String = "daily",      // "daily" | "weekly" | "monthly"
    val anchor: Int? = null,            // weekday 0-6 (weekly) or day 1-31 (monthly); null = manual
    val weekdays: List<Int> = emptyList(), // daily schedule, JS 0=Sun..6=Sat; empty = every day
    val target: Int? = null,            // countable habits: daily target (>1); null = binary
    val unit: String = "",              // unit label for countables
    val clock: String = "",             // HH:MM reminder clock
    val color: String? = null,          // hex; null = follow app accent
    val position: Int = 0,
)

/** habit.log{dayKey:1} — presence marks the period/day done. */
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "dayKey"])
data class HabitLogEntity(
    val habitId: String,
    val dayKey: String,
)

/** habit.respiros{dayKey:{reason,at}} — an honest rest; counts toward the
 *  streak but not toward completion %. */
@Entity(tableName = "habit_respiros", primaryKeys = ["habitId", "dayKey"])
data class HabitRespiroEntity(
    val habitId: String,
    val dayKey: String,
    val reason: String = "",
    val at: Long = 0,
)

/** habit.counts{dayKey:n} — daily tally for countable habits. */
@Entity(tableName = "habit_counts", primaryKeys = ["habitId", "dayKey"])
data class HabitCountEntity(
    val habitId: String,
    val dayKey: String,
    val count: Int = 0,
)

/** goals[] — quarterly goals. Milestones are a separate table. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val text: String,
    val done: Boolean = false,
    val quarter: String,                // "YYYY-Qn"
    val habitId: String? = null,        // optional linked habit
    val createdAt: Long,
    val position: Int = 0,
)

/** goals[].milestones[] — sub-steps under a goal. */
@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val text: String,
    val done: Boolean = false,
    val position: Int = 0,
)

/** routines[] — reusable intention templates. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int = 0,
)

/** routines[].items[] — the templated intentions inside a routine. */
@Entity(tableName = "routine_items")
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val routineId: String,
    val text: String,
    val priority: Int? = null,
    val targetMin: Int? = null,
    val position: Int = 0,
)

/** plans{dayKey:{intentions[]}} — week-ahead planned intentions for future days. */
@Entity(tableName = "planned_intentions")
data class PlannedIntentionEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val text: String,
    val priority: Int? = null,
    val targetMin: Int? = null,
    val createdAt: Long,
    val position: Int = 0,
)

/** prefs{} — the single preferences row (id is always "prefs"). Mirrors every
 *  web preference, with the reminders sub-object flattened. */
@Entity(tableName = "prefs")
data class PrefsEntity(
    @PrimaryKey val id: String = "prefs",
    val lang: String = "pt",            // "pt" | "en"
    val theme: String = "auto",         // "auto" | "light" | "dark"
    val accent: String? = null,         // hex; null = build default
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val textScale: Float = 1f,          // 1 | 1.15 | 1.3
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val keepAwake: Boolean = true,
    val immersive: Boolean = false,
    val autoBackup: String = "off",     // "off" | "30m" | "hourly" | "daily" | "weekly"
    val parrot: Boolean = true,
    val onboardingSeen: Boolean = false,
    val remindersEnabled: Boolean = false,
    val plannerTime: String = "08:00",
    val habitsTime: String = "09:00",
    val reflectionTime: String = "21:30",
)
