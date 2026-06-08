package com.pauta.app

import android.app.Application
import com.pauta.app.data.AppDatabase
import com.pauta.app.data.PautaRepository

/** Process-wide singletons: the Room database and the repository over it. */
class PautaApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: PautaRepository by lazy { PautaRepository(database) }
}
