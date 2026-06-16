# Native app improvements — per-session task file

> **How to use (human).** In a fresh Claude Code session, prompt:
>
> > Read `docs/NATIVE_IMPROVEMENTS.md`. Do ONLY task **A1** — follow its spec
> > and the Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR
> > → CI → squash-merge), and in the same PR set the task's Status and append
> > one line to the Log.
>
> Or, stateless: *"Read `docs/NATIVE_IMPROVEMENTS.md` and do the first task
> whose Status is `pending`."* One task per session keeps context small and
> PRs reviewable.
>
> **How to use (Claude).** This file + CLAUDE.md are the briefing — the
> findings below come from a full June 2026 review of every screen, the data
> layer, services and domain logic, so **don't re-survey the codebase**; open
> only the files the task names. If a task is too big for one PR, split it,
> ship the first slice, and edit this file to record the split. Always update
> Status + Log in the same PR. Phases A→F are priority order; tasks within a
> phase are independent unless noted.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

## Global guardrails (every task)

- **Protect the identity:** calm paper/ink aesthetic, `clickableNoRipple`
  everywhere, no gamification beyond the tide tiers, no cloud/accounts/sync,
  charts stay pure Compose Canvas (no charting libs).
- **i18n:** every user-facing string via `tr()`/`trf()`; PT is the source, add
  EN values to the `EN` map in `i18n/I18n.kt` with `// native-only` on new keys.
- **Theme:** colours only from `LocalPautaColors`; fonts only
  `SerifFamily`/`MonoFamily`/`SansFamily`.
- **Prefs are law:** any new animation must respect `prefs.reducedMotion`; any
  new haptic must respect `prefs.haptics`.
- **Backup compatibility:** the `pauta.v4` export shape must not change and
  round-trips must stay lossless — `data/WebBackup.kt` + its tests are the
  gate (`./gradlew :app:testDebugUnitTest`). Native-only state goes in new
  Room columns/tables; per task, decide explicitly whether it exports (and
  note it in the PR).
- **Dependencies:** only the androidx artifacts a task names; nothing else.
- **Comments bilingual (PT/EN), explaining *why*,** matching surrounding density.
- Ship via the CLAUDE.md workflow. Never push to `main` directly.

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

## Suggested model per task

All three models have the same 1M-token context window — pick by capability vs
cost. Running a task on a stronger model than suggested is always fine.

| Model | Use for | Tasks |
|---|---|---|
| **Claude Fable 5** | hardest / architectural / design-taste work | A5, A8, B1, C1, F1, F2 |
| **Opus 4.8** | the default executor for well-specified builds | A2, A3, A6, A7, C2, C4, D1, D2, E1 |
| **Sonnet 4.6** | small, mechanical, tightly-specified tasks | A1, A4, B2, C3, E2, T1, T2 |

---

## Phase A — feel (highest payoff; order matters: A1, A2 unblock A3)

### A1 · Wire up haptics (the pref exists but is dead) — Status: pending
- **Why:** `PrefsEntity.haptics` defaults `true` and Settings shows the toggle,
  but no code in the app fires a single haptic (verified: zero
  `HapticFeedback`/`Vibrator` usages).
- **Where:** new small helper (suggest `ui/Haptics.kt`); call sites in
  `ui/screens/HojeScreen.kt` (intention done-toggle), `ui/screens/MaresScreen.kt`
  + `ui/TideHelpers.kt` (cell tap = light tick; long-press respiro = heavy),
  `ui/screens/PautaScreen.kt`/`PautaSheets.kt` (start/pause/resume/conclude),
  `ui/screens/PinScreen.kt` (keypad), `ui/MainScaffold.kt` (tab change).
- **How:** `LocalHapticFeedback` (or `View.performHapticFeedback`), gated on
  `prefs.haptics`. Light types for toggles/ticks, `LongPress` for respiro and
  destructive confirms.
