# Pauta — native (Kotlin + Jetpack Compose)

A from-scratch native Android rewrite of Pauta, replacing the Capacitor/WebView
build. Faithful parity with the original web app has been reached — same tabs,
sheets, phrases, animations, and the Pip mascot — and the web build was retired
in June 2026 (its full history lives on the `web-legacy-final` branch). This
module **is** the shipping app.

## Non-negotiables preserved
- Name **Pauta**, appId **com.pauta.app**
- Three tabs **Hoje / Pauta / Marés** with swipe + hardware `1`/`2`/`3` shortcuts
- The design tokens, typography, maritime theme (light/dark/auto), live accent
- **Pip** the parrot + his animations
- Marés levels (Onda → Tsunami/Oceano) + the no-guilt **Respiro**
- i18n with **Portuguese as source** + an English dictionary (`com.pauta.app.i18n`)
- Native reminders via **AlarmManager**
- Offline-first: no account, no network, no tracking (only the in-app updater
  talks to GitHub Releases, exactly as the web build does)
- **Data compatibility**: imports backups exported by the web app (`pauta.v4`)
- Licence **CC BY-NC 4.0** (repo-root `LICENSE`)

## Build
```bash
cd app-native
./gradlew :app:assembleDebug      # debug APK (signed with repo-root debug.keystore)
./gradlew :app:testDebugUnitTest  # JVM unit tests (domain math + backup converter)
```
Requires JDK 17+ and the Android SDK (compileSdk 35). CI
(`.github/workflows/android-native.yml`) runs the tests and uploads the APK as a
validation artifact on every push that touches `app-native/`.

## Stack
Kotlin 2.0 · Compose (BOM 2024.09.03, Material3) · Room + KSP · DataStore ·
Lifecycle / Navigation / Activity Compose · kotlinx.serialization (backup v4) ·
Coroutines · Splashscreen. Charts and Pip are pure Compose Canvas — no network or
third-party charting deps.

## Status
Feature-complete parity with the retired web app: all three tabs, sheets and
forms, settings, insights/reviews, quarterly goals, week-ahead planning, PIN
lock, reminders, home widget, QS tile, in-app updater and `pauta.v4` backups.
Active work is tracked in `docs/NATIVE_IMPROVEMENTS.md` — one self-contained
task per session/PR.
