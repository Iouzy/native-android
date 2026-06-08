package com.pauta.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

// ── Today / archived days ────────────────────────────────────────────────────

@Entity(tableName = "days")
@Serializable
data class DayEntity(
    @PrimaryKey val dayKey: String,  // "YYYY-MM-DD"
    val reflection: String = ""
)

// ── Intentions ───────────────────────────────────────────────────────────────

@Entity(
    tableName = "intentions",
    indices = [Index("dayKey"), Index("linkedBlockId")]
)
@Serializable
data class IntentionEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val text: String,
    val done: Boolean = false,
    val priority: Int = 2,            // 1 (high) | 2 (normal) | 3 (low)
    val targetMin: Int? = null,       // optional planned duration in minutes
    val timeOfDay: String? = null,    // "morning" | "afternoon" | "night"
    val linkedBlockId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ── Focus blocks & sessions ──────────────────────────────────────────────────

@Entity(tableName = "focus_blocks", indices = [Index("status"), Index("linkedIntentionId")])
@Serializable
data class FocusBlockEntity(
    @PrimaryKey val id: String,
    val title: String,
    val linkedIntentionId: String? = null,
    val project: String? = null,
    val status: String = "active",   // "active" | "paused" | "done"
    val reflection: String = "",
    val targetMs: Long = 0L,         // 0 = open-ended Pomodoro
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = FocusBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId")]
)
@Serializable
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val startedAt: Long,
    val endedAt: Long? = null,       // null while session is active
    val note: String = "",
    val sessionIndex: Int = 0
)

// ── Habits ───────────────────────────────────────────────────────────────────

@Entity(tableName = "habits")
@Serializable
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val cadence: String = "daily",    // "daily" | "weekly" | "monthly"
    // -1 = any day (user picks); 0-6 = fixed weekday (weekly); 1-31 = fixed monthday (monthly)
    val anchor: Int = -1,
    val weekdays: String = "[]",      // JSON int array for daily schedule restrictions
    val target: Int = 0,              // 0 = boolean; >0 = countable with target
    val unit: String = "",
    val clock: String = "",           // preferred time "HH:mm"
    val color: String = "",           // hex "#rrggbb" or ""
    val recurrence: String = "forever",  // "forever" | "period" | "month"
    val endsAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

// Sparse log: entry exists only for completed days
@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "dayKey"],
    indices = [Index("dayKey")]
)
@Serializable
data class HabitLogEntity(
    val habitId: String,
    val dayKey: String             // "YYYY-MM-DD"
)

// Honest rest-day marker
@Entity(
    tableName = "habit_respiros",
    primaryKeys = ["habitId", "dayKey"]
)
@Serializable
data class HabitRespiroEntity(
    val habitId: String,
    val dayKey: String,
    val reason: String = "",
    val at: Long = System.currentTimeMillis()
)

// Countable habit daily counts
@Entity(
    tableName = "habit_counts",
    primaryKeys = ["habitId", "dayKey"]
)
@Serializable
data class HabitCountEntity(
    val habitId: String,
    val dayKey: String,
    val count: Int
)

// ── Goals ────────────────────────────────────────────────────────────────────

@Entity(tableName = "goals")
@Serializable
data class GoalEntity(
    @PrimaryKey val id: String,
    val text: String,
    val done: Boolean = false,
    val quarter: String,             // "YYYY-Qn"
    val habitId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "milestones",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
@Serializable
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val goalId: String,
    val text: String,
    val done: Boolean = false
)

// ── Routines ─────────────────────────────────────────────────────────────────

@Entity(tableName = "routines")
@Serializable
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
@Serializable
data class RoutineItemEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val text: String,
    val priority: Int = 2,
    val targetMin: Int? = null
)

// ── Week-ahead plans ──────────────────────────────────────────────────────────

@Entity(tableName = "planned_intentions", primaryKeys = ["dayKey", "id"])
@Serializable
data class PlannedIntentionEntity(
    val dayKey: String,
    val id: String,
    val text: String,
    val priority: Int = 2,
    val targetMin: Int? = null
)

// ── Preferences (singleton row, id always = "prefs") ─────────────────────────

@Entity(tableName = "prefs")
@Serializable
data class PrefsEntity(
    @PrimaryKey val id: String = "prefs",
    val lang: String = "pt",
    val theme: String = "auto",       // "auto" | "light" | "dark"
    val accent: String = "",          // hex or ""
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val keepAwake: Boolean = false,
    val immersive: Boolean = false,
    val textScale: Float = 1f,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val parrot: Boolean = false,
    val onboardingSeen: Boolean = false,
    val autoBackup: String = "hourly",
    val remindersEnabled: Boolean = false,
    val remindersPlannerTime: String = "08:00",
    val remindersHabitsTime: String = "21:00",
    val remindersReflectionTime: String = "22:00"
)
