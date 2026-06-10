package com.pauta.app

import android.app.Application
import com.pauta.app.data.AppDatabase
import com.pauta.app.data.PautaRepository
import com.pauta.app.i18n.I18n
import com.pauta.app.i18n.Lang
import com.pauta.app.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application entry point and dependency root. Holds the single Room database,
 * the shared [PautaRepository], and a process-lifetime [appScope] so background
 * components (e.g. the focus notification's action receiver) can run data
 * operations without an Activity. // PT: raiz de dependências — base de dados,
 * repositório partilhado e um scope de processo para componentes de fundo.
 */
class PautaApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: PautaRepository by lazy { PautaRepository(database) }
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    override fun onCreate() {
        super.onCreate()
        // Seed the in-memory language before any UI or notification renders, so
        // a process started in the background (focus service, reminder) speaks
        // the chosen language too — MainActivity then keeps it in step with the
        // live preference. // PT: semear a língua à nascença do processo.
        I18n.lang = if (ReminderScheduler.savedLang(this) == "en") Lang.EN else Lang.PT
    }
}
