package com.pauta.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pauta.app.PautaApplication
import com.pauta.app.data.entity.FocusBlockEntity
import com.pauta.app.data.entity.FocusSessionEntity
import com.pauta.app.data.entity.HabitCountEntity
import com.pauta.app.data.entity.HabitEntity
import com.pauta.app.data.entity.HabitLogEntity
import com.pauta.app.data.entity.HabitRespiroEntity
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.domain.HabitModel
import com.pauta.app.domain.HistoryDay
import com.pauta.app.i18n.trf
import com.pauta.app.service.FocusServiceController
import com.pauta.app.service.ReminderScheduler
import com.pauta.app.service.WidgetSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide ViewModel. For now it owns the live preferences (theme, accent,
 * language, …) the way the web store does; the tab-specific state (intentions,
 * focus blocks, habits) is exposed here as each tab lands. As an
 * [AndroidViewModel] it can be created by the default factory — no boilerplate.
 * // PT: ViewModel da app — por agora dono das preferências; o estado das tabs
 * entra à medida que cada uma é construída.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as PautaApplication).repository

    val prefs: StateFlow<PrefsEntity> =
        repo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, PrefsEntity())

    // ── Hoje ──────────────────────────────────────────────────
    // Today's key, computed once on creation. (Live midnight rollover lands with
    // the week planner in a later increment.) // PT: chave de hoje.
    val todayKey: String = DateUtils.todayKey()

    val intentions: StateFlow<List<IntentionEntity>> =
        repo.intentions(todayKey).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reflection: StateFlow<String> =
        repo.dayReflection(todayKey).stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Unfinished intentions from the most recent past day, offered as a one-tap
     *  carry-over (null = nothing to bring forward). */
    val carry: StateFlow<CarrySource?> =
        repo.carrySource(todayKey).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Read-only history of past days with content, newest first. */
    val history: StateFlow<List<HistoryDay>> =
        repo.history(todayKey).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Pauta ─────────────────────────────────────────────────
    val blocks: StateFlow<List<FocusBlockEntity>> =
        repo.blocks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeBlock: StateFlow<FocusBlockEntity?> =
        repo.activeBlock().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sessions of the running block (for the live timer). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessions: StateFlow<List<FocusSessionEntity>> =
        repo.activeBlock()
            .flatMapLatest { b -> if (b == null) flowOf(emptyList()) else repo.sessions(b.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every session, for per-block totals + daily focus. */
    val allSessions: StateFlow<List<FocusSessionEntity>> =
        repo.allSessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun startBlock(title: String, linkedToId: String? = null, project: String? = null, targetMin: Int? = null) =
        viewModelScope.launch { repo.startBlock(title, linkedToId, project, targetMin) }

    fun pauseActive(note: String = "") = viewModelScope.launch { repo.pauseActive(note) }
    fun resumeBlock(id: String) = viewModelScope.launch { repo.resumeBlock(id) }
    fun concludeActive(reflection: String, markIntentionDone: Boolean = false) =
        viewModelScope.launch { repo.concludeActive(reflection, markIntentionDone) }
    fun concludeBlock(id: String, reflection: String, markIntentionDone: Boolean = false) =
        viewModelScope.launch { repo.concludeBlock(id, reflection, markIntentionDone) }
    fun deleteBlock(id: String) = viewModelScope.launch { repo.deleteBlock(id) }
    fun setBlockReflection(id: String, text: String) = viewModelScope.launch { repo.setBlockReflection(id, text) }
    fun blockSessions(id: String) = repo.sessions(id)

    // ── Marés ─────────────────────────────────────────────────
    val habits: StateFlow<List<HabitEntity>> =
        repo.habits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habitLogs: StateFlow<List<HabitLogEntity>> =
        repo.habitLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habitRespiros: StateFlow<List<HabitRespiroEntity>> =
        repo.habitRespiros().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val habitCounts: StateFlow<List<HabitCountEntity>> =
        repo.habitCounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addHabit(
        name: String, cadence: String = "daily", anchor: Int? = null, weekdays: List<Int> = emptyList(),
        target: Int? = null, unit: String = "", clock: String = "", color: String? = null,
        recurrence: String = "forever", endsAt: Long? = null, time: String = "", description: String = "",
    ) = viewModelScope.launch {
        repo.addHabit(name, time, cadence, anchor, weekdays, target, unit, clock, color, recurrence, endsAt, description)
    }

    fun updateHabit(habit: HabitEntity) = viewModelScope.launch { repo.updateHabit(habit) }
    fun removeHabit(id: String) = viewModelScope.launch { repo.removeHabit(id) }
    fun reorderHabits(orderedIds: List<String>) = viewModelScope.launch { repo.reorderHabits(orderedIds) }
    fun toggleHabitDay(id: String, dayKey: String) = viewModelScope.launch { repo.toggleHabitDay(id, dayKey, todayKey) }
    fun toggleHabitToday(id: String) = viewModelScope.launch { repo.toggleHabitDay(id, todayKey, todayKey) }
    fun markRespiro(id: String, dayKey: String, reason: String = "") =
        viewModelScope.launch { repo.markRespiro(id, dayKey, reason, todayKey) }
    fun unmarkRespiro(id: String, dayKey: String) = viewModelScope.launch { repo.unmarkRespiro(id, dayKey) }
    fun setHabitCount(id: String, dayKey: String, n: Int) =
        viewModelScope.launch { repo.setHabitCount(id, dayKey, n, todayKey) }

    init {
        viewModelScope.launch { repo.ensurePrefs() }
        // Promote any week-ahead plan for today and clear stale plans on launch.
        viewModelScope.launch { repo.runRollover(todayKey) }
        // Keep the foreground focus-timer notification in sync with the running
        // block: start it (with the block's accumulated elapsed) when one is
        // active, tear it down when none is. Emits only on block lifecycle changes
        // (DB writes), not every second. // PT: liga/desliga a notificação de foco
        // conforme há ou não um bloco activo.
        viewModelScope.launch {
            combine(repo.activeBlock(), repo.allSessions()) { b, sessions -> b to sessions }
                .collect { (block, sessions) ->
                    val ctx = getApplication<Application>()
                    if (block == null) {
                        FocusServiceController.stop(ctx)
                    } else {
                        val segs = sessions
                            .filter { it.blockId == block.id }
                            .map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }
                        FocusServiceController.start(ctx, block.title, FocusMath.blockElapsedMs(segs, System.currentTimeMillis()))
                    }
                }
        }
        // Keep the home-screen widget's three stat lines fresh as data changes.
        // // PT: mantém as três linhas do widget atualizadas.
        viewModelScope.launch {
            combine(
                repo.intentions(todayKey), repo.allSessions(), repo.habits(), repo.habitLogs(), repo.habitRespiros(),
            ) { ints, sess, habs, logs, resps ->
                val now = System.currentTimeMillis()
                val intDone = ints.count { it.done }
                val focusMin = FocusMath.dailyFocusMs(sess.map { FocusMath.FocusSeg(it.startedAt, it.endedAt) }, todayKey, now) / 60_000L
                val logSet = logs.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
                val respSet = resps.groupBy { it.habitId }.mapValues { e -> e.value.map { it.dayKey }.toSet() }
                var mDone = 0
                var mTotal = 0
                for (h in habs) {
                    val m = HabitModel(h.id, h.createdAt, h.cadence, h.anchor, h.weekdays, h.recurrence, h.endsAt, logSet[h.id].orEmpty(), respSet[h.id].orEmpty())
                    when (HabitCalculator.dayState(m, todayKey, todayKey)) {
                        HabitCalculator.DayState.DONE, HabitCalculator.DayState.RESPIRO -> { mDone++; mTotal++ }
                        HabitCalculator.DayState.EMPTY -> mTotal++
                        else -> {}
                    }
                }
                Triple(
                    trf("{d}/{t} intenções", "d" to intDone, "t" to ints.size),
                    trf("Foco {m}m", "m" to focusMin),
                    trf("{d}/{t} marés", "d" to mDone, "t" to mTotal),
                )
            }.collect { (l1, l2, l3) -> WidgetSnapshot.publish(getApplication<Application>(), l1, l2, l3) }
        }
    }

    /** Bring the offered carry-over items into today. */
    fun carryOver() = viewModelScope.launch {
        carry.value?.let { repo.carryOver(todayKey, it.items) }
    }

    fun addIntention(text: String, priority: Int? = null, targetMin: Int? = null, timeOfDay: String? = null) =
        viewModelScope.launch { repo.addIntention(todayKey, text, priority, targetMin, timeOfDay) }

    fun toggleIntention(id: String) = viewModelScope.launch { repo.toggleIntention(id) }
    fun removeIntention(id: String) = viewModelScope.launch { repo.removeIntention(id) }
    fun setIntentionPriority(id: String, priority: Int?) =
        viewModelScope.launch { repo.setIntentionPriority(id, priority) }
    fun setIntentionText(id: String, text: String) =
        viewModelScope.launch { repo.setIntentionText(id, text) }
    fun setReflection(text: String) = viewModelScope.launch { repo.setReflection(todayKey, text) }

    fun setTheme(value: String) = update { it.copy(theme = value) }
    fun setAccent(hex: String?) = update { it.copy(accent = hex) }
    fun setLang(value: String) = update { it.copy(lang = value) }
    fun setHighContrast(value: Boolean) = update { it.copy(highContrast = value) }
    fun setReducedMotion(value: Boolean) = update { it.copy(reducedMotion = value) }
    fun setHaptics(value: Boolean) = update { it.copy(haptics = value) }
    fun setParrot(value: Boolean) = update { it.copy(parrot = value) }

    fun setRemindersEnabled(value: Boolean) = update { it.copy(remindersEnabled = value) }
    fun setPlannerTime(value: String) = update { it.copy(plannerTime = value) }
    fun setHabitsTime(value: String) = update { it.copy(habitsTime = value) }
    fun setReflectionTime(value: String) = update { it.copy(reflectionTime = value) }

    private fun update(transform: (PrefsEntity) -> PrefsEntity) {
        viewModelScope.launch { repo.updatePrefs(transform) }
    }

    init {
        // Keep the AlarmManager reminders in step with the reminder preferences
        // (and the chosen language for the notification text). // PT: mantém os
        // lembretes do AlarmManager alinhados com as preferências.
        viewModelScope.launch {
            repo.prefs
                .distinctUntilChangedBy { listOf(it.remindersEnabled, it.plannerTime, it.habitsTime, it.reflectionTime, it.lang) }
                .collect { p ->
                    val ctx = getApplication<Application>()
                    ReminderScheduler.save(ctx, p.remindersEnabled, p.plannerTime, p.habitsTime, p.reflectionTime, p.lang)
                    ReminderScheduler.reschedule(ctx)
                }
        }
    }
}
