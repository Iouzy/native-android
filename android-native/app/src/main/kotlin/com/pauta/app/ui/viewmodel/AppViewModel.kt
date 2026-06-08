package com.pauta.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pauta.app.PautaApplication
import com.pauta.app.data.PautaRepository
import com.pauta.app.data.entity.*
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HabitCalculator
import com.pauta.app.service.FocusService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repo: PautaRepository,
    private val appContext: Context
) : ViewModel() {

    // ── Today key ─────────────────────────────────────────────────────────

    private val _todayKey = MutableStateFlow(DateUtils.todayKey())
    val todayKey: StateFlow<String> = _todayKey

    /** Call on resume to detect day rollovers. */
    fun refreshDay() {
        _todayKey.value = DateUtils.todayKey()
    }

    // ── Prefs ─────────────────────────────────────────────────────────────

    val prefs: StateFlow<PrefsEntity> = repo.prefsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrefsEntity())

    fun setTheme(theme: String) = updatePrefs { it.copy(theme = theme) }
    fun setLang(lang: String) = updatePrefs { it.copy(lang = lang) }
    fun setAccent(accent: String) = updatePrefs { it.copy(accent = accent) }
    fun setHaptics(on: Boolean) = updatePrefs { it.copy(haptics = on) }
    fun setReminders(enabled: Boolean, plannerTime: String? = null, habitsTime: String? = null, reflectionTime: String? = null) {
        updatePrefs { p ->
            p.copy(
                remindersEnabled = enabled,
                remindersPlannerTime = plannerTime ?: p.remindersPlannerTime,
                remindersHabitsTime = habitsTime ?: p.remindersHabitsTime,
                remindersReflectionTime = reflectionTime ?: p.remindersReflectionTime
            )
        }
    }

    private fun updatePrefs(update: (PrefsEntity) -> PrefsEntity) {
        viewModelScope.launch {
            val current = repo.getPrefs()
            repo.upsertPrefs(update(current))
        }
    }

    // ── Intentions ────────────────────────────────────────────────────────

    val intentions: StateFlow<List<IntentionEntity>> = _todayKey
        .flatMapLatest { repo.intentionsForDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reflection: StateFlow<DayEntity?> = _todayKey
        .flatMapLatest { repo.dayFlow(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addIntention(text: String, priority: Int = 2, targetMin: Int? = null, timeOfDay: String? = null) {
        viewModelScope.launch {
            repo.addIntention(_todayKey.value, text, priority, targetMin, timeOfDay)
        }
    }

    fun toggleIntention(id: String) = viewModelScope.launch { repo.toggleIntention(id) }

    fun updateIntention(intention: IntentionEntity) = viewModelScope.launch { repo.updateIntention(intention) }

    fun removeIntention(id: String) = viewModelScope.launch { repo.removeIntention(id) }

    fun setReflection(text: String) = viewModelScope.launch { repo.setReflection(_todayKey.value, text) }

    fun carryOverIntentions() = viewModelScope.launch {
        val yesterday = DateUtils.addDays(_todayKey.value, -1)
        repo.carryOverIntentions(yesterday, _todayKey.value)
    }

    // ── Focus blocks ──────────────────────────────────────────────────────

    val activeBlock: StateFlow<FocusBlockEntity?> = repo.activeBlockFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val pausedBlocks: StateFlow<List<FocusBlockEntity>> = repo.pausedBlocksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayBlocks: StateFlow<List<FocusBlockEntity>> = _todayKey
        .flatMapLatest { repo.blocksForDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dayFocusMs: StateFlow<Long> = _todayKey
        .flatMapLatest { repo.dayFocusMs(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun startBlock(title: String, linkedIntentionId: String? = null, project: String? = null, targetMs: Long = 0L) {
        viewModelScope.launch {
            val block = repo.startBlock(title, linkedIntentionId, project, targetMs)
            startFocusService(block)
        }
    }

    fun pauseActive(note: String = "") = viewModelScope.launch {
        repo.pauseActive(note)
        updateFocusService()
    }

    fun resumeBlock(blockId: String) = viewModelScope.launch {
        repo.resumeBlock(blockId)
        val block = repo.getBlockById(blockId) ?: return@launch
        startFocusService(block)
    }

    fun concludeActive(reflection: String, markIntentionDone: Boolean = false) = viewModelScope.launch {
        repo.concludeActive(reflection, markIntentionDone)
        stopFocusService()
    }

    fun concludeBlock(blockId: String, reflection: String, markIntentionDone: Boolean = false) = viewModelScope.launch {
        repo.concludeBlock(blockId, reflection, markIntentionDone)
        // If this was the active block, stop the service
        if (activeBlock.value?.id == blockId) stopFocusService()
    }

    fun updateBlock(blockId: String, title: String? = null, project: String? = null, reflection: String? = null) =
        viewModelScope.launch { repo.updateBlock(blockId, title, project, reflection) }

    fun deleteBlock(blockId: String) = viewModelScope.launch {
        if (activeBlock.value?.id == blockId) stopFocusService()
        repo.deleteBlock(blockId)
    }

    fun addManualBlock(title: String, startedAt: Long, endedAt: Long, reflection: String) =
        viewModelScope.launch { repo.addManualBlock(title, startedAt, endedAt, reflection) }

    fun getSessionsForBlock(blockId: String): Flow<List<FocusSessionEntity>> =
        repo.sessionsForBlock(blockId)

    // Compute elapsed ms for a block (used by the timer)
    suspend fun elapsedMs(blockId: String) = repo.computeElapsedMs(blockId)

    // ── Foreground service helpers ─────────────────────────────────────────

    private fun startFocusService(block: FocusBlockEntity) {
        viewModelScope.launch {
            val elapsed = repo.computeElapsedMs(block.id)
            val accent = prefs.value.accent
            val intent = Intent(appContext, FocusService::class.java).apply {
                action = FocusService.ACTION_START
                putExtra(FocusService.EXTRA_TITLE, block.title)
                putExtra(FocusService.EXTRA_STARTED_AT, System.currentTimeMillis() - elapsed)
                putExtra(FocusService.EXTRA_ELAPSED_MS, elapsed)
                putExtra(FocusService.EXTRA_TARGET_MS, block.targetMs)
                putExtra(FocusService.EXTRA_ACCENT, accent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun updateFocusService() {
        val intent = Intent(appContext, FocusService::class.java).apply {
            action = FocusService.ACTION_UPDATE
            putExtra(FocusService.EXTRA_PAUSED, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stopFocusService() {
        val intent = Intent(appContext, FocusService::class.java).apply {
            action = FocusService.ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    // Handle notification actions routed back from FocusActionReceiver
    fun onFocusAction(kind: String) {
        when (kind) {
            "pause"    -> pauseActive()
            "resume"   -> {
                val b = activeBlock.value ?: pausedBlocks.value.firstOrNull() ?: return
                resumeBlock(b.id)
            }
            "conclude" -> concludeActive("")
        }
    }

    // ── Habits ────────────────────────────────────────────────────────────

    val habits: StateFlow<List<HabitEntity>> = repo.habitsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Current month/year for the Marés tab
    private val _maresYear  = MutableStateFlow(DateUtils.yearMonth(DateUtils.todayKey()).first)
    private val _maresMonth = MutableStateFlow(DateUtils.yearMonth(DateUtils.todayKey()).second)
    val maresYear: StateFlow<Int>  = _maresYear
    val maresMonth: StateFlow<Int> = _maresMonth

    fun maresGoToPrevMonth() {
        val (y, m) = DateUtils.prevMonth(_maresYear.value, _maresMonth.value)
        _maresYear.value = y; _maresMonth.value = m
    }
    fun maresGoToNextMonth() {
        val (y, m) = DateUtils.nextMonth(_maresYear.value, _maresMonth.value)
        _maresYear.value = y; _maresMonth.value = m
    }
    fun maresGoToToday() {
        val (y, m) = DateUtils.yearMonth(DateUtils.todayKey())
        _maresYear.value = y; _maresMonth.value = m
    }

    // Per-habit data for the current month (loaded on demand)
    // In a real app, cache these; here we use a simple ViewModel scope approach.

    private val _habitMonthData = MutableStateFlow<Map<String, HabitMonthData>>(emptyMap())
    val habitMonthData: StateFlow<Map<String, HabitMonthData>> = _habitMonthData

    data class HabitMonthData(
        val logDays: Set<String>,
        val respiroKeys: Set<String>,
        val counts: Map<String, Int>,
        val streak: Int,
        val pct: Int?,
        val observed: Int
    )

    fun loadHabitMonthData(habitId: String, year: Int, month: Int) {
        viewModelScope.launch {
            val mk = DateUtils.monthKey(year, month)
            val monthEnd = DateUtils.monthEnd(mk)
            val todayKey = DateUtils.todayKey()

            val logSet = repo.habitLogsInRange(habitId, mk, monthEnd)
            val allLogs = repo.allHabitLogs(habitId)
            val allRespiros = repo.allHabitRespiros(habitId)
            val counts = repo.habitCountsInRange(habitId, mk, monthEnd)

            val habit = repo.getHabitById(habitId) ?: return@launch
            val streak = HabitCalculator.streakInfo(habit, allLogs, allRespiros, todayKey).streakDays
            val stats = HabitCalculator.monthStats(habit, allLogs, allRespiros, counts, year, month, todayKey)

            // Get respiros for current month
            val monthRespiros = repo.habitRespirosInRange(habitId, mk, monthEnd)

            _habitMonthData.value = _habitMonthData.value + (habitId to HabitMonthData(
                logDays = logSet,
                respiroKeys = monthRespiros,
                counts = counts,
                streak = streak,
                pct = stats.pct,
                observed = stats.observed
            ))
        }
    }

    fun addHabit(name: String, cadence: String = "daily", anchor: Int = -1,
                 weekdays: String = "[]", target: Int = 0, unit: String = "",
                 clock: String = "", description: String = "", color: String = "") {
        viewModelScope.launch { repo.addHabit(name, cadence, anchor, weekdays, target, unit, clock, description, color) }
    }

    fun updateHabit(habit: HabitEntity) = viewModelScope.launch { repo.updateHabit(habit) }

    fun removeHabit(id: String) = viewModelScope.launch { repo.removeHabit(id) }

    fun toggleHabitDay(habitId: String, dayKey: String) = viewModelScope.launch {
        repo.toggleHabitDay(habitId, dayKey)
        loadHabitMonthData(habitId, _maresYear.value, _maresMonth.value)
    }

    fun markRespiro(habitId: String, dayKey: String, reason: String) = viewModelScope.launch {
        repo.markRespiro(habitId, dayKey, reason)
        loadHabitMonthData(habitId, _maresYear.value, _maresMonth.value)
    }

    fun unmarkRespiro(habitId: String, dayKey: String) = viewModelScope.launch {
        repo.unmarkRespiro(habitId, dayKey)
        loadHabitMonthData(habitId, _maresYear.value, _maresMonth.value)
    }

    fun incHabitCount(habitId: String, dayKey: String) = viewModelScope.launch {
        repo.incHabitCount(habitId, dayKey)
        loadHabitMonthData(habitId, _maresYear.value, _maresMonth.value)
    }

    fun reorderHabits(orderedIds: List<String>) = viewModelScope.launch { repo.reorderHabits(orderedIds) }

    // ── Goals ─────────────────────────────────────────────────────────────

    val goals: StateFlow<List<GoalEntity>> = repo.goalsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addGoal(text: String) = viewModelScope.launch {
        repo.addGoal(text, DateUtils.currentQuarter())
    }
    fun updateGoal(goal: GoalEntity) = viewModelScope.launch { repo.updateGoal(goal) }
    fun removeGoal(id: String) = viewModelScope.launch { repo.removeGoal(id) }

    // ── Routines ──────────────────────────────────────────────────────────

    val routines: StateFlow<List<RoutineEntity>> = repo.routinesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun applyRoutine(routineId: String) = viewModelScope.launch {
        repo.applyRoutine(routineId, _todayKey.value)
    }
    fun saveRoutineFromToday(name: String) = viewModelScope.launch {
        repo.saveRoutineFromDay(name, _todayKey.value)
    }
    fun removeRoutine(id: String) = viewModelScope.launch { repo.removeRoutine(id) }
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as PautaApplication
        return AppViewModel(app.repository, context.applicationContext) as T
    }
}
