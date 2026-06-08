package com.pauta.app.data

import com.pauta.app.data.entity.PrefsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single gateway between the UI/ViewModel and the Room database — the native
 * equivalent of the web's `useStore()`. It starts with preferences; intention,
 * focus-block and habit actions layer on in their respective phases. // PT:
 * porta única entre a UI e a base de dados, como o useStore() da web.
 */
class PautaRepository(private val db: AppDatabase) {

    private val prefsDao = db.prefsDao()

    /** Live preferences. Emits the defaults until a row has been written, so the
     *  UI always has a value to theme from. */
    val prefs: Flow<PrefsEntity> = prefsDao.observe().map { it ?: PrefsEntity() }

    /** Make sure a prefs row exists (called once on startup). */
    suspend fun ensurePrefs() {
        if (prefsDao.get() == null) prefsDao.upsert(PrefsEntity())
    }

    /** Read-modify-write a single prefs row. */
    suspend fun updatePrefs(transform: (PrefsEntity) -> PrefsEntity) {
        val current = prefsDao.get() ?: PrefsEntity()
        prefsDao.upsert(transform(current))
    }
}
