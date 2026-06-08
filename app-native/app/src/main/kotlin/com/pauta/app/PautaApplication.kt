package com.pauta.app

import android.app.Application

/**
 * Application entry point. For now it just exists so the manifest's
 * android:name resolves; as the data layer lands (Phase 1) this becomes the DI
 * root that lazily builds the Room database + repository. // PT: ponto de
 * entrada; passará a alojar a base de dados Room e o repositório na Fase 1.
 */
class PautaApplication : Application()
