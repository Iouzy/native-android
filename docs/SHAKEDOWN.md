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
**Left:** S3…S5 (3)
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

The Room 11→14 path was **reviewed, not run**: the entity diff is exactly seven
added `prefs` columns and the three migrations add exactly those seven, matching
on name, type affinity and `NOT NULL`; no other entity changed; all three are
registered; there is no `fallbackToDestructiveMigration` anywhere, so a mismatch
throws on open instead of dropping data. An emulator run was started and
abandoned when the machine ran out of headroom.

**Nothing in #187 has been executed on a device or emulator.** That remains true
until S1 says otherwise.

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

## S1 · What #187 actually shipped — Status: in-progress (PR #189)

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

S1 stays open on items 2–5. They need either the owner's own machine (which has
the SDK, the `pauta_pixel7` AVD carrying the v11 fixture, and Temurin 21) or a
runner with nested virtualisation.

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

## S2 · The sheet that closes when you pull it — Status: pending

**Depends on:** S1 (which build, and whether it reproduces on v454 too).

**Why:** `Nova maré` and `Novo bloco` are the two front doors to creating
anything in the planner. A sheet that dismisses when you drag it — *upward*, the
gesture that should expand or scroll — makes both unusable with a thumb, and it
throws away whatever was typed on the way out. That is §A, twice.

**Files to touch:**
- `ui/PautaSheet.kt` — the `ModalBottomSheet` and its two gesture additions
- possibly `ui/screens/HabitFormSheet.kt` — only if the tide form does something
  the other sheets do not

**Rule out the new thing first.** Temporarily remove `dismissImeOnBackgroundTap`
from both call sites and see whether the drag behaves. F3 added it to the same
`Column` that owns `verticalScroll`, inside the sheet's drag/nested-scroll path.
If that is the cause, the fix is to stop competing for the gesture — an
`awaitPointerEventScope` that only claims an *unconsumed* tap, or moving the
handler to a non-scrolling background layer behind the content — **not** deleting
F3's behaviour, which is a real fix the owner asked for.

If it reproduces without it, the cause is the sheet's own anchoring:
`skipPartiallyExpanded = true` leaves Expanded and Hidden as the only states.
Consider `confirmValueChange` refusing `Hidden` while the IME is up or while the
form is dirty, so a stray drag cannot discard a half-written form.

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

## S3 · The keyboard still eats the form outside Hoje — Status: pending

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

## S4 · A block that feeds a maré — Status: pending · **blocked on a decision**

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

**Blocked on the owner, and the task stops here until he answers:**

| Question | Why it changes the build |
|---|---|
| Does concluding a block **tick the tide automatically**, or only offer to? | Automatic is the point of the link; automatic is also how a paused-and-resumed block ticks a daily tide twice |
| For a **countable** tide (`n/target`), how much does one block add — one, or one per some duration? | Decides whether `targetMs` matters at all |
| Does an **abandoned** block count? | F4's cycle rule means a wrong tick is one tap from zero, so the cost of "yes" is low |

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

## S5 · F7's leftover: "página 123 de 228" — Status: pending

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

## Leftovers — too small to be tasks

- ~~**`CONTEXT.md`'s `Released:` line goes stale on every merge**, because it
  names a specific build against a rolling tag and is only updated per-PR.~~
  **Done 2026-08-15 (PR #189):** it now names the rolling `latest-native` tag and
  carries no number, so there is nothing left to go stale.
- **`EpubReader.kt:276` uses a deprecated `val scale: Float`** — the only warning
  in an otherwise clean build. Fold into the next PR touching that file.
- **The CI workflow pins deprecated actions** — `actions/setup-java@v4` warns it
  will receive no more updates, and five actions are being forced onto Node 24.
  Not urgent, but it will break rather than warn eventually.

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
  not try.

---

## Order

```
S1  ──────────────  first, and it reshapes everything below
 │
S2  ──────────────  the gesture defect: both front doors are unusable
 │
S3  ──────────────  may be `skipped` outright, depending on S1
 │
S4  ──────────────  blocked on the owner's three answers
 │
S5  ──────────────  the leftover, once the defects are gone
```

**Blocked on a decision:** S4, on the three questions in its table. S3's
existence is conditional on S1.

---

## Log (append one line per shipped task: date · task · PR · note · Verified:)

Every entry ends with **Verified:** — what was actually exercised, and on what.
This file exists because thirty tasks were compiled, merged and contradicted by
ten minutes of real use; an entry that cannot say what was verified should say
that instead.

<!-- e.g. 2026-08-16 · S1 · #n · … · Verified: Pixel 7 AVD, Android 15 -->
2026-08-15 · S1 (item 1 of 5) · #189 · **The phone is on `v1.521`** — the #187 build, read from Settings → Sobre. That closes the question `CONTEXT.md` §6 had carried longest and it decides two tasks: finding 2 is not the old build showing through, so **F3 shipped and the symptom survived it** and S3 loses its `skipped (was the old build)` branch entirely; finding 1's drag-dismiss is likewise a defect in current code, which is what S2 goes after. S1 is left `in-progress`, not `done`, because items 2–5 were **not reached**: the session ran in a cloud container, and while an Android SDK installs there fine, the host is a guest VM with no `/dev/kvm` and no `vmx`/`svm` flags, so no emulator can be accelerated and none was booted. The thing a later session should not re-derive: **the blocker is nested virtualisation, not the SDK and not permissions** — items 2–5 want the owner's machine or a runner that exposes KVM. Folded in while touching the file: `CONTEXT.md`'s `Released:` line now names the rolling `latest-native` tag instead of a build number, which is the Leftover it had been collecting staleness for. · Verified: **nothing on a device or emulator.** The build number is the owner's own reading of his phone; every other claim in this entry is about what was *not* run.
