# CLAUDE.md

Guidance for working in this repository.

## ⚠️ Active scope: native APK only (`app-native/`)

**All current work is on the native Android app under `app-native/` (Kotlin +
Jetpack Compose). Stay in that directory.** Do NOT read or modify the legacy web
build (`src/`, `index.html`, `vendor/`, `scripts/`, `tools/`, `native/android/`,
the `android.yml` workflow) — reading it just burns context.

- **Read only what you need inside `app-native/`.** Don't sweep the web `src/`
  tree. The native module already mirrors the web app's behaviour — the Kotlin
  code and `app-native/README.md` are the source of truth.
- The web `src/*.jsx` files are the original spec but are already ported. Open a
  single file **only** for a specific parity-bug reference, and read just that
  slice — never the whole tree.
- Web-build details (the no-bundler React app, `npm run check`, the `latest`
  release) live in **`docs/WEB_LEGACY.md`**. Don't load it unless a task truly
  touches the web tree.

## What this is

**Pauta** is a private, offline-first daily planner: write intentions, run focus
blocks, track habits. No account, no server, no tracking. `app-native/` is a
from-scratch Kotlin/Jetpack Compose rewrite (appId `com.pauta.app`) reaching
faithful parity with the original web app. Three tabs:

| Tab     | Meaning                                                   |
|---------|-----------------------------------------------------------|
| Hoje    | Today's intentions + nightly reflection                   |
| Pauta   | Focus blocks with a start/pause/resume/conclude timer     |
| Marés   | Habits ("tides") with daily/weekly/monthly cadence        |

## Architecture (`app-native/`)

- **Stack:** Kotlin 2.0 · Jetpack Compose (Material3, BOM 2024.09.03) · Room +
  KSP · DataStore · Lifecycle/Navigation/Activity Compose · kotlinx.serialization
  · Coroutines. Charts and the Pip mascot are pure Compose Canvas. No network or
  charting deps — the in-app updater (GitHub Releases) is the only network call.
- **State is one place:** `AppViewModel` (an `AndroidViewModel`) over
  `PautaRepository` over Room DAOs — the native equivalent of the web's
  `useStore()`. Reads are `Flow`s surfaced with `collectAsStateWithLifecycle`;
  writes are suspending repo methods. Treat state as immutable.
- **Data is `pauta.v4`-compatible:** `data/WebBackup.kt` imports/exports the same
  backup JSON the web app does — round-trips must stay lossless. Room entities in
  `data/entity/Entities.kt` mirror the web schema 1:1 (string ids, ms timestamps,
  `YYYY-MM-DD` local day keys).

## Conventions

- **i18n: Portuguese (pt-PT) is the source language.** Wrap every user-facing
  string in `tr("…")` / `trf("… {n} …", "n" to n)` from `com.pauta.app.i18n`. The
  PT string *is* the key; add the English value to the `EN` map in
  `i18n/I18n.kt`. Missing keys fall back to PT. Mark native-only keys with a
  `// native-only` comment, and copy web EN values verbatim where the key exists
  on the web. Avoid duplicate keys in the `EN` map.
- **Theme tokens, not hardcoded colours:** read `LocalPautaColors.current`
  (`paper`, `paper2`, `ink`/`ink2`/`ink3`/`ink4`, `rule`, `accent`, `onDark`…)
  and the font families `SerifFamily` / `MonoFamily` / `SansFamily`. A literal
  colour is a deliberate exception (e.g. the danger red).
- **Comments are bilingual** (PT/EN) and explain *why*; match surrounding density.
- **Signing & versioning:** every build is signed with the repo-root
  `debug.keystore` (so OTA updates install in place, data preserved — never
  regenerate it). `versionCode` is epoch-minutes (reset-proof). CI stamps
  `BuildConfig.BUILD_RUN` (run number) and `BUILD_TS` (epoch seconds).

## Commands

```bash
cd app-native
./gradlew :app:compileDebugKotlin    # fast compile check
./gradlew :app:testDebugUnitTest     # JVM unit tests (domain math + backup converter) — the gate
./gradlew :app:assembleDebug         # debug APK, signed with repo-root debug.keystore
```

Requires JDK 17 + the Android SDK (`compileSdk 35`). If the SDK isn't available
locally (common in this environment — Gradle errors with "SDK location not
found"), skip the local build and rely on CI to compile/test.

## CI / releases (native)

`.github/workflows/android-native.yml` triggers on `app-native/**` (and its own
file): it runs the unit tests (the gate), then builds the APK with
`-PbuildRun=<run> -PbuildTs=<epoch>`. **On `main` only** it prunes old assets and
publishes the rolling **`latest-native`** GitHub Release — the tag the native
in-app updater polls. Feature branches build/test but don't publish.

Ignore the web `android.yml` / the `latest` release — those ship the web build.
Note `android.yml` still *runs* on every push (so a native PR shows its `build`
job too); native-only changes don't touch `src/`, so it should stay green on its
own.

## Workflow — how to ship native changes

Handle the full cycle autonomously (autonomous squash-merges are authorised):

1. **Branch** from current `main`.
2. Before committing, run `./gradlew :app:testDebugUnitTest` (and
   `:app:compileDebugKotlin`) if the SDK is available; otherwise let CI verify.
3. **Commit**, **push**, **open a PR** to `main`.
4. **`subscribe_pr_activity`** and wait for CI. CI *success* is not delivered by
   webhook — re-check with `pull_request_read` (`get_check_runs`). If a check
   fails, diagnose and push the fix to the **same** branch (never a new PR).
5. When the `build` checks are green, **squash-merge** to `main`.
6. **Verify** the release: `get_release_by_tag latest-native` shows the new
   `pauta-native-v<N>.apk` asset on the merge commit.

**Never** strand a commit on a branch with no PR. **Never** push to `main`
directly — always go through a PR so CI runs first.

## Pointers (`app-native/app/src/main/kotlin/com/pauta/app/`)

- `MainActivity.kt` / `PautaApplication.kt` — entry point + app/DI wiring.
- `ui/MainScaffold.kt` — shell: status row, tab pager (swipe + hardware 1/2/3),
  tab bar, Pip, onboarding, settings host.
- `ui/screens/HojeScreen.kt` / `PautaScreen.kt` / `MaresScreen.kt` — the tabs;
  `PautaExtras.kt`, `PautaSheets.kt`, sheet/detail files alongside.
- `ui/screens/SettingsScreen.kt` — settings: appearance, language, accessibility,
  reminders, data (export/import), updates, danger zone.
- `ui/viewmodel/AppViewModel.kt` — the single ViewModel (prefs + all tab state +
  updater).
- `ui/theme/` — `LocalPautaColors`, accent, typography.
- `data/PautaRepository.kt` — the gateway over Room (the `useStore()` analogue).
- `data/AppDatabase.kt`, `data/dao/Daos.kt`, `data/entity/Entities.kt` — Room.
- `data/WebBackup.kt` — `pauta.v4` import/export.
- `domain/` — pure math/logic (`DateUtils`, `FocusMath`, `HabitCalculator`,
  `HistoryBuilder`, `HojeLogic`, `InsightsMath`); covered by `src/test/`.
- `i18n/I18n.kt` — `tr`/`trf` + the `EN` dictionary.
- `service/` — `AppUpdater` (in-app update), `FocusService` + focus notification,
  `ReminderScheduler`/`ReminderReceiver` (AlarmManager), widget, QS tile, boot.
- `app-native/README.md` — the module's own overview and non-negotiables.
- `docs/WEB_LEGACY.md` — the legacy web build's guidance (load only if needed).
</content>
