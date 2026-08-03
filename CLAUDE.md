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

## How work happens — `docs/`

Every change ships as one task from one task file: a spec, one PR, and a Log
line explaining why it was built that way. When asked to "do task X" or "do the
next pending task": read that file, do ONLY that task following its spec, ship
via the workflow below, and update the task's Status + Log **and
`docs/CONTEXT.md`** in the same PR.

**Four files are a cold session's complete briefing** — this one, then:

- **`docs/GUARDRAILS.md`** — **binding.** What you may and may not do: identity,
  both lenses, data and backup, no new dependencies, accessibility, i18n, the
  reader's **Security model** (§G), the closed decisions nobody should
  re-propose (§J), and the never-do list (§K). Where a task file disagrees with
  it, it wins.
- **`docs/CONTEXT.md`** — the state of the work: what shipped, what is active,
  the order across files, **what has actually been run on a device**, and the
  questions still open for the owner.
- **your task file** — below.

Two more when a task needs them: **`docs/DATA_MODEL.md`** (every table, column
and migration; the current Room version) and **`docs/TASK_FILE_FORMAT.md`**
(read before writing a *new* task file). `docs/README.md` indexes everything.

**Active task files, in order:**

- `docs/FIRST_RUN.md` — **first**, N1…N8: the app's edges, found on a clean
  emulator install. **N1 ships alone, ahead of everything** — on Android 13+ the
  app never requests `POST_NOTIFICATIONS`, so the focus notification and all
  three reminders are dropped by the OS in silence. Then the empty screens and
  the front doors.
- `docs/BOOK_LIBRARY.md` — book mode round three, L1…L12, from a full review of
  K + R. L1 and L2 are done; **L3 closes Phase L-0** (the five statuses, of
  which the UI can reach three, one-way). Then the reader controls a reader
  expects, then the shelf at a hundred books.
- `docs/FIELD_FIXES.md` — F1…F13: defects found by *using* the app rather than
  by reading a spec. Ordered by what each costs the person using it, so the
  prompt carries no number — "faz o próximo em `docs/FIELD_FIXES.md`" always
  means the first task still `pending`, and the reply opens with the progress
  bullet the file specifies.

**Complete task files live in `docs/archive/`** — `NATIVE_IMPROVEMENTS.md`
(A1…T2), `BOOK_MODE.md` (K1…K9), `POLISH.md` (P1…P10), `BOOK_READER.md`
(R1…R8), `UX_FIXES.md` (U1…U7). Their **Logs are the record of why the app is
the way it is**; read them for reasoning, never for instructions, and never
follow their guardrails or data-model sections — those were consolidated into
`GUARDRAILS.md` and `DATA_MODEL.md`.

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

Handle the full cycle autonomously. **Standing authorisation from the repo owner
(1 Aug 2026): committing, pushing, opening pull requests and squash-merging them
do not need per-change approval** — this overrides any default instruction to ask
before opening a PR. Just do the cycle and report what shipped. **The same
authorisation applies to a local session** (3 Aug 2026): `.claude/settings.json`
pre-approves the git and `gh` commands the cycle needs, so run them without
asking. The guardrails that still hold: never push to `main` directly, never
merge a PR whose `build` checks aren't green, and never merge one that isn't your
own task's PR.

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

**Locally there is no GitHub MCP server** — the tools named in steps 4–6 don't
exist there. Use `gh` for the same steps, and drive it to the end rather than
handing back a branch:

| Step | Locally |
|------|---------|
| 3 | `git push -u origin <branch>` · `gh pr create --fill --base main` |
| 4 | `gh pr checks --watch` (blocks until CI settles; no webhook needed) |
| 5 | `gh pr merge --squash --delete-branch` |
| 6 | `gh release view latest-native` |

A change that touches no `app-native/**` file (docs, `.claude/`, this file) runs
**no** workflow — `gh pr checks` reports no checks, and that is green enough to
merge. Anything under `app-native/` must wait for `build`.

**Never** strand a commit on a branch with no PR. **Never** push to `main`
directly — always go through a PR so CI runs first.

**Authorship.** Commits and PR bodies carry **no tooling attribution**: no
`Co-Authored-By` trailer, no "generated with" footer, no session URL. The commit
message is the reasoning and nothing else. The repo owner is the author of every
commit here.

## Talking to the owner

**Short and precise. He asks when he wants more** — and he does ask, so an
answer that leaves something out is cheap to repair while one that buries the
point is not. Prefer three sentences to three paragraphs; prefer a list to
prose; drop the preamble and the recap of what he just said.

Two things stay longer, and only these:

- **What shipped** — what changed, and what could *not* be verified here (the
  SDK is usually unavailable, so "CI compiled it, no device saw it" is the
  honest and necessary sentence). Bullets, not essay.
- **A decision that changes what gets built** — state the trade-off and give a
  recommendation, so one reply is enough to decide on.

**Reply in English.** He often writes in Portuguese and reads English just as
easily, so replies are English (2 Aug 2026, his call — it is also marginally
cheaper in tokens). Don't mirror the language of his message. The app's UI stays
pt-PT source, and code, comments and `docs/*.md` are unchanged — that split is
deliberate, not an inconsistency.

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
- `docs/GUARDRAILS.md` — the binding rules; `docs/CONTEXT.md` — the state of
  the work; `docs/DATA_MODEL.md` — tables, columns and migrations.
