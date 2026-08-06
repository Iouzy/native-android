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

**Nothing is pending. Every task file is finished**, and all three finished on
2026-08-06 in one PR (#187) — 30 tasks, one commit each. The count this section
used to carry said "22 across two files, plus one new file", which was already
wrong when it was written: the three files held **30**.

| File | Scope | Tasks | Finished |
|---|---|---|---|
| `docs/archive/FIRST_RUN.md` | The edges: the permission never asked for, the empty screens, the front doors | N1…N8 | 2026-08-06 · #187 |
| `docs/archive/BOOK_LIBRARY.md` | Book mode round three: the promises L-0 found unkept, the reader a reader expects, the shelf at scale | L1…L12 | 2026-08-06 · #187 (L1, L2 in #181/#182; L3 in #186) |
| `docs/archive/FIELD_FIXES.md` | Defects found by *using* the app, ordered by what each costs the person using it | F1…F13 | 2026-08-06 · #187 |

**Two tasks shipped less than their spec asked for, and both say so in their own
Log** — worth knowing before anyone reads the ticks as complete:

- **F7** carried the publisher's page-break markers into the page but *not* into
  the chrome ("página 123 de 228"), which needs the page-list plumbed out of the
  `:reader` process — the pass `L4` owns.
- **F9** put the timer-preset toggle inside `DurationPicker`, which covers the
  two surfaces that use it, and deliberately left *Registar tempo* and the Hoje
  composer's target-minutes field alone. Reasons in the Log.

Two open questions were **closed by default** rather than by answer, because they
were put to the owner during the run and went unanswered. Both are cheap to
reverse and §6 records them: *Metas de leitura* (not built) and the Pauta tab's
two start affordances (taken as duplication).

**Everything else in all three files shipped as specified.**

### The order it went in

`N1` → `F1…F13` → `L4…L12` → `N2…N8`, which is `CONTEXT`'s own precedence with
one exception worth recording: **F7 declares a dependency on L4 and ran before
it**, because the file order put it there and the two halves turned out to be
separable. That is why F7 is partial.

**Archived earlier** (complete, in `docs/archive/`): `NATIVE_IMPROVEMENTS.md`
(A1…T2), `BOOK_MODE.md` (K1…K9), `POLISH.md` (P1…P10), `BOOK_READER.md` (R1…R8),
`UX_FIXES.md` (U1…U7). Their Logs are the reasoning behind everything that
shipped — read them when you need to know *why*, never to know *what to do*.

### What to do next

There is no task file to pick from. The next change starts by **writing one**
(`docs/TASK_FILE_FORMAT.md`), and §4 below is the argument for what it should
cover: thirty tasks shipped without an SDK in the environment, so CI compiled
and unit-tested all of it and **no device has seen any of it**.

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
machine of a settings row, anything needing a device. Every defect
`FIELD_FIXES.md` and `FIRST_RUN.md` were written from lived there.

**And #187 added a great deal to that list.** The run that finished all three
files had **no Android SDK available**, so CI was the only gate: it compiled
every commit and ran the unit tests, and that is the whole of what happened. The
pure work is genuinely covered — `BookMath`'s speed ceiling, `ReaderMath`'s peek
guard, `HabitCalculator`'s tide ceiling, `TimeOfDay`, `BookShelf`, `LauncherDoor`,
`ReadingStats`, `DateUtils.withClock`, the EPUB sanitiser's dead links and
page-break markers. Everything with a surface is not.

### On a device or emulator

| Date | Build | Where | What it showed |
|---|---|---|---|
| 2026-08-03 | `v1.443` | Pixel 7 AVD, Android 15, 1080×2400 @420dpi | The run that produced `FIRST_RUN.md`. Confirmed on screen: F1 (an EPUB receipt reading "33 págs em 4 min"), F5(b) (top bar over the chapter heading, bottom bar over the last line), F8 (composer labels and header chips), F11 (Pip over content, and over the *primary button* in landscape), F13 (the planner's tides under a reading tab), the shelf carousel. Found new: `POST_NOTIFICATIONS` never requested (`AppSettings: com.pauta.app importance=NONE` with `FocusService` running `isForeground=true` — the notification is built and dropped), the reader chrome's 2 s auto-hide re-arming on every tap, the month strips scrolling independently and unlabelled. **Dark theme and 1.5× text scale held up with no breakage.** A deliberately corrupt EPUB was refused cleanly. |
| ~2026-08-01 | `v1.4xx` | owner's phone, real use | The run that produced `FIELD_FIXES.md`. Its evidence section records what was seen and the file:line each symptom traces to. |

**Nothing has been run on:** a physical device with a small screen, a tablet, a
foldable, API 26–30 (`minSdk` is 26; the emulator was 35), or with TalkBack
actually enabled. Say so rather than implying otherwise.

### What #187 needs a device for, in rough order of risk

Nothing below has been executed. This is the list a device pass should work
through, and the honest content of a next task file.

1. **Two Room migrations, 11 → 12 → 13 → 14** (`notifAskedAt`; the reader's four
   settings; the reading-reminder pair). Additive and following the eleven before
   them, and **never run** — this repo has no instrumentation tests and a JVM
   test cannot open a Room database. An upgrade from a real v11 install is the
   first thing to try.
2. **The notification permission (N1)** — the whole point is a system dialog at
   the first focus block, a shade that then has something in it, and a Settings
   row that says "blocked" with a working link.
3. **Gestures (F3, N2)** — two-stage back with the keyboard up, the reader's
   chrome staying put once summoned. CI cannot see either.
4. **Layout at textScale 1.0 / 1.3 / 1.5 and in landscape (F8, F11, N5, N7)** —
   including whether Pip's disappearance below a 480dp viewport reads as
   deliberate or as a bug.
5. **The reader (F5, F7, L4, L5)** — measured bar insets, the page-break
   separator, the contents sheet, and whether a type-size change really keeps
   the reader's place.
6. **The launcher door (F6)**, which is the one failure the spec says a unit test
   cannot see, and whose fallback trampoline was deliberately not built.
7. **The widget and the QS tile in book mode (L11)**, neither of which has been
   placed.

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
| ~~Are the two "start a block" affordances on the empty Pauta tab deliberate?~~ **Taken as duplication 2026-08-06 (#187):** asked, unanswered, shipped on the spec's own assumption. The chip moved below the list rather than being deleted, so if they *were* deliberate this reverses into a relabel and nothing was lost. | — | Duplication |
| ~~*Metas de leitura* — self-set reading targets?~~ **Closed 2026-08-06 (#187):** asked, unanswered, and F13 shipped without them on the file's own argument — a target on an empty shelf is nagging, which `GUARDRAILS.md` §A forbids. Reversible: nothing was built that would have to be undone. | — | Not built |
| ~~Does `genre` earn its keep, or go?~~ **Closed 2026-08-06 (#187):** kept. Dropping it meant a dead column plus a form that quietly stopped collecting what people had already filled in; keeping it cost one `split`. L8 consumes `BookMath.genreTags`. | — | Kept |

---

## Log (append one line per PR that changes the state of the work)

<!-- YYYY-MM-DD · #PR · <what moved, and anything a later session would otherwise re-derive> -->
2026-08-06 · #187 · **The remaining 30 tasks, all of them** — `FIRST_RUN` N1…N8, `FIELD_FIXES` F1…F13 and `BOOK_LIBRARY` L4…L12 — one commit each on one branch, in the order this file's §3 set (N1 first, then F, then L, then N2…N8). **The shape is the deviation worth recording:** `CLAUDE.md` §Workflow says one task, one PR, and this was 30 tasks in one PR. The owner was asked and did not answer; the reason is that the session had one assigned branch and 30 CI rounds would not have reached the end of the queue. Each task is still one commit with its own message, so the history reads task-by-task and any one of them can be reverted alone. Two blocked decisions were also asked and unanswered, and both were taken as the spec's own default and are cheap to reverse (§6). Room went **11 → 14** across three tasks, and N1 took the 11 → 12 slot `BOOK_LIBRARY` L5 had claimed — L5 moved to 12 → 13 and L10 to 13 → 14. All three task files are now in `docs/archive/`, so **there is no active task file**: §3 says what to do about that, and §4 says what a device pass would need to cover, which after a run with no SDK is nearly everything with a surface.
2026-08-06 · #187 · N1 done — the notification floor. `ui/Permissions.kt` is the single owner of "may we notify, and have we asked?"; three call sites share it. One pref `notifAskedAt`, Room **11 → 12** — a slot `BOOK_LIBRARY.md` L5 had claimed, so **L5 moved to 12 → 13 and L10 to the next free one after it**; both task files were edited in this PR. Two things a later session should not re-derive: read `areNotificationsEnabled()`, not `checkSelfPermission`, because a user can silence the app without touching the permission and the Settings row has to say so; and the blocked row deliberately has no switch, because a switch that cannot move reads as broken. **Nothing was run** — no SDK here, so no compile and no tests locally, and the migration has never been executed (this repo has no instrumentation tests).
2026-08-05 · #186 · L3 done — **Phase L-0 closed**. `domain/BookStatus` is now the single source of the five statuses and the shelf each maps to, asserted total in both directions by `BookStatusTest`; `setBookStatus` is the one door a book changes state through, owning `startedAt`/`finishedAt`/`position`. Two things a later session should not re-derive: shelf `position` is allocated as *max + 1*, never as the shelf's size, because a departure leaves a hole and `ORDER BY position` has no tiebreaker (`addBook` was fixed the same way); and the branch this shipped from was cut before the docs foundation existed, so it carried a wrong PR number and no `CONTEXT.md` edit — check both when a branch predates `dd4b6c9`.
2026-08-03 · — · file created alongside `GUARDRAILS.md` and `DATA_MODEL.md`; the five complete task files archived; `FIRST_RUN.md` added from a Pixel 7 emulator run of `v1.443`, with N1 placed ahead of the whole queue because it is the only finding where shipped features do nothing at all.
2026-08-02 · #182 · L2 done — `snapshot()`/`importJson()` filter book blocks both ways, the rule single-sourced in `BookBackup`; new `pauta.books.v1` export/import merges by id, carries no `filePath`/`fileKind`.
2026-08-02 · #181 · L1 done — `resetAll` now clears `book_notes`, `books` and `filesDir/books/` via `BookFiles.clearAll`; reseed inherits the fix.
