# Tide goals — task file

> **Concept.** A countable maré has one number today, and it does two jobs at
> once: it is the ceiling *and* it is what marks the day done. So a person who
> drinks 3 litres of water but *aims* for 5 sets the target to 5 and is then told,
> every single day, that they are at 60% — the cell half-filled, the streak
> broken, the ambition punished. This file splits that number in two: a
> **mínimo** that closes the tide, and a **meta** above it that is optional by
> construction. Doing the minimum is a complete day. Everything above it is
> surplus, and the app says so out loud — one global figure, *"estás a fazer
> +42% além do mínimo"* — in a place you can actually reach, which today it is
> not.
>
> Written from a conversation with the owner on 2026-08-19, not from a code
> review or a device run. Where it guesses, it says so.
>
> Ships as 3 self-contained tasks (M1…M3). Each task is one PR.

## How to use

> Faz o próximo em `docs/TIDE_GOALS.md`.

Do the **first task whose Status is `pending`**, top to bottom. Do **only** that
one. Ship it through the `CLAUDE.md` workflow, and update the task's Status, this
file's Log **and `docs/CONTEXT.md`** in the same PR. If a task is blocked on a
decision, **stop and ask** — do not skip ahead to the next one.

Open every reply with the progress bullet, before any tool call:

```
**Done:** M1 ✓
**Now:** M2 — the surplus, said out loud
**Left:** M3 (1)
```

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Guardrails

