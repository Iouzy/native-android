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

**Last updated:** 2026-08-06 · **Room:** v14 · **Released:** `v1.443`
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

**22 tasks pending across two active files, plus one new file.**

| File | Scope | Tasks | State |
|---|---|---|---|
| `docs/FIRST_RUN.md` | The edges: the permission never asked for, the empty screens, the front doors. Written from a device run, 2026-08-03 | N1…N8 | N1, N2 **done**; N3…N8 pending |
| `docs/BOOK_LIBRARY.md` | Book mode round three. Phase L-0 = promises the app already makes and does not keep | L1…L12 | **all done — file complete** |
| `docs/FIELD_FIXES.md` | Defects found by *using* the app. Ordered by what each costs the person using it | F1…F13 | F1…F7 **done** (F7 partial — its chrome half waits on L4); F8 **done**; F9 **done** (partial); F10 **done**; F11 **done**; F12 **done**; F13 **done** — file complete |

**Archived** (complete, in `docs/archive/`): `NATIVE_IMPROVEMENTS.md` (A1…T2),
`BOOK_MODE.md` (K1…K9), `POLISH.md` (P1…P10), `BOOK_READER.md` (R1…R8),
`UX_FIXES.md` (U1…U7). Their Logs are the reasoning behind everything that
shipped — read them when you need to know *why*, never to know *what to do*.

### The order

```
N1  ──────────────────────────  ahead of everything: shipped features that do nothing
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
N2 … N8  ────────────────────  see FIRST_RUN
```

**N1 is done**, so the notification floor is no longer broken and `L10` (a
reading reminder) can be built on it — it inherits the permission and needs no
permission code of its own, exactly as its Out-of-scope says.

**Phase L-0 is closed** (L1, L2, L3): the promises the app made and did not keep
— a reset that spared books, a backup that carried them, a status set the shelf
only half rendered. `FIELD_FIXES.md` ran behind that phase and is now unblocked,
so F1 follows N1.

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

**L3 has not been seen on a screen.** CI compiled it and `BookStatusTest` covers
the status→shelf map, but the EM PAUSA section, the detail sheet's five-state
table and the two-step "Abandonar" have only ever existed as source. Everything
in this section that says *shipped* still means *compiled*.

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
| ~~*Metas de leitura* — self-set reading targets?~~ **Closed 2026-08-06 (#187):** asked, unanswered, and F13 shipped without them on the file's own argument — a target on an empty shelf is nagging, which `GUARDRAILS.md` §A forbids. Reversible: nothing was built that would have to be undone. | — | Not built |
| ~~Does `genre` earn its keep, or go?~~ **Closed 2026-08-06 (#187):** kept. Dropping it meant a dead column plus a form that quietly stopped collecting what people had already filled in; keeping it cost one `split`. L8 consumes `BookMath.genreTags`. | — | Kept |

---

## Log (append one line per PR that changes the state of the work)

<!-- YYYY-MM-DD · #PR · <what moved, and anything a later session would otherwise re-derive> -->
2026-08-06 · #187 · N1 done — the notification floor. `ui/Permissions.kt` is the single owner of "may we notify, and have we asked?"; three call sites share it. One pref `notifAskedAt`, Room **11 → 12** — a slot `BOOK_LIBRARY.md` L5 had claimed, so **L5 moved to 12 → 13 and L10 to the next free one after it**; both task files were edited in this PR. Two things a later session should not re-derive: read `areNotificationsEnabled()`, not `checkSelfPermission`, because a user can silence the app without touching the permission and the Settings row has to say so; and the blocked row deliberately has no switch, because a switch that cannot move reads as broken. **Nothing was run** — no SDK here, so no compile and no tests locally, and the migration has never been executed (this repo has no instrumentation tests).
2026-08-05 · #186 · L3 done — **Phase L-0 closed**. `domain/BookStatus` is now the single source of the five statuses and the shelf each maps to, asserted total in both directions by `BookStatusTest`; `setBookStatus` is the one door a book changes state through, owning `startedAt`/`finishedAt`/`position`. Two things a later session should not re-derive: shelf `position` is allocated as *max + 1*, never as the shelf's size, because a departure leaves a hole and `ORDER BY position` has no tiebreaker (`addBook` was fixed the same way); and the branch this shipped from was cut before the docs foundation existed, so it carried a wrong PR number and no `CONTEXT.md` edit — check both when a branch predates `dd4b6c9`.
2026-08-03 · — · file created alongside `GUARDRAILS.md` and `DATA_MODEL.md`; the five complete task files archived; `FIRST_RUN.md` added from a Pixel 7 emulator run of `v1.443`, with N1 placed ahead of the whole queue because it is the only finding where shipped features do nothing at all.
2026-08-02 · #182 · L2 done — `snapshot()`/`importJson()` filter book blocks both ways, the rule single-sourced in `BookBackup`; new `pauta.books.v1` export/import merges by id, carries no `filePath`/`fileKind`.
2026-08-02 · #181 · L1 done — `resetAll` now clears `book_notes`, `books` and `filesDir/books/` via `BookFiles.clearAll`; reseed inherits the fix.
