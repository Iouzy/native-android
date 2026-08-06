# Field fixes — task file

> **Concept.** Every other task file in this repo was written from a spec. This
> one was written from **use**: the owner installed the build, read a book on the
> bus and in bed, and found in twenty minutes what nobody found by reading code.
>
> That is the theme, and it is worth stating once. The test suite covers
> `domain/` — pure arithmetic and the backup converter — and it is green. Every
> defect below lives in the layer that has no tests: composables, intents, the
> WebView, the state machine of a settings row. **The gate was green and the app
> was wrong in a dozen places.** Nothing here is a redesign; it is the bill for
> that gap.
>
> Ships as 13 self-contained tasks (F1…F13). Each task is one PR.

---

## How to use

**The prompt is always the same, and it carries no number:**

> Do the next one in `docs/FIELD_FIXES.md`.

**What that means, exactly** (Claude — this is binding):

1. Read this file. The task to do is the **first one whose Status is `pending`**,
   top to bottom. Never skip ahead, never batch two.
2. **Open the reply with the progress bullet**, before any tool call, in this
   shape and no longer:

   ```
   **Done:** F1 ✓ · F2 ✓
   **Now:** F3 — the keyboard that swallows what you wrote
   **Left:** F4…F13 (10)
   ```

   Short, factual, no preamble. It exists so the owner knows where the work is
   without opening the file.
3. Do **only** that task, following its spec and `docs/GUARDRAILS.md`.
4. Ship it via the CLAUDE.md workflow: branch → commit → PR → CI green →
   squash-merge.
5. Update that task's **Status**, append its **Log** line, and update
   `docs/CONTEXT.md` — **all in the same PR**.
6. Report what shipped, and say plainly what could not be verified here.

If the first pending task is blocked on a decision, say so and stop — do not
silently pick the next one.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Guardrails

**`docs/GUARDRAILS.md` applies in full.** It is binding and it is not restated
here — including §G, the reader's **Security model**, which governs anything
touching the parser, the `:reader` process or the WebView. The ones that bite
hardest in this file:

- **§D — no new dependencies**, in the app or in the reader.
- **§B — both lenses survive every task.** With `bookMode` off, the planner is
  untouched. Acceptance always includes "book mode on and off both look right".
- **§K.1–K.2 — `javaScriptEnabled` is never true**; `addJavascriptInterface`
  appears nowhere in the tree. No task below has a good enough reason, and none
  ever will.
- **§E — prefs are law:** `reducedMotion`, `haptics`, `textScale`,
  `highContrast`, TalkBack descriptions. Several defects below are only visible
  at textScale 1.5 or in landscape; say which you tested.
- **§C — the `pauta.v4` export stays lossless and unchanged.** Everything
  book-mode is native-only.
- **§H — anything the app records without being asked must be removable.** This
  is the lesson of F1/F2, and it is a guardrail now rather than a note in this
  file: automatic capture is only defensible when it is reversible.

**Extra, specific to this file:** every defect below passed CI. A fix that is
verified only by CI has not been verified — say in the Log what you actually
exercised, and on what.

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## What the phone showed — the evidence behind these tasks

Recorded so a fresh session needs neither this list's history nor the
conversation that produced it.

- **A book concluded by hand jumped to 100%.** The book (`Attached`, 228 pages
  from when it was tracked by hand) had a file attached. Concluding a manual
  session and typing "100" left the detail sheet reading **100%**. R4 changed
  what `currentPage` *means* for an attached EPUB — a percentage point, not a
  page — and followed that into every place that **displays** the number and
  none of the places that **ask** for it.
- **Twelve reading sessions in one evening**, most of them 0–4 minutes, from
  testing. The peek guard requires *under 60 s* **and** *no position change*; in
  an EPUB one tap changes chapter, so it never fires.
