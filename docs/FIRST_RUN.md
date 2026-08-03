# First run — task file

> **Concept.** `docs/FIELD_FIXES.md` was written from *using* the app with data
> in it. This file is the other half: what the app does **before** you have any
> data, and what it asks the **operating system** for. Both were found the same
> way — by installing `v1.443` on a clean Pixel 7 emulator and going through the
> app as a new user, in both lenses, at two text scales and in both
> orientations.
>
> The theme is that the app's edges were never walked. The first focus block
> raises a notification the OS silently drops, because nothing ever asked for
> the permission. Onboarding ends on a button that says "start blank" and offers
> no other door. The first screen of book mode is one grey line. Each of these
> is invisible to anyone whose install already has data and already granted
> everything — which is everyone who has tested this app.
>
> Ships as 8 self-contained tasks (N1…N8). Each task is one PR.

---

## How to use

**The prompt is always the same, and it carries no number:**

> Do the next one in `docs/FIRST_RUN.md`.

**What that means, exactly** (Claude — this is binding):

1. Read this file. The task to do is the **first one whose Status is
   `pending`**, top to bottom. Never skip ahead, never batch two.
2. **Open the reply with the progress bullet**, before any tool call, in this
   shape and no longer:

   ```
   **Done:** N1 ✓
   **Now:** N2 — the reader's chrome stops running away
   **Left:** N3…N8 (6)
   ```

3. Do **only** that task, following its spec, `docs/GUARDRAILS.md` and the
   Extra guardrails below.
4. Ship it via the `CLAUDE.md` workflow: branch → commit → PR → CI green →
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
here. The ones that bite hardest in this file:

- **Section A — identity.** Several tasks here add an empty state or a prompt.
  Quiet is the point: no illustration beyond Pip, no Material dialogs of our
  own, no exclamation marks.
- **Section E — prefs are law.** Every task here was found at a text scale or
  an orientation nobody tested. Acceptance in this file always names **1.0 and
  1.5**, and N7 and N5 also name **landscape**.
- **Section H — recording and reversibility.** N1 is about a permission, not a
  recording, but the same instinct applies: never make a promise to the user
  that the app cannot keep.
- **Section K.12 — never claim a green CI means it works.** Every defect in this
  file passed CI.

**Extra, specific to this file:**

- **A permission is asked for at the moment it is needed, never at launch.** No
  permission wall in front of a first-time user.
- **An empty state is a door, not a sign.** If a screen has nothing to show, it
  offers the one action that would give it something. A sentence alone is not
  enough — that is the defect N5 exists to fix.
- **Nothing here changes behaviour for an existing install** except where the
  task says so explicitly. These are first-run paths; a user with data and
  permissions granted should notice N1, N2 and N3 and nothing else.

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## The evidence — what the emulator showed

Recorded so a fresh session needs neither this list's history nor the
conversation that produced it. Build `v1.443` (`versionCode` 29761226), Pixel 7
AVD, Android 15, 1080×2400 @ 420 dpi, clean install, 2026-08-03.

- **A focus block raises no notification at all.** `FocusService` starts,
  `dumpsys` reports `isForeground=true foregroundId=1001` with the `pauta_focus`
  channel built and the two actions attached — and the shade is empty. The cause
  is one line of `dumpsys notification`: `AppSettings: com.pauta.app
  importance=NONE`. `POST_NOTIFICATIONS` is `granted=false`, because the only
  code that requests it is `SettingsScreen.kt:511`, behind the *Notificações*
  toggle in the Foco section. Until a user finds that row, the focus
  notification and all three daily reminders are dead. The row's own subtitle —
  *"Avisos locais enquanto a app está aberta."* — reads as though this were
  intended.
- **The reader's chrome hides two seconds after you deliberately ask for it.**
  `ReaderScreen.kt:190` re-arms `ChromeLingerMs` on *every* transition to
  visible, not only on entry, so a middle tap to reach `←` or read the progress
  gives a two-second window. Four pending tasks add controls to that same
  chrome: F5(a) pause, L4 contents, L5 `Aa`, L6 capture.
