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
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.HistoryBuilder
import com.pauta.app.domain.HistoryDay
import com.pauta.app.domain.HojeLogic
import com.pauta.app.domain.MarkKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The single gateway between the UI/ViewModel and the Room database — the native
 * equivalent of the web's `useStore()`. Preferences plus the Hoje (day +
 * intentions) actions live here so far; focus blocks and habits layer on in
 * their phases. // PT: porta única entre a UI e a base de dados, como o
 * useStore() da web.
 */
class PautaRepository(private val db: AppDatabase) {

    private val prefsDao = db.prefsDao()
    private val dayDao = db.dayDao()
    private val intentionDao = db.intentionDao()
    private val focusBlockDao = db.focusBlockDao()
    private val focusSessionDao = db.focusSessionDao()
    private val habitDao = db.habitDao()
    private val habitMarkDao = db.habitMarkDao()
    private val goalDao = db.goalDao()
    private val routineDao = db.routineDao()
    private val plannedDao = db.plannedIntentionDao()

    // ── ids ───────────────────────────────────────────────────
    /** Generate a web-style id (`i_…`, `b_…`, …): prefix + base-36 time + random,
     *  so native-created rows never collide with imported web ids. */
    fun newId(prefix: String): String =
        prefix + System.currentTimeMillis().toString(36) + Random.nextInt(0, 1_000_000).toString(36)

    // ── preferences ───────────────────────────────────────────
    val prefs: Flow<PrefsEntity> = prefsDao.observe().map { it ?: PrefsEntity() }

    suspend fun ensurePrefs() {
        if (prefsDao.get() == null) prefsDao.upsert(PrefsEntity())
    }

    suspend fun updatePrefs(transform: (PrefsEntity) -> PrefsEntity) {
        val current = prefsDao.get() ?: PrefsEntity()
        prefsDao.upsert(transform(current))
    }

    // ── Hoje: intentions + day reflection ─────────────────────
    /** Today's (or any day's) intentions, ordered by their stored position. */
    fun intentions(dayKey: String): Flow<List<IntentionEntity>> = intentionDao.observeForDay(dayKey)

    /** A day's nightly reflection (empty until written). */
    fun dayReflection(dayKey: String): Flow<String> =
        dayDao.observe(dayKey).map { it?.reflection ?: "" }

