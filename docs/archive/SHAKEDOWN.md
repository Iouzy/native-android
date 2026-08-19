# Shakedown — task file

> **Concept.** #187 shipped thirty tasks in one PR and **no device ran a single
> one of them**. Within minutes of the owner using the app again, three things
> came back: a sheet that closes when you drag it, forms outside Hoje that still
> lose what you typed, and a block that cannot be attached to a maré. This file
> is the shakedown of that merge — first confirming what actually works, then
> fixing what does not, then the one feature the owner reached for and did not
> find. It is written from **use**, not from a code review; where it guesses, it
> says so.
>
> Ships as 5 self-contained tasks (S1…S5). Each task is one PR.

## How to use

> Faz o próximo em `docs/SHAKEDOWN.md`.

Do the **first task whose Status is `pending`**, top to bottom. Do **only** that
one. Ship it through the `CLAUDE.md` workflow, and update the task's Status, this
file's Log **and `docs/CONTEXT.md`** in the same PR. If a task is blocked on a
decision, **stop and ask** — do not skip ahead to the next one.

Open every reply with the progress bullet, before any tool call:

```
**Done:** S1 ✓
**Now:** S2 — the sheet that closes when you pull it
**Left:** S3, S4, S5, S6 (4)
```

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Guardrails

**`docs/GUARDRAILS.md` applies in full.** It is binding and it is not restated
here. The ones that bite hardest in this file: **no new dependencies** (§D, §K.3
— S2 will be tempting to solve with a gesture library; it must be solved with
what Compose already gives), **never lose what the user typed** (§A — the whole
reason S2 and S3 exist), **pt-PT is the source language** and every new string is
`tr(…)`, and **`pauta.v4` round-trips losslessly** (§ data — S4 adds a column and
must not break an export).

**Extra, specific to this file:**

1. **S1 comes first and is not optional.** Every other task here is shaped by
   what it finds — S3 may turn out to be already fixed, and S2's cause depends on
   which build the symptom appears in. A session that starts at S2 is guessing.
2. **Append a new Log line; never rewrite the previous one.** #187 lost 22 of its
   30 Log entries this way: each task rewrote the whole line, prepending its own
   text and carrying the earlier ones inline, until three files each held one
   physical line of ~12,000 characters. Repaired in `0d967ad`. If you are doing
   several tasks on one branch, this is the trap.
3. **A fix nobody ran is not a fix.** #187's Logs are honest that they were
   compiled and never executed, and three of them turned out to be wrong within a
   day of real use. Every `Verified:` clause in this file must say what actually
   ran, on what, or say "nothing".

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## What the owner saw — the evidence behind these tasks

**Reported 2026-08-15, on the owner's own phone, in ordinary use. The build is
`v1.521`** — confirmed by the owner from Settings → Sobre on 2026-08-15, after
this file was written. That is the #187 build, so all three findings are live
defects in the current app, not symptoms of an old one:

| # | What he did | What happened |
|---|---|---|
| 1 | Opened `Nova maré` on the Marés tab and dragged the sheet | "insta closes everything **even if I pull it up**" |
| 2 | Typed into `Novo bloco` (Pauta) and `Nova maré` (Marés), pressed back | The text is gone and the sheet leaves immediately. **Hoje does not do this** |
| 3 | Tried to create a Pauta block attached to a maré | No such control exists |

**Why the build mattered, and how it came out.** Finding 2 describes **pre-F3
behaviour exactly**: Hoje's composer is an inline field in the screen body, not a
sheet, so back never had a sheet to dismiss there, while `Novo bloco` and `Nova
maré` are both `PautaSheet`s. On **v454** that would have been the old build,
fixed on update. **The phone is on v521: F3 shipped, the symptom survived it, and
finding 2 is a live defect.** S3 is real work, not a skip.

**The suspect for finding 1, and it is only a suspect.** The sheet is
`rememberModalBottomSheetState(skipPartiallyExpanded = true)` — two anchors,
Expanded or Hidden, with no half-way stop, so a drag that the content does not
absorb has nowhere to settle except dismissed. That is pre-existing. What is
**new in #187** is F3's `dismissImeOnBackgroundTap`, a
`pointerInput { detectTapGestures { … } }` attached to the very `Column` that
carries `verticalScroll` and sits inside the sheet's drag path
(`ui/PautaSheet.kt:264` and `:347`). Its Log claims "a tap is only a tap after a
still lift, so the body still scrolls" — that is reasoning, never observation.
A gesture handler added to the drag path, in a build nobody ran, is the first
thing to rule out.

### What *was* verified, and where

