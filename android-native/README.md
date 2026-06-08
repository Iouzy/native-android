# Pauta — Native Android (Kotlin / Jetpack Compose)

A full native rewrite of the Pauta planning app, replacing the React + Capacitor
WebView build with a pure Kotlin / Jetpack Compose Android app. Same package id
(`com.pauta.app`), same data model, same offline-first philosophy — no account,
no server, no tracking. All data lives in a local **Room** database.

## Why a rewrite

The web build ran React/Babel in a WebView via Capacitor. This version is 100%
native: Compose UI, a Room database, a Kotlin MVVM layer, and the focus-timer
foreground service / reminder alarms / widget / quick-settings tile reimplemented
as first-class Android components (no JS bridge).

## Architecture

```
app/src/main/kotlin/com/pauta/app/
├── PautaApplication.kt        Process singletons (DB + repository)
├── MainActivity.kt            Single activity, edge-to-edge, hosts Compose
├── data/
│   ├── entity/Entities.kt     14 Room entities (also @Serializable for backup)
│   ├── dao/Daos.kt            Room DAOs (Flow-based reactive queries)
│   ├── AppDatabase.kt         Room database
│   └── PautaRepository.kt     All mutations + queries over the DAOs
├── domain/
│   ├── DateUtils.kt           dayKey math, week/month boundaries, formatting
│   └── HabitCalculator.kt     Cadence logic, day-state, streaks, monthly %
├── ui/
│   ├── theme/                 Material 3 theme, light/dark, live accent colour
│   ├── viewmodel/AppViewModel ViewModel exposing StateFlows + action methods
│   ├── MainScaffold.kt        Bottom-nav: Hoje / Pauta / Marés
│   └── screens/               HojeScreen, PautaScreen, MaresScreen, SettingsSheet
├── i18n/Strings.kt            Portuguese-keyed i18n with English fallback
└── service/
    ├── FocusService.kt        Foreground service: ongoing timer notification
    ├── FocusActionReceiver.kt Notification button handling (pause/resume/…)
    ├── FocusActionBus.kt      Bridges notification actions → ViewModel
    ├── ReminderScheduler.kt   AlarmManager daily reminders (fire app-closed)
    ├── ReminderReceiver.kt    Posts reminder + re-arms next day
    ├── BootReceiver.kt        Re-arms alarms after reboot / app update
    ├── PautaWidgetProvider.kt Home-screen widget
    ├── FocusTileService.kt    Quick-Settings tile → jump to focus
    └── BackupManager.kt       Full JSON export/import (anti-lock-in)
```

### Three tabs map to the data model

| Tab     | Portuguese | Meaning                                              |
|---------|------------|------------------------------------------------------|
| `hoje`  | Hoje       | Today's intentions + nightly reflection              |
| `pauta` | Pauta      | Focus blocks with a start/pause/resume/conclude timer|
| `mares` | Marés      | Habits ("tides") with daily/weekly/monthly cadence   |

### Habit ("Marés") logic

`HabitCalculator` ports the web app's cadence math exactly:
- **daily / weekly / monthly** cadences; weekly/monthly lock the rest of a period
  once completed on any day in it.
- **respiros** (honest rest days) keep a streak alive and are excluded from the
  completion-percentage denominator.
- streaks walk back period-by-period; tiers (Onda → Tsunami) come from streak days.
- monthly % is "immature" (shown as `X/7`) until 7 units are observed.

## Build

```bash
cd android-native
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Requires the Android SDK (platform 35, build-tools 34) and JDK 17. The module is
signed with the repo-root `debug.keystore`, so builds install as in-place updates
(same signature as the Capacitor build — no "package conflicts" on upgrade).

## CI

`.github/workflows/android-native.yml` builds the APK on every push that touches
`android-native/`. Branch pushes upload the APK as an artifact; `main` publishes
a rolling `latest-native` GitHub Release.