- **Accept:** every listed interaction ticks with the pref on; silent when off.
- Note: `prefs.sound` is equally dead — out of scope here (see F-extra note).

### A2 · LazyColumn migration — Status: in-progress (PR #104)
- **Why:** Hoje intentions, Marés habits and the Pauta timeline render in plain
  `Column`s inside `verticalScroll` — no item animations possible, janky at
  scale.
- **Where:** `ui/screens/HojeScreen.kt`, `MaresScreen.kt`, `PautaScreen.kt`.
- **How:** restructure each tab to a single `LazyColumn` with `item{}`/`items()`
  blocks (headers/cards become items). Keep keys = entity ids (enables
  `animateItem()` in A3). Marés' horizontal month strips stay as nested
  horizontal `Row(horizontalScroll)` inside items — that's fine.
- **Accept:** visual parity (spacing/paddings unchanged), scroll behaviour
  intact, stable keys on all `items()`.

### A3 · Core-loop micro-animations — Status: done (PR #105)
- **Why:** completing an intention / filling a habit cell — the app's central
  interactions — are instant binary flips today.
- **Where:** `HojeScreen.kt` (intention row), `TideHelpers.kt`/`MaresScreen.kt`
  (cells), the "pulse line" composables in `HojeScreen.kt`/`PautaScreen.kt`.
- **How:** strikethrough draws left→right (~250ms) + circle spring-fill on
  intention done; habit cell fill springs from centre, respiro hatching draws
  in diagonally; pulse-line numbers animate count-up; list add/remove uses
  `Modifier.animateItem()`. All gated on `prefs.reducedMotion` (instant when on).
- **Accept:** reduced-motion ON reproduces today's instant behaviour exactly.

### A4 · Native date/time pickers — Status: done (PR #106)
- **Why:** `PautaSheets.kt` (ManualBlockSheet) asks for free-text `YYYY-MM-DD`
  + `HH:MM`; `ui/screens/HabitFormSheet.kt` has a free-text clock field. Worst
  UX edge in the app.
- **How:** Material3 `DatePickerDialog`/`TimePickerDialog` (or TimeInput),
  skinned with `LocalPautaColors` tokens; keep the stored string formats
  unchanged (repo/backup layers untouched).
- **Accept:** no free-text date/time entry remains; stored formats identical.

### A5 · Bottom sheets on phones — Status: done (PR #107)
- **Why:** `ui/PautaSheet.kt` is a centred `Dialog` (440dp, 30dp close button
  top-right, no enter/exit animation, no drag-dismiss) — a desktop-web idiom,
  hostile to one-handed use.
- **How:** screen width < 600dp → `ModalBottomSheet` (drag handle,
  drag-to-dismiss, `imePadding()`); ≥ 600dp keep the centred dialog. Same
  `content` slot so the ~10 existing sheets don't change internally.
- **Accept:** all sheets work in both modes; keyboard never covers fields.

### A6 · Keyboard + validation pass — Status: done (PR #108)
- **Where:** all form sheets (`PautaSheets.kt`, `HabitFormSheet.kt`,
  `WeekAheadSheet.kt`, `GoalsScreen.kt`), reflection fields in `HojeScreen.kt`.
- **How:** autofocus first field on sheet open; IME action Done submits;
  clear + dismiss keyboard after add; inline validation (accent/danger
  underline + small hint) instead of silently disabled buttons; debounced
  "guardado ✓" mono indicator after reflection edits.
- **Accept:** every sheet usable end-to-end without touching anything but the
  keyboard.

### A7 · Undo instead of confirm + habit archive — Status: done (PR #109)
- **Why:** deleting a habit cascades years of logs/respiros/counts behind one
  confirm; intention/block deletes are instant and unrecoverable; habit delete
  hides behind an undiscoverable long-press on the name (`MaresScreen.kt`).
