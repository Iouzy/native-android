package com.pauta.app.data

import com.pauta.app.data.dao.*
import com.pauta.app.data.entity.*
import com.pauta.app.domain.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class PautaRepository(private val db: AppDatabase) {

    // ── Convenience accessors ─────────────────────────────────────────────

    private val intentionDao get() = db.intentionDao()
    private val blockDao     get() = db.focusBlockDao()
    private val habitDao     get() = db.habitDao()
    private val dayDao       get() = db.dayDao()
    private val goalDao      get() = db.goalDao()
    private val routineDao   get() = db.routineDao()
    private val planDao      get() = db.planDao()
    private val prefsDao     get() = db.prefsDao()

    fun newId(): String = UUID.randomUUID().toString()

    // ── Direct entity lookups (used by the ViewModel) ─────────────────────

    suspend fun getBlockById(id: String): FocusBlockEntity? = blockDao.getById(id)

    suspend fun getHabitById(id: String): HabitEntity? = habitDao.getById(id)

    suspend fun habitRespirosInRange(habitId: String, fromKey: String, toKey: String): Set<String> =
        habitDao.getRespiros(habitId, fromKey, toKey).map { it.dayKey }.toSet()

    // ── Prefs ─────────────────────────────────────────────────────────────

    fun prefsFlow(): Flow<PrefsEntity> = prefsDao.get().map { it ?: PrefsEntity() }

    suspend fun upsertPrefs(prefs: PrefsEntity) = prefsDao.upsert(prefs)

    suspend fun getPrefs(): PrefsEntity = prefsDao.getSuspend() ?: PrefsEntity()

    // ── Days / reflection ─────────────────────────────────────────────────

    fun dayFlow(dayKey: String): Flow<DayEntity?> = dayDao.getDay(dayKey)

    suspend fun setReflection(dayKey: String, text: String) {
        dayDao.upsert(DayEntity(dayKey, text))
    }

    // ── Intentions ────────────────────────────────────────────────────────

    fun intentionsForDay(dayKey: String): Flow<List<IntentionEntity>> =
        intentionDao.getByDay(dayKey)

    suspend fun addIntention(
        dayKey: String,
        text: String,
        priority: Int = 2,
        targetMin: Int? = null,
        timeOfDay: String? = null
    ) {
        intentionDao.insert(
            IntentionEntity(
                id = newId(),
                dayKey = dayKey,
                text = text,
                priority = priority,
                targetMin = targetMin,
                timeOfDay = timeOfDay
            )
        )
    }

    suspend fun updateIntention(intention: IntentionEntity) = intentionDao.update(intention)

    suspend fun toggleIntention(id: String) {
        val existing = intentionDao.getById(id) ?: return
        intentionDao.update(existing.copy(done = !existing.done))
    }

    suspend fun removeIntention(id: String) = intentionDao.delete(id)

    fun intentionFocusMs(intentionId: String): Flow<Long> =
        intentionDao.getFocusMsForIntention(intentionId).map { it ?: 0L }

    // ── Focus blocks ──────────────────────────────────────────────────────

    fun activeBlockFlow(): Flow<FocusBlockEntity?> = blockDao.getActive()

    fun pausedBlocksFlow(): Flow<List<FocusBlockEntity>> = blockDao.getPaused()

    fun sessionsForBlock(blockId: String): Flow<List<FocusSessionEntity>> =
        blockDao.getSessionsForBlock(blockId)

    fun dayFocusMs(dayKey: String, nowMs: Long): Flow<Long> =
        blockDao.getDayFocusMs(dayKey, nowMs).map { it ?: 0L }

    fun blocksForDay(dayKey: String): Flow<List<FocusBlockEntity>> =
        blockDao.getByDay(dayKey)

    suspend fun startBlock(
        title: String,
        linkedIntentionId: String? = null,
        project: String? = null,
        targetMs: Long = 0L
    ): FocusBlockEntity {
        // Only one active block at a time — pause any currently active
        val current = blockDao.getActiveSuspend()
        if (current != null) blockDao.update(current.copy(status = "paused"))

        val block = FocusBlockEntity(
            id = newId(),
            title = title,
            linkedIntentionId = linkedIntentionId,
            project = project,
            status = "active",
            targetMs = targetMs
        )
        blockDao.insert(block)

        val session = FocusSessionEntity(
            id = newId(),
            blockId = block.id,
            startedAt = System.currentTimeMillis(),
            sessionIndex = 0
        )
        blockDao.insertSession(session)
        return block
    }

    suspend fun pauseActive(note: String = "") {
        val block = blockDao.getActiveSuspend() ?: return
        val sessions = blockDao.getSessionsForBlockSuspend(block.id)
        val lastSession = sessions.lastOrNull()
        if (lastSession != null && lastSession.endedAt == null) {
            blockDao.updateSession(
                lastSession.copy(endedAt = System.currentTimeMillis(), note = note)
            )
        }
        blockDao.update(block.copy(status = "paused"))
    }

    suspend fun resumeBlock(blockId: String) {
        // Pause any currently active block first
        val active = blockDao.getActiveSuspend()
        if (active != null && active.id != blockId) {
            pauseActive()
        }

        val block = blockDao.getById(blockId) ?: return
        val sessions = blockDao.getSessionsForBlockSuspend(blockId)
        val newSession = FocusSessionEntity(
            id = newId(),
            blockId = blockId,
            startedAt = System.currentTimeMillis(),
            sessionIndex = sessions.size
        )
        blockDao.insertSession(newSession)
        blockDao.update(block.copy(status = "active"))
    }

    suspend fun concludeBlock(blockId: String, reflection: String, markIntentionDone: Boolean = false) {
        val block = blockDao.getById(blockId) ?: return
        val sessions = blockDao.getSessionsForBlockSuspend(blockId)
        val lastSession = sessions.lastOrNull()
        if (lastSession != null && lastSession.endedAt == null) {
            blockDao.updateSession(lastSession.copy(endedAt = System.currentTimeMillis()))
        }
        blockDao.update(block.copy(status = "done", reflection = reflection))
        if (markIntentionDone && block.linkedIntentionId != null) {
            val intention = intentionDao.getById(block.linkedIntentionId)
            if (intention != null) intentionDao.update(intention.copy(done = true))
        }
    }

    suspend fun concludeActive(reflection: String, markIntentionDone: Boolean = false) {
        val block = blockDao.getActiveSuspend() ?: return
        concludeBlock(block.id, reflection, markIntentionDone)
    }

    suspend fun updateBlock(blockId: String, title: String? = null, project: String? = null, reflection: String? = null) {
        val block = blockDao.getById(blockId) ?: return
        blockDao.update(block.copy(
            title = title ?: block.title,
            project = project ?: block.project,
            reflection = reflection ?: block.reflection
        ))
    }

    suspend fun deleteBlock(blockId: String) = blockDao.delete(blockId)

    suspend fun addManualBlock(title: String, startedAt: Long, endedAt: Long, reflection: String) {
        val block = FocusBlockEntity(
            id = newId(),
            title = title,
            status = "done",
            reflection = reflection,
            createdAt = startedAt
        )
        blockDao.insert(block)
        blockDao.insertSession(
            FocusSessionEntity(
                id = newId(),
                blockId = block.id,
                startedAt = startedAt,
                endedAt = endedAt
            )
        )
    }

    // ── Computed elapsed ms for an active/paused block ────────────────────

    suspend fun computeElapsedMs(blockId: String, nowMs: Long = System.currentTimeMillis()): Long {
        val sessions = blockDao.getSessionsForBlockSuspend(blockId)
        return sessions.sumOf { s ->
            val end = s.endedAt ?: nowMs
            maxOf(0L, end - s.startedAt)
        }
    }

    // ── Habits ────────────────────────────────────────────────────────────

    fun habitsFlow(): Flow<List<HabitEntity>> = habitDao.getAll()

    suspend fun addHabit(
        name: String,
        cadence: String = "daily",
        anchor: Int = -1,
        weekdays: String = "[]",
        target: Int = 0,
        unit: String = "",
        clock: String = "",
        description: String = "",
        color: String = "",
        recurrence: String = "forever",
        endsAt: Long? = null
    ) {
        val all = habitDao.getAllSuspend()
        habitDao.insert(
            HabitEntity(
                id = newId(),
                name = name,
                cadence = cadence,
                anchor = anchor,
                weekdays = weekdays,
                target = target,
                unit = unit,
                clock = clock,
                description = description,
                color = color,
                recurrence = recurrence,
                endsAt = endsAt,
                sortOrder = all.size
            )
        )
    }

    suspend fun updateHabit(habit: HabitEntity) = habitDao.update(habit)

    suspend fun removeHabit(id: String) = habitDao.delete(id)

    suspend fun reorderHabits(orderedIds: List<String>) {
        orderedIds.forEachIndexed { idx, id ->
            val habit = habitDao.getById(id) ?: return@forEachIndexed
            habitDao.update(habit.copy(sortOrder = idx))
        }
    }

    // Habit day mutations

    suspend fun toggleHabitDay(habitId: String, dayKey: String) {
        val has = habitDao.hasLog(habitId, dayKey) > 0
        if (has) habitDao.deleteLog(habitId, dayKey)
        else habitDao.insertLog(HabitLogEntity(habitId, dayKey))
    }

    suspend fun markRespiro(habitId: String, dayKey: String, reason: String) {
        habitDao.deleteLog(habitId, dayKey)
        habitDao.insertRespiro(HabitRespiroEntity(habitId, dayKey, reason))
    }

    suspend fun unmarkRespiro(habitId: String, dayKey: String) {
        habitDao.deleteRespiro(habitId, dayKey)
    }

    suspend fun setHabitCount(habitId: String, dayKey: String, count: Int) {
        if (count <= 0) habitDao.deleteCount(habitId, dayKey)
        else habitDao.upsertCount(HabitCountEntity(habitId, dayKey, count))
    }

    suspend fun incHabitCount(habitId: String, dayKey: String) {
        val current = habitDao.getCount(habitId, dayKey) ?: 0
        habitDao.upsertCount(HabitCountEntity(habitId, dayKey, current + 1))
    }

    // Habit data loaders for a date range (used by ViewModel to compute stats)

    suspend fun habitLogsInRange(habitId: String, fromKey: String, toKey: String): Set<String> =
        habitDao.getLogs(habitId, fromKey, toKey).map { it.dayKey }.toSet()

    suspend fun allHabitLogs(habitId: String): Set<String> =
        habitDao.getAllLogs(habitId).map { it.dayKey }.toSet()

    suspend fun allHabitRespiros(habitId: String): Set<String> =
        habitDao.getAllRespiros(habitId).map { it.dayKey }.toSet()

    suspend fun habitCountsInRange(habitId: String, fromKey: String, toKey: String): Map<String, Int> =
        habitDao.getCounts(habitId, fromKey, toKey).associate { it.dayKey to it.count }

    fun habitLogDaysInRange(habitId: String, fromKey: String, toKey: String): Flow<List<String>> =
        habitDao.getLogDays(habitId, fromKey, toKey)

    // ── Goals ─────────────────────────────────────────────────────────────

    fun goalsFlow(): Flow<List<GoalEntity>> = goalDao.getAll()

    suspend fun addGoal(text: String, quarter: String) =
        goalDao.insertGoal(GoalEntity(id = newId(), text = text, quarter = quarter))

    suspend fun updateGoal(goal: GoalEntity) = goalDao.updateGoal(goal)

    suspend fun removeGoal(id: String) = goalDao.deleteGoal(id)

    // ── Routines ──────────────────────────────────────────────────────────

    fun routinesFlow(): Flow<List<RoutineEntity>> = routineDao.getAll()

    suspend fun applyRoutine(routineId: String, dayKey: String) {
        val items = routineDao.getItems(routineId)
        for (item in items) {
            intentionDao.insert(
                IntentionEntity(
                    id = newId(),
                    dayKey = dayKey,
                    text = item.text,
                    priority = item.priority,
                    targetMin = item.targetMin
                )
            )
        }
    }

    suspend fun saveRoutineFromDay(name: String, dayKey: String) {
        val intentions = intentionDao.getByDaySuspend(dayKey)
        val routine = RoutineEntity(id = newId(), name = name)
        routineDao.insertRoutine(routine)
        routineDao.insertItems(intentions.mapIndexed { idx, it ->
            RoutineItemEntity(
                id = newId(),
                routineId = routine.id,
                text = it.text,
                priority = it.priority,
                targetMin = it.targetMin
            )
        })
    }

    suspend fun removeRoutine(id: String) = routineDao.deleteRoutine(id)

    // ── Plans (week-ahead) ────────────────────────────────────────────────

    fun plansForDay(dayKey: String): Flow<List<PlannedIntentionEntity>> = planDao.getForDay(dayKey)

    suspend fun addPlannedIntention(dayKey: String, text: String, priority: Int = 2) {
        planDao.insert(PlannedIntentionEntity(dayKey = dayKey, id = newId(), text = text, priority = priority))
    }

    suspend fun removePlannedIntention(dayKey: String, id: String) = planDao.delete(dayKey, id)

    // ── Carry-over ────────────────────────────────────────────────────────

    suspend fun carryOverIntentions(fromDayKey: String, toDayKey: String) {
        val yesterday = intentionDao.getByDaySuspend(fromDayKey)
        val unfinished = yesterday.filter { !it.done }
        for (i in unfinished) {
            intentionDao.insert(i.copy(id = newId(), dayKey = toDayKey))
        }
    }
}
