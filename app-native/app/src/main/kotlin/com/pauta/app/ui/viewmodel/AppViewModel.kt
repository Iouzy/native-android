package com.pauta.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pauta.app.PautaApplication
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
import com.pauta.app.data.SafBackup
import com.pauta.app.domain.CarrySource
import com.pauta.app.domain.DateUtils
import com.pauta.app.domain.FocusMath
import com.pauta.app.domain.HistoryDay
import android.content.Context
import com.pauta.app.service.AppUpdater
import com.pauta.app.service.BackupScheduler
import com.pauta.app.service.FocusServiceController
import com.pauta.app.service.HabitReminderScheduler
import com.pauta.app.service.MaresWidget
import com.pauta.app.service.ReminderScheduler
import com.pauta.app.service.WhatsNew
import com.pauta.app.service.WhatsNewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A reversible delete that a snackbar's "Anular" can put back — it carries
 * everything needed to reinsert. An intention restores as-is; a block restores
 * together with its sessions (the delete cascaded them). // PT: uma remoção que o
 * snackbar pode anular — guarda os dados para repor.
 */
sealed interface PendingUndo {
    data class Intention(val entity: IntentionEntity) : PendingUndo
    data class Block(val block: FocusBlockEntity, val sessions: List<FocusSessionEntity>) : PendingUndo
}

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

    /** False until the first real prefs row arrives from Room — gates the
     *  onboarding overlay so it never flashes for an existing user while the
     *  defaults are in place. // PT: evita o flash do onboarding ao arrancar. */
    val prefsReady: StateFlow<Boolean> =
        repo.prefs.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Undo ──────────────────────────────────────────────────
    // One-shot snackbar-undo requests, emitted whenever a single intention or
    // block is deleted; the shell collects them and shows "removido · Anular".
    // Buffered so a quick second delete still queues a snackbar instead of being
    // dropped. // PT: pedidos de "anular" para o snackbar, ao apagar uma intenção/bloco.
    private val _undoRequests = Channel<PendingUndo>(Channel.BUFFERED)
    val undoRequests: Flow<PendingUndo> = _undoRequests.receiveAsFlow()

    /** Put back the item a snackbar's "Anular" refers to. */
    fun undo(pending: PendingUndo) = viewModelScope.launch {
        when (pending) {
            is PendingUndo.Intention -> repo.restoreIntention(pending.entity)
            is PendingUndo.Block -> repo.restoreBlock(pending.block, pending.sessions)
        }
    }

    // ── Hoje ──────────────────────────────────────────────────
    // Today's key as LIVE state: re-checked by a half-minute ticker and on every
    // resume, so crossing midnight (or a timezone/clock change) rolls the whole
    // app into the new day without a restart — every day-keyed flow below
    // switches with it, like the web app re-rendering off dayKeyOf(now).
    // // PT: chave de hoje como estado vivo — a app vira o dia sem reiniciar.
    private val _todayKey = MutableStateFlow(DateUtils.todayKey())
    val todayKey: StateFlow<String> = _todayKey

    /** Re-evaluate the current day; on a flip, move the key and run the daily
     *  rollover (promote the week plan, clear stale plans). Cheap to call often. */
    fun maybeRollover() {
        val now = DateUtils.todayKey()
        if (now != _todayKey.value) {
            _todayKey.value = now
            viewModelScope.launch { repo.runRollover(now) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val intentions: StateFlow<List<IntentionEntity>> =
        todayKey.flatMapLatest { repo.intentions(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val reflection: StateFlow<String> =
        todayKey.flatMapLatest { repo.dayReflection(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Unfinished intentions from the most recent past day, offered as a one-tap
     *  carry-over (null = nothing to bring forward). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val carry: StateFlow<CarrySource?> =
        todayKey.flatMapLatest { repo.carrySource(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All-time intentions + day rows — the Revisão (insights) sheet's inputs. */
    val allIntentions: StateFlow<List<IntentionEntity>> =
        repo.allIntentions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allDays: StateFlow<List<DayEntity>> =
        repo.allDays().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Read-only history of past days with content, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<HistoryDay>> =
        todayKey.flatMapLatest { repo.history(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Search (E1) ───────────────────────────────────────────
    // The History search box's live query + its results. The text drives a
    // (debounce-free) FTS lookup: flatMapLatest cancels the previous lookup when
    // the query changes, so only the latest keystroke's results land; a blank
    // query short-circuits to no results. State lives here so it survives
    // recomposition and rotation, and the History screen clears it on close.
    // // PT: pesquisa do Histórico — consulta viva e resultados FTS.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<SearchHit>> =
        _searchQuery
            .map { it.trim() }
            .distinctUntilChanged()
            .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else flow { emit(repo.search(q)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

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
    fun setLastSessionNote(blockId: String, note: String) =
        viewModelScope.launch { repo.setLastSessionNote(blockId, note) }
    fun resumeBlock(id: String) = viewModelScope.launch { repo.resumeBlock(id) }
    fun concludeActive(reflection: String, markIntentionDone: Boolean = false) =
        viewModelScope.launch { repo.concludeActive(reflection, markIntentionDone) }
    fun concludeBlock(id: String, reflection: String, markIntentionDone: Boolean = false) =
        viewModelScope.launch { repo.concludeBlock(id, reflection, markIntentionDone) }
    /** Delete a block with snackbar-undo: snapshot it (and its sessions) first,
     *  delete, then offer to put it back. // PT: apaga com opção de anular. */
    fun deleteBlock(id: String) = viewModelScope.launch {
        val snapshot = repo.blockWithSessions(id) ?: return@launch
        repo.deleteBlock(id)
        _undoRequests.send(PendingUndo.Block(snapshot.first, snapshot.second))
    }
    fun updateBlock(id: String, title: String, project: String?, targetMs: Long?) =
        viewModelScope.launch { repo.updateBlock(id, title, project, targetMs) }
    fun setSessionNote(rowId: Long, note: String) = viewModelScope.launch { repo.setSessionNote(rowId, note) }
    fun addManualBlock(title: String, startMs: Long, endMs: Long) =
        viewModelScope.launch { repo.addManualBlock(title, startMs, endMs) }
    fun setBlockReflection(id: String, text: String) = viewModelScope.launch { repo.setBlockReflection(id, text) }
    fun blockSessions(id: String) = repo.sessions(id)

    // ── Marés ─────────────────────────────────────────────────
    val habits: StateFlow<List<HabitEntity>> =
        repo.habits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Archived tides — the Settings → Dados restore list (hidden from the grid). */
    val archivedHabits: StateFlow<List<HabitEntity>> =
        repo.archivedHabits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    /** Archive (hide, keep data) or restore a tide. */
    fun setHabitArchived(id: String, archived: Boolean) =
        viewModelScope.launch { repo.setHabitArchived(id, archived) }
    fun reorderHabits(orderedIds: List<String>) = viewModelScope.launch { repo.reorderHabits(orderedIds) }
    fun toggleHabitDay(id: String, dayKey: String) = viewModelScope.launch { repo.toggleHabitDay(id, dayKey, todayKey.value) }
    fun toggleHabitToday(id: String) = viewModelScope.launch { repo.toggleHabitDay(id, todayKey.value, todayKey.value) }
    fun markRespiro(id: String, dayKey: String, reason: String = "") =
        viewModelScope.launch { repo.markRespiro(id, dayKey, reason, todayKey.value) }
    fun unmarkRespiro(id: String, dayKey: String) = viewModelScope.launch { repo.unmarkRespiro(id, dayKey) }
    fun setHabitCount(id: String, dayKey: String, n: Int) =
        viewModelScope.launch { repo.setHabitCount(id, dayKey, n, todayKey.value) }

    // ── Objetivos ─────────────────────────────────────────────
    val goals: StateFlow<List<GoalEntity>> =
        repo.goals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val milestones: StateFlow<List<MilestoneEntity>> =
        repo.milestones().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addGoal(text: String, quarter: String) = viewModelScope.launch { repo.addGoal(text, quarter) }
    fun toggleGoal(id: String) = viewModelScope.launch { repo.toggleGoal(id) }
    fun removeGoal(id: String) = viewModelScope.launch { repo.removeGoal(id) }
    fun addMilestone(goalId: String, text: String) = viewModelScope.launch { repo.addMilestone(goalId, text) }
    fun toggleMilestone(id: String) = viewModelScope.launch { repo.toggleMilestone(id) }
    fun removeMilestone(id: String) = viewModelScope.launch { repo.removeMilestone(id) }

    // ── Rotinas ───────────────────────────────────────────────
    // D1: routines + their items, surfaced for the Hoje "Rotinas" manager sheet.
    // The manager groups items by routineId; applying a routine seeds today.
    // // PT: rotinas e itens para o gestor; aplicar semeia o dia de hoje.
    val routines: StateFlow<List<RoutineEntity>> =
        repo.routines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val routineItems: StateFlow<List<RoutineItemEntity>> =
        repo.routineItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addRoutine(name: String) = viewModelScope.launch { repo.addRoutine(name) }
    fun renameRoutine(id: String, name: String) = viewModelScope.launch { repo.renameRoutine(id, name) }
    fun deleteRoutine(id: String) = viewModelScope.launch { repo.deleteRoutine(id) }
    fun addRoutineItem(routineId: String, text: String, priority: Int? = null, targetMin: Int? = null) =
        viewModelScope.launch { repo.addRoutineItem(routineId, text, priority, targetMin) }
    fun updateRoutineItem(rowId: Long, text: String, priority: Int?, targetMin: Int?) =
        viewModelScope.launch { repo.updateRoutineItem(rowId, text, priority, targetMin) }
    fun removeRoutineItem(rowId: Long) = viewModelScope.launch { repo.removeRoutineItem(rowId) }
    fun reorderRoutineItems(routineId: String, orderedRowIds: List<Long>) =
        viewModelScope.launch { repo.reorderRoutineItems(routineId, orderedRowIds) }
    /** Append a routine's items to today as fresh intentions. */
    fun applyRoutine(routineId: String) = viewModelScope.launch { repo.applyRoutine(todayKey.value, routineId) }
    /** Snapshot today's intentions as a new named routine. */
    fun saveRoutineFromToday(name: String) = viewModelScope.launch { repo.saveRoutineFromToday(name, todayKey.value) }

    // ── updater ───────────────────────────────────────────────
    val updateChecking: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val updateChecked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val updateAvailable: MutableStateFlow<AppUpdater.Update?> = MutableStateFlow(null)
    val updateDownloading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** 0–100 while the APK streams; null when the size is unknown. */
    val updateDownloadProgress: MutableStateFlow<Int?> = MutableStateFlow(null)
    /** True after a download attempt fails so the UI can offer a retry. */
    val updateDownloadError: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** Android refused: "install unknown apps" isn't allowed for this app yet —
     *  we sent the user to the toggle; they should allow it and tap again. */
    val updateNeedsPerm: MutableStateFlow<Boolean> = MutableStateFlow(false)
    /** The check itself failed (offline / transient network error after retries),
     *  as opposed to a successful "up to date" — so the UI can say so instead of
     *  lying. // PT: a verificação falhou (sem rede), distinto de "atualizado". */
    val updateCheckFailed: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun checkForUpdate() = viewModelScope.launch {
        updateChecking.value = true
        updateCheckFailed.value = false
        updateDownloadError.value = false
        updateNeedsPerm.value = false
        when (val result = AppUpdater.check()) {
            is AppUpdater.CheckResult.Available -> {
                updateAvailable.value = result.update
                updateChecked.value = true
            }
            AppUpdater.CheckResult.UpToDate -> {
                updateAvailable.value = null
                updateChecked.value = true
            }
            // Network failure after backoff: don't show "up to date" — leave
            // updateChecked false so the card shows an error + retry instead.
            AppUpdater.CheckResult.Failed -> {
                updateAvailable.value = null
                updateChecked.value = false
                updateCheckFailed.value = true
            }
        }
        updateChecking.value = false
    }

    fun installUpdate(context: Context) = viewModelScope.launch {
        val u = updateAvailable.value ?: return@launch
        if (updateDownloading.value) return@launch // already downloading
        // Gate on the "install unknown apps" permission first: without it the
        // installer intent silently does nothing, so send the user to the system
        // toggle and ask them to tap again — same flow as the web UpdateChecker.
        if (!AppUpdater.canInstall(context)) {
            updateNeedsPerm.value = true
            AppUpdater.openInstallSettings(context)
            return@launch
        }
        updateNeedsPerm.value = false
        updateDownloading.value = true
        updateDownloadProgress.value = null
        updateDownloadError.value = false
        val file = AppUpdater.download(context, u.url) { pct ->
            updateDownloadProgress.value = pct
        }
        updateDownloading.value = false
        if (file != null) {
            // Stash this release's notes so the "what's new" screen can show them on
            // the next launch, once the installed build's run number has bumped past
            // the one we last showed. // PT: guarda as notas para o ecrã de novidades.
            WhatsNew.stashNotes(context, u.notes)
            AppUpdater.install(context, file)
        } else {
            updateDownloadError.value = true
        }
    }

    // ── What's new (post-update) ──────────────────────────────
    // Non-null on the first launch after an in-place update for an already-set-up
    // user: the release notes to show once. Null on a fresh install or an
    // unchanged build. Evaluated off the main thread at startup (reads a tiny
    // SharedPreferences, before Room is needed). // PT: as novidades a mostrar uma
    // vez após uma atualização (null numa instalação nova ou build igual).
    val whatsNew: MutableStateFlow<WhatsNewState?> = MutableStateFlow(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            whatsNew.value = WhatsNew.pending(getApplication())
        }
    }

    /** Dismiss the "what's new" screen and remember this build as seen, so it
     *  shows only once. // PT: fecha o ecrã de novidades e marca esta build vista. */
    fun dismissWhatsNew() {
        WhatsNew.markSeen(getApplication())
        whatsNew.value = null
    }

    init {
        viewModelScope.launch { repo.ensurePrefs() }
        // Once the real prefs row arrives, gate the PIN unlock screen.
        viewModelScope.launch { prefsReady.first { it }; needsUnlock.value = prefs.value.pinHash != null }
        // Promote any week-ahead plan for today and clear stale plans on launch.
        viewModelScope.launch { repo.runRollover(todayKey.value) }
        // The midnight ticker: while the process lives, re-check the day every
        // half-minute (resume gives an immediate check via maybeRollover()).
        // // PT: relógio da meia-noite — vira o dia com a app aberta.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                maybeRollover()
            }
        }
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
                        // Pass the block's soft target so the notification counts
                        // down to it and the lock-screen "target reached" alert can
                        // arm (C2). // PT: passa o alvo do bloco para a contagem
                        // decrescente e o aviso de alvo atingido.
                        FocusServiceController.start(
                            ctx, block.title,
                            FocusMath.blockElapsedMs(segs, System.currentTimeMillis()),
                            block.targetMs,
                        )
                    }
                }
        }
        // Keep the Glance Marés widget in sync: nudge a re-render whenever the
        // tides or the day change (it reads the repo itself, so this just tells it
        // to recompose — marking from the widget refreshes itself). // PT: atualiza
        // o widget de Marés quando as marés ou o dia mudam.
        viewModelScope.launch {
            val habitMarks = combine(repo.habitLogs(), repo.habitRespiros(), repo.habitCounts()) { l, r, c -> Triple(l, r, c) }
            combine(todayKey, repo.habits(), habitMarks) { _, _, _ -> Unit }
                .collect { MaresWidget.refresh(getApplication<Application>()) }
        }
    }

    /** Bring the offered carry-over items into today. */
    fun carryOver() = viewModelScope.launch {
        carry.value?.let { repo.carryOver(todayKey.value, it.items) }
    }

    fun addIntention(text: String, priority: Int? = null, targetMin: Int? = null, timeOfDay: String? = null) =
        viewModelScope.launch { repo.addIntention(todayKey.value, text, priority, targetMin, timeOfDay) }

    fun toggleIntention(id: String) = viewModelScope.launch { repo.toggleIntention(id) }
    /** Delete an intention with snackbar-undo: snapshot it first, delete, then
     *  offer to put it back. // PT: apaga com opção de anular. */
    fun removeIntention(id: String) = viewModelScope.launch {
        val entity = repo.getIntention(id) ?: return@launch
        repo.removeIntention(id)
        _undoRequests.send(PendingUndo.Intention(entity))
    }
    fun setIntentionPriority(id: String, priority: Int?) =
        viewModelScope.launch { repo.setIntentionPriority(id, priority) }
    fun setIntentionText(id: String, text: String) =
        viewModelScope.launch { repo.setIntentionText(id, text) }
    fun setReflection(text: String) = viewModelScope.launch { repo.setReflection(todayKey.value, text) }

    /** Week-ahead plans (become intentions when their day arrives). */
    val plans: StateFlow<List<PlannedIntentionEntity>> =
        repo.plans().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addPlan(dayKey: String, text: String) = viewModelScope.launch { repo.addPlan(dayKey, text) }
    fun removePlan(id: String) = viewModelScope.launch { repo.removePlan(id) }

    /** Wipe all user data (intentions, blocks, habits, goals…); prefs are kept. */
    fun resetAll() = viewModelScope.launch { repo.resetAll() }

    // ── PIN lock ──────────────────────────────────────────────
    /** True from cold-start until the user verifies PIN; false = no lock or unlocked. */
    val needsUnlock: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun unlockApp() { needsUnlock.value = false }
    fun setPinCode(pin: String) = viewModelScope.launch { repo.setPin(pin) }
    fun clearPinCode() = viewModelScope.launch { repo.clearPin() }

    /** C3: toggle offering biometric unlock at the lock screen (PIN stays the
     *  fallback). // PT: liga/desliga o desbloqueio biométrico. */
    fun setBiometricEnabled(value: Boolean) = update { it.copy(biometricEnabled = value) }

    fun verifyPinCode(pin: String): Boolean {
        val p = prefs.value
        val salt = p.pinSalt ?: return false
        val stored = p.pinHash ?: return false
        return repo.hashPin(pin, salt) == stored
    }

    // ── Demo data ─────────────────────────────────────────────
    fun reseed() = viewModelScope.launch { repo.reseed(todayKey.value) }

    // ── Auto-backup ───────────────────────────────────────────
    fun setAutoBackupCadence(value: String) = update { it.copy(autoBackup = value) }

    /** B1: store (or clear) the user-chosen SAF backup folder. The persistable
     *  URI permission is taken at the Settings call site before this. */
    fun setBackupFolder(uri: String?) = update { it.copy(backupFolderUri = uri) }

    // Off the main dispatcher: writing the local copy — and especially the SAF
    // copy, which can hit a cloud provider — is blocking I/O. // PT: fora da
    // main thread; a escrita (sobretudo SAF) é I/O bloqueante.
    fun maybeAutoBackup(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        repo.maybeAutoBackup(context.filesDir, todayKey.value) { uri, name, json ->
            SafBackup.write(context, uri, name, json)
        }
    }

    /** Produce the pauta.v4 backup JSON, then hand it to [onReady] (for sharing). */
    fun exportBackup(onReady: (String) -> Unit) =
        viewModelScope.launch { onReady(repo.exportJson(todayKey.value)) }

    /** Replace all data from a pauta.v4 backup; reports success to [onDone]. */
    fun importBackup(text: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(runCatching { repo.importJson(text) }.isSuccess)
    }

    fun setTheme(value: String) = update { it.copy(theme = value) }
    fun setAccent(hex: String?) = update { it.copy(accent = hex) }
    fun setLang(value: String) = update { it.copy(lang = value) }
    fun setHighContrast(value: Boolean) = update { it.copy(highContrast = value) }
    fun setTextScale(value: Float) = update { it.copy(textScale = value) }
    fun setImmersive(value: Boolean) = update { it.copy(immersive = value) }
    fun setKeepAwake(value: Boolean) = update { it.copy(keepAwake = value) }
    fun setReducedMotion(value: Boolean) = update { it.copy(reducedMotion = value) }
    fun setHaptics(value: Boolean) = update { it.copy(haptics = value) }
    fun setSound(value: Boolean) = update { it.copy(sound = value) }
    fun setParrot(value: Boolean) = update { it.copy(parrot = value) }
    fun setOnboardingSeen() = update { it.copy(onboardingSeen = true) }

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

        // D2: keep the per-habit reminder alarms in step with the tides' own clocks
        // and the master reminders toggle, mirroring the (id → clock) projection of
        // the active (non-archived) tides so they can re-arm at boot without Room.
        // // PT: alinha os lembretes por maré com as horas das marés e o interruptor
        // mestre de lembretes.
        viewModelScope.launch {
            combine(
                repo.habits()
                    .map { hs -> hs.filter { it.clock.isNotBlank() }.associate { it.id to it.clock } }
                    .distinctUntilChanged(),
                repo.prefs.map { it.remindersEnabled }.distinctUntilChanged(),
            ) { clocks, enabled -> clocks to enabled }
                .collect { (clocks, enabled) ->
                    val ctx = getApplication<Application>()
                    HabitReminderScheduler.save(ctx, enabled, clocks)
                    HabitReminderScheduler.reschedule(ctx)
                }
        }

        // B1: keep the periodic backup job in step with the cadence preference,
        // so the backup runs with the app closed (and stops when set to "off").
        // // PT: alinha a tarefa de cópia periódica com a frequência escolhida.
        viewModelScope.launch {
            repo.prefs
                .distinctUntilChangedBy { it.autoBackup }
                .collect { p -> BackupScheduler.reschedule(getApplication<Application>(), p.autoBackup) }
        }
    }
}