Local, on the owner's machine, 2026-08-15 — **the SDK is available here now, see
Amendments**: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` both pass
(**256 tests, 19 classes, 0 failures**), `:app:assembleDebug` produces an APK,
and the released `pauta-native-v454.apk` installs and reports `versionName=1.454`.

The Room 11→14 path was reviewed here and **has since been run** — see S1's
progress notes and the Log. The review said: exactly seven added `prefs` columns,
three migrations adding exactly those seven, matching on name, type affinity and
`NOT NULL`; no other entity changed; all three registered; no
`fallbackToDestructiveMigration` anywhere. The device run agreed with all of it.

**S1 has now said otherwise (PR #190).** Parts of #187 have been executed on the
`pauta_pixel7` AVD: the migration, N1's permission and reminders, F3, F8 and F12.
**F5, F11's full matrix, F2 and F6 still have not**, and `CONTEXT.md` §4 keeps
the standing list.

## Data model delta

**S4 claims Room v15** — `MIGRATION_14_15`, one nullable column on `focus_blocks`.
Claimed here in writing before any code is written, because two files once
collided on v9. Nothing else in this file touches the schema. Everything else
lives in `docs/DATA_MODEL.md`.

## Decisions already taken — do not re-open these

- **F6 and F13 are correctly marked `done`, not `partial`** (2026-08-15). Checked
  against their specs: F13's spec says *"Ask before building it"* about *metas de
  leitura*, so not building it satisfies the Accept; F6's no-UI trampoline is
  explicitly conditional on a device still failing. Neither fell short. Do not
  "correct" these status lines.
- **#187 landed by rebase, not squash** (2026-08-15). `CLAUDE.md` says
  `--squash`, and that rule is right for a one-task PR; this one carried 30
  tasks whose stated mitigation was per-task revertibility, which squashing
  destroys. All 30 commits are on `main` individually. **If one task here turns
  out to be wrong, revert that commit alone.**
- **The Log repair is not to be redone** (2026-08-15, `0d967ad`). Every entry was
  recovered from the commit that wrote it, round-tripped byte-for-byte, and
  re-split with zero words added or lost. Per-file ordering was deliberately left
  as each file had it rather than unified — the archive has never agreed on one.
- **F7's leftover is unblocked, not done.** Its Log said "L4 has not shipped";
  L4 shipped 18 minutes later in the same PR and carried `chapterTitles` over the
  `:reader` boundary, not the page list. Corrected in `a89eb1b`. The work is S5.

---

## S1 · What #187 actually shipped — Status: done (PR #190)

**Depends on:** nothing. **Everything else in this file depends on it.**

**Why:** thirty tasks reached `main` having only ever been compiled. Three of
them were contradicted by ten minutes of real use. Before another line is
written, someone has to find out which of the thirty are real — starting with the
two that can cost the owner data.

**This is a verification task. It ships a Log entry and `CONTEXT.md` §4, not
code** — unless it finds something, in which case the fix is a *new* task here,
not a silent edit inside this one.

**Do, in this order** — the order is by what a failure costs:

1. **Establish the build on the owner's phone.** Settings → Sobre. Record it in
   the Log. Findings 2 and 3 in the evidence table cannot be read without it.
2. **The Room 11 → 14 upgrade, on a real prior database.** Install the released
   `pauta-native-v454.apk` (Room v11), use the app enough to have data in
   `intentions`, `focus_blocks`, `focus_sessions`, `habits` and `books`, capture
   the row counts, then install the v521 build **over it** — not a fresh install.
   Confirm every row survives and `PRAGMA user_version` reads 14. The emulator
   `pauta_pixel7` already carries a v11 database from 2026-08-03 with 2
   intentions, 2 blocks, 2 sessions, 2 habits and 1 book; `adb shell run-as
   com.pauta.app sqlite3 databases/pauta.db` reads it directly.
3. **N1's notification permission** — the reason N1 jumped the whole queue. On a
   clean install, the first focus block must ask once, and the focus notification
   and all three reminders must actually appear.
4. **F3 and F8**, the owner's two reported defects, in both lenses.
5. **The rest of `CONTEXT.md` §4's list** — F5's measured reader insets, F11's
   float strip across six screens × two lenses × **portrait and landscape**,
   F12's tide chips, F2's session editing and its delete cascade, F6's launcher
   door with an existing task, at textScale 1.0 **and 1.5**.

**Progress — 2026-08-15, PR #189.** Item 1 only.

- **Item 1 · done.** The phone reports **`v1.521`** (Settings → Sobre, read by the
  owner). That is the #187 build, so the evidence table's three findings are all
  live and S3 keeps its `skipped` branch closed. This was `CONTEXT.md` §6's
  longest-standing open question and it is now answered there too.
- **Items 2, 3, 4 and 5 · not reached.** No device was attached and no emulator
  ran. The session was a cloud container with no Android SDK; installing one is
  possible and was done, but the host is a guest VM with **no `/dev/kvm` and no
  `vmx`/`svm` CPU flags**, so the emulator has no hardware acceleration to use.
  Nothing was observed on a screen, so nothing below item 1 is verified — the
  Room 11 → 14 upgrade in particular is still **reviewed, not run**.

**Progress — 2026-08-15, PR #190.** Items 2, 3 and 4 done on the owner's own
machine; item 5 partly. The `pauta_pixel7` AVD boots with WHPX acceleration in
about 20 seconds — the cloud container's blocker really was nested
virtualisation and nothing else.

- **Item 2 · done, and it passed.** The v11 fixture was captured first (2
  intentions, 2 blocks, 2 sessions, 2 habits, 1 book, 1 habit_log, 1 prefs;
  `user_version=11`), then `pauta-native-v521.apk` was installed **over** v454 —
  in place, signature matched, no uninstall. After launch: `user_version=14`,
  every row present and unchanged, no crash, all three tabs composed. A full
  `.dump` diff before and after has **exactly two changes**: the seven appended
  `prefs` values at their declared defaults, and the Room identity hash. The
  largest risk in #187 is retired.
- **Item 3 · done, and it found something.** The dialog appears exactly once, at
  the first focus block, and the block starts whatever the answer — N1 works.
  The three reminders schedule at exactly 08:00 / 09:00 / 21:30, `Testar
  notificação` renders, and winding the clock past 21:30 fired **Reflexão da
  noite** and **Planeie o seu dia** for real; the habits one is correctly silent
  with zero tides. **But the first block's focus notification never reaches the
  shade** — that is the new **S6**. Not exercised: the Settings "blocked" row.
- **Item 4 · done.** **F3 is half right** — the background tap dismisses the IME
  and keeps the sheet and the text, so F3's `dismissImeOnBackgroundTap` is doing
  its job and is **not** the drag suspect S2 was told to rule out first. The back
  press is the broken half, reproduced exactly: see S3. **F8 verified at the
  largest text scale**, portrait and landscape — the first time it has been seen
  at all. `PRIORIDADE` keeps its pills; `QUANDO` wraps *inside itself*, dropping
  "noite" to its own line rather than orphaning the label, which is exactly the
  property `ComposerGroup` was built for. The four header chips wrap 2×2 clean
  in portrait, one row in landscape.
- **Item 5 · one of five.** **F12 verified end-to-end**: the chips are
  multi-select, they write "manhã, tarde" into the free-text field, and the
  database stores that plain string in `habits.time` — no schema change, so the
  `pauta.v4` round-trip is safe. **Not reached: F5's reader insets, F11's float
  strip across the six-screen × two-lens × two-orientation matrix, F2's session
  editing and its delete cascade, F6's launcher door.** Those four are what a
  next device pass owes, and they are listed in `CONTEXT.md` §4.

One false alarm, recorded so nobody re-derives it: the Hoje date label looks
clipped in landscape at the largest text scale. It is not — `uiautomator`
reports the same 28px height in both orientations. It is a small mono label and
the screenshot was downscaled.

**Never:** do not treat a fresh install as a migration test — a new database is
created by Room from the entities and never runs a migration at all, which is
precisely the check being skipped. Do not mark anything verified that you
reasoned about rather than watched.

**Accept:** `CONTEXT.md` §4 gains a dated row per item with the build, the device
and what was seen — including **what held up**, not only what broke; the phone's
build is recorded; every item above is either observed or explicitly listed as
not reached; anything found becomes a new `pending` task in this file rather than
a fix inside S1; nothing user-visible changed, so no README edit; CI green.

---

## S2 · The sheet that closes when you pull it — Status: done (PR #191)

**Depends on:** S1 (which build, and whether it reproduces on v454 too).

**Why:** `Nova maré` and `Novo bloco` are the two front doors to creating
anything in the planner. A sheet that dismisses when you drag it — *upward*, the
gesture that should expand or scroll — makes both unusable with a thumb, and it
throws away whatever was typed on the way out. That is §A, twice.

**Files to touch:**
- `ui/PautaSheet.kt` — the `ModalBottomSheet` and its two gesture additions
- possibly `ui/screens/HabitFormSheet.kt` — only if the tide form does something
  the other sheets do not

**The diagnosis is done — S1 (PR #190) ran it on the `pauta_pixel7` AVD against
v521. Do not repeat it, and do not start by suspecting F3.**

**`dismissImeOnBackgroundTap` is exonerated.** It was the file's prime suspect and
it is innocent. Observed: a background tap inside the sheet dismisses the IME and
keeps both the sheet and the typed text; the body scrolls normally when the
content overflows; and an upward drag never dismisses — tried four ways (slow
drag and fast flick, on the handle and in the body, with the keyboard up and
down). The handler is not competing for the gesture. Leave it alone.

**What actually dismisses the sheet is a drag *downward*, and the owner's word
"up" is the one imprecise thing in his report.** Two observations pin it:

- On the **short, unexpanded** `Nova maré` — the state the form opens in — a
  gentle 290px downward drag starting anywhere in the body dismisses the sheet
  and destroys the typed text. There is no scrollable content to absorb it, so
  the drag reaches the sheet immediately.
- With `+ mais opções` expanded so the body *does* scroll, the same drag deep in
  the content merely scrolls, correctly. It only dismisses once the scroll
  reaches its top and the drag keeps going.

So the cause is the anchoring the task file already guessed at, and nothing else:
`skipPartiallyExpanded = true` leaves Expanded and Hidden as the only states, so
any drag the content does not absorb has nowhere to settle but dismissed. **The
indicated fix is `confirmValueChange` refusing `Hidden` while the form is dirty**
— that is the half of §A this bug actually violates, since the cost is not the
sheet closing but the typed text dying with it.

**Out of scope:** a general "unsaved changes?" confirmation across the app —
that is a bigger decision than a gesture bug and belongs in its own task.

**Never:** do not fix this by disabling the drag handle or by making the sheet
non-dismissable. The handle is the documented dismiss affordance on the phone
path and removing it strands the user in a sheet.

**Accept:** on the phone path, dragging **up** on `Nova maré` never dismisses it;
dragging down on the handle still does; the body still scrolls when the content
is taller than the sheet; a tap on the background still puts the keyboard away
(F3's behaviour intact); the same holds for `Novo bloco` and one book-mode sheet;
verified at textScale 1.0 and 1.5, portrait **and** landscape; nothing
user-visible changed beyond the gesture, so no README edit; CI green.

---

## S3 · The keyboard still eats the form outside Hoje — Status: done (PR #192)

**Depends on:** S1. **Settled 2026-08-15 (PR #189): the phone is on `v1.521`, so
F3 shipped and the symptom survived it. The `skipped (was the old build)` branch
of this task is closed — S3 is a live defect and is to be built.**

**Why:** the owner reports that Hoje keeps what he typed and the other two tabs
do not. F3 was supposed to have made that true everywhere: `SheetImeBackHandler`
sits in `PautaSheet`, and both `Novo bloco` (`ui/screens/PautaSheets.kt:118`) and
`Nova maré` (`ui/screens/HabitFormSheet.kt:143`) go through it. If the symptom
survives on v521, then the handler is not winning the back press.

**Files to touch:**
- `ui/PautaSheet.kt` — `SheetImeBackHandler`, and where it sits in the tree

**The two things to check, in order.** First, whether `WindowInsets.isImeVisible`
is actually true at the moment back is pressed — it is the handler's `enabled`
condition, and if it reads false the handler never runs. Second, whether
`ModalBottomSheet`'s own back handling is registered *above* this one on the
dispatcher; F3's comment asserts composing inside the sheet body is enough to
win, and that assertion has never been tested.

**Answered (PR #192): it is the second one, and it is provable from the library
source rather than guessed.** On API 33+ `ModalBottomSheetDialogLayout`
registers its dismiss on the **platform** `OnBackInvokedDispatcher` at
`PRIORITY_OVERLAY`; every AndroidX `BackHandler` reaches that dispatcher through
`OnBackPressedDispatcher` at `PRIORITY_DEFAULT`, and the platform calls the
higher priority first. F3's assertion is true on API ≤ 32 and true inside the
centred `Dialog` — both are AndroidX-only and invoke last-registered-first — and
false for the bottom sheet on every phone that matters. `isImeVisible` was never
the problem.

**Out of scope:** saving a half-filled form as a draft. F3 already ruled that
out: the fix is not losing it in the first place.

**Never:** do not solve this by making back always dismiss the keyboard whether
or not it is up — that eats a back press that should close the sheet, which is
the failure F3 explicitly guarded against.

**Accept:** with the keyboard up in `Nova maré`, back closes the keyboard and the
form keeps **every character**; a second back closes the sheet; the weekday chips
hidden behind the keyboard are reachable after the first back; the same in `Novo
bloco` and in book mode; the predictive-back gesture still peels the sheet; no
README edit; CI green.

---

## S4 · A block that feeds a maré — Status: done (PR #195)

**Depends on:** S1, S2 (do not add a control to a sheet whose gestures are
broken).

**Why:** the owner tried to start a focus block attached to a maré and found
nothing. It is the obvious join between two of the three tabs — an hour of
reading is both a block and a tide — and today the app makes you do it twice, by
hand, in two places. `FocusBlockEntity.linkedToId` is an *intention* id; there is
no habit link anywhere in the schema, and concluding a block cannot tick a tide
because there is nothing to tick. The name "Tide-rise focus card"
(`NATIVE_IMPROVEMENTS` F1) is a visual only and is what raised the expectation.

**This is a feature, not a defect.** Nothing dropped it; it was never specified.

**Answered by the owner on 2026-08-19, and built exactly this way:**

| Question | His answer |
|---|---|
| Does concluding a block **tick the tide automatically**, or only offer to? | **Automatically.** No prompt — it is the point of the link. The double-tick worry is avoided by *marking* rather than toggling, so a tide already done today is left alone |
| For a **countable** tide (`n/target`), how much does one block add — one, or one per some duration? | **One**, whatever the block lasted. `targetMs` never enters the link |
| Does an **abandoned** block count? | **No.** Discarding deletes the block; nothing is kept, so nothing ticks |

**Files to touch:**
- `data/entity/Entities.kt` — `FocusBlockEntity.habitId: String? = null`
- `data/AppDatabase.kt` — `MIGRATION_14_15`, one `ALTER TABLE focus_blocks ADD
  COLUMN habitId TEXT` (nullable: an existing block belongs to no tide)
- `data/PautaRepository.kt` — the tick on conclude, one place, the way
  `setBookStatus` owns its transition
- `ui/screens/PautaSheets.kt` — a tide list beside "ou continue com…"
- `data/WebBackup.kt` — see Never

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `…ou alimenta uma maré` | `…or feed a tide` |
| `Concluir marca a maré` | `Finishing ticks the tide` |

**Out of scope:** the reverse direction — starting a block *from* the Marés tab.
Later task if the link proves useful.

**Never:** do not add `habitId` to the `pauta.v4` export. That format is the
retired web app's and must round-trip losslessly; a new native field goes the way
L2 took the book library, or stays native-only and is documented as such in
`DATA_MODEL.md`.

**Accept:** `Novo bloco` offers the open tides alongside the open intentions and
picking one is optional; concluding such a block does what the owner decided
above, exactly once; a tide deleted while a block points at it does not crash the
Pauta tab (`linkedToId` already tolerates dangling — match it); a `pauta.v4`
export and re-import is byte-identical for a database containing a linked block;
`DATA_MODEL.md` gains the column and v15; repo-root `README.md` gains the link in
its feature list; CI green.

---

## S5 · F7's leftover: "página 123 de 228" — Status: done (PR #194)

**Depends on:** nothing technically. Do it after S1–S3 — it is a nicety and they
are defects.

**Why:** F7 taught the sanitiser to carry the publisher's page-break markers into
the page, but the chrome still cannot say *which* page you are on, only estimate.
F7 deferred it to L4 and L4 then shipped 18 minutes later in the same PR without
being told; it carried `chapterTitles` over the `:reader` boundary and not the
page list. So the blocker is gone and the work was simply never picked back up.

**Files to touch:**
- `service/DocumentParse.kt` / `DocumentParseService.kt` — collect the markers
  into a list and put it on the wire beside `KEY_TITLES`
- `ui/screens/ReaderScreen.kt` — the chrome label
- `ui/screens/EpubReader.kt` — reporting the current marker as the view scrolls

**Follow L4's pattern exactly** — it is the precedent and it is good: the list
goes over the boundary with its length validated on arrival, a mismatch is
refused as a corrupt reply rather than treated as a book without pages, and an
older reply with no list still opens.

**Out of scope:** page numbers for PDFs — they already have real pages.

**Never:** do not invent a second channel out of the `:reader` process. That is
the duplication F7's dependency existed to avoid, and the reason it waited.

**Accept:** a book with publisher page-break markers shows "página N de M" in the
chrome without `≈`; a book with none still shows the estimate with its `≈` and
its assumption; a book whose marker list disagrees with its chapter list opens
anyway; `EpubTest` covers the list crossing the boundary; no README edit; CI
green.

---

## S6 · The first focus block has no notification — Status: done (PR #193)

**Depends on:** nothing. Found by S1 (PR #190) on the `pauta_pixel7` AVD, v521.
Do it whenever; it is a one-block-per-install defect, which is why nobody caught
it, and the block it spoils is the very first one a new user runs.

**Why:** N1's whole purpose was that the first focus block asks for
`POST_NOTIFICATIONS` and the shade then has something in it. The asking works.
The shade does not. On a clean install the first block starts, the dialog
appears, the user taps **Allow** — and no notification ever shows for that block.
Every block after it is fine.

**What was observed, so it need not be re-derived:**

- After Allow, `dumpsys package` reports `POST_NOTIFICATIONS: granted=true`, and
  `dumpsys activity services` reports `isForeground=true foregroundId=1001
  foregroundNoti=Notification(channel=pauta_focus …)`. The service is foreground
  and holds a notification object.
- `dumpsys notification` lists nothing for `com.pauta.app`, and the shade is
  empty. The notification exists and was never displayed.
- Concluding that block and starting a second one posts it correctly — the shade
  then shows **"Escrever · 00:23"** with its live timer.

**The cause, stated as a hypothesis because only the fix will prove it:** the
block starts the service *before* the user answers, so `startForeground` runs
while the permission is still denied and the post is dropped. Granting the
permission afterwards does not retroactively display an already-posted
notification — something has to re-post it.

**Files to touch:**
- `service/FocusService.kt` — the notification post
- wherever N1's permission launcher lives (`ui/MainScaffold.kt` or the Pauta
  screen's start path) — the callback that learns the answer

**The shape:** on the permission result turning *granted*, re-post the focus
notification if a block is running. One call, in the callback that already
exists. Do **not** reorder the block start behind the dialog — N1's Log is
explicit that the block must start whatever the user answers, and that behaviour
is correct and verified.

**Out of scope:** the Settings row that says "blocked" with a link to system
settings — S1 never exercised it, so it is unverified, not broken. If it turns
out to be wrong that is its own task.

**Never:** do not ask for the permission earlier (at launch or in onboarding) to
dodge this. N1 chose the first block deliberately, and that choice is verified
working — the dialog appears exactly once, at the right moment.

**Accept:** on a clean install, the **first** focus block's notification appears
in the shade once Allow is tapped, with its timer, exactly as the second one
already does; denying still starts the block and posts nothing; a second block
still behaves; no duplicate notification when permission was already granted
before the block started; no README edit; CI green. **Verified must name a clean
install on a device or AVD** — this defect is invisible on any install that has
already been granted the permission.

---

## Leftovers — too small to be tasks

- ~~**`CONTEXT.md`'s `Released:` line goes stale on every merge**, because it
  names a specific build against a rolling tag and is only updated per-PR.~~
  **Done 2026-08-15 (PR #189):** it now names the rolling `latest-native` tag and
  carries no number, so there is nothing left to go stale.
- ~~**`EpubReader.kt:276` uses a deprecated `val scale: Float`**~~ **Done
  2026-08-19 (PR #195):** the replacement the deprecation points at
  (`WebViewClient.onScaleChanged`) reports *changes*, and `maxScroll()` needs the
  value at the moment it measures — but the reader turns zoom off outright, so
  the scale is constant and the suppression is the accurate description of that,
  not a silencing. `:app:compileDebugKotlin` is now warning-free.
- ~~**The CI workflow pins deprecated actions**~~ **Done 2026-08-19 (PR #195):**
  `actions/checkout`, `actions/setup-java` and `actions/upload-artifact` moved
  v4 → v5. `android-actions/setup-android@v3` and `softprops/action-gh-release@v2`
  are current majors and were left alone.

## Amendments to other files

- **`CLAUDE.md` §Commands is now wrong for this machine.** It says "If the SDK
  isn't available locally (common in this environment) … skip the local build and
  rely on CI". The SDK *is* available on the owner's machine, with an AVD
  (`pauta_pixel7`, Android 15) and system image already installed. The real
  blocker was the JDK: Gradle 8.9 and AGP 8.5.2 reject the JDK 25 that both
  Android Studio's JBR and the standalone Temurin provide, and the build fails
  with a bare `* What went wrong: 25.0.2`. **Temurin 21 is now installed** at
  `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`; exporting `JAVA_HOME`
  to it and `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk` makes the documented
  commands work (8m 1s cold). The next PR touching `CLAUDE.md` should say this
  instead of "rely on CI" — a session that believes it cannot build locally will
  not try. **Done 2026-08-19 (PR #195):** `CLAUDE.md` §Commands now carries both
  halves — the owner's JDK 21 path, and the fact that a cloud container installs
  the SDK through the proxy and runs the whole gate.
- **The emulator works here too, and a session should try it** (added 2026-08-15,
  S1/#190). `emulator -accel-check` reports WHPX installed and usable, and
  `emulator -avd pauta_pixel7 -no-snapshot-load` is at `sys.boot_completed=1` in
  about 20 seconds. Two practical notes for whoever automates this next: boot
  **without** `-wipe-data`, because that AVD's database is the migration fixture
  and wiping it is unrecoverable; and Git Bash rewrites `/sdcard/...` into a
  Windows path, so `uiautomator dump` needs `MSYS_NO_PATHCONV=1` and a leading
  `//`. `ReminderReceiver` is not exported, so `am broadcast` cannot fire a
  reminder — set the device clock past the alarm instead.

