# Context — the state of the world

> **What this is.** The briefing a session needs *before* it opens a task file:
> what exists, what shipped, what is being worked on, what is known to be broken
> and what has actually been run on a device. It replaces the paragraph of
> history that otherwise gets re-derived — or worse, guessed at — every time a
> cold session starts.
>
> **This file is updated in the same PR as the work it describes.** A task
> shipped, a file completed, an order changed: it lands here too. A stale
> CONTEXT is worse than none, because it is believed.

**Last updated:** 2026-08-03 · **Room:** v11 · **Released:** `v1.443`
(2026-08-02) · **Branch of record:** `main`

---

## 1 · What the app is

**Pauta** — a free, private, offline-first daily planner *and* reading companion
for Android. Kotlin + Jetpack Compose, no account, no server, no tracking. One
`bookMode` boolean turns the same three tabs from **Hoje / Pauta / Marés** into
**Estante / Sessão / Hábitos**, and a second launcher icon opens straight into
the second face.

It began as a no-build React app wrapped in Capacitor. The native rewrite
reached parity and replaced it in **June 2026**; the whole web-era tree is
preserved on the `web-legacy-final` branch. Book mode and the in-app PDF/EPUB
reader came after, on the native tree only.

The only code that opens a socket is the in-app updater, which polls the rolling
`latest-native` GitHub release and installs updates in place.

## 2 · Where to look for what

| You need | Read |
|---|---|
| What you may and may not do | **`docs/GUARDRAILS.md`** — binding |
| A column, a migration, what `currentPage` means | **`docs/DATA_MODEL.md`** |
| The state of the work | **this file** |
| The shape a task file takes | `docs/TASK_FILE_FORMAT.md` |
| Which file owns an area | `docs/README.md` |
| Build, run, conventions, workflow | `CLAUDE.md` |
| Why something *looks* the way it does | the **Log** of the file that shipped it, in `docs/archive/` |

**A cold session's complete briefing** is: `CLAUDE.md` → `docs/GUARDRAILS.md` →
this file → your task file. Nothing else needs opening unless a task names it.

## 3 · The work, at a glance

**23 tasks pending across two active files, plus one new file.**

| File | Scope | Tasks | State |
|---|---|---|---|
| `docs/FIRST_RUN.md` | The edges: the permission never asked for, the empty screens, the front doors. Written from a device run, 2026-08-03 | N1…N8 | **N1 first, ahead of everything** |
| `docs/BOOK_LIBRARY.md` | Book mode round three. Phase L-0 = promises the app already makes and does not keep | L1…L12 | L1, L2 **done**; L3…L12 pending |
| `docs/FIELD_FIXES.md` | Defects found by *using* the app. Ordered by what each costs the person using it | F1…F13 | all pending |

**Archived** (complete, in `docs/archive/`): `NATIVE_IMPROVEMENTS.md` (A1…T2),
`BOOK_MODE.md` (K1…K9), `POLISH.md` (P1…P10), `BOOK_READER.md` (R1…R8),
`UX_FIXES.md` (U1…U7). Their Logs are the reasoning behind everything that
shipped — read them when you need to know *why*, never to know *what to do*.

### The order

```
N1  ──────────────────────────  ahead of everything: shipped features that do nothing
 │
L3  ──────────────────────────  closes Phase L-0
 │
F1 → F2 ─────────────────────  one story; F2 already depends on F1
 │
F3 → F4 ─────────────────────  the remaining data-loss tasks
 │
F5 … F13  ───────────────────  as written in that file
 │
L4 · L5 · L6  ───────────────  independent of each other
L7 → L8
L9 · L10 · L11 · L12
 │
N2 … N8  ────────────────────  unless N8 is pulled ahead of L3 (see FIRST_RUN)
```

**Why N1 jumps the queue:** it is the only known defect where features that
already shipped do nothing at all. `L10` (a reading reminder) would add a fourth
notification onto the same broken floor.

**Why L3 is next after it:** it is the last of Phase L-0 — the app failing to do
what it says it does — and `FIELD_FIXES.md` explicitly runs behind that phase.

## 4 · What has actually been run

`FIELD_FIXES.md` exists because a green test suite and a working app turned out
to be different things. This section is the antidote: what a machine has
genuinely observed, and when.

### Automated