- **How:** snackbar-undo for intention/block delete (hold entity in memory,
  reinsert on undo). Habits get `archived: Boolean` (Room migration v2→v3 in
  `data/AppDatabase.kt`, default false): archive hides from grid/today-strip
  but keeps all data; archived list + restore lives in Settings → data. Move
  delete (and archive) into `HabitDetailSheet.kt`/edit sheet; remove the
  long-press-to-delete gesture. **Backup note:** archived habits still export
  as normal v4 habits (flag itself is native-only and may be lost on a web
  round-trip — acceptable, document in PR).
- **Accept:** WebBackup tests green; no data-destroying single-tap path left.

### A8 · Real back stack + predictive back — Status: done (PR #110)
- **Why:** Settings is a `Dialog`, History swaps in-place, Goals/YearReview/
  TierGuide are hand-rolled overlays in `MainScaffold.kt` — none participate
  in predictive back.
- **How:** promote them to navigation destinations (navigation-compose is
  already a dep), `android:enableOnBackInvokedCallback="true"` in the
  manifest, verify Android 14+ predictive-back animation on each.
- **Accept:** back gesture peels each overlay predictively; state survives
  rotation.

## Phase B — data safety

### B1 · SAF auto-backup + WorkManager + backup rules — Status: done (PR #111)
- **Why:** auto-backups go to app-private `filesDir/autobackups`
  (`data/PautaRepository.kt` ~line 745) and only run while the app is open
  (called from `AppViewModel`); uninstall or device loss takes them. Also
  `allowBackup` is default-on with `pinHash`/`pinSalt` inside the Room DB.
- **How:** Settings option to pick a folder via SAF
  (`ACTION_OPEN_DOCUMENT_TREE` + persisted URI permission); write the existing
  cadence there (keep filesDir copies as fallback); move the cadence to
  WorkManager (`androidx.work:work-runtime-ktx` — allowed) so it runs without
  opening the app; add `dataExtractionRules` excluding PIN material (or
  document the decision to include it).
- **Accept:** backup lands in the user folder on schedule with app closed;
  rules XML present and referenced in the manifest.

### B2 · Updater polish — Status: done (PR #115)
- **Why:** `service/AppUpdater.kt` swallows all errors (`runCatching` → null →
  silently "no update") and never retries; release notes are fetched in the
  JSON but never shown.
- **How:** surface the GitHub release `body` (rendered plainly) in the
  Settings update card; retry with backoff (2s/4s/8s) on transient failures;
  distinguish "offline / failed" from "up to date" in the UI state.
- **Accept:** airplane-mode check shows an error state, not "up to date".

## Phase C — surfaces (where a planner earns daily use)

### C1 · Glance Marés widget — Status: done (PR #116)
- **Why:** the current widget (`res/layout/widget_pauta.xml`,
  `service/PautaWidgetProvider.kt` + `WidgetSnapshot.kt`) is three static text
  lines. The killer widget for this app is today's tides, tappable.
- **How:** new Glance widget (`androidx.glance:glance-appwidget` — allowed):
  today's due habits as circles (done = filled accent, respiro = hatched feel,
  pending = outline), tap marks done via the repo (process-wide scope like
  `FocusActionReceiver` does), header shows `n/m marés`. Paper/ink colours,
  dark-mode aware. Keep or retire the old widget — decide and note in PR.
- **Accept:** marking from the widget updates the app and the widget without
  opening the UI.

### C2 · Actionable notifications — Status: done (PR #117)
- **Where:** `service/ReminderReceiver.kt`/`ReminderScheduler.kt`,
  `service/FocusService.kt`, `FocusActionReceiver.kt`.
- **How:** reflection reminder gets `RemoteInput` direct-reply that saves the
  night's reflection from the shade; habits reminder lists pending tides
  (inbox style) with up to 3 "Feito ✓" actions; focus notification shows
  progress toward `targetMs` when set, and FocusService posts a one-time
  gentle "target reached" notification (today the goal-reached prompt is
  in-app only — a timer must alert from the lock screen).
