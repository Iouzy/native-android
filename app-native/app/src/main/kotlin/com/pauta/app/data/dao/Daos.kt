package com.pauta.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.pauta.app.data.entity.BookEntity
import com.pauta.app.data.entity.BookNoteEntity
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
import kotlinx.coroutines.flow.Flow

/**
 * Reactive DAOs. Reads return [Flow]s so the Compose UI recomposes as the data
 * changes (the native equivalent of the web's single reactive store); writes are
 * suspending. Each DAO also exposes plain `getAll…()` snapshots used by the
 * backup/export path (Phase 8). // PT: DAOs reativos — leituras em Flow para a
 * UI recompor; escritas suspensas; snapshots para o backup.
 */

@Dao
interface DayDao {
    @Upsert suspend fun upsert(day: DayEntity)
    @Query("SELECT * FROM days WHERE dayKey = :dayKey") fun observe(dayKey: String): Flow<DayEntity?>
    @Query("SELECT * FROM days ORDER BY dayKey DESC") fun observeAll(): Flow<List<DayEntity>>
    @Query("SELECT * FROM days") suspend fun getAll(): List<DayEntity>
    @Query("DELETE FROM days") suspend fun clear()
}

@Dao
interface IntentionDao {
    @Upsert suspend fun upsert(intention: IntentionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<IntentionEntity>)
    @Update suspend fun update(intention: IntentionEntity)
    @Delete suspend fun delete(intention: IntentionEntity)
    @Query("DELETE FROM intentions WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM intentions WHERE dayKey = :dayKey ORDER BY position")
    fun observeForDay(dayKey: String): Flow<List<IntentionEntity>>

    @Query("SELECT * FROM intentions ORDER BY dayKey, position")
    fun observeAll(): Flow<List<IntentionEntity>>

    @Query("SELECT * FROM intentions WHERE id = :id") suspend fun getById(id: String): IntentionEntity?
    @Query("SELECT * FROM intentions WHERE dayKey = :dayKey ORDER BY position") suspend fun getForDay(dayKey: String): List<IntentionEntity>
    @Query("SELECT COUNT(*) FROM intentions WHERE dayKey = :dayKey") suspend fun countForDay(dayKey: String): Int
    @Query("SELECT * FROM intentions") suspend fun getAll(): List<IntentionEntity>
    @Query("DELETE FROM intentions") suspend fun clear()
}

@Dao
interface FocusBlockDao {
    @Upsert suspend fun upsert(block: FocusBlockEntity)
    @Delete suspend fun delete(block: FocusBlockEntity)
    @Query("DELETE FROM focus_blocks WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM focus_blocks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FocusBlockEntity>>

    @Query("SELECT * FROM focus_blocks WHERE status = 'active' LIMIT 1")
    fun observeActive(): Flow<FocusBlockEntity?>

    @Query("SELECT * FROM focus_blocks WHERE id = :id") suspend fun getById(id: String): FocusBlockEntity?
    @Query("SELECT * FROM focus_blocks") suspend fun getAll(): List<FocusBlockEntity>
    @Query("DELETE FROM focus_blocks") suspend fun clear()
}

@Dao
interface FocusSessionDao {
    @Insert suspend fun insert(session: FocusSessionEntity): Long
    @Update suspend fun update(session: FocusSessionEntity)
    @Insert suspend fun insertAll(sessions: List<FocusSessionEntity>)

    @Query("SELECT * FROM focus_sessions WHERE blockId = :blockId ORDER BY position")
    fun observeForBlock(blockId: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE blockId = :blockId ORDER BY position")
    suspend fun getForBlock(blockId: String): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions") fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions") suspend fun getAll(): List<FocusSessionEntity>
    @Query("DELETE FROM focus_sessions WHERE blockId = :blockId") suspend fun deleteForBlock(blockId: String)
    @Query("DELETE FROM focus_sessions") suspend fun clear()
}

@Dao
interface HabitDao {
    @Upsert suspend fun upsert(habit: HabitEntity)
    @Delete suspend fun delete(habit: HabitEntity)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteById(id: String)

