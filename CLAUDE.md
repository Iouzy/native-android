# CLAUDE.md

Guidance for working in this repository.

## What this is

**Pauta** is a private, offline-first daily planner — a native Android app
(Kotlin + Jetpack Compose, appId `com.pauta.app`) living in **`app-native/`**:
write intentions, run focus blocks, track habits. No account, no server, no
tracking. Three tabs:

| Tab     | Meaning                                                   |
|---------|-----------------------------------------------------------|
| Hoje    | Today's intentions + nightly reflection                   |
| Pauta   | Focus blocks with a start/pause/resume/conclude timer     |
| Marés   | Habits ("tides") with daily/weekly/monthly cadence        |

The original web app (the spec this module was ported from, full parity
reached) was retired in June 2026. Its complete history is archived on the
branch **`web-legacy-final`** (commit `d8de027`) — e.g.
`git show web-legacy-final:src/App.jsx` if a parity question ever needs it.
Don't resurrect web files; the Kotlin code is the source of truth.

## Roadmap — `docs/NATIVE_IMPROVEMENTS.md`

The active task file: ~20 self-contained improvement tasks (A1…T2), each sized
for one session/PR, with per-task specs, guardrails, status tracking and a
suggested model. When asked to "do task X" or "do the next pending task": read
that file, do ONLY that task following its spec + Global guardrails, ship via
the workflow below, and update the task's Status + Log in the same PR.

## Architecture (`app-native/`)

- **Stack:** Kotlin 2.0 · Jetpack Compose (Material3, BOM 2024.09.03) · Room +
  KSP · DataStore · Lifecycle/Navigation/Activity Compose · kotlinx.serialization
  · Coroutines. Charts and the Pip mascot are pure Compose Canvas. No network or
  charting deps — the in-app updater (GitHub Releases) is the only network call.
- **State is one place:** `AppViewModel` (an `AndroidViewModel`) over
  `PautaRepository` over Room DAOs. Reads are `Flow`s surfaced with
  `collectAsStateWithLifecycle`; writes are suspending repo methods. Treat state
  as immutable.
- **Data is `pauta.v4`-compatible:** `data/WebBackup.kt` imports/exports the
  backup JSON format the retired web app used — round-trips must stay lossless.
  Room entities in `data/entity/Entities.kt` mirror that schema 1:1 (string ids,
  ms timestamps, `YYYY-MM-DD` local day keys).

## Conventions

- **i18n: Portuguese (pt-PT) is the source language.** Wrap every user-facing
  string in `tr("…")` / `trf("… {n} …", "n" to n)` from `com.pauta.app.i18n`. The
  PT string *is* the key; add the English value to the `EN` map in
  `i18n/I18n.kt`. Missing keys fall back to PT. Mark new keys with a
  `// native-only` comment; keys ported from the web app keep their original EN
  values verbatim. Avoid duplicate keys in the `EN` map.
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

## CI / releases

`.github/workflows/android-native.yml` triggers on `app-native/**` (and its own
file): it runs the unit tests (the gate), then builds the APK with
`-PbuildRun=<run> -PbuildTs=<epoch>`. **On `main` only** it prunes old assets and
publishes the rolling **`latest-native`** GitHub Release — the tag the in-app
updater polls. Feature branches build/test but don't publish. (The legacy web
workflow and its `latest` release were retired with the web tree.)

## Workflow — how to ship changes

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
- `data/PautaRepository.kt` — the gateway over Room.
- `data/AppDatabase.kt`, `data/dao/Daos.kt`, `data/entity/Entities.kt` — Room.
- `data/WebBackup.kt` — `pauta.v4` import/export.
- `domain/` — pure math/logic (`DateUtils`, `FocusMath`, `HabitCalculator`,
  `HistoryBuilder`, `HojeLogic`, `InsightsMath`); covered by `src/test/`.
- `i18n/I18n.kt` — `tr`/`trf` + the `EN` dictionary.
- `service/` — `AppUpdater` (in-app update), `FocusService` + focus notification,
  `ReminderScheduler`/`ReminderReceiver` (AlarmManager), widget, QS tile, boot.
- `app-native/README.md` — the module's own overview and non-negotiables.
- `docs/NATIVE_IMPROVEMENTS.md` — the active improvement roadmap (see above).