`./gradlew :app:testDebugUnitTest` covers `domain/` — the pure arithmetic
(`BookMath`, `ReaderMath`, `HabitCalculator`, `InsightsMath`), the EPUB parser
and the `pauta.v4` backup converter. CI runs it on every push touching
`app-native/` and publishes the APK.

**Untested by construction:** composables, intents, the WebView, the state
machine of a settings row, anything needing a device. Every defect in
`FIELD_FIXES.md` and `FIRST_RUN.md` lives there.

### On a device or emulator

| Date | Build | Where | What it showed |
|---|---|---|---|
| 2026-08-03 | `v1.443` | Pixel 7 AVD, Android 15, 1080×2400 @420dpi | The run that produced `FIRST_RUN.md`. Confirmed on screen: F1 (an EPUB receipt reading "33 págs em 4 min"), F5(b) (top bar over the chapter heading, bottom bar over the last line), F8 (composer labels and header chips), F11 (Pip over content, and over the *primary button* in landscape), F13 (the planner's tides under a reading tab), the shelf carousel. Found new: `POST_NOTIFICATIONS` never requested (`AppSettings: com.pauta.app importance=NONE` with `FocusService` running `isForeground=true` — the notification is built and dropped), the reader chrome's 2 s auto-hide re-arming on every tap, the month strips scrolling independently and unlabelled. **Dark theme and 1.5× text scale held up with no breakage.** A deliberately corrupt EPUB was refused cleanly. |
| ~2026-08-01 | `v1.4xx` | owner's phone, real use | The run that produced `FIELD_FIXES.md`. Its evidence section records what was seen and the file:line each symptom traces to. |

**Nothing has been run on:** a physical device with a small screen, a tablet, a
foldable, API 26–30 (`minSdk` is 26; the emulator was 35), or with TalkBack
actually enabled. Say so rather than implying otherwise.

## 5 · Known and not yet written down

Things a session might otherwise trip over.

- **`CLAUDE.md` carried a merge-conflict marker** (`>>>>>>> origin/main`) on
  `main` from before 2026-08-03, and described `FIELD_FIXES.md` as `F1…F16` when
  it holds F1…F13. Both fixed in the PR that created this file. If you see
  either again, a merge went wrong.
- **Two task files are open at once**, which is unusual here and deliberate:
  they were written the same week from different angles — one from a code
  review, one from use. `docs/README.md` explains the precedence.
- **Room version collisions are real.** v9 was claimed by `UX_FIXES` U2 while
  `BOOK_READER` R2 was in flight. Claim the number in the task file before
  writing code. See `docs/DATA_MODEL.md`.
- **Three tasks were dropped from `FIELD_FIXES.md`** — a chapter index, the
  shelf at scale, notes anchored to a position — in favour of `BOOK_LIBRARY.md`
  L4, L8 and L6, which cover the same ground with more of it. Don't reinstate
  them.

## 6 · Open questions for the owner

Carry these forward until answered; a session that hits one should stop rather
than guess.

| Question | Where it blocks | Current default |
|---|---|---|
| Are the two "start a block" affordances on the empty Pauta tab deliberate — one quick, one with options? | `FIRST_RUN.md` N7 | Written as duplication, with a note that it collapses to a relabel if not |
| *Metas de leitura* — self-set reading targets? Proposed, then argued against on the grounds that a target on an empty shelf is a form of nagging. | `FIELD_FIXES.md` F13 | Not built. Ask first |
| Does `genre` earn its keep, or go? | `BOOK_LIBRARY.md` L7 | The task decides; L8 consumes it if kept |

---

## Log (append one line per PR that changes the state of the work)

<!-- YYYY-MM-DD · #PR · <what moved, and anything a later session would otherwise re-derive> -->
2026-08-03 · — · file created alongside `GUARDRAILS.md` and `DATA_MODEL.md`; the five complete task files archived; `FIRST_RUN.md` added from a Pixel 7 emulator run of `v1.443`, with N1 placed ahead of the whole queue because it is the only finding where shipped features do nothing at all.
2026-08-02 · #182 · L2 done — `snapshot()`/`importJson()` filter book blocks both ways, the rule single-sourced in `BookBackup`; new `pauta.books.v1` export/import merges by id, carries no `filePath`/`fileKind`.
2026-08-02 · #181 · L1 done — `resetAll` now clears `book_notes`, `books` and `filesDir/books/` via `BookFiles.clearAll`; reseed inherits the fix.