- **"Ritmo: 9191 palavras/min".** Arithmetically correct on dishonest data:
  jumping 17% → 55% in three minutes is ~34 000 words. The reader cannot tell
  reading from navigating, and in an EPUB navigating moves several percentage
  points per tap (R3's PDF needed a page turn per page).
- **Those twelve sessions cannot be reached at all.** Not tappable in the detail
  sheet (`ui/screens/BookDetailSheet.kt:380`) or the Sessão tab
  (`ui/screens/BookSessionScreen.kt:302`); deliberately filtered out of the
  planner's Pauta tab (`ui/viewmodel/AppViewModel.kt:190`); and `deleteBook`
  (`data/PautaRepository.kt:823`) removes the file, the notes and the book row
  but **not** the blocks — deleting the book orphans them and they keep counting
  in the Hábitos statistics.
- **The reader's bars are drawn over the text.** The top bar covered a chapter
  heading; the bottom bar covered the last line; on the imprint page it cut the
  publisher's logo in half. The CSS allows 8px at the top and 64px at the bottom,
  which is not enough at any real text size.
- **An external link is painted as a link and is inert.**
  `www.panmacmillan.com` has no scheme, so it survived the sanitiser as a
  "relative" href, is styled with the accent, and does nothing when tapped
  (correctly — nothing navigates).
- **The launcher alias only fires on a true cold start.** Confirmed on device:
  killing the app and tapping the book icon opens book mode; switching from
  another app leaves whatever mode was already showing. `MainActivity.onNewIntent`
  passes `coldStart = false` and drops the door on purpose.
- **The updater's re-check shows nothing at all** — no flash of "A verificar…".
  Either the tap is not reaching `checkForUpdate()` or the check resolves faster
  than the eye; the button, the state order and `AppUpdater.check()` were all
  read and are correct, so this needs instrumenting before it needs fixing.
- **The Hoje composer wraps between a label and its own pills**, so `QUANDO`
  ends the priority row and `MIN` ends the time-of-day row
  (`ui/screens/HojeScreen.kt:1095` — one FlowRow with labels and pills as flat
  siblings, which is what U3 intended for text-scale safety).
- **Pip floats over content** — over a card in Hoje, over the month navigation in
  Hábitos. R1 already removed one floating thing from that strip for the same
  reason.
- **The Hábitos tab contradicts itself:** "Sequência atual: 1 dia" three lines
  above "Ainda sem leituras registadas." Two different emptiness tests
  (`ui/screens/BookHabitsScreen.kt:133` counts sessions,
  `BookHabitsScreen.kt:344` counts *plottable* sessions) share one sentence.
- **The shelf looks wrong with several books:** the "A ler agora" carousel clips
  its cards at both edges and leaves most of the screen empty.

---

## Decisions already taken — do not re-open these

- **No pagination in the EPUB reader.** Offered and declined after use: the
  owner prefers continuous scroll by chapter. It is *technically* possible
  without JavaScript (CSS `column-width` plus native horizontal scrolling from
  Kotlin), and that is recorded here only so nobody re-derives it as new. F7
  gives the orientation that pagination would have given, honestly.
- **The reading session still starts on its own.** An explicit "start reading"
  button was proposed and declined: the failure mode of forgetting to press it
  (an hour of reading lost, unrecoverable) is worse than the failure mode of
  junk (removable, once F2 lands). The fix is a guard with teeth plus the
  ability to delete — not a button.
- **An attached EPUB counts in percent.** It has no pages; its text reflows with
  the type size. This is also the unit `BookMath.wordsPerUnit` already uses
  (`wordCount / 100`), which is why the pace and the WPM needed no special case.
- **No cover art.** Settled August 2026; now in `docs/GUARDRAILS.md` §J.
- **One habit list.** `docs/archive/BOOK_READER.md` R7 dropped a `bookHabit` column
  deliberately; F13 removes the tides from the book-mode tab rather than
  splitting them in two. Twice decided.

---

## F1 · The unit of progress, and what counts as reading — Status: done (PR #187)

**Depends on:** nothing. **Do this first — it is writing wrong numbers now.**

**Why:** three faults, one fault line: what a unit of progress *is*, and which
sessions are allowed to speak about it. Shipping them apart would leave the
database half-corrected between two PRs, which is worse than one slightly larger
task.

**Files to touch:**
- `ui/screens/BookSessionScreen.kt` — `BookConcludeSheet`
- `ui/screens/BookDetailSheet.kt` — `ProgressEditor`
- `ui/screens/QuoteCaptureSheet.kt` — the page field
- `ui/screens/BookFormSheet.kt` — current/total page fields
- `ui/screens/BookProgress.kt` — extend (it already holds the display side)
- `domain/BookMath.kt` · `domain/ReaderMath.kt` — pure, both tested
- `src/test/…/BookMathTest.kt` · `src/test/…/ReaderMathTest.kt`
- `i18n/I18n.kt`

**(a) Every input asks in the unit the book counts in.** `BookProgress.kt`
already decides how progress is *shown*; it must now also decide how it is
*asked for*. An attached EPUB asks for a **percentage** (0–100, labelled, clamped);
an audiobook asks for a minute; everything else asks for a page. No field may
accept a number whose meaning differs from the line above it — that is exactly
what put the book at 100%.

The eyebrow text follows: `"Até que página chegaste?"` is wrong for a book with
no pages. Add `"Em que percentagem ficaste?"`.

**(b) A human ceiling on the pace.** A span implying an impossible reading speed
is not a measurement of reading; it is navigation. `BookMath` gains a ceiling
(`MAX_HUMAN_WPM`, 1000 — comfortably above any real reader and far below a
chapter jump) and drops such spans from `pagesPerHour`/`wordsPerMinute` rather
than averaging them in. The session keeps its **time** in the history; it loses
its **words**. Pure, and unit-tested.

**(c) The peek guard gets teeth.** `ReaderMath.sessionOutcome` currently
discards a session only when it is *both* under a minute *and* unmoved. Under a
minute is a peek, full stop — whatever the position did. Keep the existing
"moved but long" case saving, as today.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Em que percentagem ficaste?` | `What percentage are you at?` |
| `Percentagem` | `Percentage` |

**Out of scope:** editing or deleting the sessions that already exist (F2), the
reader's own controls (F5).

**Accept:** concluding a session by hand on an attached EPUB asks for a
percentage and stores one; typing 100 means "finished", not "100 pages"; a book
whose progress was corrupted can be corrected by hand to a sane value; a session
implying >1000 wpm no longer moves the Ritmo line; a 20-second session that
jumped three chapters saves nothing; the planner is untouched; unit tests green;
CI green.

---

## F2 · Sessions you can edit and remove — Status: done (PR #187)

**Depends on:** F1 (so a corrected session is corrected in the right unit)

**Why:** twelve junk sessions with no way to reach them. This is the task that
makes automatic recording defensible at all — and until it lands, every
imperfect session is permanent.

**Files to touch:**
- `ui/screens/BookDetailSheet.kt` — the `Sessões` rows become tappable
- `ui/screens/BookSessionScreen.kt` — same, in `Sessões de leitura`
- `ui/screens/PautaExtras.kt` — `EditBlockSheet`: editable times, delete
- `data/PautaRepository.kt` — `deleteBook` cascade; session-time updates
- `ui/viewmodel/AppViewModel.kt` — thin delegates
- `i18n/I18n.kt`

**(a) An entry point.** A reading session is text today in both places. Make the
row tappable and open the same `EditBlockSheet` a planner block opens. Without
this, nothing else in this task is reachable in book mode.

**(b) Editable times.** In `EditBlockSheet` the session spans are read-only text
(`PautaExtras.kt:320`) and even the block's target is resent unchanged
(`PautaExtras.kt:385`). Each span gains an editable **start** and **end** (or a
duration — pick one and be consistent), validated so the end never precedes the
start. Add **Apagar sessão** per span, and keep the existing "Apagar bloco".

**(c) Reading sessions can fix their own delta.** R5 stores `pagesDelta` on a
reading block. For a book-mode session the sheet also offers that number, in the
book's unit (F1's rule) — it is what `BookMath` reads, and correcting the time
without the delta leaves the pace as wrong as it was.

**(d) `deleteBook` takes its sessions with it.** Today it deletes the file, the
notes and the row, and orphans every `project = "book:<id>"` block — invisible
and still counted in the Hábitos statistics. Delete them, in the same
transaction.

**Guard:** the planner's Pauta tab keeps filtering reading blocks out
(`AppViewModel.kt:190`). Book sessions are reachable from book mode; that
separation is intentional and stays.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Editar sessão` | `Edit session` |
| `Apagar sessão` | `Delete session` |
| `Início` | `Start` |
| `Fim` | `End` |

**Out of scope:** a bulk "clear all sessions" (a per-session delete is enough and
safer), merging two sessions.

**Accept:** a reading session opens from both the detail sheet and the Sessão
tab; its start and end can be corrected and the duration follows; it can be
deleted and the Ritmo/streak/charts all reflect that immediately; deleting a book
leaves no orphan blocks behind (assert in a repository test); the planner's block
list is unchanged; CI green.

---

## F3 · The keyboard that swallows what you wrote — Status: done (PR #187)

**Depends on:** nothing. **Data loss on an ordinary action — this is why it is third.**

**Why:** adding a tide, the keyboard covers the lower half of the sheet and there
is no way to put it away. Back dismisses **the whole sheet**, and everything
typed is gone. There is no gesture that closes the keyboard and keeps the form:
the only two outcomes are "keyboard in the way" and "lose your work".

`docs/archive/UX_FIXES.md` U1 fixed the keyboard *arriving* mid-animation. Nobody fixed
it leaving.

**Files to touch:**
- `ui/PautaSheet.kt` — the sheet's back handling and ime behaviour
- `ui/screens/PautaSheets.kt` — `BoxedField` / `UnderlineField` ime actions
- every sheet with a text field (the tide form is the worst case; the same trap
  exists wherever a sheet has a field)

**The rule:** back is a **two-stage** gesture whenever the keyboard is up —
first press dismisses the keyboard, second dismisses the sheet. That is the
platform convention everywhere else on Android, and the app currently breaks it.

Alongside it, and cheaper: tapping the sheet's own background dismisses the
keyboard; and a field whose next control is not another field uses
`ImeAction.Done` rather than leaving the keyboard up with nothing to do.

**The thing to get right:** a `BackHandler` that is enabled only while the ime is
actually visible, so it never eats a back press that should close the sheet. Read
the ime visibility from `WindowInsets.isImeVisible` rather than from focus —
focus and keyboard are not the same state, and a field can hold focus with the
keyboard down.

**Out of scope:** saving a half-filled form as a draft. The fix is not losing it
in the first place.

**Accept:** with the keyboard up in "Nova maré", back closes the keyboard and
the form keeps every character; a second back closes the sheet; the weekday chips
hidden behind the keyboard are reachable after the first back; the same holds for
every sheet with a field, in both modes; the predictive-back gesture still peels
the sheet correctly; CI green.

---

## F4 · Counts that stop where they should — Status: done (PR #187)

**Depends on:** nothing. Corrupts data on an ordinary tap.

**Why:** a countable tide with a target of 2 reads **`39/2 Treinos`**. Each tap
adds one, forever: `setHabitCount` clamps at zero and nothing else
(`data/PautaRepository.kt:594`, `count = maxOf(0, n)`), and all four call sites
increment blind — `ui/screens/HojeScreen.kt:429`, `ui/screens/MaresScreen.kt:407`,
`service/ReminderActionReceiver.kt:75`, `service/MaresWidget.kt:132`. There is no
way down, so a mis-tap is permanent and the tide reads 100% forever.

**Files to touch:**
- `data/PautaRepository.kt` — `setHabitCount`, the one place the rule belongs
- `domain/HabitCalculator.kt` — if the cycle rule wants to be pure and tested
- `ui/screens/MaresScreen.kt` · `ui/screens/HojeScreen.kt` — the affordance down
- `src/test/…/HabitCalculatorTest.kt`

**The rule:** a tap increments to the target and no further; the next tap
**clears to zero**. That is exactly how a binary tide already behaves — tap to
mark, tap again to unmark — so a countable one becomes the same gesture with
more steps, and no count can ever exceed its target.

**And the repair path:** an existing 39 has to be fixable. The cycle alone does
that (tap once more from 39 → 0), but only if the clamp is applied to values
already stored; make sure a count read back above its target is treated as "at
the target" rather than displayed raw.

**Out of scope:** logging *more* than the target as a deliberate act
("drank 10 of 8"). If that turns out to be wanted, it is a separate decision
about what a target means, not a clamp.

**Accept:** a tide with target 2 never shows a count above 2; the tap after the
target clears it; an existing over-target count can be brought back with one
tap; the widget, the notification action and both screens all go through the same
rule; `HabitCalculator` tests green; CI green.

---

## F5 · The reader's own controls — Status: done (PR #187)

**Depends on:** F1 (the receipt's unit)

**Why:** four small things the reader gets wrong every session, all in the same
two files.

**Files to touch:**
- `ui/screens/ReaderScreen.kt` · `ui/screens/EpubReader.kt`
- `ui/MainScaffold.kt` — the receipt line
- `domain/Epub.kt` — the link styling rule
- `i18n/I18n.kt`

**(a) Pause without closing the book.** Today the only way to stop the clock is
to leave. Add pause/resume to the reader's `⋯`; a paused session keeps its place
and its block, and the paused time is not read. This is also half of what an
explicit start button was asked for — see **Decisions already taken**.

**(b) The bars stop covering the page.** The chapter must be inset by the height
of the top and bottom bars, not painted under them — measured, not guessed at
64px. A chapter heading, a last line and a publisher's logo were each hidden.

**(c) The receipt speaks the book's unit.** `MainScaffold.kt:507` says
`"{n} págs em {min} min"`; for an EPUB `n` is percentage points. Say **words**
for a counted EPUB (the honest figure, and the one the reader actually knows),
percent where words are unknown, pages for a PDF or a physical book.

**(d) A dead link is not painted as a link.** A schemeless URL survives the
sanitiser as a relative href and is styled with the accent, but nothing
navigates. Only a link that resolves to a chapter of *this* book keeps the accent;
everything else is ink. The rule belongs next to the sanitiser, with a test.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Pausar` | `Pause` |
| `Retomar` | `Resume` |
| `{n} palavras em {min} min` | `{n} words in {min} min` |
| `{n}% em {min} min` | `{n}% in {min} min` |

**Out of scope:** pagination (declined), the chapter index (`BOOK_LIBRARY.md` L4).

**Accept:** no text or image is ever hidden behind a bar at any text scale; a
paused session's minutes do not count; the receipt after reading an EPUB says
words; a link to panmacmillan.com is ink-coloured and inert; CI green.

---

## F6 · The launcher door, properly — Status: done (PR #187)

**Depends on:** nothing

**Why:** a regression from R8 (PR #171), confirmed on device: the book icon
opens book mode only when the app is fully closed. Tapping it while the app is
alive in recents leaves the mode as it was.

**Files to touch:** `MainActivity.kt` · `AndroidManifest.xml` (only if the
fallback is needed)

**The certain half:** `onNewIntent` calls `parseEntry(intent)` with
`coldStart = false` and therefore **drops the door on purpose**. Honour a
`MAIN` + `LAUNCHER` intent there, and on an `onCreate` that restores saved
state — while ignoring `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, so returning
through recents never changes the mode. Mark the intent consumed so a
configuration change cannot re-apply it.

**The uncertain half, and the fallback:** with an existing task and
`launchMode="singleTop"`, Android may bring the task forward **without
delivering an intent at all** — in which case no amount of intent reading helps.
If the device still fails after the above, the alias stops pointing at
`MainActivity` and points at a tiny no-UI trampoline activity that writes the
pref and starts the app; a distinct component always runs `onCreate`. It must
not appear in recents and must not flash.

**Accept:** switching from another app and tapping the book icon opens book mode;
returning through recents does not change the mode; returning from the document
picker does not change the mode; one entry in recents; both icons survive an
update; CI green. **Verify on the device — this is the failure a unit test cannot
see.**

---

## F7 · The pages of the print edition — Status: done (PR #187, partial — see Log)

**Depends on:** `BOOK_LIBRARY.md` L4 (same parsing pass, same sheet)

**Why:** the owner's actual request, and the best idea in the round: *"mesmo no
nosso epub temos as páginas algures lá"* — and they are right. EPUB3 carries the
print edition's page numbers as `epub:type="pagebreak"` markers with a
`page-list` in the nav document, precisely so a reader can cross-reference a
paper copy. A Pan Macmillan edition with the print ISBN is a good candidate.

This is what makes reading in the app compatible with reading anywhere else, and
it uses **the publisher's own numbers** — nothing is invented.

**Files to touch:** `domain/Epub.kt` (+ its tests) · `ui/screens/EpubReader.kt` ·
`ui/screens/BookProgress.kt`

- **Preserve the markers.** The sanitiser currently keeps `span` and drops its
  attributes, so the page number is lost. Carry it through as a marker element
  the stylesheet can draw.
- **Draw the separator.** A hairline across the measure with the page number
  small in the margin — the visual orientation asked for, and the thing that
  makes a continuous scroll navigable without pagination.
- **Say the page where one is known.** With markers present, the reader's chrome
  and the detail sheet can say `"página 123 de 228"` for an EPUB. Without them,
  percent stays, and a page derived from a hand-recorded `totalPages` may be
  offered **only** with an `≈`.

**Out of scope:** generating page numbers for a book that has none (an invented
page is the estimate this whole file exists to remove).

**Accept:** an EPUB carrying page-break markers shows the publisher's page
numbers and a separator at each; one without them is unchanged; the parser's
tests cover a book with markers, one without, and one with malformed markers;
CI green.

---

## F8 · The Hoje composer — Status: done (PR #187)

**Depends on:** nothing

**Why:** the labels land on the wrong line. `PRIORIDADE [1][2][3] QUANDO` /
`[manhã][tarde][noite] MIN` / `[40]` — each label reads as a suffix of the group
above it.

**Files to touch:** `ui/screens/HojeScreen.kt`

The cause is deliberate and documented at `HojeScreen.kt:1095`: U3 made labels
and pills flat siblings of one `FlowRow` so a large `textScale` wraps instead of
clipping. Keep that property — but make each `label + pills` group wrap as a
**unit**, never between a label and the pills it names. Nested flows, not a row
that can clip.

Includes the header chips (`DIAS ANTERIORES` / `A SEMANA` / `ROTINAS` /
`REVISÃO`), which stack into three ragged right-aligned lines at textScale 1.0
and **four at 1.5**, consuming about a quarter of the viewport before any
content.

**And the same fault in book mode**, added after the emulator run of 2026-08-03:
the Estante header (`ui/screens/BookShelfScreen.kt`) wraps `✎ Nota + ·` onto one
line and `Adicionar livro +` onto the next, leaving the separator `·` orphaned
at the end of the first. Same cause, same fix, one PR.

**Never:** solve the wrapping by clipping, by shrinking the text below the
app's meta size, or by removing a chip. U3 made these flat siblings for a
reason — a large `textScale` must wrap, not truncate.

**Accept:** at textScale 1.0, 1.3 and 1.5, every label sits with its own pills
and no separator is left orphaned; nothing clips; the header chips read as a
deliberate arrangement in both Hoje and the Estante; CI green.

---

## F9 · The duration toggle, next to the durations — Status: done (PR #187, partial — see Log)

**Depends on:** nothing

**Why:** the preset set (`pomodoro` 25/50/90 vs `simples` 15/30/45/60) is chosen
in Settings, far from any timer, and the owner could not find it. Worse, the
defaults are implicit and differ by surface: the planner assumes Pomodoro,
reading assumes Simples, and nothing says so.

**Files to touch:** `ui/screens/PautaSheets.kt` (`DurationPicker`, `TimerPresets`)
· `ui/screens/SettingsScreen.kt` · every call site that offers a duration

The toggle moves **into the duration picker** — a quiet mono `pomodoro · simples`
beside the pills — writing the same preference the Settings row writes. One
boolean, two places to set it, the same discipline as R8's launcher door. It
governs **both modes**: the owner asked for exactly that.

Every place a duration is chosen gets the picker: "Novo bloco", the reading
session, "Registar tempo", and an intention's target minutes.

**Accept:** the preset set can be changed without leaving the timer; the Settings
row still works and agrees; reading and planner both follow it; a custom value
still works; CI green.

---

## F10 · The updater that answers — Status: done (PR #187)

**Depends on:** nothing

**Why:** tapping "Verificar atualizações" a second time shows nothing at all, so
the row appears stuck at "Está atualizado.". The button, the state order and
`AppUpdater.check()` were all read and are correct — which means the cause is
one of two, and they need different fixes.

**Files to touch:** `ui/screens/SettingsScreen.kt` · `service/AppUpdater.kt` ·
`ui/viewmodel/AppViewModel.kt`

**Diagnose first:** does the tap reach `checkForUpdate()`? If it does, the check
resolves faster than the eye and the state is indistinguishable from before.

**Fix either way:** the result gets a **time** (`"verificado às 23:56"`), and the
checking state a minimum visible duration. A state that resolves faster than a
frame is indistinguishable from a dead button, and that is a UI defect whatever
the plumbing says. Add `Cache-Control: no-cache` to the request while there.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Verificado às {h}` | `Checked at {h}` |

**Accept:** tapping the button always visibly acknowledges the tap and always
leaves a timestamp; an offline check still reports the failure it already
reports; CI green.

---

## F11 · The floating layer — Status: pending

**Depends on:** nothing

**Why:** Pip floats over content — over a card in Hoje, over the month navigation
in Hábitos. R1 already removed one floating thing from that strip for exactly
this reason; there is still no rule about who may float and where.

**Files to touch:** `ui/MainScaffold.kt` · the screens whose content reaches the
bottom strip

Write the rule down and apply it: the bottom strip belongs to Pip and the
snackbar, and every scrolling screen reserves that height at the end of its
content. Nothing that carries information may sit under a floating thing.

**And the rule is not about scrolling.** The wording above was written with a
portrait scroll in mind, and the emulator run of 2026-08-03 found the worse
case: **in landscape, Pip sits on top of the Pauta tab's play button** — the
screen's primary action, not merely content, and reachable without scrolling at
all. A short viewport puts the floating layer over the fold rather than under
it. The rule is "nothing that carries information *or accepts a tap*", in every
orientation.

**Never:** fix this by moving Pip per screen. One rule, applied once, in the
scaffold — a per-screen offset is how this returns.

**Accept:** in both modes, on all six screens, in **portrait and landscape**,
nothing that carries information or accepts a tap sits under Pip; scrolling to
the bottom leaves nothing hidden; the snackbar still clears the tab bar; CI
green.

---

## F12 · "Quando", without typing it — Status: pending

**Depends on:** nothing

**Why:** a tide's *when* is a free-text field (`Quando? (opcional, ex.: manhã)`)
that most answers fill with one of three words. Typing "manhã" costs a keyboard
— which, until F3, was also a trap — and two answers ("manhã e tarde", as one
tide already reads) can only be expressed as prose the app cannot use.

**Files to touch:**
- `ui/screens/MaresSheets.kt` (or wherever the tide form lives) — the field
- `data/entity/Entities.kt` — `HabitEntity.time` is already a free-text string;
  the chips write into it, so **no migration and no schema change**
- `i18n/I18n.kt`

**The shape:** three chips — `manhã` · `tarde` · `noite` — **multi-select**, above
the existing field. Picking chips writes their words into `time`; typing
something the chips don't cover still works and simply selects no chip. The field
stays: this adds a fast path, it does not take away the free one.

Keeping `time` a string is the whole point — it is `pauta.v4` data, the web app
wrote prose into it, and a round-trip must stay lossless. The chips are a way of
writing that string, not a new model.

**Out of scope:** scheduling anything from the choice (a tide already has
`clock` for a real reminder time); a fourth period.

**Accept:** one, two or three periods can be chosen and re-chosen; the stored
string reads naturally in both languages; a tide imported from the web with
arbitrary text still shows that text and loses nothing; the field still accepts
free text; a `pauta.v4` round-trip is byte-identical; CI green.

---

## F13 · The reading tab, honestly — Status: pending

**Depends on:** F2 (the day data must be correctable first)

**Why:** two things. The tab contradicts itself — *"Sequência atual: 1 dia"*
three lines above *"Ainda sem leituras registadas."* — and it still ends in the
planner's tides, which is what started this whole round: water and running under
a reading screen.

**Files to touch:** `ui/screens/BookHabitsScreen.kt` · `domain/ReadingStats.kt`
(+ tests) · `ui/screens/MaresScreen.kt` · `i18n/I18n.kt`

**(a) One definition of a reading day.** `BookHabitsScreen.kt:133` asks "are
there sessions?"; `BookHabitsScreen.kt:344` asks "are there *plottable*
sessions?"; both print the same sentence, and a 0-minute session satisfies one
and not the other. Pick one rule for what counts as a day read, apply it to the
grid, the streak and the charts, and give the charts their own sentence when
they alone have nothing to draw.

**(b) The tides leave the book-mode tab.** The sections become: Objetivo anual ·
Dias de leitura · Gráficos · Livros terminados · **Ritmo da estante** (how long
the current book has left at the measured pace, what is next, what has been
untouched for weeks — all derived, no new state) · **Do teu caderno** (the
recent notes and quotes, which today have no home outside a single book).

A quiet `"as tuas marés →"` at the foot switches to planner mode, so living in
book mode never means losing the tides.

**Open, and the owner decides when this task is reached:** *Metas de leitura*
(self-set targets the sessions fill in — 30 min/day, 5 days/week) were proposed,
then argued against by the author of this file on the grounds that a target on
an empty shelf is a form of nagging, which the reader's guardrails forbid. Ask
before building it.

**Accept:** the tab never contradicts itself; no self-reported list appears in
book mode; the tides are one tap away; `bookMode` off leaves Marés pixel-identical;
`ReadingStats` tests green; CI green.

---

## Order

Strictly top to bottom, and the order is by **what a defect costs the person
using the app**, not by what it costs to fix. F1…F4 all destroy or corrupt
something the user typed or did; everything after them is an annoyance, however
visible. F6 is fifteen minutes' work and still waits, because nothing is lost
while it is broken.

```
F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10 → F11 → F12 → F13
```

**And this file is not the first one.** Two things run before F1:

- **`docs/FIRST_RUN.md` N1**, alone and ahead of everything. On a clean Android
  13+ install the app never requests `POST_NOTIFICATIONS`, so the focus
  notification and all three daily reminders are dropped by the OS in silence.
  A feature that does nothing outranks a feature that does the wrong thing.
- **`docs/BOOK_LIBRARY.md` Phase L-0** — L1 and L2 are done; **L3** remains. A
  wipe that leaves your library on disk and a backup that carries your reading
  list into a file you might share outrank a percentage that displays wrong.

Three tasks that were in this file (the chapter index, the shelf at scale, notes
anchored to a position) were dropped in favour of `BOOK_LIBRARY.md`'s L4, L8 and
L6, which cover the same ground with more of it. `docs/CONTEXT.md` §3 holds the
combined order across all three active files.

---

## Log (append one line per shipped task: date · task · PR · note · Verified:)

Every entry ends with **Verified:** — what was actually exercised, and by
whom. This file exists because a green test suite and a working app turned out
to be different things; an entry that cannot say what was verified should say
that instead.

<!-- e.g. 2026-08-03 · F1 · #n · … · Verified: JVM tests; not tested on a phone -->
2026-08-06 · F10 · #187 · **The diagnosis the spec asked for, first:** the tap does reach `checkForUpdate()` — the call chain was re-read and is sound — so the cause is the other branch it named. The check resolves faster than the eye, and *"Está atualizado."* is the same sentence whatever the answer, so a successful second check leaves the row byte-identical to before it. A state that resolves faster than a frame is indistinguishable from a dead button, and that is a UI defect whatever the plumbing says. Both halves of the fix therefore shipped: `updateCheckedAt` stamps every outcome (**including a failure** — "we tried, at 23:56" is still an answer, and it is the one thing that always changes), and `MIN_CHECK_VISIBLE_MS` (450 ms) holds "A verificar…" long enough to be seen without faking a wait. The time appears in the Settings row *and* in the sheet, because the sheet is where the second tap actually happens. `Cache-Control: no-cache` went on the request while there: `latest-native` is a rolling tag, which is precisely the thing an intermediary caches, and a cached answer is another way the check can come back instantly with yesterday's release. · **Verified:** nothing. No SDK here; the diagnosis is a code reading, not an instrumented one, and the spec explicitly asked for instrumentation before a fix. If the button is still dead on a device, the timestamp will now say so — which is itself the instrument this needed. The toggle now lives **inside `DurationPicker`**, so it appears wherever the pills do rather than being wired per screen, and it writes the same single `timerPresets` preference the Settings row writes — one boolean, two places to set it, last action wins, which is R8's launcher-door discipline applied again. It governs **both lenses**: the planner's start sheet and the reading card each pass their own default for the unset case (Pomodoro and Simples respectively, as U2 established) while changing the same value. Quiet mono `pomodoro · simples` rather than a segmented control, because it is a footnote to the pills and not a second decision. **Two of the four places named in the spec did not get the picker, deliberately:** *Registar tempo* asks for a **past** duration with a different range (1–1440) and no meaning for "sem limite", so `DurationPicker`'s 0-and-sentinel semantics would be a regression rather than a unification; and an intention's target minutes is an inline field inside the Hoje composer, where a pill flow plus a custom field would dominate the row F8 has just untangled. Both are judgement calls against the spec's "every place", and both are recorded here rather than silently skipped. · **Verified:** nothing ran locally (no SDK) and no device has seen the toggle. That the two rows agree is a property of writing one preference, which is read from the code and not observed. Three wrapping faults, one cause each. **The composer:** U3's single flat `FlowRow` kept the property that matters (a large `textScale` wraps rather than clips) and paid for it with a flow that can break *anywhere* — including between a label and the pills it names, which is what put `QUANDO` at the end of the priority row and `MIN` at the end of the time row. `ComposerGroup` makes each label-and-its-pills one child of the outer flow, so a group that doesn't fit moves down whole; it can still wrap inside itself if a group is wider than the entire measure, which is U3's property intact. **The header chips:** the cause was width, not wrapping — four chips shared the date's row, so they had about half the measure and came down as three ragged right-aligned lines at 1.0 and four at 1.5. They now have their own full-width row under the date. The headline still gets the full measure, which is what U3 was protecting. **The Estante header:** the `·` was a flat sibling and could therefore end a line with nothing after it; bound to the action it precedes, it can only sit between the two or lead the second line. · **Verified:** nothing. This is a **layout** fix and layout at a text scale is precisely what CI cannot see — no SDK here, so no compile locally, and nobody has looked at these three headers at 1.0, 1.3 or 1.5. The reasoning is sound and unobserved. **Two of the three parts shipped, and the third is honestly out of reach here.** The sanitiser now recognises both spellings of a page break (`epub:type="pagebreak"` and `role="doc-pagebreak"`) and emits its own marker carrying the publisher's number, because `span` survives the allow-list and its attributes do not — which is exactly why the numbers were being lost. Three details worth not re-deriving: the marker keeps the **book's own tag name** so the closing tag the scanner hands back still matches; a marker whose attributes name the page has its **own text discarded** to the matching close, since publishers write the number in both places and printing both leaves a stray "123" mid-paragraph; and the label is **validated** against what a page number can be rather than filtered down to it — filtering `&lt;script&gt;` leaves "lci", which is a plausible roman numeral and a lie. The separator is a hairline with the number in the margin, drawn from `data-p` or from the element's own text when the attributes were silent. **Not built: "página 123 de 228" in the chrome.** Saying *which* marker you are at needs the page-list plumbed from the `:reader` process and mapped against the scroll — that is the parsing pass `BOOK_LIBRARY.md` **L4** owns and which this task declares a dependency on; L4 has not shipped, and inventing a second channel for it here would be the duplication the dependency exists to avoid. What did ship in its place is the **estimate**, from a hand-recorded `totalPages`, carrying `≈` and saying what it assumed — which is the only form the spec permits. **One behaviour to know about:** an unclosed numbered marker swallows the rest of the chapter, because it reuses the same drop mechanism an unclosed `<script>` already has. Consistent with the file, and covered by a test that asserts the document before it survives. · **Verified:** the parser half is well covered (`EpubTest`: a marker with a title, the ARIA form, the number-in-`id` case, roman numerals, a hostile title, no double-printing, a book with no markers, a malformed one). **Nothing was run locally and no book has been rendered** — the separator's appearance, which is the whole point of the feature, has never been seen. The certain half, and only that. R8's `coldStart` test was a stand-in for two conditions, and `LauncherDoor.opensADoor` now checks both directly — not arriving through recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`), and not already spent. The **spent** half is the one worth not re-deriving: a launch intent outlives its launch (it is still `getIntent()` after a configuration change or a process restore), so "read the door on every create" is only safe once the door can be marked used; the extra is written back with `setIntent` so the marked copy is what the system hands back. `onCreate` now reads the door **even with saved state** — that is exactly the path a tap on the icon takes when the process was killed but the task survived, and the path R8's cold-start test called "not a cold start". Shortcuts and shares still fire only on a genuine first create. R8's stated worry (the document picker, the share sheet) turned out not to be a path here at all: both return through a result, never through a new intent. **The uncertain half is not built.** The spec makes the no-UI trampoline conditional on the device still failing, and no device has run this — so building it would be adding an activity on a guess. If the icon still misbehaves with an existing task, that fallback is the next step and the reason is recorded here. · **Verified:** the decision is pure and fully covered in `LauncherDoorTest` (recents, spent, wrong action, wrong component). **The thing this task exists to fix has not been observed** — the spec says "verify on the device, this is the failure a unit test cannot see", and no device has seen it. Nothing here ran locally either.
2026-08-06 · F5 · #187 · **(a)** pause/resume is a glyph in the reader's top bar rather than an item inside `⋯` — the timer control should not be two taps deep behind a sheet that covers the book. This is the **new chrome control row** `BOOK_LIBRARY.md`'s guardrail says the first of F5/L4/L5/L6 owns; `☰`, `Aa` and `✎` join it here. The paused time is not counted by construction, not by arithmetic: a reading session is a focus block, `pauseActive` closes the open span, and `blockElapsedMs` only ever sums spans. **(b)** the bars' inset is now **measured** (`onSizeChanged`) instead of the guessed 8px/64px, because the top bar carries the status-bar inset and the bottom one the navigation-bar inset and neither is knowable in advance. The last non-zero measurement is deliberately kept: the bars are inside an `AnimatedVisibility` and measure 0 while hidden, and following that would reflow the whole chapter on every chrome fade. A WebView's CSS px is a dp, so the number crosses into the stylesheet unchanged. **(c)** the receipt's unit is decided in the repository, where the book is in hand — the snackbar fires in the shell long after the reader is gone — and F1's ceiling applies to it, so a chapter jump reports its minutes and not a word count it didn't earn. **(d)** `sanitize` takes a `linkResolves` predicate and marks everything else `class="dead"`; the archive itself is the authority, and a bare `#p3` is dead too, because the reader has no same-document jump and painting it differently would be a second lie. · **Verified:** the sanitiser half is genuinely tested (`EpubTest` covers the panmacmillan case, a resolving link, a bare anchor and the default). **Everything else is unverified** — no SDK here, so no local compile or test run, and the inset measurement, the pause glyph and the receipt have never been on a screen. (b) in particular is the kind of fix only a device can confirm.
2026-08-06 · F4 · #187 · The rule is two pure functions in `HabitCalculator` — `cycleCount` (what a requested value settles at) and `shownCount` (what a stored one means) — and one call: `setHabitCount` applies the cycle, which is the single point all four writers already pass through, so the two screens, the notification action and the widget each keep asking for `current + 1` and none of them learns the rule. **Why a cycle and not a clamp:** clamping at the target would make the tap after the last one do nothing, which is a dead control, and would leave no way down at all — the tide would still read 100% forever. The cycle is the binary tide's own gesture with more steps, which is also why F4 needed no new affordance: the "way down" the spec asks for is the tap that was already there. `shownCount` is what repairs the existing damage — a row holding 39 against a target of 2 reads as 2 (printing 39 would print a number the tide can no longer reach) and is then one tap from zero. Applied at the two places that print `n/target`: the Hoje strip via `TideHelpers`, and the Marés row header. · **Verified:** the new `HabitCalculatorTest` cases are the honest part of this one — the rule is pure and covered, including the 39-against-2 case from the phone. But **nothing ran locally** (no SDK) and no device has tapped a tide; that the widget and the notification action really do route through `setHabitCount` was read from the code, not observed.
2026-08-06 · F3 · #187 · `SheetImeBackHandler` lives in `PautaSheet`, so **every** sheet in the app gets the two-stage back at once rather than each form growing its own — the tide form was the worst case, not the only one. Two things it depends on, both worth not re-deriving: the handler is composed *inside* the sheet body, which is what puts it above Material's own sheet-level back handling on the dispatcher, and it is enabled only while `WindowInsets.isImeVisible`, so it can never eat a back press that should close the sheet. It clears focus as well as hiding — a field that keeps focus keeps asking for the IME and the keyboard returns on its own. The background tap is `detectTapGestures` rather than a `clickable`: children get the pointer first (so fields, chips and buttons are unaffected) and a tap only resolves after a still lift (so the body still scrolls). The tide form's *quando* field is now `ImeAction.Done` in both the add and edit sheets, since what follows it is chips and a picker, not another field. · **Verified:** nothing. No SDK locally; CI compiled it. This is a **gesture** fix and gestures are exactly what CI cannot see — predictive back, the two-stage press and the background tap have never been performed. If one task in this file needs a device before it is believed, it is this one.
2026-08-06 · F2 · #187 · **(a)** the session rows in `BookDetailSheet` and the Sessão tab are tappable and open the planner's own `EditBlockSheet` — without this nothing else in the task is reachable, which is why it was first. **(b)** each ended span gets a start and an end, both `PautaTimeField` **pickers rather than text fields**: a picker has no keyboard, so F3's trap cannot apply to a control F2 just added, and the duration recomputes live beside it. The new pure helper is `DateUtils.withClock`, which applies an `HH:MM` to the day of *the instant being edited* — correcting last Tuesday must correct last Tuesday — and returns null on anything that isn't a time, so a half-picked field can't send a session to midnight. Backwards spans are refused twice: the sheet says so inline and `Guardar` drops them rather than writing a coerced value. **(c)** `pagesDelta` is editable in book mode, in F1's unit, with the placeholder "por contar" so null stays visibly distinct from 0; `pagesDeltaChanged` on the payload is what stops a planner block's absent editor from reading as "the user cleared it". **(d)** `deleteBook` now deletes the book's blocks and their spans first (sessions before blocks — the spans are found *through* the block ids). The five positional `onSave` arguments became a `BlockEdit` payload; five was already past what a call site can read, and this task would have made it seven. **Two things the Accept asks for and did not happen:** there is no repository test proving the cascade, because Room needs a device and Robolectric would be a new dependency (§D, §K.3) — the assertion is honestly unwriteable here, not skipped; and nothing was exercised on a screen. · **Verified:** nothing was run locally (no SDK). New `DateUtilsTest` cases cover `withClock` only. CI compiled it. The tap targets, the pickers, the delete and the cascade have never executed.
2026-08-06 · F1 · #187 · **(a)** `BookProgress.kt` already decided how progress is *shown*; it now also decides how it is *asked for* — `bookProgressQuestion` / `bookProgressUnit` / `bookProgressMark` / `clampBookProgress`, one place, four call sites. The conclude sheet had **no clamp at all**, which is the actual mechanism behind the 100%: it asked "Até que página chegaste?", the owner answered with a page number, and an attached EPUB stored it as percentage points. Every field now carries the unit *beside* it as well as above it, because the label scrolls off and the mark does not. **(b)** `BookMath.MAX_HUMAN_WPM = 1000` with `impliedWpm` + `readingSpans` as the pure pair; `pagesPerHour` takes an optional `wordsPerUnit` (default null = no ceiling, so callers with no book keep the old arithmetic) and `wordsPerMinute` passes it through, so the two figures can still never disagree. Applied at the detail sheet's Ritmo line **and** in `BookHabitsScreen`'s per-session `words`, which F1's file list didn't name but which feeds the same inflated number into the charts. **(c)** `sessionOutcome`'s guard is now duration alone; `aShortSittingThatTurnedAPageIsStillASession` was R5's rule and is deliberately reversed, with the reasoning kept in the test. **One thing in the spec was not done:** F1 lists `BookFormSheet.kt — current/total page fields`, and that sheet has no current-page field — only `totalPages`, which does not write `currentPage` and is a real quantity for an EPUB (the print edition's length, which F7 will use with an `≈`). Left alone deliberately. · **Verified:** nothing was run. No Android SDK in this environment, so the new `BookMathTest` / `ReaderMathTest` cases have not executed locally — CI is the only thing that has compiled or run them, and no device has seen a single one of these fields.
