package com.pauta.app.data

import com.pauta.app.data.entity.DayEntity
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
}