- **Accept:** reflection written from the notification appears in Hoje; tide
  marked from the notification updates streaks; target alert fires with the
  app backgrounded.

### C3 · Biometric unlock — Status: done (PR #118)
- **Why:** PIN-only today (`ui/screens/PinScreen.kt`, hash in prefs).
- **How:** `androidx.biometric:biometric` (allowed); when a PIN is set, offer
  fingerprint/face at the lock screen with PIN as fallback; toggle in
  Settings → PIN section.
- **Accept:** unlock works with biometrics; PIN path unchanged; no biometric
  hardware → today's behaviour exactly.

### C4 · Quick capture + per-app language — Status: done (PR #119)
- **How (3 small platform items):**
  1. Static app shortcuts (`res/xml/shortcuts.xml`): "Nova intenção",
     "Começar bloco", "Marés" → route via intents like the existing
     `SHORTCUT_FOCUS` action in `MainActivity.kt`.
  2. `ACTION_SEND` (text/plain) share target → confirm-and-add as today's
     intention.
  3. Per-app language: `android:localeConfig` + map `prefs.lang` through
     `AppCompatDelegate.setApplicationLocales` (appcompat already a dep) so
     the in-app PT/EN choice and Android 13+ system setting stay in sync.
- **Accept:** all three reachable from the OS; language changes stay in sync
  both directions.

## Phase D — finish the features the data layer already has

### D1 · Routines UI — Status: done (PR #120)
- **Why:** `RoutineEntity`/`RoutineItemEntity` exist in Room and round-trip in
  the v4 backup, but there is **no UI at all** — dormant shipped feature.
- **How:** manage routines (create/edit/reorder; items carry text, priority,
  targetMin — fields already exist) — suggest Settings or a Hoje sheet; on
  Hoje, "Aplicar rotina" seeds today's intentions from a routine (fresh ids,
  preserve priority/targetMin, append after existing).
- **Accept:** create routine → apply → intentions appear; backup round-trip
  unchanged (shape already supports it).

### D2 · Per-habit reminders — Status: done (PR #121)
- **Why:** the habit form collects + validates an `HH:MM` `clock` per habit,
  but `ReminderScheduler.kt` only schedules 3 global alarms — the field is
  never used (silent broken promise).
- **How:** schedule a daily alarm per habit with a non-blank clock, skipping
  days where the habit isn't due (`HabitCalculator.dailyDueOn`) or already
  done/respiro'd; notification carries a "Feito ✓" action (reuse C2 plumbing
  if landed); requestCode space distinct from the 3 global kinds; reschedule
  on boot via the existing `BootReceiver`.
- **Accept:** habit with clock fires on due days only; marking from the
  notification works; no duplicate with the global habits reminder.

## Phase E — memory (the soul feature)

### E1 · Search — Status: done (PR #122)
- **Why:** years of reflections, intentions and block notes are write-only;
  no way to find anything.
- **How:** Room FTS4 table over intention text, day reflections, block
  titles/reflections (populate via triggers or on write in
  `PautaRepository.kt`); search field at the top of the History view
  (`ui/screens/HistoryView.kt`), results grouped by day, tap → that day.
- **Accept:** matches across all three sources; accent-insensitive
  (`unicode61` tokenizer) for PT.

### E2 · Memórias — Status: done (PR #123)
- **How:** on Hoje, when a reflection exists for the same date one (or more)
  years ago, show a quiet card: `HÁ UM ANO` eyebrow + the reflection in serif
  italic; dismissible per day. Pure local query over `days`.
- **Accept:** appears only when history exists; PT/EN; dismiss persists for
  the day.

## Phase F — signature visuals

### F1 · Tide-rise focus card — Status: done (PR #125)
- **Why:** unify the app's two metaphors: the focus block fills like a rising
  tide.
