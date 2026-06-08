package com.pauta.app.data.dao

import androidx.room.*
import com.pauta.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentionDao {
    @Query("SELECT * FROM intentions WHERE dayKey = :dayKey ORDER BY priority ASC, createdAt ASC")
    fun getByDay(dayKey: String): Flow<List<IntentionEntity>>

    @Query("SELECT * FROM intentions WHERE dayKey = :dayKey ORDER BY priority ASC, createdAt ASC")
    suspend fun getByDaySuspend(dayKey: String): List<IntentionEntity>

    @Query("SELECT * FROM intentions WHERE id = :id")
    suspend fun getById(id: String): IntentionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(intention: IntentionEntity)

    @Update
    suspend fun update(intention: IntentionEntity)

    @Query("DELETE FROM intentions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM intentions WHERE dayKey >= :fromKey AND dayKey <= :toKey ORDER BY dayKey DESC, priority ASC")
    suspend fun getRange(fromKey: String, toKey: String): List<IntentionEntity>

    @Query("SELECT SUM(fi.endedAt - fi.startedAt) FROM focus_sessions fi JOIN focus_blocks fb ON fi.blockId = fb.id WHERE fb.linkedIntentionId = :intentionId AND fi.endedAt IS NOT NULL")
    fun getFocusMsForIntention(intentionId: String): Flow<Long?>

    @Query("SELECT * FROM intentions")
    suspend fun getAll(): List<IntentionEntity>
}

@Dao
interface FocusBlockDao {
    @Query("SELECT * FROM focus_blocks WHERE status = 'active' LIMIT 1")
    fun getActive(): Flow<FocusBlockEntity?>

    @Query("SELECT * FROM focus_blocks WHERE status = 'active' LIMIT 1")
    suspend fun getActiveSuspend(): FocusBlockEntity?

    @Query("SELECT * FROM focus_blocks WHERE status = 'paused' ORDER BY createdAt DESC")
    fun getPaused(): Flow<List<FocusBlockEntity>>

    @Query("SELECT * FROM focus_blocks WHERE id = :id")
    suspend fun getById(id: String): FocusBlockEntity?

    @Query("SELECT * FROM focus_blocks WHERE DATE(createdAt / 1000, 'unixepoch', 'localtime') = :dayKey ORDER BY createdAt ASC")
    fun getByDay(dayKey: String): Flow<List<FocusBlockEntity>>

    @Query("SELECT * FROM focus_blocks WHERE createdAt >= :fromMs AND createdAt < :toMs ORDER BY createdAt ASC")
    suspend fun getInRange(fromMs: Long, toMs: Long): List<FocusBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: FocusBlockEntity)

    @Update
    suspend fun update(block: FocusBlockEntity)

    @Query("DELETE FROM focus_blocks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM focus_sessions WHERE blockId = :blockId ORDER BY sessionIndex ASC")
    fun getSessionsForBlock(blockId: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE blockId = :blockId ORDER BY sessionIndex ASC")
    suspend fun getSessionsForBlockSuspend(blockId: String): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT SUM(CASE WHEN endedAt IS NOT NULL THEN endedAt - startedAt ELSE :nowMs - startedAt END) FROM focus_sessions WHERE blockId IN (SELECT id FROM focus_blocks WHERE DATE(createdAt / 1000, 'unixepoch', 'localtime') = :dayKey)")
    fun getDayFocusMs(dayKey: String, nowMs: Long): Flow<Long?>

    @Query("SELECT * FROM focus_blocks")
    suspend fun getAllBlocks(): List<FocusBlockEntity>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAllSessions(): List<FocusSessionEntity>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllSuspend(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: String)

    // Logs
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND dayKey >= :fromKey AND dayKey <= :toKey")
    suspend fun getLogs(habitId: String, fromKey: String, toKey: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY dayKey DESC")
    suspend fun getAllLogs(habitId: String): List<HabitLogEntity>

    @Query("SELECT dayKey FROM habit_logs WHERE habitId = :habitId AND dayKey >= :fromKey AND dayKey <= :toKey")
    fun getLogDays(habitId: String, fromKey: String, toKey: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun deleteLog(habitId: String, dayKey: String)

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun hasLog(habitId: String, dayKey: String): Int

    // Respiros
    @Query("SELECT * FROM habit_respiros WHERE habitId = :habitId AND dayKey >= :fromKey AND dayKey <= :toKey")
    suspend fun getRespiros(habitId: String, fromKey: String, toKey: String): List<HabitRespiroEntity>

    @Query("SELECT * FROM habit_respiros WHERE habitId = :habitId ORDER BY dayKey DESC")
    suspend fun getAllRespiros(habitId: String): List<HabitRespiroEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRespiro(respiro: HabitRespiroEntity)

    @Query("DELETE FROM habit_respiros WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun deleteRespiro(habitId: String, dayKey: String)

    // Counts
    @Query("SELECT * FROM habit_counts WHERE habitId = :habitId AND dayKey >= :fromKey AND dayKey <= :toKey")
    suspend fun getCounts(habitId: String, fromKey: String, toKey: String): List<HabitCountEntity>

    @Query("SELECT count FROM habit_counts WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun getCount(habitId: String, dayKey: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCount(count: HabitCountEntity)

    @Query("DELETE FROM habit_counts WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun deleteCount(habitId: String, dayKey: String)

    // Global dumps for backup
    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsGlobal(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_respiros")
    suspend fun getAllRespirosGlobal(): List<HabitRespiroEntity>

    @Query("SELECT * FROM habit_counts")
    suspend fun getAllCountsGlobal(): List<HabitCountEntity>
}

@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE dayKey = :dayKey")
    fun getDay(dayKey: String): Flow<DayEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: DayEntity)

    @Query("SELECT * FROM days ORDER BY dayKey DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<DayEntity>

    @Query("SELECT * FROM days")
    suspend fun getAll(): List<DayEntity>
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY quarter DESC, createdAt ASC")
    fun getAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM milestones WHERE goalId = :goalId ORDER BY rowid ASC")
    fun getMilestones(goalId: String): Flow<List<MilestoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: MilestoneEntity)

    @Update
    suspend fun updateMilestone(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteMilestone(id: String)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt ASC")
    fun getAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY rowid ASC")
    suspend fun getItems(routineId: String): List<RoutineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RoutineItemEntity>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM planned_intentions WHERE dayKey = :dayKey ORDER BY rowid ASC")
    fun getForDay(dayKey: String): Flow<List<PlannedIntentionEntity>>

    @Query("SELECT * FROM planned_intentions WHERE dayKey >= :fromKey AND dayKey <= :toKey ORDER BY dayKey ASC, rowid ASC")
    suspend fun getRange(fromKey: String, toKey: String): List<PlannedIntentionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlannedIntentionEntity)

    @Query("DELETE FROM planned_intentions WHERE dayKey = :dayKey AND id = :id")
    suspend fun delete(dayKey: String, id: String)

    @Query("DELETE FROM planned_intentions WHERE dayKey < :beforeKey")
    suspend fun pruneBefore(beforeKey: String)
}

@Dao
interface PrefsDao {
    @Query("SELECT * FROM prefs WHERE id = 'prefs'")
    fun get(): Flow<PrefsEntity?>

    @Query("SELECT * FROM prefs WHERE id = 'prefs'")
    suspend fun getSuspend(): PrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: PrefsEntity)
}
