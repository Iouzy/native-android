# Pauta

**Intentions, focus blocks and habits — your daily pauta.**

A free, private, offline-first daily planner for Android. Write what matters
today, run focus blocks with a real timer, and keep habits ("marés") with a
maritime, no-guilt streak system. No account, no server, no tracking — your
data lives on your device and exports to a single JSON file.

| Tab     | What it does                                              |
|---------|-----------------------------------------------------------|
| Hoje    | Today's intentions + nightly reflection                   |
| Pauta   | Focus blocks with a start/pause/resume/conclude timer     |
| Marés   | Habits with daily/weekly/monthly cadence and tide levels  |

Native Kotlin + Jetpack Compose app — sources in [`app-native/`](app-native/)
(see its README for the build guide and module non-negotiables).

## Install

Download `pauta-native-v<N>.apk` from the rolling
[`latest-native`](../../releases/tag/latest-native) release and install it.
The app checks that same release for updates and installs them in place —
data preserved.

## Build

```bash
cd app-native
./gradlew :app:assembleDebug      # requires JDK 17 + Android SDK (compileSdk 35)
./gradlew :app:testDebugUnitTest  # domain + backup unit tests
```

## History

Pauta started as a no-build React web app wrapped with Capacitor. The native
rewrite reached full parity and replaced it in June 2026 — the entire web-era
tree is preserved on the [`web-legacy-final`](../../tree/web-legacy-final)
branch.

## Licence

[CC BY-NC 4.0](LICENSE)
