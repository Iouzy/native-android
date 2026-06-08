package com.pauta.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pauta.app.data.AppDatabase
import com.pauta.app.data.PautaRepository
import com.pauta.app.data.entity.IntentionEntity
import com.pauta.app.data.entity.PrefsEntity
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.HistoryDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val repo = PautaRepository(AppDatabase.get(app))

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

    init {
        viewModelScope.launch { repo.ensurePrefs() }
        // Promote any week-ahead plan for today and clear stale plans on launch.
        viewModelScope.launch { repo.runRollover(todayKey) }
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

    private fun update(transform: (PrefsEntity) -> PrefsEntity) {
        viewModelScope.launch { repo.updatePrefs(transform) }
    }
}
