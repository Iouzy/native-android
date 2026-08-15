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

**Last updated:** 2026-08-15 · **Room:** v14 · **Released:** the rolling
`latest-native` tag — always the newest `main` build · **Branch of record:**
`main`

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

**The active file is `docs/SHAKEDOWN.md` (S1…S5).** The three files before it all
finished on 2026-08-06 in one PR (#187) — 30 tasks, one commit each. The count
this section used to carry said "22 across two files, plus one new file", which
was already wrong when it was written: the three files held **30**.

`SHAKEDOWN.md` exists because #187 merged on 2026-08-15 and ordinary use
contradicted it the same day: a sheet that dismisses when dragged, forms outside
Hoje still losing what was typed, and no way to attach a block to a maré. **S1 is
the device pass over everything #187 shipped**, and it comes first because it
decides the shape of the rest.

| File | Scope | Tasks | Finished |
|---|---|---|---|
| `docs/archive/FIRST_RUN.md` | The edges: the permission never asked for, the empty screens, the front doors | N1…N8 | 2026-08-06 · #187 |
| `docs/archive/BOOK_LIBRARY.md` | Book mode round three: the promises L-0 found unkept, the reader a reader expects, the shelf at scale | L1…L12 | 2026-08-06 · #187 (L1, L2 in #181/#182; L3 in #186) |
| `docs/archive/FIELD_FIXES.md` | Defects found by *using* the app, ordered by what each costs the person using it | F1…F13 | 2026-08-06 · #187 |

**Two tasks shipped less than their spec asked for, and both say so in their own
Log** — worth knowing before anyone reads the ticks as complete:

- **F7** carried the publisher's page-break markers into the page but *not* into
  the chrome ("página 123 de 228"), which needs the page-list plumbed out of the
  `:reader` process — the pass `L4` owns. **L4 shipped 18 minutes later in this
  same PR and F7 was never revisited**, and it carried `chapterTitles` over the
  wire, not the page list. So the blocker is gone and the work is not: this is
  the one piece of #187 that is outstanding rather than decided, and it is the
  obvious first entry of the next task file.
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

**`docs/SHAKEDOWN.md`, first pending task, top to bottom.** That is S1: the
device pass §4 below argues for. Thirty tasks shipped without an SDK in the
environment, so CI compiled and unit-tested all of it and **no device has seen
any of it** — and the first day of real use already found three things, which is
the strongest possible argument for doing S1 before writing another line.

**S1 is `in-progress` (#189) and only its first item is done.** The one thing it
had to settle before anything else could be read — **which build the phone is on**
— came back **`v1.521`**, the #187 build. So the two findings that describe pre-F3
behaviour are not the old build showing through: F3 shipped and the symptom
survived it, and both are live defects. **S3 is real work, not a skip.**

Items 2–5 of S1 — the Room 11 → 14 upgrade on a real prior database, N1's
notification permission, F3/F8 in both lenses, and the rest of §4's list — are
**still not reached**. #189 ran in a cloud container: an Android SDK installs
there, but the host is a guest VM with no `/dev/kvm` and no `vmx`/`svm` flags, so
no emulator can run. Those items need the owner's machine (SDK, the
`pauta_pixel7` AVD with the v11 fixture, Temurin 21) or a runner exposing KVM.

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
| 2026-08-15 | `v521` (post-merge) | owner's machine, **local build only — no app run** | The repo builds locally for the first time: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass (**256 tests, 19 classes, 0 failures**, 8m 1s cold), `:app:assembleDebug` produces an APK. The released `pauta-native-v454.apk` installs on `pauta_pixel7` and reports `versionName=1.454`; that AVD still holds a **Room v11** database from 2026-08-03 (2 intentions, 2 blocks, 2 sessions, 2 habits, 1 book), which is the fixture S1 should upgrade. The Room 11→14 path was **reviewed, not run** — seven added `prefs` columns matched 1:1 against three migrations, all registered, no `fallbackToDestructiveMigration`, so a mismatch throws on open rather than dropping data. An emulator run was started and abandoned when the machine ran out of headroom. **No screen of #187 has been looked at.** |
| 2026-08-15 | `v1.521` source | cloud container, **build only — no app run** | S1's session (#189). An Android SDK installs cleanly in the container (platform-tools, platform 35, build-tools 35) and `:app:testDebugUnitTest` passes there: **256 tests, 19 classes, 0 failures**, matching the owner's own local run exactly. **The emulator does not work and cannot be made to:** the `system-images;android-35;google_apis;x86_64` download fails through the proxy, and the host is a guest VM with no `/dev/kvm` and no `vmx`/`svm` CPU flags, so there would be no acceleration even with an image. A cloud session can therefore compile and unit-test this repo but can never do a device pass — which is the whole of S1 items 2–5. |
| 2026-08-15 | `v1.521` | **`pauta_pixel7` AVD, Android 15, 1080×2400 — the first device pass over #187** | S1 items 2–4, on the owner's machine (WHPX acceleration; the cloud container's blocker was nested virtualisation, not the SDK). **The Room 11→14 upgrade ran on the real v11 fixture**: v454 installed, `user_version=11`, 2 intentions / 2 blocks / 2 sessions / 2 habits / 1 book / 1 habit_log / 1 prefs captured, then v521 installed **over** it. Result: `user_version=14`, every row intact, and a `.dump` diff whose *only* changes are the seven appended `prefs` values at their declared defaults (`0, 1.0, 1.62, 22, 'app', 0, '21:00'`) and the Room identity hash. No crash, all three tabs composed. **N1 (item 3):** the permission dialog appears exactly once, at the first focus block, and the block starts either way — but see the first-block notification defect below. Three `REMINDER_FIRE` alarms are scheduled at exactly the configured 08:00 / 09:00 / 21:30; `Testar notificação` renders; advancing the clock past 21:30 fired **Reflexão da noite** and **Planeie o seu dia** for real. The habits reminder is correctly silent with zero tides (`postHabits` returns early on an empty list). **F3 (item 4): half right.** The background tap *does* dismiss the IME and keep the sheet and its text. The **back press does not** — one back with the keyboard up in `Nova maré` killed keyboard, sheet and the typed word together, which is S3 reproduced exactly. **F8: verified at the largest text scale, first time ever seen** — `PRIORIDADE` keeps its pills, and `QUANDO` wraps *inside itself* ("noite" drops to its own line) instead of orphaning the label, which is precisely the property F8 claimed; the four header chips wrap 2×2 with no clipping, and one row in landscape. **F12 verified end-to-end**: the chips are multi-select, write "manhã, tarde" into the free-text field, and store that plain string in `habits.time` — no schema change, round-trip safe. |
| 2026-08-15 | `v1.521` | owner's phone, real use | Three findings within a day of the merge, now `SHAKEDOWN.md`'s evidence section: `Nova maré` dismisses when dragged *upward*; `Novo bloco` and `Nova maré` still lose typed text on back while Hoje does not; no way to attach a block to a maré. The build was not recorded at the time and **was confirmed as `v1.521` on 2026-08-15 (#189)** — the #187 build, so two of the three are not the old build showing through: F3 shipped and the symptom survived it. All three are live. |

**Nothing has been run on:** a physical device with a small screen, a tablet, a
foldable, API 26–30 (`minSdk` is 26; the emulator was 35), or with TalkBack
actually enabled. Say so rather than implying otherwise.

### What #187 needs a device for, in rough order of risk

Items 1–4 were worked through on 2026-08-15 (S1, PR #190) and are annotated
below. Items 5–7 are still untouched, and are the honest content of the next
device pass.

1. ~~**Two Room migrations, 11 → 12 → 13 → 14**~~ — **run and passed** on the real
   v11 fixture. See the AVD row above: `user_version` 11→14, every row intact,
   only the seven declared `prefs` defaults added. This was the largest single
   risk in #187 and it is now retired.
2. **The notification permission (N1)** — **mostly passed, one new defect.** The
   dialog appears once at the first focus block; the reminders are scheduled at
   the right times and two of them fired for real. But the **first** block's
   focus notification never reaches the shade, because the service goes
   foreground before the user answers. That is `SHAKEDOWN.md` S6. The Settings
   "blocked" row and its link were **not** exercised.
3. **Gestures (F3, N2)** — F3 **half-passed**: background tap keeps the sheet,
   back press destroys it (S3, reproduced). The sheet's drag behaviour is
   diagnosed in S2. **N2's reader chrome was not reached.**
4. **Layout at textScale (F8, F11, N5, N7)** — **F8 passed at the largest scale
   and in landscape** (first observation ever). F11's full matrix — six screens ×
   two lenses × both orientations — was **not** covered, nor N5/N7, nor whether
   Pip's disappearance below 480dp reads as deliberate.
5. **The reader (F5, F7, L4, L5)** — measured bar insets, the page-break
   separator, the contents sheet, and whether a type-size change really keeps
   the reader's place. **Not reached.**
6. **The launcher door (F6)**, which is the one failure the spec says a unit test
   cannot see, and whose fallback trampoline was deliberately not built. **Not
   reached.**
7. **The widget and the QS tile in book mode (L11)**, neither of which has been
   placed. **Not reached.**

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
| ~~**Which build is the phone on?**~~ **Answered 2026-08-15 (#189): `v1.521`** — the owner read it from Settings → Sobre. That is the #187 build, so all three findings are live defects and none of them is the old build. `SHAKEDOWN.md` S3 loses its `skipped` branch and is real work. | — | `v1.521` |
| **Does concluding a block tick its maré automatically, or only offer to?** Automatic is the point of the link; automatic is also how a paused-and-resumed block ticks a daily tide twice. | `SHAKEDOWN.md` S4 | None — S4 stops here |
| **For a countable tide (`n/target`), how much does one block add** — one, or one per some duration? Decides whether `targetMs` matters to the link at all. | `SHAKEDOWN.md` S4 | None — S4 stops here |
| **Does an abandoned block count?** F4's cycle rule means a wrong tick is one tap from zero, so the cost of "yes" is low. | `SHAKEDOWN.md` S4 | None — S4 stops here |

---

## Log (append one line per PR that changes the state of the work)

<!-- YYYY-MM-DD · #PR · <what moved, and anything a later session would otherwise re-derive> -->
2026-08-15 · #189 · **S1 item 1: the phone is on `v1.521`.** §6's longest-standing open question is answered and two tasks move with it — F3 shipped and its symptom survived, so `SHAKEDOWN.md` S3 loses its `skipped (was the old build)` branch and is real work, and S2's drag-dismiss is a defect in current code rather than in an old build. S1 stays `in-progress`: items 2–5 need a device and none was reached. **The thing not to re-derive:** a cloud session can build and unit-test this repo (SDK installs fine, 256 tests pass there) but **cannot ever run the app** — no `/dev/kvm`, no `vmx`/`svm`, and the system-image download fails through the proxy. Device work belongs on the owner's machine, and §4 now carries a row saying so. Also folded in: the `Released:` line names the rolling tag instead of a build number, so it stops going stale on every merge.
2026-08-15 · — · #187 **merged by rebase**, not squash — 30 task commits are on `main` individually, so a task that turns out wrong is reverted alone; `CLAUDE.md` says `--squash` and that rule is right for a one-task PR, not for this one. Released as `pauta-native-v521.apk`. Two things a later session should not re-derive: the repo **builds locally on the owner's machine now** (the blocker was never the SDK, it was that both available JDKs are 25 and Gradle 8.9 rejects them — Temurin 21 is installed, see `SHAKEDOWN.md` Amendments), and the Room 11→14 path has been **reviewed and not run**, with the review's reasoning in that file's evidence section. `SHAKEDOWN.md` created the same day after real use contradicted three of #187's tasks; S1 is the device pass, and it must record the phone's build before anything else can be interpreted.
2026-08-06 · #187 · **The remaining 30 tasks, all of them** — `FIRST_RUN` N1…N8, `FIELD_FIXES` F1…F13 and `BOOK_LIBRARY` L4…L12 — one commit each on one branch, in the order this file's §3 set (N1 first, then F, then L, then N2…N8). **The shape is the deviation worth recording:** `CLAUDE.md` §Workflow says one task, one PR, and this was 30 tasks in one PR. The owner was asked and did not answer; the reason is that the session had one assigned branch and 30 CI rounds would not have reached the end of the queue. Each task is still one commit with its own message, so the history reads task-by-task and any one of them can be reverted alone. Two blocked decisions were also asked and unanswered, and both were taken as the spec's own default and are cheap to reverse (§6). Room went **11 → 14** across three tasks, and N1 took the 11 → 12 slot `BOOK_LIBRARY` L5 had claimed — L5 moved to 12 → 13 and L10 to 13 → 14. All three task files are now in `docs/archive/`, so **there is no active task file**: §3 says what to do about that, and §4 says what a device pass would need to cover, which after a run with no SDK is nearly everything with a surface.
2026-08-06 · #187 · N1 done — the notification floor. `ui/Permissions.kt` is the single owner of "may we notify, and have we asked?"; three call sites share it. One pref `notifAskedAt`, Room **11 → 12** — a slot `BOOK_LIBRARY.md` L5 had claimed, so **L5 moved to 12 → 13 and L10 to the next free one after it**; both task files were edited in this PR. Two things a later session should not re-derive: read `areNotificationsEnabled()`, not `checkSelfPermission`, because a user can silence the app without touching the permission and the Settings row has to say so; and the blocked row deliberately has no switch, because a switch that cannot move reads as broken. **Nothing was run** — no SDK here, so no compile and no tests locally, and the migration has never been executed (this repo has no instrumentation tests).
2026-08-05 · #186 · L3 done — **Phase L-0 closed**. `domain/BookStatus` is now the single source of the five statuses and the shelf each maps to, asserted total in both directions by `BookStatusTest`; `setBookStatus` is the one door a book changes state through, owning `startedAt`/`finishedAt`/`position`. Two things a later session should not re-derive: shelf `position` is allocated as *max + 1*, never as the shelf's size, because a departure leaves a hole and `ORDER BY position` has no tiebreaker (`addBook` was fixed the same way); and the branch this shipped from was cut before the docs foundation existed, so it carried a wrong PR number and no `CONTEXT.md` edit — check both when a branch predates `dd4b6c9`.
2026-08-03 · — · file created alongside `GUARDRAILS.md` and `DATA_MODEL.md`; the five complete task files archived; `FIRST_RUN.md` added from a Pixel 7 emulator run of `v1.443`, with N1 placed ahead of the whole queue because it is the only finding where shipped features do nothing at all.
2026-08-02 · #182 · L2 done — `snapshot()`/`importJson()` filter book blocks both ways, the rule single-sourced in `BookBackup`; new `pauta.books.v1` export/import merges by id, carries no `filePath`/`fileKind`.
2026-08-02 · #181 · L1 done — `resetAll` now clears `book_notes`, `books` and `filesDir/books/` via `BookFiles.clearAll`; reseed inherits the fix.