- **Each habit's month strip scrolls alone.** `MaresScreen.kt:740` gives every
  row its own `horizontalScroll` state. Scrolling "Beber água" to day 24 leaves
  "Meditar" showing day 1, so the rows no longer line up — and no cell carries a
  day number, so after one scroll nothing says which days are on screen.
- **Onboarding's last button is "Começar em branco"** (`OnboardingOverlay.kt:216`)
  and there is no second button. Import lives in Settings → Dados e privacidade.
  A reinstall has no visible path back to a backup.
- **The Estante empty state is one italic line** — *"Nenhum livro em curso"* —
  with the only entrance a small `Adicionar livro +` in the header. It is the
  first screen of book mode. `MaresScreen.kt:344` does the opposite on the tab
  next to it: Pip, a sentence, and five tappable suggestions.
- **Those five suggestions vanish after the first tap**, because they live
  inside the `EmptyState` branch. Of five shortcuts, exactly one is ever usable.
- **The empty Pauta tab offers two primary actions** — the dark
  `COMEÇAR / Um novo bloco ▶` card (`PautaScreen.kt:562`) and a
  `Começar um bloco de foco` chip directly under it (`PautaScreen.kt:378`).
  Both open the same sheet.
- **"Adicionar livro" asks nine questions** — título, autor, série, nº, formato,
  ficheiro, total de páginas, género, estado — with only the title required.
  `HabitFormSheet.kt:166` does the opposite two taps away: name, when, and
  `+ mais opções`. Attaching an EPUB also leaves **formato** on `Físico`,
  although the parser has just proved otherwise.
- **In landscape, Pip sits on top of the play button.** Not beside it — on it.
- **What held up:** dark theme at every screen visited; text scale 1.5 on the
  Sessão, Marés and Settings layouts; and a deliberately corrupted EPUB, refused
  with *"O livro parece danificado."* rather than rendered.

---

## Decisions already taken — do not re-open these

- **The permission is asked for with a system dialog, at the first focus block.**
  A quiet in-app row was the alternative and it loses: a reminder that never
  fires is worse than a prompt. Decided 2026-08-03. See N1.
- **No permission wall at launch.** Asking for everything on first open is the
  pattern this app exists to avoid.
- **The empty states are not illustrated.** N5 and N6 borrow the *structure* of
  the Marés empty state — Pip, a sentence, real actions — not a new drawing.
  `GUARDRAILS.md` A stands.
- **N4 does not add a second onboarding page.** The restore path is a second
  button on the page that already exists. An onboarding flow that grows is an
  onboarding flow nobody finishes.

---

## N1 · The permission nobody asked for — Status: pending

**Depends on:** nothing. **Do this first, and on its own — it is the only task
in any file where features that already shipped do nothing at all.**

**Why:** the app posts notifications from three places — the focus/reading
service, the three daily reminders, and the per-habit reminders — and on a clean
Android 13+ install every one of them is dropped by the OS, silently, because
`POST_NOTIFICATIONS` was never requested. The only request site is behind a
Settings toggle most users will never open. Nothing in the UI reveals the
failure: the focus block runs, the service is genuinely foreground, and the
shade stays empty.

This also blocks `BOOK_LIBRARY.md` L10, which adds a fourth notification onto
the same floor.

**Files to touch:**
- `ui/screens/SettingsScreen.kt` — the request already exists at `:511`; the row
  gains a denied state and the subtitle is corrected
- `ui/screens/PautaScreen.kt` — the request at the first focus block
- `ui/MainScaffold.kt` or a small `ui/Permissions.kt` (new) — one place that
  owns "ask if we have never asked", so three call sites don't each grow logic
- `data/entity/Entities.kt` + `data/AppDatabase.kt` — one pref, Room **11 → 12**
- `i18n/I18n.kt`

**How:**

One pref, `notifAskedAt: Long = 0` (`// native-only`), records that we have
asked once. Android will not show the dialog a second time after a denial, so
asking repeatedly is noise; asking once, at the right moment, is the whole
design.