    /** Add an intention to a day. Priority is coerced to 1..3 (else unset),
     *  targetMin kept only if > 0 — matching the web's addIntention. Returns the
     *  new id, or null for blank text. */
    suspend fun addIntention(
        dayKey: String,
        text: String,
        priority: Int? = null,
        targetMin: Int? = null,
        timeOfDay: String? = null,
    ): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val id = newId("i_")
        intentionDao.upsert(
            IntentionEntity(
                id = id,
                dayKey = dayKey,
                text = t,
                done = false,
                priority = priority?.takeIf { it in 1..3 },
                targetMin = targetMin?.takeIf { it > 0 },
                timeOfDay = timeOfDay,
                createdAt = System.currentTimeMillis(),
                position = intentionDao.countForDay(dayKey),
            ),
        )
        return id
    }

    suspend fun toggleIntention(id: String) {
        val it = intentionDao.getById(id) ?: return
        intentionDao.update(it.copy(done = !it.done))
    }

    suspend fun setIntentionText(id: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val it = intentionDao.getById(id) ?: return
        intentionDao.update(it.copy(text = t))
    }

    suspend fun setIntentionPriority(id: String, priority: Int?) {
        val it = intentionDao.getById(id) ?: return
        intentionDao.update(it.copy(priority = priority?.takeIf { p -> p in 1..3 }))
    }

    suspend fun removeIntention(id: String) = intentionDao.deleteById(id)

    /** Persist a new order by rewriting positions in the given id sequence. */
    suspend fun reorderIntentions(dayKey: String, orderedIds: List<String>) {
        val byId = intentionDao.getForDay(dayKey).associateBy { it.id }
        orderedIds.forEachIndexed { index, id ->
            byId[id]?.let { intentionDao.update(it.copy(position = index)) }
        }
    }

    suspend fun setReflection(dayKey: String, text: String) {
        dayDao.upsert(DayEntity(dayKey = dayKey, reflection = text))
    }

    // ── carry-over ────────────────────────────────────────────
    /** The most recent past day's unfinished intentions, offered as a one-tap
     *  carry-over (null when there's nothing to bring forward). */
    fun carrySource(todayKey: String): Flow<CarrySource?> =
        intentionDao.observeAll().map { HojeLogic.carrySource(it, todayKey) }

    // ── history ───────────────────────────────────────────────
    /** Every intention ever / every day row — the Revisão sheet's raw inputs. */
    fun allIntentions(): Flow<List<IntentionEntity>> = intentionDao.observeAll()
    fun allDays(): Flow<List<DayEntity>> = dayDao.observeAll()

    /** Read-only history of past days with content, newest first. */
    fun history(todayKey: String): Flow<List<HistoryDay>> =
        combine(dayDao.observeAll(), intentionDao.observeAll()) { days, intentions ->
            HistoryBuilder.build(days, intentions, todayKey)
        }

    /** Copy the given items into [todayKey] as fresh intentions, preserving
     *  priority + planned duration (fresh ids/timestamps), appended after any
     *  existing ones — mirroring the web's carryOverIntentions. */
    suspend fun carryOver(todayKey: String, items: List<IntentionEntity>) {
        var pos = intentionDao.countForDay(todayKey)
        for (src in items) {
            intentionDao.upsert(
                IntentionEntity(
                    id = newId("i_"),
                    dayKey = todayKey,
                    text = src.text,
                    done = false,
                    priority = src.priority,
                    targetMin = src.targetMin,
                    timeOfDay = null,
                    createdAt = System.currentTimeMillis(),
                    position = pos++,
                ),
            )
        }
    }

    // ── week-ahead plans ──────────────────────────────────────
    /** Every planned (week-ahead) item, ordered by day then position. */
    fun plans(): Flow<List<PlannedIntentionEntity>> = plannedDao.observeAll()

    /** Plan an intention for a future day; it becomes a real intention when the
     *  day arrives (runRollover). // PT: plano que vira intenção no proprio dia. */
    suspend fun addPlan(dayKey: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val pos = plannedDao.getForDay(dayKey).size
        plannedDao.upsert(
            PlannedIntentionEntity(
                id = newId("p_"),
                dayKey = dayKey,
                text = t,
                createdAt = System.currentTimeMillis(),
                position = pos,
            ),
        )
    }

    suspend fun removePlan(id: String) = plannedDao.deleteById(id)

    // ── rollover ──────────────────────────────────────────────
    /** Midnight rollover: promote a week-ahead plan for today into today's
     *  intentions, then drop every plan up to and including today (promoted or
     *  stale) — the Room analogue of the web's rollOverDay. Idempotent: yesterday's
     *  intentions already carry their own dayKey, so nothing to "archive". */
    suspend fun runRollover(todayKey: String) {
        val planned = plannedDao.getForDay(todayKey)
        if (planned.isNotEmpty()) {
            var pos = intentionDao.countForDay(todayKey)
            for (p in planned) {
                intentionDao.upsert(
                    IntentionEntity(
                        id = newId("i_"),
                        dayKey = todayKey,
                        text = p.text,
                        done = false,
                        priority = p.priority,
                        targetMin = p.targetMin,
                        timeOfDay = null,
                        createdAt = System.currentTimeMillis(),
                        position = pos++,
                    ),
                )
            }
        }
        plannedDao.deleteUpTo(todayKey)
    }

    // ── Pauta: focus blocks ───────────────────────────────────
    /** All focus blocks, newest first. */
    fun blocks(): Flow<List<FocusBlockEntity>> = focusBlockDao.observeAll()

    /** The single running block, if any. */
    fun activeBlock(): Flow<FocusBlockEntity?> = focusBlockDao.observeActive()

    /** A block's sessions, in order. */
    fun sessions(blockId: String): Flow<List<FocusSessionEntity>> =
        focusSessionDao.observeForBlock(blockId)

    /** Every session across all blocks (for per-block + daily focus totals). */
    fun allSessions(): Flow<List<FocusSessionEntity>> = focusSessionDao.observeAll()

    /** Close the open (last) session of a block, if it's still running. */
    private suspend fun endOpenSession(blockId: String, now: Long, note: String? = null) {
        val last = focusSessionDao.getForBlock(blockId).lastOrNull() ?: return
        if (last.endedAt == null) {
            focusSessionDao.update(last.copy(endedAt = now, note = note ?: last.note))
        }
    }

    /** Pause every block currently "active" — keeps the invariant of at most one
     *  running block (resume/start call this first). */
    private suspend fun pauseAnyActive(now: Long) {
        focusBlockDao.getAll().filter { it.status == "active" }.forEach { b ->
            endOpenSession(b.id, now)
            focusBlockDao.upsert(b.copy(status = "paused"))
        }
    }

    /** Start a new focus block (auto-pausing any other running one). targetMin>0
     *  becomes a soft Pomodoro target in ms. Returns the id, null for blank title. */
    suspend fun startBlock(
        title: String,
        linkedToId: String? = null,
        project: String? = null,
        targetMin: Int? = null,
    ): String? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val now = System.currentTimeMillis()
        pauseAnyActive(now)
        val id = newId("b_")
        focusBlockDao.upsert(
            FocusBlockEntity(
                id = id,
                title = t,
                linkedToId = linkedToId,
                project = project?.trim()?.ifEmpty { null },
                targetMs = targetMin?.takeIf { it > 0 }?.let { it * 60_000L },
                status = "active",
                reflection = "",
                createdAt = now,
            ),
        )
        focusSessionDao.insert(FocusSessionEntity(blockId = id, startedAt = now, endedAt = null, position = 0))
        return id
    }

    /** Pause the running block, ending its session (with an optional note). */
    suspend fun pauseActive(note: String = "") {
        val now = System.currentTimeMillis()
        val active = focusBlockDao.getAll().firstOrNull { it.status == "active" } ?: return
        endOpenSession(active.id, now, note.trim())
        focusBlockDao.upsert(active.copy(status = "paused"))
    }

    /** Attach/replace the note on a block's most recent ended session — the
     *  pause sheet collects it after the timer has already stopped, so pausing
     *  loses no seconds while the user types. // PT: nota da pausa, escrita
     *  depois de o cronómetro já ter parado. */
    suspend fun setLastSessionNote(blockId: String, note: String) {
        val last = focusSessionDao.getForBlock(blockId).lastOrNull { it.endedAt != null } ?: return
        focusSessionDao.update(last.copy(note = note.trim()))
    }

    /** Resume a paused block with a fresh session (auto-pausing any other). */
    suspend fun resumeBlock(blockId: String) {
        val now = System.currentTimeMillis()
        pauseAnyActive(now)
        val b = focusBlockDao.getById(blockId) ?: return
        val pos = focusSessionDao.getForBlock(blockId).size
        focusSessionDao.insert(FocusSessionEntity(blockId = blockId, startedAt = now, endedAt = null, position = pos))
        focusBlockDao.upsert(b.copy(status = "active"))
    }

    /** Conclude the running block; optionally tick its linked intention done. */
    suspend fun concludeActive(reflection: String, markIntentionDone: Boolean = false) {
        val now = System.currentTimeMillis()
        val active = focusBlockDao.getAll().firstOrNull { it.status == "active" } ?: return
        endOpenSession(active.id, now)
        focusBlockDao.upsert(active.copy(status = "done", reflection = reflection.trim()))
        if (markIntentionDone) markLinkedDone(active.linkedToId)
    }

    /** Conclude a paused (non-active) block. */
    suspend fun concludeBlock(blockId: String, reflection: String, markIntentionDone: Boolean = false) {
        val b = focusBlockDao.getById(blockId) ?: return
        focusBlockDao.upsert(b.copy(status = "done", reflection = reflection.trim()))
        if (markIntentionDone) markLinkedDone(b.linkedToId)
    }

    private suspend fun markLinkedDone(intentionId: String?) {
        if (intentionId == null) return
        intentionDao.getById(intentionId)?.let { intentionDao.update(it.copy(done = true)) }
    }

    suspend fun setBlockTitle(id: String, title: String) {
        val t = title.trim(); if (t.isEmpty()) return
        focusBlockDao.getById(id)?.let { focusBlockDao.upsert(it.copy(title = t)) }
    }

    /** The web's updateBlock(): edit title / project / soft target after the
     *  fact (EditBlockSheet). // PT: editar um bloco depois de criado. */
    suspend fun updateBlock(id: String, title: String, project: String?, targetMs: Long?) {
        val b = focusBlockDao.getById(id) ?: return
        val t = title.trim().ifEmpty { b.title }
        focusBlockDao.upsert(b.copy(title = t, project = project?.trim()?.takeIf { it.isNotEmpty() }, targetMs = targetMs))
    }

    /** Edit the note of one specific session row — the timeline's inline
     *  "adicionar nota…". // PT: nota de uma sessão concreta, pela linha. */
    suspend fun setSessionNote(rowId: Long, note: String) {
        val s = focusSessionDao.getAll().firstOrNull { it.rowId == rowId } ?: return
        focusSessionDao.update(s.copy(note = note.trim()))
    }

    suspend fun setBlockReflection(id: String, text: String) {
        focusBlockDao.getById(id)?.let { focusBlockDao.upsert(it.copy(reflection = text.trim())) }
    }

    suspend fun deleteBlock(id: String) {
        focusSessionDao.deleteForBlock(id)
        focusBlockDao.deleteById(id)
    }

    /** Log a finished block from a manual time entry (forgot to run the timer):
     *  a single closed session [startMs, endMs]. Returns the id, null if invalid. */
    suspend fun addManualBlock(
        title: String,
        startMs: Long,
        endMs: Long,
        project: String? = null,
        linkedToId: String? = null,
    ): String? {
        val t = title.trim()
        if (t.isEmpty() || endMs <= startMs) return null
        val id = newId("b_")
        focusBlockDao.upsert(
            FocusBlockEntity(
                id = id,
                title = t,
                linkedToId = linkedToId,
                project = project?.trim()?.ifEmpty { null },
                targetMs = null,
                status = "done",
                reflection = "",
                createdAt = startMs,
            ),
        )
        focusSessionDao.insert(FocusSessionEntity(blockId = id, startedAt = startMs, endedAt = endMs, position = 0))
        return id
    }

    // ── Marés: habits + marks ─────────────────────────────────
    fun habits(): Flow<List<HabitEntity>> = habitDao.observeAll()
    fun habitLogs(): Flow<List<HabitLogEntity>> = habitMarkDao.observeLogs()
    fun habitRespiros(): Flow<List<HabitRespiroEntity>> = habitMarkDao.observeRespiros()
    fun habitCounts(): Flow<List<HabitCountEntity>> = habitMarkDao.observeCounts()

    /** Build the calculator's [HabitModel] (static fields + done/respiro day
     *  sets) for a habit, from the current marks. */
    private suspend fun modelOf(h: HabitEntity): HabitModel {
        val log = habitMarkDao.getAllLogs().asSequence().filter { it.habitId == h.id }.map { it.dayKey }.toSet()
        val resp = habitMarkDao.getAllRespiros().asSequence().filter { it.habitId == h.id }.map { it.dayKey }.toSet()
        return HabitModel(
            id = h.id, createdAt = h.createdAt, cadence = h.cadence, anchor = h.anchor,
            weekdays = h.weekdays, recurrence = h.recurrence, endsAt = h.endsAt, log = log, respiros = resp,
        )
    }

    suspend fun addHabit(
        name: String,
        time: String = "",
        cadence: String = "daily",
        anchor: Int? = null,
        weekdays: List<Int> = emptyList(),
        target: Int? = null,
        unit: String = "",
        clock: String = "",
        color: String? = null,
        recurrence: String = "forever",
        endsAt: Long? = null,
        description: String = "",
    ): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val id = newId("h_")
        habitDao.upsert(
            HabitEntity(
                id = id, name = n, time = time.trim(), description = description.trim(),
                createdAt = System.currentTimeMillis(), recurrence = recurrence, endsAt = endsAt,
                cadence = cadence, anchor = anchor, weekdays = weekdays,
                target = target?.takeIf { it > 1 }, unit = unit.trim(), clock = clock.trim(),
                color = color?.takeIf { isHexColor(it) }?.trim(),
                position = habitDao.getAll().size,
            ),
        )
        return id
    }

    suspend fun updateHabit(habit: HabitEntity) = habitDao.upsert(habit)
    suspend fun getHabit(id: String): HabitEntity? = habitDao.getById(id)

    suspend fun removeHabit(id: String) {
        habitMarkDao.run {
            // marks are sparse rows keyed by habitId; clear this habit's marks.
            getAllLogs().filter { it.habitId == id }.forEach { removeLog(id, it.dayKey) }
            getAllRespiros().filter { it.habitId == id }.forEach { removeRespiro(id, it.dayKey) }
            getAllCounts().filter { it.habitId == id }.forEach { removeCount(id, it.dayKey) }
        }
        habitDao.deleteById(id)
    }

    suspend fun reorderHabits(orderedIds: List<String>) {
        val byId = habitDao.getAll().associateBy { it.id }
        orderedIds.forEachIndexed { index, id ->
            byId[id]?.let { habitDao.upsert(it.copy(position = index)) }
        }
    }

    /** Toggle a day's completion, honouring the web's period/anchor rules. */
    suspend fun toggleHabitDay(id: String, dayKey: String, todayKey: String = DateUtils.todayKey()) {
        val h = habitDao.getById(id) ?: return
        val m = modelOf(h)
        if (!HabitCalculator.isActiveOn(m, dayKey) || dayKey > todayKey) return
        if (dayKey in m.log) { habitMarkDao.removeLog(id, dayKey); return } // toggle off
        if (h.cadence != "daily") {
            val (kind, key) = HabitCalculator.periodMark(m, dayKey)
            if (kind == MarkKind.DONE && key != dayKey) return
            if (!HabitCalculator.isAnchorDay(m, dayKey)) return
            forEachDayInPeriod(m, dayKey) { habitMarkDao.removeRespiro(id, it) }
        } else {
            habitMarkDao.removeRespiro(id, dayKey)
        }
        habitMarkDao.addLog(HabitLogEntity(habitId = id, dayKey = dayKey))
    }

    /** Mark a day (or its period) as an honest respiro; clears any completion. */
    suspend fun markRespiro(id: String, dayKey: String, reason: String = "", todayKey: String = DateUtils.todayKey()) {
        val h = habitDao.getById(id) ?: return
        val m = modelOf(h)
        if (!HabitCalculator.isActiveOn(m, dayKey) || dayKey > todayKey) return
        if (h.cadence != "daily") {
            if (HabitCalculator.periodMark(m, dayKey).first == MarkKind.DONE) return
            if (!HabitCalculator.isAnchorDay(m, dayKey)) return
            forEachDayInPeriod(m, dayKey) { habitMarkDao.removeRespiro(id, it); habitMarkDao.removeLog(id, it) }
        } else {
            habitMarkDao.removeLog(id, dayKey)
        }
        habitMarkDao.upsertRespiro(HabitRespiroEntity(habitId = id, dayKey = dayKey, reason = reason.trim(), at = System.currentTimeMillis()))
    }

    suspend fun unmarkRespiro(id: String, dayKey: String) = habitMarkDao.removeRespiro(id, dayKey)

    /** Set a countable habit's daily tally; syncs the binary log (done at target). */
    suspend fun setHabitCount(id: String, dayKey: String, n: Int, todayKey: String = DateUtils.todayKey()) {
        val h = habitDao.getById(id) ?: return
        val m = modelOf(h)
        if (!HabitCalculator.isActiveOn(m, dayKey) || dayKey > todayKey) return
        val target = h.target ?: 1
        val count = maxOf(0, n)
        if (count <= 0) {
            habitMarkDao.removeCount(id, dayKey); habitMarkDao.removeLog(id, dayKey)
        } else {
            habitMarkDao.upsertCount(HabitCountEntity(habitId = id, dayKey = dayKey, count = count))
            if (count >= target) { habitMarkDao.addLog(HabitLogEntity(id, dayKey)); habitMarkDao.removeRespiro(id, dayKey) }
            else habitMarkDao.removeLog(id, dayKey)
        }
    }

    private suspend fun forEachDayInPeriod(m: HabitModel, dayKey: String, action: suspend (String) -> Unit) {
        val (start, end) = HabitCalculator.periodRange(m, dayKey)
        var k = start
        while (k <= end) { action(k); k = DateUtils.addDays(k, 1) }
    }

    private fun isHexColor(s: String): Boolean =
        Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$").matches(s.trim())

    // ── Objetivos (quarterly goals) ───────────────────────────
    fun goals(): Flow<List<GoalEntity>> = goalDao.observeGoals()
    fun milestones(): Flow<List<MilestoneEntity>> = goalDao.observeMilestones()

    suspend fun addGoal(text: String, quarter: String): String? {
        val t = text.trim(); if (t.isEmpty()) return null
        val id = newId("g_")
        goalDao.upsertGoal(
            GoalEntity(id = id, text = t, done = false, quarter = quarter, habitId = null,
                createdAt = System.currentTimeMillis(), position = goalDao.getAllGoals().count { it.quarter == quarter }),
        )
        return id
    }

    suspend fun toggleGoal(id: String) =
        goalDao.getAllGoals().firstOrNull { it.id == id }?.let { goalDao.upsertGoal(it.copy(done = !it.done)) } ?: Unit

    suspend fun setGoalText(id: String, text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        goalDao.getAllGoals().firstOrNull { it.id == id }?.let { goalDao.upsertGoal(it.copy(text = t)) }
    }

    suspend fun removeGoal(id: String) {
        goalDao.deleteMilestonesForGoal(id)
        goalDao.deleteGoalById(id)
    }

    suspend fun addMilestone(goalId: String, text: String): String? {
        val t = text.trim(); if (t.isEmpty()) return null
        val id = newId("m_")
        goalDao.upsertMilestone(
            MilestoneEntity(id = id, goalId = goalId, text = t, done = false,
                position = goalDao.getAllMilestones().count { it.goalId == goalId }),
        )
        return id
    }

    suspend fun toggleMilestone(id: String) =
        goalDao.getAllMilestones().firstOrNull { it.id == id }?.let { goalDao.upsertMilestone(it.copy(done = !it.done)) } ?: Unit

    suspend fun removeMilestone(id: String) = goalDao.deleteMilestoneById(id)

    // ── backup ────────────────────────────────────────────────
    /** Gather everything into a [WebBackup.Snapshot] for export. */
    suspend fun snapshot(todayKey: String): WebBackup.Snapshot = WebBackup.Snapshot(
        todayKey = todayKey,
        days = dayDao.getAll(),
        intentions = intentionDao.getAll(),
        blocks = focusBlockDao.getAll(),
        sessions = focusSessionDao.getAll(),
        habits = habitDao.getAll(),
        logs = habitMarkDao.getAllLogs(),
        respiros = habitMarkDao.getAllRespiros(),
        counts = habitMarkDao.getAllCounts(),
        goals = goalDao.getAllGoals(),
        milestones = goalDao.getAllMilestones(),
        routines = routineDao.getAllRoutines(),
        routineItems = routineDao.getAllItems(),
        plans = plannedDao.getAll(),
        prefs = prefsDao.get() ?: com.pauta.app.data.entity.PrefsEntity(),
    )

    /** The pauta.v4 backup JSON (web-compatible). */
    suspend fun exportJson(todayKey: String): String = WebBackup.export(snapshot(todayKey))

    /** Wipe all user data — equivalent to the web's resetAll(). Preferences are
     *  left intact (the user's theme/language/accent are not their data). */
    suspend fun resetAll() {
        intentionDao.clear(); dayDao.clear()
        focusSessionDao.clear(); focusBlockDao.clear()
        habitMarkDao.clearLogs(); habitMarkDao.clearRespiros(); habitMarkDao.clearCounts(); habitDao.clear()
        goalDao.clearMilestones(); goalDao.clearGoals()
        routineDao.clearItems(); routineDao.clearRoutines()
        plannedDao.clear()
    }

    /** Replace all data with the contents of a pauta.v4 backup (web or native). */
    suspend fun importJson(text: String) {
        val s = WebBackup.import(text)
        // Clear every table, then repopulate.
        intentionDao.clear(); dayDao.clear()
        focusSessionDao.clear(); focusBlockDao.clear()
        habitMarkDao.clearLogs(); habitMarkDao.clearRespiros(); habitMarkDao.clearCounts(); habitDao.clear()
        goalDao.clearMilestones(); goalDao.clearGoals()
        routineDao.clearItems(); routineDao.clearRoutines()
        plannedDao.clear()

        s.days.forEach { dayDao.upsert(it) }
        intentionDao.insertAll(s.intentions)
        s.blocks.forEach { focusBlockDao.upsert(it) }
        focusSessionDao.insertAll(s.sessions)
        s.habits.forEach { habitDao.upsert(it) }
        habitMarkDao.insertLogs(s.logs); habitMarkDao.insertRespiros(s.respiros); habitMarkDao.insertCounts(s.counts)
        goalDao.insertGoals(s.goals); goalDao.insertMilestones(s.milestones)
        routineDao.insertRoutines(s.routines); routineDao.insertItems(s.routineItems)
        plannedDao.insertAll(s.plans)
        prefsDao.upsert(s.prefs)
    }

    /** Load demo data (mirrors the web store's seed()). Prefs are preserved. */
    suspend fun reseed(todayKey: String) {
        resetAll()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun daysAgo(n: Int) = now - n * day
        fun at(daysBack: Int, h: Int, m: Int): Long {
            val base = daysAgo(daysBack)
            val midnight = base - (base % day)
            return midnight + h * 3_600_000L + m * 60_000L
        }

        // ── Habits (5) ────────────────────────────────────────────────────
        data class SeedHabit(val id: String, val name: String, val time: String, val ageDays: Int, val pct: Int, val todayDone: Boolean)
        val seedHabits = listOf(
            SeedHabit("h1", "Caminhada", "manhã", 60, 87, false),
            SeedHabit("h2", "Leitura", "antes de dormir", 60, 90, true),
            SeedHabit("h3", "Sem telemóvel após as 22h", "noite", 45, 57, false),
            SeedHabit("h4", "Beber 2L de água", "ao longo do dia", 90, 87, true),
            SeedHabit("h5", "Diário", "antes de dormir", 4, 75, false),
        )
        val rng = Random(42) // deterministic seed for reproducible demo data
        val logs = mutableListOf<HabitLogEntity>()
        seedHabits.forEachIndexed { idx, sh ->
            habitDao.upsert(HabitEntity(id = sh.id, name = sh.name, time = sh.time, createdAt = daysAgo(sh.ageDays), position = idx))
            for (i in sh.ageDays - 1 downTo 0) {
                val k = DateUtils.addDays(todayKey, -i)
                if (i == 0) { if (sh.todayDone) logs += HabitLogEntity(sh.id, k) }
                else if (rng.nextInt(100) < sh.pct) logs += HabitLogEntity(sh.id, k)
            }
        }
        habitMarkDao.insertLogs(logs)

        // ── Today's intentions ────────────────────────────────────────────
        intentionDao.insertAll(listOf(
            IntentionEntity("i1", todayKey, "Estudar 45 min para o teste", false, priority = 1, createdAt = now, position = 0),
            IntentionEntity("i2", todayKey, "Tratar das compras da semana", false, priority = 2, createdAt = now, position = 1),
            IntentionEntity("i3", todayKey, "Rever os apontamentos da semana", true, createdAt = now, position = 2),
        ))

        // ── Yesterday ─────────────────────────────────────────────────────
        val yKey = DateUtils.addDays(todayKey, -1)
        val yTime = daysAgo(1)
        intentionDao.insertAll(listOf(
            IntentionEntity("iy1", yKey, "Planear a semana", true, createdAt = yTime, position = 0),
            IntentionEntity("iy2", yKey, "Caminhada longa", true, createdAt = yTime, position = 1),
            IntentionEntity("iy3", yKey, "Ler 30 páginas", false, createdAt = yTime, position = 2),
        ))
        dayDao.upsert(DayEntity(yKey, "Dia mais leve do que parecia. A caminhada desbloqueou-me a cabeça."))

        // ── Past sparse days ──────────────────────────────────────────────
        for (i in 2..6) {
            if (i % 2 != 0) continue
            val k = DateUtils.addDays(todayKey, -i)
            val t = daysAgo(i)
            intentionDao.insertAll(listOf(
                IntentionEntity("ix${i}a", k, if (i == 4) "Preparar a apresentação" else "Rever a matéria", true, createdAt = t, position = 0),
                IntentionEntity("ix${i}b", k, "Estudar durante 45 minutos", i == 2, createdAt = t, position = 1),
            ))
            if (i == 4) dayDao.upsert(DayEntity(k, "Apresentação ficou mais clara. Falta praticar."))
        }

        // ── Focus blocks ──────────────────────────────────────────────────
        val blocks = mutableListOf<FocusBlockEntity>()
        val sessions = mutableListOf<FocusSessionEntity>()
        fun addBlock(id: String, title: String, linkedTo: String?, daysBack: Int, h: Int, m: Int, durMin: Int, status: String, reflection: String = "") {
            val s = at(daysBack, h, m)
            blocks += FocusBlockEntity(id = id, title = title, linkedToId = linkedTo, status = status, reflection = reflection, createdAt = s)
            sessions += FocusSessionEntity(blockId = id, startedAt = s, endedAt = s + durMin * 60_000L)
        }
        addBlock("b1", "Arrumar a cozinha", null, 0, 8, 15, 32, "done", "Demorou menos do que parecia.")
        addBlock("b2a", "Estudar 45 min para o teste", "i1", 0, 9, 5, 40, "paused")
        sessions += FocusSessionEntity(blockId = "b2a", startedAt = at(0, 11, 10), endedAt = at(0, 11, 55))
        addBlock("b3", "Rever os apontamentos da semana", "i3", 0, 10, 0, 30, "done", "Fiz um resumo de uma página.")
        addBlock("by1", "Planear a semana", null, 1, 9, 0, 40, "done", "Defini três coisas para a semana.")
        addBlock("by2", "Caminhada longa", null, 1, 12, 0, 55, "done", "Ouvi música e arejei a cabeça.")
        for (i in 2..13) {
            if (i % 2 == 0 || i == 5 || i == 9) {
                val dur = 25 + (i * 7) % 50
                val title = if (i % 3 == 0) "Organizar a casa" else if (i % 2 == 0) "Leitura" else "Estudo"
                addBlock("bp$i", title, null, i, 10, 0, dur, "done")
            }
        }
        blocks.forEach { focusBlockDao.upsert(it) }
        focusSessionDao.insertAll(sessions)

        // ── Goals ──────────────────────────────────────────────────────────
        val q = DateUtils.currentQuarter()
        goalDao.insertGoals(listOf(
            GoalEntity("g1", "Aprender a tocar guitarra", false, q, createdAt = daysAgo(20)),
            GoalEntity("g2", "Correr 5 km sem parar", false, q, createdAt = daysAgo(10)),
        ))
    }

    // ── PIN lock ───────────────────────────────────────────────────────────

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun generateSalt(): String {
        val bytes = ByteArray(8)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        updatePrefs { it.copy(pinHash = hash, pinSalt = salt) }
    }

    suspend fun clearPin() = updatePrefs { it.copy(pinHash = null, pinSalt = null) }

    suspend fun verifyPin(pin: String): Boolean {
        val p = prefsDao.get() ?: return false
        val salt = p.pinSalt ?: return false
        val stored = p.pinHash ?: return false
        return hashPin(pin, salt) == stored
    }

    // ── Auto-backup ────────────────────────────────────────────────────────

    suspend fun setAutoBackup(value: String) = updatePrefs { it.copy(autoBackup = value) }

    suspend fun maybeAutoBackup(filesDir: File, todayKey: String) {
        val p = prefs.value
        val cadenceMs: Long = when (p.autoBackup) {
            "30m"    -> 30 * 60_000L
            "hourly" -> 60 * 60_000L
            "daily"  -> 24 * 60 * 60_000L
            "weekly" -> 7 * 24 * 60 * 60_000L
            else     -> return
        }
        val now = System.currentTimeMillis()
        if (now - p.lastAutoBackupMs < cadenceMs) return

        val json = exportJson(todayKey)
        val dir = File(filesDir, "autobackups").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date(now))
        File(dir, "pauta-auto-$ts.json").writeText(json)

        // Keep only the last 5 auto-backups.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }

        updatePrefs { it.copy(lastAutoBackupMs = now) }
    }

    fun listAutoBackups(filesDir: File): List<File> =
        File(filesDir, "autobackups")
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
