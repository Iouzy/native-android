# Pauta → Native (Kotlin + Compose) — Migration Plan

Approved plan for rewriting Pauta as a 100% native Android app (Jetpack Compose),
replacing Capacitor/WebView. The **web app is the specification**: each phase is
"done" only when its behaviour is indistinguishable from the web app, side by
side — not when a Kotlin file exists.

## Decisions
- **Fresh start.** The earlier `android-native/` scaffold was discarded; nothing
  is reused from it (it remains in git history). New project lives in `app-native/`.
- **Review at the end.** Work proceeds through the phases without per-phase
  approval gates; parity is reviewed globally near the cutover.
- Branch: development happens on the session's working branch; PRs target `main`.

## Non-negotiables (preserved)
Name **Pauta** + appId **com.pauta.app** · 3 tabs Hoje/Pauta/Marés with swipe +
shortcuts 1/2/3 · design tokens, typography, maritime theme (light/dark/auto),
live accent · **Pip** + animations · Marés levels (Onda → Tsunami/Oceano) +
**Respiro** · i18n PT-source + EN dictionary · AlarmManager reminders ·
offline-first (no account/network/tracking) · **import of `pauta.v4` backups** ·
licence CC BY-NC 4.0.

## Phases
0. **Clean start.** Discard old scaffold; new Compose project, build config,
   signing (committed `debug.keystore`), CI building an artifact (no release yet).
1. **Foundations.** Room data model mirroring `pauta.v4` 1:1; theme tokens to the
   pixel + vendored fonts; navigation shell (swipe + 1/2/3); live language/accent.
2. **Tab Hoje** — full parity with `tab-hoje.jsx` (pulse, intentions, reflection,
   carry-over, tide strip, week planner, history, share card, midnight rollover).
3. **Tab Pauta + Focus service** — timer (start/pause/resume/conclude/switch),
   timeline, manual blocks, Pomodoro, link-to-intention; foreground service with
   notification + action buttons that work in the background.
4. **Tab Marés** — monthly grid, daily/weekly/monthly cadence + anchors/weekdays,
   countables, **levels** + tier-up toast/haptic, **Respiro**, and the charts
   (WaveChart, HeatmapAllTime, 12-month TrendSheet) in Canvas.
5. **Pip** — own animation engine (breathing, random blink, peek⇄out + bubble) +
   the full bilingual phrase library; hides on sheets/onboarding/background.
6. **Settings / reminders / widget / tile / updater** — complete SettingsSheet
   incl. real accessibility (textScale, reduced-motion, high-contrast, immersive);
   AlarmManager reminders + boot re-arm; home widget; QS tile; in-app APK updater.
7. **Extras** — quarterly goals + milestones, routines, week planner, onboarding,
   weekly insights (the `extras.jsx` content).
8. **Data compatibility** — rebuild `pauta.v4` import + `{app,version:4,…}` export;
   bidirectional round-trip with real backups; weekday re-base, `when`, legacy
   priorities, local dayKey, active-block auto-pause. Unit tests.
9. **Cutover** — epoch-monotonic versionCode, keystore intact, move the rolling
   release publish to the native build (gated on `main`), with explicit rollback.

## Data contract (pauta.v4 → Room)
dayKey = `YYYY-MM-DD` in **local** time (not UTC); timestamps in ms epoch; habit
`log`/`respiros`/`counts` are sparse maps (presence = done); a block carries
`sessions[]` (pause/resume cycles summed for focus time); export is wrapped as
`{app:"pauta",version:4,exportedAt,data}`. Entities: days+intentions, focus
blocks+sessions, habits (+log/respiro/count), goals+milestones, routines, plans,
prefs. Import re-bases weekday JS(0=Sun)→native(0=Mon), maps `when`
manha/tarde/noite, coerces legacy priorities, and auto-pauses active blocks.

## Risks
Subtle parity gaps (the reason the earlier cutover was reverted — hence parity,
not file-existence, is the bar) · data compatibility (one mismapped field
corrupts backups → dedicated Phase 8) · aggressive OEMs killing alarms/services
(fallbacks + boot re-arm) · exact-alarm / POST_NOTIFICATIONS permissions (runtime
+ graceful degradation) · Material3 vs CSS visual fidelity (pixel comparison) ·
versionCode/keystore must never regress · rebuilding the backup converter from
scratch loses the old tests' safety net (mitigated by new Phase-8 tests).

## What ships meanwhile
Throughout development the **web app remains the shipping release** — users lose
nothing. Native APKs are validation artifacts gaining tabs phase by phase. The
only change for real users is the Phase 9 cutover, with rollback ready.