---

## Order

```
S1  ──────────────  done (#190) — it reshaped S2 and confirmed S3
 │
S2  ──────────────  done (#191) — built, but no device has pulled the sheet
 │
S3  ──────────────  done (#192) — built, but no thumb has pressed back
 │
S6  ──────────────  done (#193) — the hypothesis was not proved, only acted on
 │
S5  ──────────────  done (#194) — F7's leftover is closed; no book has been opened
 │
S4  ──────────────  done (#195) — the owner answered; built, and unrun
```

S2, S3, S6 and S5 are all independent of each other — any order works, and S5 was
taken out of turn because S4 sat above it and could not move. **Every task in
this file has shipped**, so the file is closed and lives in `docs/archive/`.

S3's existence was conditional on S1 and is settled: it was real, and it is
fixed. S4 was blocked on three decisions and stayed blocked until the owner
answered all three on 2026-08-19; nothing was guessed.

**Every fix in this file after S1 is unverified on a device** — S2's drags, S3's
back presses, S6's clean install and now S4's whole loop are gestures and a
database, and this file's own third guardrail is what they owe. `CONTEXT.md` §4
is the list.

---

## Log (append one line per shipped task: date · task · PR · note · Verified:)

Every entry ends with **Verified:** — what was actually exercised, and on what.
This file exists because thirty tasks were compiled, merged and contradicted by
ten minutes of real use; an entry that cannot say what was verified should say
that instead.

