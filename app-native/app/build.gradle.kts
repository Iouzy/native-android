import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// versionCode = minutes since the Unix epoch. A monotonic, reset-proof scheme
// (same as the web/Capacitor build) so an OTA update is always "newer" and can
// never silently downgrade an installed user. // PT: minutos desde a época Unix
// — monotónico e à prova de reset, para os updates nunca recuarem.
val epochMinutes = (Date().time / 60_000).toInt()

// The CI run number this APK was built from (0 locally). The in-app updater
// compares it against the run number in the published release's asset name, so
// it only offers an update when the release is genuinely newer. // PT: número da
// run do CI; o updater compara-o com o da release publicada.
val buildRun = (project.findProperty("buildRun") as String?)?.toIntOrNull() ?: 0

// Wall-clock build timestamp in epoch seconds (0 locally when buildTs is not
// passed). Exposed as BuildConfig.BUILD_TS so Settings can show a YYYY-MM-DD
// date label that matches the web app's "Versão de …" line. // PT: timestamp
// da build em segundos; Settings mostra uma data em vez de um número de run.
val buildTs = (project.findProperty("buildTs") as String?)?.toLongOrNull() ?: 0L

android {
    namespace = "com.pauta.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pauta.app"
        minSdk = 26
        targetSdk = 35
        versionCode = epochMinutes
        // Human-readable version: 1.<CI run number> in CI builds, 1.0 locally.
        // PT: versão legível — 1.<run> em CI, 1.0 em builds locais.
        versionName = "1.$buildRun"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "BUILD_RUN", "$buildRun")
        buildConfigField("long", "BUILD_TS", "${buildTs}L")
    }

    signingConfigs {
        getByName("debug") {
            // The repo-root debug.keystore is committed so every build — local,
            // CI, branch or main — signs with the SAME key. Identical signatures
            // let OTA updates install in place (no "package conflicts", data
            // preserved). Never regenerate it. // PT: keystore fixo → updates
            // in-place preservam os dados.
            val ks = rootProject.file("../debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // B1: WorkManager runs the auto-backup on a schedule with the app fully
    // closed (the old cadence only fired while the app was open). // PT: corre a
    // cópia automática em segundo plano, mesmo com a app fechada.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // C1: Glance powers the tappable Marés home widget — today's tides as
    // circles, marked straight from the home screen via the repo. // PT: o
    // Glance dá o widget de Marés (toca para marcar a maré no ecrã inicial).
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // C3: biometric unlock — offer fingerprint/face at the PIN lock screen, with
    // the PIN keypad as the fallback. BiometricPrompt needs a FragmentActivity,
    // so this also pulls in androidx.fragment (MainActivity extends it). // PT:
    // desbloqueio biométrico no ecrã de PIN, com o PIN como alternativa.
    implementation("androidx.biometric:biometric:1.1.0")

    // Pure-JVM unit tests for the domain math + the pauta.v4 backup converter.
    // These run on CI with no emulator and gate the APK build.
    testImplementation("junit:junit:4.13.2")
}
