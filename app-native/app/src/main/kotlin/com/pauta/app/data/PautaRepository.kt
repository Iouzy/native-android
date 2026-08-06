package com.pauta.app.data

import android.content.Context
import android.net.Uri
import com.pauta.app.domain.BookImport
import com.pauta.app.domain.BookStatus
import com.pauta.app.service.DocumentParse
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
import com.pauta.app.data.dao.SearchHit
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.HistoryBuilder
import com.pauta.app.domain.HistoryDay
import com.pauta.app.domain.HojeLogic
import com.pauta.app.domain.MarkKind
import com.pauta.app.domain.ReaderMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlin.random.Random
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * native-only (R5): what a reading session left behind when the reader closed —
 * the line the snackbar shows, and everything "Anular" needs to take it back.
 * // PT: o recibo de uma sessão de leitura guardada, com o que anular precisa.
 */
data class ReaderSessionRecord(
    val blockId: String,
    val bookId: String,
    /** Pages gained; signed, so a session spent scrolling backwards says so. */
    val pagesDelta: Int,
    val durationMs: Long,
    /** The book's progress before this session wrote to it. */
    val previousPage: Int,
)

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
    private val searchDao = db.searchDao()
    private val bookDao = db.bookDao()
    private val bookNoteDao = db.bookNoteDao()

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

    /** Snapshot one intention before a delete, so a snackbar can undo it. */
    suspend fun getIntention(id: String): IntentionEntity? = intentionDao.getById(id)

    /** Re-insert a just-deleted intention (snackbar undo), keeping its id, day,
     *  position and done state so it returns exactly where it was. */
    suspend fun restoreIntention(intention: IntentionEntity) = intentionDao.upsert(intention)

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

    // ── search (E1) ───────────────────────────────────────────
    /**
     * Full-text search across intention text, day reflections and focus-block
     * titles/reflections (the `search_index` FTS4 table, kept in sync by triggers),
     * newest day first. Each whitespace-separated word becomes a prefix term, so
     * "guit" finds "guitarra", and every term must match (FTS implicit AND). The
     * unicode61 tokenizer strips diacritics on both the stored text and the query,
     * so it's accent-insensitive for Portuguese. User punctuation is reduced to
     * spaces first, so characters like `"`, `*`, `-`, `:` or `(` can never be read
     * as FTS operators. Blank input → no results. // PT: pesquisa FTS insensível a
     * acentos; cada palavra vira prefixo e todas têm de casar; pontuação é
     * neutralizada para não virar operador FTS.
     */
    suspend fun search(raw: String): List<SearchHit> {
        if (!AppDatabase.searchAvailable) return emptyList()
        val terms = raw
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" ") { "$it*" }
        val sql = "SELECT docId, source, dayKey, text FROM search_index " +
            "WHERE search_index MATCH ? ORDER BY dayKey DESC LIMIT 300"
        return searchDao.search(SimpleSQLiteQuery(sql, arrayOf<Any?>(match)))
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

    /**
     * F2 · correct one session's span. The end never precedes the start, and an
     * open (running) session keeps its null end rather than being closed by an
     * edit — closing a live session is `concludeActive`'s job, not this one.
     * // PT: corrigir o início e o fim de uma sessão; o fim nunca antes do início.
     */
    suspend fun setSessionTimes(rowId: Long, startedAt: Long, endedAt: Long?) {
        val s = focusSessionDao.getAll().firstOrNull { it.rowId == rowId } ?: return
        if (s.endedAt == null) return
        val end = (endedAt ?: s.endedAt).coerceAtLeast(startedAt)
        focusSessionDao.update(s.copy(startedAt = startedAt, endedAt = end))
    }

    /**
     * F2 · remove one span. The block stays: a block whose spans are all gone is
     * a block with no time in it, which is a thing the user can then delete on
     * purpose — deleting it for them would take the reflection and the title with
     * it. // PT: apaga uma sessão; o bloco fica, com as suas notas.
     */
    suspend fun deleteSession(rowId: Long) = focusSessionDao.deleteByRowId(rowId)

    /**
     * F2 · a reading session's own page span (R5's `pagesDelta`). Correcting the
     * time without this leaves the pace exactly as wrong as it was, because
     * `BookMath` reads the delta and not the clock. Null means "nobody counted",
     * which stays distinct from 0. // PT: o delta de páginas de uma sessão de
     * leitura; null = ninguém contou, que não é zero.
     */
    suspend fun setBlockPagesDelta(id: String, pagesDelta: Int?) {
        focusBlockDao.getById(id)?.let { focusBlockDao.upsert(it.copy(pagesDelta = pagesDelta)) }
    }

    suspend fun setBlockReflection(id: String, text: String) {
        focusBlockDao.getById(id)?.let { focusBlockDao.upsert(it.copy(reflection = text.trim())) }
    }

    suspend fun deleteBlock(id: String) {
        focusSessionDao.deleteForBlock(id)
        focusBlockDao.deleteById(id)
    }

    /** Snapshot a block and its sessions before a delete, so a snackbar can
     *  restore the whole thing (the block delete cascades its sessions). */
    suspend fun blockWithSessions(id: String): Pair<FocusBlockEntity, List<FocusSessionEntity>>? {
        val block = focusBlockDao.getById(id) ?: return null
        return block to focusSessionDao.getForBlock(id)
    }

    /** Re-insert a deleted block and its sessions (snackbar undo). Session rowIds
     *  reset to 0 so Room re-issues them — the originals were freed by the delete,
     *  and sessions are addressed by blockId + position, not rowId. */
    suspend fun restoreBlock(block: FocusBlockEntity, sessions: List<FocusSessionEntity>) {
        focusBlockDao.upsert(block)
        focusSessionDao.insertAll(sessions.map { it.copy(rowId = 0) })
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
    /** Active (non-archived) tides — the grid, today-strip and widget all read
     *  this, so archiving a tide hides it everywhere at once. */
    fun habits(): Flow<List<HabitEntity>> = habitDao.observeActive()

    /** Archived tides — the Settings restore list. */
    fun archivedHabits(): Flow<List<HabitEntity>> = habitDao.observeArchived()
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

    /** Archive (or restore) a tide — a non-destructive alternative to delete:
     *  it leaves the grid + today-strip but every log/respiro/count is kept.
     *  // PT: arquivar/restaurar — esconde a maré sem apagar nada. */
    suspend fun setHabitArchived(id: String, archived: Boolean) {
        habitDao.getById(id)?.let { habitDao.upsert(it.copy(archived = archived)) }
    }

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
        // F4: the ceiling, in the one place all four call sites pass through —
        // the two screens, the notification action and the widget. Each of them
        // asks for `current + 1` and none of them needs to know the rule.
        // // PT: o tecto vive aqui, onde os quatro sítios passam.
        val count = HabitCalculator.cycleCount(n, h.target)
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

    // ── Rotinas (modelos de intenções) ────────────────────────
    // A routine is a reusable template of intentions; its items carry only the
    // planning fields (text + optional priority/targetMin). Applying one seeds
    // today with fresh intentions. These power D1's manager; the v4 backup shape
    // already round-trips them. // PT: rotina = modelo de intenções; aplicar
    // semeia o dia. O backup v4 já as suporta.
    fun routines(): Flow<List<RoutineEntity>> = routineDao.observeRoutines()
    fun routineItems(): Flow<List<RoutineItemEntity>> = routineDao.observeItems()

    /** Create an empty routine; returns its id, or null for a blank name. */
    suspend fun addRoutine(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val id = newId("r_")
        routineDao.upsertRoutine(RoutineEntity(id = id, name = n, position = routineDao.getAllRoutines().size))
        return id
    }

    suspend fun renameRoutine(id: String, name: String) {
        val n = name.trim(); if (n.isEmpty()) return
        routineDao.getRoutineById(id)?.let { routineDao.upsertRoutine(it.copy(name = n)) }
    }

    /** Delete a routine and its items. A routine is a re-creatable template, so
     *  the manager guards this behind a two-step confirm (no snackbar-undo). */
    suspend fun deleteRoutine(id: String) {
        routineDao.deleteItemsForRoutine(id)
        routineDao.deleteRoutineById(id)
    }

    suspend fun addRoutineItem(routineId: String, text: String, priority: Int? = null, targetMin: Int? = null) {
        val t = text.trim(); if (t.isEmpty()) return
        routineDao.insertItems(
            listOf(
                RoutineItemEntity(
                    routineId = routineId, text = t,
                    priority = priority?.takeIf { it in 1..3 },
                    targetMin = targetMin?.takeIf { it > 0 },
                    position = routineDao.getItemsForRoutine(routineId).size,
                ),
            ),
        )
    }

    /** Edit one item's planning fields; text is kept non-blank (a blank edit
     *  keeps the previous text rather than emptying the item). */
    suspend fun updateRoutineItem(rowId: Long, text: String, priority: Int?, targetMin: Int?) {
        val item = routineDao.getItemByRowId(rowId) ?: return
        routineDao.updateItem(
            item.copy(
                text = text.trim().ifEmpty { item.text },
                priority = priority?.takeIf { it in 1..3 },
                targetMin = targetMin?.takeIf { it > 0 },
            ),
        )
    }

    suspend fun removeRoutineItem(rowId: Long) = routineDao.deleteItemByRowId(rowId)

    /** Persist a new item order by rewriting positions in the given rowId order. */
    suspend fun reorderRoutineItems(routineId: String, orderedRowIds: List<Long>) {
        val byId = routineDao.getItemsForRoutine(routineId).associateBy { it.rowId }
        orderedRowIds.forEachIndexed { index, rid ->
            byId[rid]?.let { routineDao.updateItem(it.copy(position = index)) }
        }
    }

    /** Seed today's intentions from a routine: its items become fresh intentions
     *  (preserving priority + targetMin), appended after any existing ones — the
     *  very path carry-over uses, exactly like the web's applyRoutine. // PT:
     *  semeia o dia com intenções novas a partir da rotina. */
    suspend fun applyRoutine(todayKey: String, routineId: String) {
        val items = routineDao.getItemsForRoutine(routineId)
        if (items.isEmpty()) return
        carryOver(
            todayKey,
            items.map {
                IntentionEntity(
                    id = "", dayKey = todayKey, text = it.text,
                    priority = it.priority, targetMin = it.targetMin, createdAt = 0L, position = it.position,
                )
            },
        )
    }

    /** Save today's current intentions as a new named routine (planning fields
     *  only) — the web's saveRoutineFromToday. Returns the new id, or null when
     *  the name is blank or there's nothing to save. */
    suspend fun saveRoutineFromToday(name: String, todayKey: String): String? {
        val n = name.trim(); if (n.isEmpty()) return null
        val intentions = intentionDao.getForDay(todayKey)
        if (intentions.isEmpty()) return null
        val id = addRoutine(n) ?: return null
        routineDao.insertItems(
            intentions.mapIndexed { i, it ->
                RoutineItemEntity(routineId = id, text = it.text, priority = it.priority, targetMin = it.targetMin, position = i)
            },
        )
        return id
    }

    // ── Modo livro: estante + notas (K2) ──────────────────────
    // native-only: book mode is a device-local lens, so none of this touches the
    // pauta.v4 export. Reading sessions reuse the focus-block tables (a block with
    // project = "book:<id>"); these methods cover only books + their notes.
    // // PT: estante e notas do modo livro — dados locais, fora do v4.
    fun booksReading(): Flow<List<BookEntity>> = bookDao.observeByStatus(BookStatus.READING)
    fun booksTbr(): Flow<List<BookEntity>> = bookDao.observeByStatus(BookStatus.TBR)

    /** L3: the shelf a paused book was missing. Between "a ler agora" and "a
     *  seguir" — a book you put down is still a book you chose. // PT: a
     *  prateleira dos livros em pausa. */
    fun booksPaused(): Flow<List<BookEntity>> = bookDao.observeByStatus(BookStatus.PAUSED)

    /** Finished + abandoned shelf, newest finish first. The DAO streams a single
     *  status at a time, so we merge "done" and "dnf" and re-sort by finishedAt
     *  DESC (nulls last). // PT: lidos + abandonados, terminados mais recentes primeiro. */
    fun booksDone(): Flow<List<BookEntity>> =
        combine(bookDao.observeByStatus(BookStatus.DONE), bookDao.observeByStatus(BookStatus.DNF)) { done, dnf ->
            (done + dnf).sortedByDescending { it.finishedAt ?: Long.MIN_VALUE }
        }

    suspend fun getBook(id: String): BookEntity? = bookDao.getById(id)

    /** Add a book to the shelf; returns its new id ("bk_…"). A book added directly
     *  as "reading" stamps startedAt now (a book queued as "tbr" starts later). */
    suspend fun addBook(
        title: String,
        author: String,
        series: String,
        seriesNumber: Int?,
        format: String,
        totalPages: Int,
        genre: String,
        status: String,
        // R2: the add form allocates the id up front so a file can be attached
        // *before* the book exists (the copy has to be validated while the sheet
        // is still open). Both are null on every other path. // PT: id reservado
        // pelo formulário, para o ficheiro poder ser anexado antes de gravar.
        id: String? = null,
        file: ImportedFile? = null,
    ): String {
        val now = System.currentTimeMillis()
        val bookId = id ?: newId("bk_")
        bookDao.upsert(
            BookEntity(
                id = bookId,
                title = title.trim(),
                author = author.trim(),
                series = series.trim(),
                seriesNumber = seriesNumber,
                format = format,
                totalPages = totalPages.coerceAtLeast(0),
                currentPage = 0,
                status = status,
                startedAt = if (status == BookStatus.READING) now else null,
                finishedAt = null,
                rating = null,
                genre = genre.trim(),
                // Append after the shelf's current maximum, not its size — see
                // [setBookStatus]: a book that left the shelf took its index with
                // it. // PT: acrescenta depois do máximo, não do tamanho.
                position = (
                    bookDao.getAll()
                        .filter { it.status == status }
                        .maxOfOrNull { it.position } ?: -1
                    ) + 1,
                createdAt = now,
                filePath = file?.path,
                fileKind = file?.kind,
                fileName = file?.name ?: "",
            ),
        )
        return bookId
    }

    suspend fun updateBook(book: BookEntity) = bookDao.upsert(book)

    /**
     * Removes the book, its notes, its attached file (R2) and — F2 — its reading
     * sessions.
     *
     * The sessions used to survive. A reading session is a `FocusBlockEntity` with
     * `project = "book:<id>"`, the planner's flow filters those out, and book mode
     * looks them up through the book — so once the book was gone they were
     * reachable from nowhere and still counted in the Hábitos statistics. That is
     * an orphan the user cannot see, cannot delete and cannot stop being measured
     * by, which is precisely what GUARDRAILS §H forbids.
     *
     * Sessions go before blocks: the span rows are found through the block ids,
     * so deleting the blocks first would leave nothing to find them by.
     * // PT: apaga o livro, as notas, o ficheiro e as sessões de leitura — as
     * sessões primeiro, senão perdem-se os ids por onde se encontram.
     */
    suspend fun deleteBook(context: Context, id: String) {
        val project = "book:$id"
        focusBlockDao.getForProject(project).forEach { focusSessionDao.deleteForBlock(it.id) }
        focusBlockDao.deleteForProject(project)
        bookDao.getById(id)?.let { BookFiles.delete(context, it) }
        bookNoteDao.run { getAll().filter { it.bookId == id }.forEach { deleteById(it.id) } }
        bookDao.deleteById(id)
    }

    /** Update reading progress (page for physical/ebook, minute for audiobook);
     *  never negative. // PT: progresso de leitura — página ou minuto. */
    suspend fun updateProgress(id: String, currentPage: Int) {
        val b = bookDao.getById(id) ?: return
        bookDao.upsert(b.copy(currentPage = currentPage.coerceAtLeast(0)))
    }

    /**
     * L3: the one place a book changes shelf, so the rules of a move live
     * together instead of at each call site (which is how "Marcar como lido"
     * came to be a one-way door and how [position] came to be stale).
     *
     * - `startedAt` is stamped on the *first* move into "a ler" and never
     *   overwritten — a re-read is the same book, and the date you first opened
     *   it doesn't change.
     * - `finishedAt` belongs to `done`/`dnf` and to nothing else: moving out of
     *   them clears it, so the "Lidos" shelf never sorts on a date that no
     *   longer means anything.
     * - `position` orders within the destination shelf, so it is recomputed on
     *   arrival. [addBook] set it once at creation and nothing ever moved it,
     *   which left every shelf colliding on the same indices under
     *   `ORDER BY position`. Arrival appends after the current *maximum* rather
     *   than counting the shelf: a departure leaves a hole, so a count lands on
     *   an index somebody already holds — pause the middle of three books, then
     *   resume it, and it ties with the last one. `ORDER BY position` has no
     *   tiebreaker, so a tie is an arbitrary order.
     *
     * Notes, sessions, progress and (unless one is passed) the rating are never
     * touched: abandoning a book is a judgement about the book, not a delete.
     * // PT: a única porta por onde um livro muda de estado.
     */
    suspend fun setBookStatus(id: String, status: String, rating: Int? = null) {
        if (status !in BookStatus.ALL) return
        val b = bookDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val finished = status == BookStatus.DONE || status == BookStatus.DNF
        bookDao.upsert(
            b.copy(
                status = status,
                startedAt = if (status == BookStatus.READING) b.startedAt ?: now else b.startedAt,
                finishedAt = if (finished) b.finishedAt ?: now else null,
                rating = rating ?: b.rating,
                position = (
                    bookDao.getAll()
                        .filter { it.status == status && it.id != id }
                        .maxOfOrNull { it.position } ?: -1
                    ) + 1,
            ),
        )
    }

    /** Mark a book finished now, optionally recording a rating (kept if null). */
    suspend fun finishBook(id: String, rating: Int?) = setBookStatus(id, BookStatus.DONE, rating)

    // ── Ficheiros anexados (R2) ───────────────────────────────
    /**
     * Copies a picked document into private storage and, when the book already
     * exists, points its row at it. The form's add path calls this with an id
     * that has no row yet — the columns then travel into [addBook] instead.
     *
     * A PDF is only kept if it actually opens in the `:reader` process; that both
     * gives us the page count and makes "starts with %PDF-, isn't a PDF" fail
     * closed. `totalPages` is filled from that count **only** when it is still 0
     * — a number the user typed is never overwritten. // PT: copia o ficheiro,
     * confirma que abre no processo :reader e preenche o total de páginas só se
     * ainda estiver a zero.
     */
    suspend fun attachFile(context: Context, bookId: String, uri: Uri): AttachResult {
        val imported = try {
            BookFiles.importFrom(context, uri, bookId)
        } catch (e: BookImport.RejectedException) {
            return when (e.reason) {
                BookImport.Rejection.UNSUPPORTED -> AttachResult.UnsupportedType
                else -> AttachResult.Rejected(e.reason)
            }
        } catch (_: Exception) {
            return AttachResult.CopyFailed
        }
        if (imported == null) return AttachResult.CopyFailed

        var pages = 0
        if (imported.kind == "pdf") {
            pages = DocumentParse.pdfPageCount(context, imported.path)
            if (pages <= 0) {
                BookFiles.deleteAt(context, imported.path)
                return AttachResult.Rejected(BookImport.Rejection.CORRUPT)
            }
        }

        bookDao.getById(bookId)?.let { b ->
            bookDao.upsert(
                b.copy(
                    filePath = imported.path,
                    fileKind = imported.kind,
                    fileName = imported.name,
                    totalPages = if (b.totalPages == 0 && pages > 0) pages else b.totalPages,
                ),
            )
        }
        return AttachResult.Ok(imported.kind, pages, imported)
    }

    /** Forgets the attached file and deletes it. The book stays. */
    suspend fun detachFile(context: Context, bookId: String) {
        val b = bookDao.getById(bookId)
        if (b != null) {
            BookFiles.delete(context, b)
            bookDao.upsert(b.copy(filePath = null, fileKind = null, fileName = "", readPosition = ""))
        } else {
            // A staged file for a book that was never saved. // PT: ficheiro
            // preparado para um livro que não chegou a existir.
            for (ext in listOf("pdf", "epub")) BookFiles.fileFor(context, bookId, ext).delete()
        }
    }

    /** The reader's bookmark — page index (pdf) or "spine:percent" (epub). R5 puts
     *  it under the reader's lock: this and [endReaderSession] both read-modify-write
     *  the same book row, and the debounced bookmark landing after the close would
     *  undo the progress the close just wrote. // PT: sob a mesma tranca do fecho,
     *  senão o marcador atrasado apagava o progresso. */
    suspend fun setReadPosition(bookId: String, position: String) = readerSessionLock.withLock {
        val b = bookDao.getById(bookId)
        if (b != null) bookDao.upsert(b.copy(readPosition = position))
    }

    // ── O leitor e a sessão (R5) ──────────────────────────────
    // Opening the reader *is* starting to read, so the reader drives the same
    // FocusBlockEntity the Sessão tab does (project "book:<id>") — the timer, the
    // notification and the history all keep working, and nobody types a page
    // number. Both ends run under one lock: they are launched independently by the
    // UI, and a close that overtook its own open would leave a reading session
    // running with nothing on screen. // PT: abrir o leitor é começar a ler; as
    // duas pontas correm sob a mesma tranca para não se ultrapassarem.
    private val readerSessionLock = Mutex()

    /**
     * Starts the reading session for [bookId], or joins the one already running for
     * that book rather than opening a second. Returns the block id, or null when
     * there is nothing to start (a book with no title, which cannot happen from the
     * shelf). // PT: começa — ou entra n' — a sessão de leitura deste livro.
     */
    suspend fun beginReaderSession(bookId: String, title: String): String? =
        readerSessionLock.withLock {
            val project = "book:$bookId"
            val running = focusBlockDao.getAll().firstOrNull { it.status == "active" && it.project == project }
            running?.id ?: startBlock(title = title, project = project)
        }

    /**
     * Closes the reader's session: the bookmark always moves, and a session worth
     * keeping ([ReaderMath.sessionOutcome]) is concluded with its own page span
     * while the book's progress follows the reader — never asked for.
     *
     * A peek leaves nothing behind: the block is deleted and the progress is left
     * exactly as it was, which matters because a hand-typed page can be far ahead
     * of a bookmark someone opened to check a quote.
     *
     * Returns what to tell the user (and what it would take to undo), or null when
     * there was no session to conclude. // PT: fecha a sessão do leitor — marcador
     * sempre, sessão e progresso só se valer a pena.
     */
    suspend fun endReaderSession(
        bookId: String,
        startPage: Int,
        endPage: Int,
        position: String,
    ): ReaderSessionRecord? = readerSessionLock.withLock {
        val now = System.currentTimeMillis()
        val project = "book:$bookId"
        val block = focusBlockDao.getAll().firstOrNull { it.status == "active" && it.project == project }
        val durationMs = if (block == null) 0L else {
            endOpenSession(block.id, now)
            FocusMath.blockElapsedMs(
                focusSessionDao.getForBlock(block.id).map { FocusMath.FocusSeg(it.startedAt, it.endedAt) },
                now,
            )
        }
        val outcome = ReaderMath.sessionOutcome(durationMs, startPage, endPage)

        val book = bookDao.getById(bookId)
        if (book != null) {
            bookDao.upsert(
                book.copy(
                    readPosition = position,
                    currentPage = if (outcome.save) outcome.page.coerceAtLeast(0) else book.currentPage,
                ),
            )
        }

        if (block == null) return@withLock null
        if (!outcome.save) {
            deleteBlock(block.id)
            return@withLock null
        }
        focusBlockDao.upsert(block.copy(status = "done", pagesDelta = outcome.pagesDelta))
        ReaderSessionRecord(
            blockId = block.id,
            bookId = bookId,
            pagesDelta = outcome.pagesDelta,
            durationMs = durationMs,
            previousPage = book?.currentPage ?: startPage,
        )
    }

    /**
     * Takes back a session the reader just saved: the block goes, and the book's
     * progress returns to where it was. The bookmark deliberately stays — "don't
     * record this" is not "I was never there", and the next open should still land
     * on the page they reached. // PT: anula a sessão guardada; o marcador fica.
     */
    suspend fun undoReaderSession(record: ReaderSessionRecord) {
        readerSessionLock.withLock {
            deleteBlock(record.blockId)
            val book = bookDao.getById(record.bookId)
            if (book != null) bookDao.upsert(book.copy(currentPage = record.previousPage))
        }
    }

    /** Total words in the attached book (real for EPUB, estimated elsewhere). */
    suspend fun setWordCount(bookId: String, words: Int) {
        val b = bookDao.getById(bookId) ?: return
        bookDao.upsert(b.copy(wordCount = words.coerceAtLeast(0)))
    }

    /** Count of books finished on/after the given epoch ms — the K7 annual goal. */
    suspend fun booksFinishedThisYear(yearStartMs: Long): Int = bookDao.countFinishedSince(yearStartMs)

    fun notesForBook(bookId: String): Flow<List<BookNoteEntity>> = bookNoteDao.observeForBook(bookId)

    /** Capture a quote / annotation / thought against a book; returns the new id. */
    suspend fun addNote(bookId: String, kind: String, text: String, page: Int?): String {
        val id = newId("bn_")
        bookNoteDao.insert(
            BookNoteEntity(
                id = id,
                bookId = bookId,
                kind = kind,
                text = text.trim(),
                page = page,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun deleteNote(id: String) = bookNoteDao.deleteById(id)

    // ── backup ────────────────────────────────────────────────
    /**
     * Gather the planner's data into a [WebBackup.Snapshot] for export.
     *
     * L2: **book data does not go in here.** A reading session is a
     * [FocusBlockEntity] whose title is the book's, so handing every block to the
     * exporter put a list of everything its owner had been reading into a file
     * meant to be shared. Deciding what is planner data is this layer's job —
     * [WebBackup] is only the format, and still round-trips whatever it is given.
     * // PT: o v4 leva só dados do planeador; os blocos de leitura ficam de fora.
     */
    suspend fun snapshot(todayKey: String): WebBackup.Snapshot {
        val (blocks, sessions) =
            BookBackup.plannerOnly(focusBlockDao.getAll(), focusSessionDao.getAll())
        return WebBackup.Snapshot(
            todayKey = todayKey,
            days = dayDao.getAll(),
            intentions = intentionDao.getAll(),
            blocks = blocks,
            sessions = sessions,
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
    }

    /** The pauta.v4 backup JSON (web-compatible). */
    suspend fun exportJson(todayKey: String): String = WebBackup.export(snapshot(todayKey))

    // ── the library's own backup (L2) ─────────────────────────
    // A separate file in a separate format, because book data is device-local and
    // must never enter pauta.v4. "Offline-first" cannot also mean
    // "unrecoverable": without this, a reinstall lost the shelf, the ratings, the
    // notes and the reading history permanently. // PT: ficheiro à parte — o v4
    // não leva livros, mas a biblioteca também não pode ficar sem salvação.

    /** Everything book-shaped, ready for [BookBackup.export]. */
    suspend fun bookLibrary(): BookBackup.Library {
        val (blocks, sessions) = BookBackup.booksOnly(focusBlockDao.getAll(), focusSessionDao.getAll())
        return BookBackup.Library(
            books = bookDao.getAll(),
            notes = bookNoteDao.getAll(),
            blocks = blocks,
            sessions = sessions,
        )
    }

    /** The `pauta.books.v1` library JSON. */
    suspend fun exportBooksJson(): String = BookBackup.export(bookLibrary())

    /**
     * Merge a library file into the shelf and return how many books it brought.
     *
     * **Merges, never wipes.** This is a rescue path, not a sync protocol: a
     * wipe-on-import is how someone loses the shelf they were trying to protect,
     * so rows are upserted by id and anything already here stays. Re-importing
     * the same file twice is a no-op rather than a doubling — a block's segments
     * are replaced rather than appended, since they carry no stable id of their
     * own.
     *
     * Throws [BookBackup.NotALibraryException] when the file isn't ours, having
     * changed nothing. // PT: junta à biblioteca (nunca apaga) e devolve quantos
     * livros trouxe; recusa um ficheiro que não seja nosso sem tocar em nada.
     */
    suspend fun importBooksJson(text: String): Int {
        val lib = BookBackup.import(text)
        lib.books.forEach { bookDao.upsert(it) }
        lib.notes.forEach { bookNoteDao.upsert(it) }
        val segsByBlock = lib.sessions.groupBy { it.blockId }
        lib.blocks.forEach { block ->
            focusBlockDao.upsert(block)
            focusSessionDao.deleteForBlock(block.id)
            // rowId is autogenerated, so the copies must go in fresh.
            // // PT: o rowId é gerado, as cópias entram de novo.
            focusSessionDao.insertAll(segsByBlock[block.id].orEmpty().map { it.copy(rowId = 0) })
        }
        return lib.books.size
    }

    /** Wipe all user data — equivalent to the web's resetAll(). Preferences are
     *  left intact (the user's theme/language/accent are not their data). Book
     *  mode's library and its attached files are data too (L1): a row-only wipe
     *  would leave the documents, which is the part a user would most mind. */
    suspend fun resetAll(context: Context) {
        intentionDao.clear(); dayDao.clear()
        focusSessionDao.clear(); focusBlockDao.clear()
        habitMarkDao.clearLogs(); habitMarkDao.clearRespiros(); habitMarkDao.clearCounts(); habitDao.clear()
        goalDao.clearMilestones(); goalDao.clearGoals()
        routineDao.clearItems(); routineDao.clearRoutines()
        plannedDao.clear()
        bookNoteDao.clear(); bookDao.clear()
        BookFiles.clearAll(context)
    }

    /**
     * Replace all planner data with the contents of a pauta.v4 backup (web or
     * native).
     *
     * L2: the focus tables are cleared **planner-only**, and the incoming blocks
     * are filtered the same way — the rule holds in both directions.
     *
     * Clearing them outright deleted every reading session on the device
     * whenever a planner backup was restored: data that isn't in the file being
     * restored, and so had nothing to come back from. Sessions go first, while
     * the blocks they hang off still exist to be selected.
     *
     * Filtering the *incoming* side matters because every v4 file written before
     * this fix carries book blocks. Letting them back in would resurrect reading
     * sessions for books that may no longer exist and — since v4 has no field for
     * it — overwrite a live session's measured `pagesDelta` with null.
     * // PT: restaurar um backup do planeador não apaga as sessões de leitura, e
     * também não deixa entrar as que ficheiros antigos ainda trazem.
     */
    suspend fun importJson(text: String) {
        val s = WebBackup.import(text)
        val (blocks, sessions) = BookBackup.plannerOnly(s.blocks, s.sessions)
        // Clear the planner's tables, then repopulate. The library is not in this
        // file and is not touched. // PT: limpa o planeador; a biblioteca fica.
        intentionDao.clear(); dayDao.clear()
        focusSessionDao.clearPlanner(); focusBlockDao.clearPlanner()
        habitMarkDao.clearLogs(); habitMarkDao.clearRespiros(); habitMarkDao.clearCounts(); habitDao.clear()
        goalDao.clearMilestones(); goalDao.clearGoals()
        routineDao.clearItems(); routineDao.clearRoutines()
        plannedDao.clear()

        s.days.forEach { dayDao.upsert(it) }
        intentionDao.insertAll(s.intentions)
        blocks.forEach { focusBlockDao.upsert(it) }
        focusSessionDao.insertAll(sessions)
        s.habits.forEach { habitDao.upsert(it) }
        habitMarkDao.insertLogs(s.logs); habitMarkDao.insertRespiros(s.respiros); habitMarkDao.insertCounts(s.counts)
        goalDao.insertGoals(s.goals); goalDao.insertMilestones(s.milestones)
        routineDao.insertRoutines(s.routines); routineDao.insertItems(s.routineItems)
        plannedDao.insertAll(s.plans)
        prefsDao.upsert(s.prefs)
    }

    /** Load demo data (mirrors the web store's seed()). Prefs are preserved. */
    suspend fun reseed(context: Context, todayKey: String) {
        resetAll(context)
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

    // Clearing the PIN also drops the C3 biometric opt-in — biometric unlock is
    // only ever offered on top of a PIN, so it shouldn't linger once there's none.
    // // PT: tirar o PIN também desliga a biometria (só existe sobre um PIN).
    suspend fun clearPin() = updatePrefs { it.copy(pinHash = null, pinSalt = null, biometricEnabled = false) }

    suspend fun verifyPin(pin: String): Boolean {
        val p = prefsDao.get() ?: return false
        val salt = p.pinSalt ?: return false
        val stored = p.pinHash ?: return false
        return hashPin(pin, salt) == stored
    }

    // ── Auto-backup ────────────────────────────────────────────────────────

    suspend fun setAutoBackup(value: String) = updatePrefs { it.copy(autoBackup = value) }

    // B1: the user-chosen SAF folder the cadence also writes to (persisted tree
    // URI string), or null to clear it back to filesDir-only. // PT: pasta SAF.
    suspend fun setBackupFolderUri(uri: String?) = updatePrefs { it.copy(backupFolderUri = uri) }

    /**
     * Run an auto-backup if the chosen cadence is due. Always keeps the rolling
     * filesDir copies as a fallback, and — when a SAF folder is configured —
     * hands the same JSON to [writeToFolder] so it also lands in the user's
     * folder (which survives uninstall). The Android/SAF side lives in the
     * caller so the repo stays Context-free; cadence + pruning + the timestamp
     * stay here, the single source of truth, whether the trigger is the app
     * resuming or the WorkManager job firing with the app closed. // PT: corre a
     * cópia se for altura; guarda sempre cópia local e, se houver pasta SAF,
     * entrega o mesmo JSON ao [writeToFolder].
     */
    suspend fun maybeAutoBackup(
        filesDir: File,
        todayKey: String,
        writeToFolder: ((folderUri: String, fileName: String, json: String) -> Unit)? = null,
    ) {
        val p = prefsDao.get() ?: return
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
        val ts = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date(now))
        val fileName = "pauta-auto-$ts.json"

        val dir = File(filesDir, "autobackups").apply { mkdirs() }
        File(dir, fileName).writeText(json)

        // Keep only the last 5 auto-backups.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }

        // Mirror the same copy into the user's SAF folder, if one is set.
        p.backupFolderUri?.let { writeToFolder?.invoke(it, fileName, json) }

        updatePrefs { it.copy(lastAutoBackupMs = now) }
    }

    fun listAutoBackups(filesDir: File): List<File> =
        File(filesDir, "autobackups")
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