Request at exactly two moments:

1. **Starting a focus block** (planner or reading) when `notifAskedAt == 0`.
   This is the honest moment: the app is about to promise an ongoing
   notification.
2. **Turning on any reminder toggle** — the existing behaviour at `:511`,
   unchanged, plus the habit reminders which do not ask today.

The Settings row stops lying. Three states, not two:

| State | Row reads |
|---|---|
| granted, enabled | as today |
| never asked | as today — the toggle asks |
| **denied at OS level** | the toggle shows off and disabled, with `tr("Bloqueado nas definições do sistema")` and a tap that opens `Settings.ACTION_APP_NOTIFICATION_SETTINGS` |

And the subtitle is wrong on its own terms: *"Avisos locais enquanto a app está
aberta"* describes neither the foreground service (which runs with the app
closed) nor the alarms (which fire with the app closed). Replace it with what
happens.

Read the OS state with `NotificationManagerCompat.from(context).areNotificationsEnabled()`
rather than only `checkSelfPermission` — a user can disable the channel or the
app's notifications without touching the permission, and the row should tell the
truth in that case too.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Bloqueado nas definições do sistema` | `Blocked in system settings` |
| `Abrir definições` | `Open settings` |
| `Lembretes e o bloco em curso, mesmo com a app fechada.` | `Reminders and the running block, even with the app closed.` |

**Out of scope:** the reading reminder (`BOOK_LIBRARY.md` L10 — it inherits this
and needs no permission code of its own); a rationale screen before the dialog;
notification channel settings beyond the link.

**Never:** ask at launch; ask more than once; block any feature on the answer —
a denied permission means the timer still runs and the Settings row says why,
not that the app stops.

**Accept:** on a clean install, starting the first focus block asks for
notification permission once and the ongoing notification then appears in the
shade; declining leaves the timer working and the Settings row reading
"Bloqueado nas definições do sistema" with a working link; a second focus block
does not ask again; enabling a habit reminder on a never-asked install asks;
the migration is tested; `bookMode` on and off both behave; README updated (the
install/behaviour text mentions notifications); CI green.

---

## N2 · The reader's chrome stops running away — Status: pending

**Depends on:** nothing (but if F5, L4, L5 or L6 has shipped, this is more
valuable, not less)

**Why:** `ReaderScreen.kt:189-196` starts the chrome visible, hides it after
`ChromeLingerMs` (2 s) — and re-arms that timer every time `chrome` becomes
true, including the deliberate middle tap that asked for it back. Reaching `←`,
reading the progress line, or aiming at `⋯` all have to happen inside two
seconds. Four pending tasks put more controls there.

**Files to touch:**
- `ui/screens/ReaderScreen.kt` — the `chrome` state and its `LaunchedEffect`

**How:**

Separate *arriving* from *asking*. The linger belongs only to the first
appearance:

```kotlin
var chrome by remember { mutableStateOf(true) }
var summoned by remember { mutableStateOf(false) }   // true once the user tapped