    // The reactive grid/today-strip see only active tides; archived ones live in
    // their own stream for the Settings restore list. getAll() (export, reorder)
    // still returns every habit. // PT: a grelha vê só marés activas; arquivadas à parte.
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY position")
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id") suspend fun getById(id: String): HabitEntity?
    @Query("SELECT * FROM habits") suspend fun getAll(): List<HabitEntity>
    @Query("DELETE FROM habits") suspend fun clear()
}

@Dao
interface HabitMarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addLog(log: HabitLogEntity)
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun removeLog(habitId: String, dayKey: String)

    @Upsert suspend fun upsertRespiro(respiro: HabitRespiroEntity)
    @Query("DELETE FROM habit_respiros WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun removeRespiro(habitId: String, dayKey: String)

    @Upsert suspend fun upsertCount(count: HabitCountEntity)
    @Query("DELETE FROM habit_counts WHERE habitId = :habitId AND dayKey = :dayKey")
    suspend fun removeCount(habitId: String, dayKey: String)

    @Query("SELECT * FROM habit_logs") fun observeLogs(): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_respiros") fun observeRespiros(): Flow<List<HabitRespiroEntity>>
    @Query("SELECT * FROM habit_counts") fun observeCounts(): Flow<List<HabitCountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLogs(logs: List<HabitLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRespiros(respiros: List<HabitRespiroEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCounts(counts: List<HabitCountEntity>)

    @Query("SELECT * FROM habit_logs") suspend fun getAllLogs(): List<HabitLogEntity>
    @Query("SELECT * FROM habit_respiros") suspend fun getAllRespiros(): List<HabitRespiroEntity>
    @Query("SELECT * FROM habit_counts") suspend fun getAllCounts(): List<HabitCountEntity>

    @Query("DELETE FROM habit_logs") suspend fun clearLogs()
    @Query("DELETE FROM habit_respiros") suspend fun clearRespiros()
    @Query("DELETE FROM habit_counts") suspend fun clearCounts()
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsertGoal(goal: GoalEntity)
    @Delete suspend fun deleteGoal(goal: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoalById(id: String)

    @Upsert suspend fun upsertMilestone(milestone: MilestoneEntity)
    @Query("DELETE FROM milestones WHERE id = :id") suspend fun deleteMilestoneById(id: String)
    @Query("DELETE FROM milestones WHERE goalId = :goalId") suspend fun deleteMilestonesForGoal(goalId: String)

    @Query("SELECT * FROM goals ORDER BY position") fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM milestones ORDER BY position") fun observeMilestones(): Flow<List<MilestoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGoals(goals: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMilestones(milestones: List<MilestoneEntity>)

    @Query("SELECT * FROM goals") suspend fun getAllGoals(): List<GoalEntity>
    @Query("SELECT * FROM milestones") suspend fun getAllMilestones(): List<MilestoneEntity>
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM milestones") suspend fun clearMilestones()
}

@Dao
interface RoutineDao {
    @Upsert suspend fun upsertRoutine(routine: RoutineEntity)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutineById(id: String)
    @Insert suspend fun insertItems(items: List<RoutineItemEntity>)
    @Update suspend fun updateItem(item: RoutineItemEntity)
    @Query("DELETE FROM routine_items WHERE routineId = :routineId") suspend fun deleteItemsForRoutine(routineId: String)
    @Query("DELETE FROM routine_items WHERE rowId = :rowId") suspend fun deleteItemByRowId(rowId: Long)

    @Query("SELECT * FROM routines ORDER BY position") fun observeRoutines(): Flow<List<RoutineEntity>>
    @Query("SELECT * FROM routine_items ORDER BY position") fun observeItems(): Flow<List<RoutineItemEntity>>

    // Snapshots used by the D1 manager when it needs the current rows to edit
    // (rename, reorder, append an item at the end). // PT: snapshots para o gestor.
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun getRoutineById(id: String): RoutineEntity?
    @Query("SELECT * FROM routine_items WHERE rowId = :rowId") suspend fun getItemByRowId(rowId: Long): RoutineItemEntity?
    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY position") suspend fun getItemsForRoutine(routineId: String): List<RoutineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRoutines(routines: List<RoutineEntity>)
    @Query("SELECT * FROM routines") suspend fun getAllRoutines(): List<RoutineEntity>
    @Query("SELECT * FROM routine_items") suspend fun getAllItems(): List<RoutineItemEntity>
    @Query("DELETE FROM routines") suspend fun clearRoutines()
    @Query("DELETE FROM routine_items") suspend fun clearItems()
}

@Dao
interface PlannedIntentionDao {
    @Upsert suspend fun upsert(item: PlannedIntentionEntity)
    @Query("DELETE FROM planned_intentions WHERE id = :id") suspend fun deleteById(id: String)
    @Query("DELETE FROM planned_intentions WHERE dayKey <= :dayKey") suspend fun deleteUpTo(dayKey: String)

    @Query("SELECT * FROM planned_intentions WHERE dayKey = :dayKey ORDER BY position")
    fun observeForDay(dayKey: String): Flow<List<PlannedIntentionEntity>>

    @Query("SELECT * FROM planned_intentions WHERE dayKey = :dayKey ORDER BY position")
    suspend fun getForDay(dayKey: String): List<PlannedIntentionEntity>

    @Query("SELECT * FROM planned_intentions ORDER BY dayKey, position")
    fun observeAll(): Flow<List<PlannedIntentionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PlannedIntentionEntity>)
    @Query("SELECT * FROM planned_intentions") suspend fun getAll(): List<PlannedIntentionEntity>
    @Query("DELETE FROM planned_intentions") suspend fun clear()
}

// native-only (K1): the personal book shelf. Reads stream by status so each
// shelf section (reading / tbr / done) recomposes on its own; snapshots back the
// reset/clear paths. // PT: estante de livros — leituras por estado, snapshots
// para limpar.
@Dao
interface BookDao {
    @Upsert suspend fun upsert(book: BookEntity)
    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM books WHERE status = :status ORDER BY position")
    fun observeByStatus(status: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id") suspend fun getById(id: String): BookEntity?

    // Books finished on/after the given epoch ms — feeds the K7 annual goal count.
    // // PT: livros terminados desde uma data — para o objetivo anual.
    @Query("SELECT COUNT(*) FROM books WHERE status = 'done' AND finishedAt >= :fromMs")
    suspend fun countFinishedSince(fromMs: Long): Int

    @Query("SELECT * FROM books") suspend fun getAll(): List<BookEntity>
    @Query("DELETE FROM books") suspend fun clear()
}

// native-only (K1): quotes / annotations / thoughts captured against a book.
// // PT: notas e citações de um livro.
@Dao
interface BookNoteDao {
    @Insert suspend fun insert(note: BookNoteEntity): Long
    @Query("DELETE FROM book_notes WHERE id = :id") suspend fun deleteById(id: String)

    @Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookNoteEntity>>

    @Query("SELECT * FROM book_notes") suspend fun getAll(): List<BookNoteEntity>
    @Query("DELETE FROM book_notes") suspend fun clear()
}

@Dao
interface PrefsDao {
    @Upsert suspend fun upsert(prefs: PrefsEntity)
    @Query("SELECT * FROM prefs WHERE id = 'prefs' LIMIT 1") fun observe(): Flow<PrefsEntity?>
    @Query("SELECT * FROM prefs WHERE id = 'prefs' LIMIT 1") suspend fun get(): PrefsEntity?
}