**`docs/GUARDRAILS.md` applies in full.** It is binding and it is not restated
here. The ones that bite hardest in this file: **no gamification** (§A — M2 is
the whole reason this needs saying; see below), **no new dependencies** (§D, §K.3
— the chart glyph is drawn by hand like the other five), **no Settings row is
ever deleted** (§J — M3 wanted to move two and may not), **no fourth tab** (§J —
M3's door is an icon, not a tab), and **pt-PT is the source language** (§F).

**Extra, specific to this file:**

1. **A number that can read as failure is not allowed to.** The entire file
   exists because `3/5` reads as *missing two*. Nothing shipped here may
   reintroduce that: no fraction whose denominator is the meta, no percentage
   below 100 on a day the minimum was met, no "you only did…" anywhere.
2. **M2 is a measurement, not a score.** §A forbids gamification, and a surplus
   figure is one hop away from one. It is a plain sentence of fact in the review
   sheet — no badge, no streak of surplus days, no celebration animation, no
   comparison to last week framed as a drop. If it starts to feel like a game,
   it has gone wrong.
3. **Existing tides change nothing.** Every countable tide today keeps behaving
   exactly as it does — `target` *is* the mínimo, the meta is new and optional.
   A person who never opens the field must never see a difference.

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Shared context

### Where the numbers live today

- `HabitEntity.target: Int?` — non-null makes a tide **countable**; `null` is
  binary. `isCount` is `target != null && cadence == "daily"`, and that
  definition is repeated in `ui/TideHelpers.kt` and `MaresScreen.kt` — both must
  keep agreeing.
- `PautaRepository.setHabitCount` is the **single door** every count goes
  through (both screens, the notification action, the widget, and since S4 a
  concluded focus block). It writes `habit_counts` and syncs `habit_logs`:
  `count >= target` adds the log, below it removes the log. **The log is what
  "done" means** — the streak, the grid cell and the completion % all read it.
- `HabitCalculator.cycleCount(requested, target)` is F4's ceiling: a tap climbs
  to the target and the tap after that clears to zero. It exists because counts
  had no ceiling and a mis-tap was permanent (the owner's phone held `39/2`).
  `shownCount(stored, target)` reads a stored value above target as "at target".
- `InsightsMath` already produces `WeekReview`, `MonthReview`, `YearReview` and
  `Narrative`; `InsightsSheet` renders the first, `YearReviewScreen` the last.

### Data model delta — **Room 15 → 16 is claimed by M1**

| Table | Column | Type | Default | Meaning |
|---|---|---|---|---|
| `habits` | `stretch` | Int? | `null` | the **meta**: what the person is aiming for, above the mínimo. `null` = no meta, which is every tide that exists today |

`target` keeps its name and its meaning becomes explicit: **it is the mínimo**,
the number that closes the day. `stretch` is native-only and is **not** in the
`pauta.v4` export (§K.4 — the format is frozen); `DATA_MODEL.md` gets the column
and the row. A `stretch` below or equal to `target` is meaningless and is stored
as `null` rather than defended against downstream.

### The vocabulary, fixed here so three surfaces don't invent three

| Concept | pt-PT | Field | What it does |
|---|---|---|---|
| the floor that closes the tide | **mínimo** | `target` | crossing it writes the log: done, streak, 100% |
| what you're aiming for | **meta** | `stretch` | optional; the ceiling the tap climbs to; never affects "done" |
| what you did above the floor | **extra** | — | derived; shown as `+n`, never as a fraction |

## Decisions already taken — do not re-open these

- **`target` stays the mínimo; the meta is the new field.** The alternative —
  `target` becomes the meta and a new `minimum` column appears below it — would
  silently redefine every existing tide, turning days that were partial into
  days that are done and rewriting streaks retroactively. Decided 2026-08-19
  with the owner: existing data does not move.
- **The tap ceiling moves from the mínimo to the meta, and F4's rule survives
  unchanged.** This was two open questions ("can you exceed the meta?" and "what
  replaces tap-to-clear once there is no ceiling?") and they answer each other:
  the ceiling becomes `stretch ?: target`, so the tap climbs to the meta and the
  tap after that clears to zero — exactly F4's gesture, one number further out.
  Unbounded counting was considered and lost: it leaves no ceiling for the
  correction gesture to hang on, and F4 exists because a count that cannot be
  corrected is a count that is permanently wrong. **The cost is real and is
  accepted:** you cannot log 6 when your meta is 5 — you raise the meta. If the
  owner reverses this, it is a one-line change in M1 plus a new correction
  gesture, which is a task of its own.
- **A day below the mínimo never subtracts from the surplus figure** (M2). Each
  day's ratio is floored at 1.0. A missed day is the streak's business; if it
  also ate your surplus, the statistic would become another way to feel behind,
  which is the exact thing this file removes.
- **This is not *Metas de leitura*** (`GUARDRAILS.md` §J). That decision refused
  *new* self-set targets on an empty book shelf as a form of nagging. Tides
  already have targets; this makes the existing one *less* punishing rather than
  adding a goal to a surface that had none. Different question, opposite
  direction.
- **The two Análise rows stay in Settings** (M3). Moving them out was proposed
  and refused by §J's "no Settings row is ever deleted" — Settings is also a
  searchable directory (those rows carry `keywords`), and the new door is about
  discoverability, not about taking a path away.

---

## M1 · The mínimo closes the tide, the meta is optional — Status: pending

**Depends on:** nothing.

**Why:** today, aiming higher costs you. A tide with `target = 5` shows `3/5`
after a genuinely good day, renders a half-filled PARTIAL cell, writes no log,
and breaks the streak — so the rational move is to set the target as low as
possible, which is the opposite of what a goal is for. Splitting the number lets
the tide close at 3 and record the 2 above it as something you *gained*.

**Files to touch:**
- `data/entity/Entities.kt` — `HabitEntity.stretch: Int? = null`, `// native-only`
- `data/AppDatabase.kt` — version 16, `MIGRATION_15_16`, one
  `ALTER TABLE habits ADD COLUMN stretch INTEGER`, registered
- `domain/HabitCalculator.kt` — the ceiling becomes the meta; `shownCount` and
  `cycleCount` take it
- `data/PautaRepository.kt` — `setHabitCount` reads the new ceiling; `addHabit` /
  `updateHabit` carry `stretch`
- `ui/TideHelpers.kt` — `TideToday` gains the meta so every surface agrees
- `ui/screens/MaresScreen.kt` — the row's count pill, and the form field
- `ui/screens/MaresSheets.kt` (or wherever the habit form lives) — the `meta`
  input beside `mínimo`

The ceiling, in one place, keeping F4's rule intact:

```kotlin
/** The number a tap climbs to: the meta when there is one, else the mínimo.
 *  F4's rule is unchanged — one tap past the ceiling clears the day. */
fun countCeiling(target: Int?, stretch: Int?): Int?
```

`cycleCount` and `shownCount` then take that ceiling rather than `target`
directly, and `setHabitCount` keeps writing the log at **`count >= target`** —
the mínimo, untouched. That single line is what makes the day close early.

**What the row shows.** Below the mínimo it is the fraction it always was
(`2/3`). At or above it, the denominator stops moving and the extra is a
separate, quieter token: `3/3 +2`. **Never `3/5`** — a fraction whose
denominator is the meta is the defect this task exists to remove. The meta
appears in the form and, if it earns the space, as a faint mark on the row; it
is never the thing you are measured against.

**S4's block link:** a concluded focus block adds one through the same door, so
it inherits all of this for free — including that it stops at the ceiling.
`HabitCalculator.feedFromBlock` reads "already done" from the log, so a block
concluded after the mínimo is reached currently adds nothing. **Decide and say
so in the Log:** either it keeps adding into the surplus (consistent with "more
is never punished") or it stops at done (consistent with "the tide is closed").
Recommendation: keep adding, up to the ceiling.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `mínimo` | `minimum` |
| `meta (opcional)` | `goal (optional)` |
| `+{n}` | `+{n}` |
| `A meta tem de ser maior que o mínimo.` | `The goal must be higher than the minimum.` |

**Out of scope:** the surplus statistic (M2) and where it is read (M3). This task
stores and displays; it does not summarise.

**Never:** do not add `stretch` to the `pauta.v4` export (§K.4). Do not change
what `target` means for existing rows. Do not make the meta required — an empty
meta is the normal case and the field must accept being left alone. Do not move
the "done" threshold to the meta "for consistency": that is the bug.

**Accept:** a tide with mínimo 3 and meta 5 marks done at 3 — grid cell full,
streak counts it, completion 100% — and reads `3/3 +2` at five; a tide with no
meta behaves exactly as it does today, verified against an install that predates
this change; the tap climbs to the meta and the next tap clears to zero; a meta
below the mínimo cannot be saved and says why; `pauta.v4` export and re-import is
byte-identical for a database containing a tide with a meta; `DATA_MODEL.md`
gains the column and v16; repo-root `README.md` describes the mínimo/meta split
in the tides section; CI green.

---

## M2 · The surplus, said out loud — Status: pending

**Depends on:** M1.

**Why:** M1 stops the extra being punished; it does not make it *visible*. The
owner asked for one global figure — "you're x% doing extra work" — because the
whole point of doing more than the floor is lost if the app never mentions it.

**Files to touch:**
- `domain/InsightsMath.kt` — the figure, pure and tested
- `ui/screens/InsightsSheet.kt` — where it is read (weekly / monthly review)
- `ui/screens/YearReviewScreen.kt` — the same figure over the year

```kotlin
data class Surplus(
    val ratio: Float,      // 1.42 → "+42%"
    val daysAbove: Int,    // days the mínimo was passed
    val daysDue: Int,      // days a countable tide was due at all
)

/** Averaged per day per tide, each day's ratio floored at 1.0. */
fun surplus(habits: List<NamedHabit>, counts: …, from: String, to: String): Surplus?
```

**Magnitude is the headline, frequency is the second line.** *"Estás a fazer
+42% além do mínimo"*, then *"passaste do mínimo em 22 de 30 dias"*. Magnitude
alone can be one heroic day; frequency alone cannot tell a 3.1 from a 6.

**Average the per-day ratios; do not divide two sums.** Water measured in litres
would otherwise drown out exercise measured in sets — a ratio is dimensionless,
a sum of raw counts is not. Days below the mínimo enter the average as `1.0`,
never below (see Decisions). A period with no countable tide due returns `null`
and the sheet shows nothing rather than `+0%`.

**Exactly at the minimum is not a zero.** A period whose ratio is 1.0 says
*"cumpriste todos os mínimos"* instead of `+0%` — the same fact, stated as the
completed thing it is rather than as an absence. `+0%` is the kind of number
that reads as a shortfall to the one person who most needs it not to.

**The window follows the sheet.** Weekly review → that week; monthly → that
month; year review → the year. No fixed 30-day window invented alongside three
that already exist.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Estás a fazer +{p}% além do mínimo.` | `You're doing +{p}% beyond your minimum.` |
| `Passaste do mínimo em {d} de {t} dias.` | `You passed your minimum on {d} of {t} days.` |
| `Cumpriste todos os mínimos.` | `You met every minimum.` |

**Out of scope:** per-tide surplus lines, a chart of surplus over time, and any
per-day celebration at the moment you cross the floor. One global figure, in the
reviews. If it proves worth more, that is a later task with evidence behind it.

**Never:** do not let a missed day pull the figure below 100%. Do not render it
as a badge, a medal, a "personal best", or a streak of surplus days (§A). Do not
show it when the period has no countable tide — an invented `+0%` is a number
presented as measured (§K.11).

**Accept:** a month averaging 5 of 3 reads `+67%`; a month with two missed days
and otherwise 5 of 3 reads the *same* `+67%` as one without them; a month in
which every tide met its mínimo and no more reads *"cumpriste todos os
mínimos"* rather than `+0%`; the figure is absent, not zero, when no countable
tide was due in the period; unit tests cover the floor, the per-day averaging,
the exactly-at-minimum case and the empty period; repo-root `README.md` mentions
the figure where it describes tides; CI green.

---

## M3 · A door to the numbers — Status: pending

**Depends on:** nothing (M2 fills the room; M3 builds the door). Doing M3 first
is legal and leaves an emptier room.

**Why:** the weekly review is reachable from a `revisão ↗` chip on Hoje — one of
four chips — and from **Settings → Análise**, which is where nobody looks for
their own numbers, because reviewing your week is not a setting. The owner asked
for a chart icon beside the gear, and that is right: the status row is global, so
it is one tap from any tab.

**Files to touch:**
- `ui/PautaIcons.kt` — a sixth hand-drawn `ImageVector`, `Chart`
- `ui/MainScaffold.kt` — `StatusRow` gains the icon, left of the gear
- `ui/screens/HojeScreen.kt` — the `revisão ↗` chip goes
- (the destination) — the existing `InsightsSheet`, opened from the new icon

**The icon is hidden in book mode** (owner, 2026-08-19): that lens already has
reading statistics as its whole third tab, and two competing stats destinations
in one lens is worse than one fewer door. `StatusRow` already knows the lens —
it carries the gear's long-press lens toggle.

**The Hoje chip is dropped, not duplicated** (owner, 2026-08-19). §J closed the
same question once already for the Pauta tab's two start affordances; two doors
to one room is the pattern this app keeps deciding against.

**The Settings rows stay.** See Decisions — §J forbids deleting a Settings row,
and a searchable directory entry is not the same thing as prime real estate.

**Never:** do not make this a fourth tab (§J). Do not let the status row become a
toolbar — this is the one addition, and the next icon needs a better argument
than this one had. Do not delete the Settings rows.

**Accept:** the chart icon sits beside the gear on every tab in planner mode and
is absent in book mode; tapping it opens the weekly review; the `revisão ↗` chip
is gone from Hoje and the other three chips still work; Settings → Análise still
opens the same review; the icon has a `contentDescription` and is reachable by
TalkBack (§E); at 1.5× text scale the status row does not clip and the two icons
keep their touch targets; repo-root `README.md` says where the numbers live;
CI green.

---

## Order

```
M1  ──────────────  the split (schema, ceiling, display)
 │
M2  ──────────────  the surplus figure — needs M1's mínimo to be a mínimo
 
M3  ──────────────  independent; the door can be built before or after either
```

M1 → M2 is a real dependency: the surplus figure is meaningless while `target`
is still both floor and ceiling. **M3 depends on nothing** and can be taken first
if a session wants a small, visible win; it will simply open onto a review that
does not yet carry the new figure.

**Nothing in this file is blocked on a decision.** The two questions that were
open on 2026-08-19 — whether the count may exceed the meta, and what replaces
F4's tap-to-clear — are answered together in Decisions, and the answer is cheap
to reverse if the owner wants unbounded counting later.

---

## Leftovers — too small to be tasks

- **`isCount` is defined twice** — `ui/TideHelpers.kt` and `MaresScreen.kt` both
  spell out `target != null && cadence == "daily"`. M1 touches both; fold them
  into one helper while there.

## Log (append one line per shipped task: date · task · PR · note · Verified:)

Every entry ends with **Verified:** — what was actually exercised, and on what.
`SHAKEDOWN.md` exists because thirty tasks were compiled, merged and
contradicted by ten minutes of real use; an entry that cannot say what was
verified should say that instead.

<!-- e.g. 2026-08-20 · M1 · #n · … · Verified: Pixel 7 AVD, Android 15 -->