LaunchedEffect(state.ready) {
    if (state.ready && !summoned) { delay(ChromeLingerMs); chrome = false }
}
```

A middle tap sets `summoned = true` and toggles `chrome`; from then on the
chrome is under the reader's control and a second tap puts it away. A scroll of
the page may also hide it — that is the one automatic dismissal worth keeping,
because it means "I am reading again", and it is what every reader does.

**Out of scope:** what goes *in* the chrome (F5, L4, L5, L6 each own their
control); the PDF half's own gesture handling beyond the shared `onTapMiddle`.

**Never:** hide the chrome on a timer once the user has asked for it. If the
reader wants it gone, they tap.

**Accept:** entering the reader shows the chrome and it fades once; a middle tap
brings it back and it stays until the next tap or a scroll; the back arrow is
reachable without racing a timer; `reducedMotion` still skips the fade; both
formats behave the same; nothing user-visible changed outside the reader; CI
green.

---

## N3 · One month, one strip — Status: pending

**Depends on:** nothing

**Why:** every habit row in Marés owns its own `horizontalScroll` state
(`MaresScreen.kt:740`), so scrolling one row desynchronises it from the others
and the month stops being readable as a grid. Worse, `DayCell` carries no date:
after any scroll there is nothing on screen that says which days you are looking
at, and the tab's whole subject is *which days*.

**Files to touch:**
- `ui/screens/MaresScreen.kt` — one hoisted `ScrollState` for the tab; `DayCell`
  and `CellDay` gain the day number
- `ui/screens/BookHabitsScreen.kt` — the reading-days grid uses the same
  renderer (R7) and inherits both changes

**How:**

Hoist one `rememberScrollState()` in the tab and pass it to every strip, so all
rows move together and a habit added later starts aligned. Scroll it to today on
first composition rather than to day 1 — the useful end of the month is the one
you are in.

Label the cells. The day number inside a 28 dp cell is too small at any text
scale, so put it **under** the strip as a sparse ruler: 1, 8, 15, 22, and the
last day, in the mono meta treatment. That reads at a glance, survives
`textScale`, and costs one row per tab rather than one per habit.

**Out of scope:** vertical alignment of habit *names* with the strip; a
week-based layout; anything about what a cell means (that is F4 and F12).

**Never:** make the strips independently scrollable again "so each habit can be
inspected". The comparison across habits is the feature.

**Accept:** scrolling any month strip moves all of them; the strip opens on
today; the ruler names at least five days and stays legible at textScale 1.5;
the reading-days grid in book mode inherits both; `bookMode` on and off both
look right; CI green.

---

## N4 · A way back in — Status: pending

**Depends on:** nothing

**Why:** onboarding's last page offers `Começar em branco` and nothing else
(`OnboardingOverlay.kt:216`). The wording implies an alternative that is not
there. Someone reinstalling after a phone change has a `.json` backup in their
files and no visible way to use it: `Importar dados` is in Settings → Dados e
privacidade, four taps past a screen that just told them to start blank.

This is the cheapest task in this file and the one with the largest worst case.

**Files to touch:**
- `ui/OnboardingOverlay.kt` — a second action on the last page
- `ui/screens/SettingsScreen.kt` — the import launcher is at `:156`; lift it or
  expose the same entry point
- `i18n/I18n.kt`

**How:**

On the last page only, a ghost `Restaurar uma cópia` beside the primary button,
opening the same `GetContent()` launcher Settings uses. A successful import
closes onboarding and lands on Hoje with the data in place; a failed one says so
and leaves onboarding where it was.

Both `pauta.v4` and `pauta.books.v1` are accepted here — the same two importers
Settings already offers. If that means two rows rather than one button, two rows
is correct; silently guessing the format is not.

**Out of scope:** a third onboarding page; importing from the web app's URL;
cloud restore of any kind.

**Never:** make restore the primary button. Most first runs are genuinely first
runs.

**Accept:** the last onboarding page offers restore; importing a `pauta.v4` file
there produces the same result as importing from Settings; a malformed file
reports the same error; declining still starts blank; onboarding is still shown
exactly once; README updated (install steps mention restoring); CI green.

---

## N5 · The shelf's front door — Status: pending

**Depends on:** `BOOK_LIBRARY.md` L3 if it has shipped (the paused shelf changes
what "empty" means); otherwise nothing

**Why:** the Estante with no books is one italic line, `Nenhum livro em curso`,
and 80% empty screen. It is the **first screen of book mode** and of the second
launcher icon. The only entrance is a small `Adicionar livro +` in the header,
which reads as a header link rather than the thing to do. Marés, one tab away,
already solves this problem in this codebase.

**Files to touch:**
- `ui/screens/BookShelfScreen.kt` — the empty branch
- `ui/EmptyState.kt` — reuse; extend only if the shape genuinely does not fit
- `i18n/I18n.kt`

**How:**

Use the shared `EmptyState` with Pip, as Marés does: an opening sentence, a line
of explanation, and **one real action** — `Adicionar o primeiro livro`, opening
the same `BookFormSheet` the header opens.

Say what the shelf is for, once: three shelves — a ler, a seguir, lidos — and
that a book can carry a PDF or an EPUB. A first-time user has no way to know the
app reads books at all; the shelf is where that is discovered or not.

The header's `Adicionar livro +` stays. It is correct once there *are* books.

**Out of scope:** suggested titles of any kind (there is no catalogue and there
will not be one); the sections' own empty states once at least one book exists;
search (L8).

**Never:** add an illustration. Pip and type, as everywhere else.

**Accept:** the empty Estante shows Pip, a sentence and a working primary action
that adds a book; the state disappears entirely once one book exists; it is
legible at textScale 1.5 and in landscape; the planner's Hoje tab is unchanged;
CI green.

---

## N6 · The suggestions don't leave — Status: pending

**Depends on:** nothing

**Why:** `MaresScreen.kt:344` offers five one-tap habits — Beber água, Ler,
Meditar, Exercício, Dormir cedo — inside the `EmptyState` branch. Tapping one
creates the habit and, with it, destroys the row. Of five shortcuts, exactly one
is ever usable, and the second habit costs the full form.

**Files to touch:**
- `ui/screens/MaresScreen.kt` — the suggestion row moves out of the empty branch
- `i18n/I18n.kt` (only if a heading is needed)

**How:**

Render the suggestions whenever the month has **fewer than three** habits,
filtered to those not already present, above `+ adicionar maré`. Past three the
row disappears for good — someone with three tides has understood the feature
and does not need prompting.

Keep it quiet: the same accent chips, no heading shouting at a user who is
mid-list.

**Out of scope:** suggesting habits based on anything (no inference, no
history); a longer list; suggestions in book mode's Hábitos tab.

**Never:** show a suggestion for a habit that already exists this month.

**Accept:** with zero, one or two tides the unused suggestions are visible and
each adds a tide; with three or more they are gone; an added suggestion never
reappears; the empty state still reads as it does today; textScale 1.5 does not
break the row; CI green.

---

## N7 · One way to start a block — Status: pending

**Depends on:** nothing. **Blocked on a decision — read below before starting.**

**Why:** the empty Pauta tab shows the dark `COMEÇAR / Um novo bloco ▶` hero
card (`PautaScreen.kt:562`) and, roughly 200 dp below it, a
`Começar um bloco de foco` starter chip (`PautaScreen.kt:378`). Both open
`showStart`. Two primary actions for one thing, on the emptiest screen in the
app.

**The decision, and it is the owner's:** if the two are deliberate — the card
meaning "start now", the chip meaning "start with options" — then this is a
**labelling** task and shrinks to changing two strings. If they are not, one
goes. **Ask, then do.** This spec assumes duplication because that is what the
screen looks like; do not assume it if the owner says otherwise.

**Files to touch:**
- `ui/screens/PautaScreen.kt` — the empty-state branch
- `i18n/I18n.kt` (only if the labelling reading wins)

**How (if duplication):**

The hero card stays; it is the app's own idiom and it reads as the primary
action. The starter chip goes from the *empty* state only — it remains useful
below a list of today's blocks, where the hero card has scrolled away.

While there: in landscape the hero card's play circle is under Pip. That is
`FIELD_FIXES.md` F11's rule and this task should not fix it locally; if F11 has
not shipped, note the collision in the Log rather than papering over it here.

**Out of scope:** the floating layer rule (F11); the contents of the start
sheet; the quick-start chips built from today's intentions.

**Never:** delete the hero card. It is the identity of that tab.

**Accept:** the empty Pauta tab offers exactly one way to start a block, or two
that are labelled as genuinely different things; the starter chip still exists
where a list is present; `bookMode` on shows the Sessão tab unchanged; textScale
1.0 and 1.5 both read correctly; CI green.

---

## N8 · The book form asks nine questions — Status: pending

**Depends on:** nothing. **Worth pulling ahead of `BOOK_LIBRARY.md` L3 and L7**,
both of which make this sheet longer — L3 adds statuses, L7 decides genre's
fate.

**Why:** `BookFormSheet` opens with título, autor, série, nº na série, formato,
ficheiro, total de páginas, género and estado. Only the title is required.
`HabitFormSheet.kt:166`, two taps away in the other lens, asks for a name and a
`quando` and hides everything else behind `+ mais opções` — and it is the same
app, the same week's work, the better pattern.

Adding a book is the most common action in book mode and it currently looks like
a cataloguing form.

**Files to touch:**
- `ui/screens/BookFormSheet.kt` — the collapse, and the format inference
- `i18n/I18n.kt`

**How:**

Above the fold: **título**, **autor**, **anexar ficheiro**. Everything else
behind `+ mais opções (série, formato, páginas, género, estado)`, expanded by
default when *editing* an existing book — someone editing has come for one of
those fields.

**The file answers what it can.** `DocumentParse` already returns the format and
the OPF metadata:

- attaching a `.epub` or `.pdf` sets `format = "ebook"`, replacing the `physical`
  default. Today it stays `Físico` after a successful parse, which is the app
  contradicting itself.
- if the title field is still empty, offer the OPF's `dc:title` — **offer**, as
  a prefill the user can overwrite, never a silent overwrite of typed text.

**Out of scope:** the five statuses (L3 owns the pills and their transitions);
whether `genre` survives (L7); reading the author from the OPF if L7 removes the
field it would sit beside.

**Never:** overwrite something the user typed with something the file said.
**Never** make a field required that is optional today — the point is fewer
questions, not stricter ones.

**Accept:** adding a book needs a title and nothing else, with two fields and one
attach action visible; every field still reachable behind `+ mais opções`;
editing opens expanded; attaching an EPUB sets the format to Ebook; an empty
title is offered the file's own; a typed title is never replaced; a book added
before this task still edits correctly; textScale 1.5 does not clip the sheet;
CI green.

---

## Leftovers — too small to be tasks

Fold each into the next PR that touches the same file, or do them together in
one sweep at the end. **Strike anything not done, with a reason, in the Log.**

- **Hoje prints `0/2 intenções` twice** on one screen — the progress card and
  the *Reflexão da noite* card. One of them is enough. `ui/screens/HojeScreen.kt`.
- **The document picker opens on "Recent", which is empty** on a clean device.
  Pass `EXTRA_INITIAL_URI` pointing at Downloads.
  `ui/screens/BookFormSheet.kt:101`, `ui/screens/ReaderScreen.kt:610`.
- **The Estante header leaves an orphan `·`** at textScale 1.5, wrapping
  `✎ Nota + ·` onto one line and `Adicionar livro +` onto the next. Same family
  as `FIELD_FIXES.md` F8 — fix it there if F8 lands first, here if not.

## Amendments to other files

Made in the PR that created this file, recorded here so the change is traceable:

- **`FIELD_FIXES.md` F11** — Accept now names landscape. The rule "nothing that
  carries information sits under a floating thing" was written with a portrait
  scroll in mind; in landscape Pip covers the Pauta tab's primary button, which
  the old wording would have let pass.
- **`FIELD_FIXES.md` F8** — scope now names the Estante header, not only Hoje's.
  Same cause, same fix, one PR.

---

## Order

N1 is first and ships alone. N8 may be pulled ahead of `BOOK_LIBRARY.md` L3 —
that is the owner's call and the reason is in N8. The rest are independent of
each other and run after `FIELD_FIXES.md`.

```
N1  (alone, ahead of everything)
 │
 ├─ N8  (optionally here, before L3)
 │
 └─ after FIELD_FIXES:  N2 · N3 · N4 · N5 · N6 · N7
                        (independent; N5 prefers L3 shipped)
```

**Blocked on a decision:** N7. Ask before starting it.

---

## Log (append one line per shipped task: date · task · PR · note · Verified:)

Every entry ends with **Verified:** — what was actually exercised, and where.
This file exists because a green gate and a working app are different things; an
entry that cannot say what was verified should say that instead.

<!-- YYYY-MM-DD · N1 · #n · <what shipped, what was rejected and why> · Verified: <what ran, where> -->
