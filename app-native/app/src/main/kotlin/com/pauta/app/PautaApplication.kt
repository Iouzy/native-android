package com.pauta.app

import android.app.Application
import com.pauta.app.data.AppDatabase
import com.pauta.app.data.PautaRepository
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
}