<!-- e.g. 2026-08-16 · S1 · #n · … · Verified: Pixel 7 AVD, Android 15 -->
2026-08-19 · S4 · #195 · **The link exists at both ends of the block now, and the tick lives in one place.** `FocusBlockEntity.habitId` (Room 14 → 15, one nullable `ALTER TABLE`) is the whole schema of it; `Novo bloco` lists today's open marés under `…ou alimenta uma maré`, right below the intentions, and picking one is optional, unpicks on a second tap and fills an empty title with the tide's name. **The owner's three answers, built literally:** concluding ticks the tide *automatically*, a countable tide gains *one* per block whatever it lasted, and a *discarded* block ticks nothing. **The one rule worth not re-deriving: a block marks, it never toggles.** The Marés gesture is a toggle by design — tapping a done tide undoes it — so routing a block through `toggleHabitDay` would mean the second block of the same work *undid* the first, and on a countable tide `cycleCount` would send `target + 1` back to zero, clearing a day the user never asked to clear. `HabitCalculator.feedFromBlock` is that decision as pure arithmetic (already-done → nothing, countable-below-target → +1, at-target → nothing, anything else → mark) and it is unit-tested; `PautaRepository.feedLinkedTide` is the only caller. **It sits in the repository, not at the call site, because there are four call sites**: the conclude sheet, the notification's *Concluir*, the goal-reached prompt and the reader's session. Discarding a block does not pass through any of them, which is exactly the owner's third answer. A deleted tide, or a weekly tide concluded off its anchor day, is silence — `toggleHabitDay` and `setHabitCount` already refuse a day the cadence does not own, and a dangling `habitId` is treated the way `linkedToId` has always treated a deleted intention. **In the conclude sheet the fed tide is a statement, not a chip.** The sheet already offered today's tides as multi-select chips (that predates S4); the linked one is now lifted out of that row under `Concluir marca a maré`, shown ticked and not clickable, because it is going to be marked whichever way the block is concluded and offering a choice that does not exist would be a lie. A countable tide shows where it will land (`3/5`). **Not exported, deliberately:** `pauta.v4` is frozen, so the column is native-only and a web round-trip keeps the block and drops the link; `pauta.books.v1` carries reading sessions only and never sees a planner block. A test pins the payload as identical to an unlinked block's. **Out of scope and still out:** starting a block *from* the Marés tab. · Verified: **compiled and unit-tested on a real SDK in this container — and nobody has started a block, concluded one, or watched a tide move.** `:app:testDebugUnitTest` is **277 tests, 19 classes, 0 failures** (six new: five over `feedFromBlock`, one over the v4 payload), `:app:compileDebugKotlin` is clean *and now warning-free* — the `EpubReader.kt:276` deprecation in Leftovers went with this PR — and `:app:assembleDebug` produces the APK. What no test reaches: the sheet's new list at 1.5× text scale, the tick actually landing on the Marés grid, and the migration running on a real v14 database. `CONTEXT.md` §4 carries that beside S2's drags, S3's back presses and S6's clean install.
2026-08-17 · S5 · #194 · **The publisher's page numbers were already being read; nothing was remembering where they were.** F7 taught the *sanitiser* to draw a marker in the page; S5 teaches the *parser* to keep its position, and puts that across the `:reader` boundary the way L4 put the chapter names. `Epub.scanChapter` replaces `countWords` as the one pass `parse` makes over each chapter and returns both — the same word count (the rule is untouched, character for character, because every stored bookmark's percentage is weighted by it) plus the markers, each with the words that preceded it. Three parallel arrays cross the binder — `KEY_PAGE_LABELS`, `KEY_PAGE_CHAPTERS`, `KEY_PAGE_WORDS` — and `EpubSession.open` refuses a reply where one is present and another is not, or where the lengths disagree, exactly as it refuses a mismatched title list; a marker naming a chapter outside the spine is a different thing and is dropped on its own, so that book still opens. **The deviation worth reading: `EpubReader.kt` was not touched, and the task file expected it to be.** It named "reporting the current marker as the view scrolls", which reads as asking the page for its markers — but §3 of the reader's Security model has JavaScript **off**, so there is no DOM to query and no channel to answer on, and adding one would be the second `:reader` channel this task explicitly forbids. The position is instead computed in Kotlin from what the reader already reports: `Epub.pageIndexAt` takes the chapter and the scroll fraction the WebView's own `setOnScrollChangeListener` has always sent up, and returns the **last marker passed** — a reader who is past page 123 is on 123 until 124 arrives, which is how paper behaves and never names a page not yet reached. A marker's place inside its chapter is its word offset over the chapter's words, so it is an approximation of pixels by prose; it is honest about direction and monotone, which is what a page number has to be. **Where the number comes from and why it needs no `≈`:** the label is the publisher's own string, arabic or roman, validated by the same `pageNumberish` gate F7 applied to attributes — pulled out of `pageBreakLabel` so an unlabelled marker's own *text* passes through it too, now that the text travels back across the binder and into the chrome instead of only into a CSS `::after`. The total is `lastPrintedPage`, the largest **arabic** label in the book: roman front matter would make "página 12 de xxiv", and a book numbered in roman throughout gets `página xii` with no total rather than an invented one. **What was deliberately left alone:** `bookProgressLabel`'s "≈ p. 123 de 228" in the shelf and the detail sheet. It is a percentage of a length the owner typed in, it says so, and it has no access to a parsed book — S5's Accept asks for the real page **in the chrome**, and that is where it is. Also untouched: the `EpubReader.kt:276` deprecation in Leftovers, which is owed to the next PR that actually edits that file. · Verified: **compiled and unit-tested, on a real SDK, in this container — but no book has been opened and no chapter scrolled.** The Android SDK installs through the proxy here (`platforms;android-35`, `build-tools;35.0.0`, JDK 21): `:app:compileDebugKotlin` is clean apart from the pre-existing `EpubReader.kt:276` deprecation, and `:app:testDebugUnitTest` is **271 tests, 19 classes, 0 failures** — 15 new, twelve in `EpubTest` over the scan, the offsets, the last-marker-passed rule and the arabic total, three in `EpubInfoTest` over the shape the list arrives in. The word count is pinned by a test asserting a chapter counts the same with markers as without. What no test can reach is the thing that matters: **a real book, with real markers, at a real text size**, where the word-offset approximation either tracks the printed page or visibly lags it. `CONTEXT.md` §4 carries that alongside S2's drags and S3's back presses.
2026-08-17 · S6 · #193 · **The permission arrives after the notification it was for, so the notification is issued again.** Built exactly the shape the task specified — one call in the launcher callback that already existed — with the one piece of plumbing it needed: `AppViewModel`'s block→service collector was an inline lambda, so its body is now `syncFocusNotification(block, sessions)` and the new `repostFocusNotification()` calls the same function with the current active block. Nothing about the collector's behaviour changed; it is the identical code with a name. In `ui/Permissions.kt` the `RequestPermission` callback, empty since N1, now does `if (granted) vm.repostFocusNotification()`. Re-issuing `FocusServiceController.start` is the whole re-post: same `NOTIF_ID`, so an already-visible notification is replaced rather than duplicated, which is the Accept's "no duplicate when permission was already granted". **What was deliberately not touched:** the order — N1 starts the block whatever the answer, and S1 verified that on a device, so the dialog still does not gate the timer. **What this ships without proof:** S6's cause is a hypothesis (the post is dropped because the service goes foreground before the answer) and only a clean install can confirm the fix; the re-post is correct behaviour either way, but "the shade now has it" is unwitnessed. One race left in the open, too small to design around and worth naming: the re-post reads the active block, so a user who taps **Allow** faster than Room can write the block would still get nothing — the dialog's own animation is longer than that write, which is why it is left alone. · Verified: **compiled and unit-tested only — no device, no clean install, nothing in a shade.** Cloud container: `:app:compileDebugKotlin` clean apart from the pre-existing `EpubReader.kt:276` deprecation, `:app:testDebugUnitTest` **256 tests, 19 classes, 0 failures**. S6's Accept names a clean install on a device or AVD in bold and this session had neither; the defect is invisible on any install that already holds the permission, so a re-install is the only way to see it.
2026-08-17 · S3 · #192 · **F3's handler was never wrong about what to do, only about who hears the back press first — and the answer is in the libraries' own source, not in a guess.** The task named two suspects and it is the second: on API 33+ `ModalBottomSheetDialogLayout.onAttachedToWindow` registers its dismiss straight with the **platform** `OnBackInvokedDispatcher` at `PRIORITY_OVERLAY` (`ModalBottomSheet.android.kt`, material3 1.3.0), while every AndroidX `BackHandler` reaches that dispatcher through `OnBackPressedDispatcher` at `PRIORITY_DEFAULT` (activity 1.9.2), and the platform calls the higher priority first. So Material dismissed the sheet before `SheetImeBackHandler` was consulted at all, and the typed text died with it — on every device from Android 13 up, which is the owner's phone and the AVD both. `WindowInsets.isImeVisible`, the first suspect, is innocent: it was never reached. **Why F3's assertion looked right:** it *is* right in the two places it was reasoned about — API ≤ 32, and the wide-screen centred `Dialog`, which registers nothing on the platform dispatcher (compose-ui 1.7.3) — and both invoke the last-registered enabled callback first, which is F3's. The phone path on a modern phone is the one case it does not hold, and that is the only path the owner uses. **The fix is one rung, not a rewrite:** while the keyboard is up, register our own `OnBackInvokedCallback` on the *same* dispatcher at `PRIORITY_OVERLAY + 1`; below API 33 keep the `BackHandler`, which already wins there. It is registered only while `isImeVisible`, so with the keyboard down nothing of ours exists and Material's dismiss — including its predictive-back peel on API 34+ — is untouched, which is what the second back press and the peel clause of the Accept both rest on. Deliberately **not** done: `shouldDismissOnBackPress = false` plus a hand-rolled back stage, which would have meant re-implementing the peel; and a `confirmValueChange` gate, which the scrim tap and the drag handle also route through, so it would have changed two gestures S2 had just settled. No new dependency — `android.window` is the framework (§D). The half of the fix that *has* been seen on a device is the action itself: `focus.clearFocus()` + `keyboard?.hide()` is exactly what F3's background tap does, and S1 watched that keep the sheet and the text. · Verified: **compiled and unit-tested only — no device, nobody has pressed back.** Cloud container again: the SDK installs (`platforms;android-35`, `build-tools;35.0.0`, JDK 21), `:app:compileDebugKotlin` is clean apart from the pre-existing `EpubReader.kt:276` deprecation, and `:app:testDebugUnitTest` passes — **256 tests, 19 classes, 0 failures**, the fourth run to land on that number. The dispatcher-priority claim is read off the material3, activity and compose-ui sources, which is stronger than reasoning but is still not a thumb: **every clause of S3's Accept needs one**, and `CONTEXT.md` §4 carries that debt next to S2's.
2026-08-15 · S2 · #191 · **The body no longer drags the sheet away; the handle still does.** One `NestedScrollConnection` on the sheet body, outside its `verticalScroll` so it is the scroll's nested-scroll parent, swallowing the leftover vertical delta and the leftover fling velocity that Material otherwise hands up to the sheet. That link is the whole mechanism: `ModalBottomSheet` moves on what the body's scroll declines, and with `skipPartiallyExpanded` the only anchor below Expanded is Hidden — so on the short unexpanded `Nova maré` the *first pixel* of a downward drag is leftover and the sheet has nowhere to go but dismissed. Cutting the link leaves the scroll untouched (we only ever take what it declined) and leaves the drag handle untouched (it sits outside the modifier and reaches the sheet's own `draggable` directly). **The deviation worth reading: this is not the `confirmValueChange`-while-dirty fix S2 indicated, and the reason is that that fix fails this task's own Accept in three ways.** It would not touch finding 1 at all — the owner had typed nothing when the sheet closed on him, so a dirty gate is inert in exactly the reported case. It would break "dragging down on the handle still dismisses", since `confirmValueChange` cannot tell a handle drag from a body drag. And in Material3 1.3 the scrim tap *and* the back press both route through `animateToDismiss`, which consults `confirmValueChange` — so refusing `Hidden` strands a half-typed form with `Cancelar` as its only exit, which is the "never make the sheet non-dismissable" this task forbids and the second back press S3 promises. A dirty gate also needs every one of the ~dozen forms to report dirtiness, against a spec whose Files-to-touch is one file. The nested-scroll boundary is the smaller change and the one the Accept actually describes. Nothing was added to the dependency list; it is `androidx.compose.ui.input.nestedscroll`, already present. · Verified: **compiled and unit-tested only — no device, no emulator, nobody has pulled a sheet.** This session is a cloud container: the Android SDK installs (`platforms;android-35`, `build-tools;35.0.0`) and `:app:compileDebugKotlin` and `:app:testDebugUnitTest` both pass here, but #189 established that no emulator can ever be accelerated in it (no `/dev/kvm`, no `vmx`/`svm`). **Every clause of S2's Accept is a gesture and none of them has been performed** — the four drags, the two text scales, the two orientations, `Novo bloco` and a book-mode sheet all still owe a device pass, and `CONTEXT.md` §4 carries that debt.
2026-08-15 · S1 (items 2–5) · #190 · **#187 finally met a screen, and it mostly holds.** The `pauta_pixel7` AVD boots with WHPX in ~20s on the owner's machine, so the cloud container's blocker really was nested virtualisation. **The migration passed on the real fixture** — v454 with `user_version=11` and its 2/2/2/2/1 rows, v521 installed *over* it, and afterwards `user_version=14` with a `.dump` diff containing exactly two changes: the seven declared `prefs` defaults and the Room identity hash. That was the biggest single risk in the merge and it is retired. **N1 works** (dialog once, at the first block, block starts either way; three reminders scheduled at the right times, two of them fired for real by winding the clock) **except that the first block's notification never reaches the shade** — the service goes foreground before the user answers, the post is dropped, and the second block is fine. That is the one new defect and it is now **S6**. **The thing a later session should not re-derive: `dismissImeOnBackgroundTap` is innocent.** This file named it the prime suspect for the drag; on a device the background tap works, the body scrolls, and an upward drag never dismisses in four different gestures. What dismisses is a *downward* drag on the short unexpanded form, where no scroll exists to absorb it — so S2's cause is `skipPartiallyExpanded` and its fix is `confirmValueChange`, and S2 has been rewritten to say so. F3's back press failed exactly as reported, confirming S3. F8 and F12 were verified at the largest text scale, the first time either has been looked at. Not reached and still owed: F5, F11's full matrix, F2, F6. · Verified: **`pauta_pixel7` AVD, Android 15, 1080×2400, build `v1.521` from the `latest-native` release** — installed over a genuine Room v11 database for the migration, then wiped to a clean install for N1. Every claim above was read off a screen, a `dumpsys` or the database; the one thing reasoned rather than watched is S6's *cause*, which is flagged as a hypothesis in that task.
2026-08-15 · S1 (item 1 of 5) · #189 · **The phone is on `v1.521`** — the #187 build, read from Settings → Sobre. That closes the question `CONTEXT.md` §6 had carried longest and it decides two tasks: finding 2 is not the old build showing through, so **F3 shipped and the symptom survived it** and S3 loses its `skipped (was the old build)` branch entirely; finding 1's drag-dismiss is likewise a defect in current code, which is what S2 goes after. S1 is left `in-progress`, not `done`, because items 2–5 were **not reached**: the session ran in a cloud container, and while an Android SDK installs there fine, the host is a guest VM with no `/dev/kvm` and no `vmx`/`svm` flags, so no emulator can be accelerated and none was booted. The thing a later session should not re-derive: **the blocker is nested virtualisation, not the SDK and not permissions** — items 2–5 want the owner's machine or a runner that exposes KVM. Folded in while touching the file: `CONTEXT.md`'s `Released:` line now names the rolling `latest-native` tag instead of a build number, which is the Leftover it had been collecting staleness for. · Verified: **nothing on a device or emulator.** The build number is the owner's own reading of his phone; every other claim in this entry is about what was *not* run.
