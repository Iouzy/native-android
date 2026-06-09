package com.pauta.app.data

import com.pauta.app.data.entity.DayEntity
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.HistoryBuilder
import com.pauta.app.domain.HistoryDay
import com.pauta.app.domain.HojeLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.random.Random

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
}