- **How:** in the active block card (`ui/screens/PautaExtras.kt` /
  `PautaScreen.kt`), when `targetMs` is set, a barely-visible accent "water
  level" rises bottom-up with progress (Canvas behind content, very low
  alpha on `surfaceDark`); on reaching target, a single quiet wave-crest
  sweep + haptic (A1) — once, not looping. Static fill at the right level
  when `reducedMotion`.
- **Accept:** subtle (legibility of the 42sp timer unaffected); no battery
  churn (animate with the existing 1s tick, no per-frame loop while idle).

### F2 · Pip moods + empty states — Status: done (PR #126)
- **Why:** Pip (`ui/Parrot.kt`) breathes, blinks and speaks but is
  context-blind; empty states are plain ink4 text.
- **How:** state-driven moods using the existing frame-loop rig: celebratory
  flutter (once) when the last intention of the day completes; sleepy
  half-lid eyes after the reflection hour; calm watching during an active
  block. Small Pip pose next to the Hoje/Pauta/Marés empty-state phrases.
  Honour `prefs.parrot` (hide) and `prefs.reducedMotion` (static poses).
- **Accept:** moods triggered by real state; empty states show Pip; both
  prefs respected.

## Phase T — hygiene (any time, can ride along with another task)

### T1 · Midnight ticker — Status: pending
- **Why:** `AppViewModel` runs a 30-second `while(true)` ticker for the
  process lifetime just to catch midnight.
- **How:** `delay(ms until next local midnight)` + re-check on `ON_RESUME`
  (covers doze/clock changes); keep `runRollover` semantics identical.

### T2 · Version surfacing — Status: pending
- **Why:** `versionName` is a static `"1.0"`; About already shows
  `BUILD_TS`/`BUILD_RUN`.
- **How:** derive a human `versionName` (e.g. `1.<BUILD_RUN>`) in
  `app-native/app/build.gradle.kts`; show run + date together in Settings.
- Also dead `prefs.sound`: implement a soft conclude chime (default off) or
  remove the toggle — decide here.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-06-15 · A1 · #97 · haptics helper + 6 call sites -->
