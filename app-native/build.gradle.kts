// Root build script for the native Pauta rewrite (Kotlin + Jetpack Compose).
// Plugin versions are declared here once and applied per-module. // PT: versões
// dos plugins declaradas uma vez aqui e aplicadas por módulo.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
