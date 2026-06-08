package com.pauta.app

import android.app.Application
import com.pauta.app.data.AppDatabase

/**
 * Application entry point and dependency root. Lazily builds the Room database
 * (the single, offline source of truth); the repository and ViewModel wiring
 * layer on top of it in the next steps. // PT: ponto de entrada e raiz de
 * dependências; constrói a base de dados Room de forma preguiçosa.
 */
class PautaApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