2026-06-12 · A2 · #104 · Hoje/Marés/Pauta tabs → single LazyColumn, stable item keys (unblocks A3)
2026-06-13 · A3 · #105 · intention strike+circle spring, tide-cell fill+respiro hatch, pulse count-up, list animateItem — all gated on reducedMotion
2026-06-13 · A4 · #106 · native DatePicker/TimePicker behind tappable fields (ManualBlock date+time, habit clock, Settings reminders); LocalPautaColors-skinned; stored YYYY-MM-DD/HH:MM unchanged
2026-06-13 · A5 · #107 · PautaSheet → ModalBottomSheet on phones (<600dp; drag handle, drag-dismiss, imePadding), centred dialog kept ≥600dp; same content slot, all ~dozen sheets unchanged
2026-06-13 · A6 · #108 · keyboard+validation: autofocus first field (Start/Manual/AddHabit/Switch/WeekAhead day1/Goals-empty), IME Done submits (Manual title→duration chain), inline danger underline+hint replaces disabled buttons, debounced "guardado ✓" on Hoje reflection (reducedMotion-gated); shared FieldError/DangerRed/rememberAutoFocusRequester
2026-06-13 · A8 · #110 · Settings/History/Goals/YearReview/TierGuide → NavHost destinations (predictive back); enableOnBackInvokedCallback=true; single Activity-scoped AppViewModel re-pinned across routes (LocalViewModelStoreOwner); BackHandler dropped from promoted screens (guarded one kept only for Settings' nested PIN flows); HistoryView owns its status-bar inset; nav push/peel transitions gated on reducedMotion
2026-06-13 · A7 · #109 · snackbar "Anular" for intention/block delete (snapshot+reinsert from memory; block restores its sessions), app-wide themed SnackbarHost in MainScaffold; native-only habit `archived` (Room v2→v3) hides from grid/today/widget but keeps marks + still exports as a normal v4 habit; delete+archive moved into the edit sheet, long-press-to-delete dropped; Settings→Dados "Marés arquivadas" manager (1-tap restore, 2-step guarded delete)
2026-06-13 · B2 · #115 · AppUpdater.check → CheckResult (Available/UpToDate/Failed) so offline ≠ "atualizado"; 2s/4s/8s backoff retry shared by check+download; release `body` surfaced as plain "Novidades" notes in the Settings card; failed-check error + "Tentar outra vez" re-check branch
2026-06-13 · C1 · #116 · Glance Marés widget — today's tides as tappable circles (done=filled accent / respiro=hatched ring / pending=outline), marked via the repo on the process scope (works app-closed); header n/m marés; day/night ColorProviders mirror Color.kt; reuses computeTodayTides; retired the 3-line text widget (PautaWidgetProvider/WidgetSnapshot)
2026-06-13 · C2 · #117 · actionable notifications — reflection reminder RemoteInput direct-reply (appends, never overwrites; re-posts a saved-confirmation), habits reminder InboxStyle of pending tides + up to 3 "✓ tide" done actions (mark via repo on the process scope, refresh notif + widget), focus notification counts down to targetMs (system chronometer, no per-second wakeups) with a one-shot AlarmManager "Alvo de foco alcançado" alert on a high-importance channel (fires from the lock screen, app backgrounded; armed on start, cancelled on pause/conclude, kept across sticky restart). New ReminderNotifications/ReminderActionReceiver/FocusTargetScheduler/FocusTargetReceiver/FocusNotifications. Also: post-update "what's new" overlay shows the stashed release notes once (device-local SharedPreferences, not exported in v4)
2026-06-13 · B1 · #111 · SAF backup folder (ACTION_OPEN_DOCUMENT_TREE + persisted permission, DocumentsContract write/prune, no extra dep) written alongside filesDir fallback; WorkManager periodic BackupWorker/BackupScheduler runs the cadence with app closed (gate stays in repo; resume path moved off Main); native-only `backupFolderUri` (Room v3→v4, not exported in v4); data_extraction_rules.xml + backup_rules.xml exclude pauta.db (PIN+journal)/reminder prefs/autobackups from cloud-backup, device-transfer keeps all
2026-06-13 · C4 · #119 · static launcher shortcuts (Nova intenção→Hoje, Começar bloco→Pauta via SHORTCUT_FOCUS, Marés→Marés; explicit intents need no filter, accent vector glyphs, labels in strings.xml + values-en); ACTION_SEND text/plain share target → PautaSheet confirm → addIntention(today); per-app language android:localeConfig + AppCompatDelegate.setApplicationLocales two-way synced with prefs.lang (adopt explicit OS change on fresh activity, mirror in-app change after; API 33+, savedInstanceState guard stops recreate re-firing the entry); unified AppEntry parsed in MainActivity + onNewIntent so entries work cold or warm; no new deps, no v4/Room changes
2026-06-13 · C3 · #118 · biometric unlock — androidx.biometric; MainActivity→FragmentActivity (still a ComponentActivity, Compose unaffected); ui/Biometric.kt (canUseBiometric/findFragmentActivity/promptBiometric, weak class, UI-gate only, negative button→PIN keypad); PinScreen LOCK auto-prompts once + "Usar biometria" re-summon (SET/DISABLE unchanged); Settings→Privacidade toggle shown only with PIN set + biometrics available; native-only `biometricEnabled` (Room v4→v5, not exported in v4, cleared with the PIN)
2026-06-15 · D2 · #121 · per-habit reminders — HabitReminderScheduler mirrors the global one (persists active tides' id→clock + master flag to SharedPreferences, re-arms at boot Room-free; per-id request codes, dedicated receiver); HabitReminderReceiver re-arms next day then posts only on a still-open due day (DayState.EMPTY via pendingTides → honours dailyDueOn, skips done/respiro, clears stale); postHabitReminder reuses C2 ACTION_HABIT_DONE (mark app-closed, then clears the notif); global digest excludes clock'd tides (new digestTides) → no double-reminder; gated on remindersEnabled; no Room migration / no v4 change (reuses HabitEntity.clock, bookkeeping is device-local)
2026-06-15 · D1 · #120 · Routines UI — RoutinesSheet manager off a Hoje "Rotinas ↗" chip: create from scratch / "guardar como rotina" (snapshot today), rename, edit items (text/priority/targetMin), reorder ↑/↓, two-step delete, one-tap "aplicar" → seeds today via the carry-over path (fresh ids, priority/targetMin preserved, appended). RoutineDao gains updateItem/deleteItemByRowId + getRoutineById/getItemByRowId/getItemsForRoutine; repo+VM routine CRUD/reorder/apply/saveFromToday. No Room migration (tables predate v1), no WebBackup change (v4 already round-trips); item-less routine kept native↔native, dropped on a web round-trip (matches web sanitize)
2026-06-15 · E2 · #123 · Memórias — quiet "on this day" card on Hoje (above the night reflection) when a prior-year reflection lands on today's MM-DD: mono "HÁ UM ANO"/"HÁ N ANOS" eyebrow + serif-italic reflection, newest year first; pure HojeLogic.memories() over `days` (+ unit tests). Dismiss-for-the-day via corner × → native-only PrefsEntity.memoriaDismissedDay (Room v6→v7, stamped with todayKey, clears at midnight, not exported in v4 — WebBackup untouched). i18n há um ano/há {n} anos/dispensar
2026-06-15 · F1 · #125 · Tide-rise focus card — accent water level rises bottom-up with target progress behind the active block card + full-screen zen (Canvas/drawBehind, ~0.12 alpha over surfaceDark); one quiet wave-crest sweep + gated haptic on reaching target, once; reducedMotion → static fill at level (no sweep); driven by the existing 1s tick + a one-shot Animatable (no idle per-frame loop); shared FocusTide.kt (rememberTideCrest + DrawScope.drawTide); no deps, no Room/v4 change. Note: introduces the first prefs.haptics-gated HapticFeedback (target-reached only; A1 still pending)
2026-06-15 · F2 · #126 · Pip moods + empty states — corner companion now reads real state (passed from the shell): WATCHING gaze + slower breath during an active focus block, SLEEPY half-lids after prefs.reflectionTime (re-checked each minute), one-shot celebratory flutter on the day's last intention completing (false→true edge → a decoupled trigger+effect so re-adding right after can't strand the hop); drawParrot gains eyeOpen+gaze params; new PipPose beside the Hoje/Pauta/Marés empty-state phrases; prefs.parrot hides Pip (corner + poses), prefs.reducedMotion holds a static pose (per-frame loop gated off, bubble/scale snap to target). UI-only — no strings/deps/Room/v4 change
2026-06-15 · E1 · #122 · Search — `search_index` FTS4 table (unicode61 + remove_diacritics → accent-insensitive PT) over intention text / day reflections / block title+reflection, kept in sync by SQL triggers on the 3 source tables (covers single edits, import, reseed, reset); deliberately NOT a Room @Entity (so Room won't create/validate it — we own the tokenizer), created in MIGRATION_5_6 (+backfill) and a fresh-install RoomDatabase.Callback, DB v5→v6. SearchDao @RawQuery→SearchHit; repo.search() prefixes each word + neutralises punctuation so it can't be read as an FTS operator. HistoryView gains a live search field (blank=day list, typing=hits grouped by day with a source tag); tapping any day (row or hit) opens a read-only day detail (intentions/blocks/reflection — handles block-only days), nested BackHandler peels it back first. VM searchQuery+searchResults (flatMapLatest, cleared on close). Block day derived from createdAt in local time. Index is derived state → not in the v4 export, WebBackup tests unaffected
