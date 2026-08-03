# Task file format — the shape every `docs/*.md` follows

> **What this is.** Eight task files have now driven this app from a web port to
> a reader, one PR at a time, mostly by sessions that had never seen the
> codebase before. The format they converged on is *why* that worked. This file
> writes it down so the next one doesn't have to rediscover it.
>
> **Read this before creating a new task file.** Follow it for anything new. The
> five files in [`archive/`](archive/README.md) predate it and are **not**
> retrofitted — they are already close, and rewriting shipped history to match a
> template destroys the one thing that makes them valuable.

---

## The one idea

A task file is written for **a session that knows nothing**. It opens cold,
reads four files, and must be able to ship a correct PR without asking anything.
Everything below serves that: it is not bureaucracy, it is the context a
stranger needs.

Three consequences worth stating, because they are what people get wrong:

1. **Record the *why*, not the *what*.** The diff already says what changed. The
   file must say why it changed that way and what was rejected — otherwise the
   next session re-derives it, or worse, undoes it.
2. **Write decisions down as closed.** A decision that isn't recorded gets
   re-proposed every few sessions. Say "settled, don't re-open", with the reason
   and the date.
3. **Be honest about what wasn't verified.** A green CI and a working app are
   different things. Say which one you have.

## What a task file no longer has to carry

Four things used to be restated in every file, and three of them have moved.
**Do not copy them into a new file** — inherit them by reference and say only
what is specific to your round.

| Was per-file | Now lives in | Your file says |
|---|---|---|
| Global guardrails | [`GUARDRAILS.md`](GUARDRAILS.md) | "applies in full", then the 3–5 that bite hardest here |
| Data model / migrations | [`DATA_MODEL.md`](DATA_MODEL.md) | only the columns *your* tasks add |
| Security model | [`GUARDRAILS.md`](GUARDRAILS.md) §G | nothing — it is binding without restating |
| State of the work | [`CONTEXT.md`](CONTEXT.md) | nothing — you update it, you don't duplicate it |

A file that re-explains the guardrails will drift from them, and then two
documents disagree about what is allowed. One copy, referenced.

---

## Required sections, in order

### 1 · Title and Concept

```markdown
# <Name> — task file

> **Concept.** What this file is for, in three or four sentences: the problem,
> the shape of the answer, and what changes for the person using the app.
>
> Ships as N self-contained tasks (X1…Xn). Each task is one PR.
```

Say what the *user* gets, not what the code gets. If the file exists because
something is broken, say what broke and **how it was found** — a file written
from a code review and a file written from a bus journey are different documents
and the reader should know which one they have.

### 2 · How to use

Both audiences, always — the human's prompt and the binding rules for Claude.
The prompt carries **no task number** if the file is meant to be worked through
in order:

```markdown
> Do the next one in `docs/<FILE>.md`.
```

State explicitly: do the **first task whose Status is `pending`**, top to bottom;
only that one; ship via the `CLAUDE.md` workflow; update Status, the Log **and
`CONTEXT.md`** in the same PR; and stop rather than skip if a task is blocked on
a decision.

**Specify the progress bullet.** A file worked through in order should open every
reply with it, before any tool call, so the owner knows where the work is without
opening the file:

```
**Done:** F1 ✓ · F2 ✓
**Now:** F3 — the keyboard that swallows what you wrote
**Left:** F4…F13 (10)
```

A format described in prose comes back different every time. Show it.

### 3 · Guardrails

Two paragraphs, no more:

```markdown
**`docs/GUARDRAILS.md` applies in full.** It is binding and it is not restated
here. The ones that bite hardest in this file: …

**Extra, specific to this file:** …
```

The "extra" list is for constraints this round invents — a rule about where new
controls may live, a promise that existing installs see no change. If you find
yourself writing a rule that should apply to *everything*, put it in
`GUARDRAILS.md` and add a line to that file's Log.

### 4 · Status legend

```markdown
`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`
```

### 5 · Shared context — the section that saves a session

Whatever the tasks *share* goes here once, not repeated per task:

- **The evidence**, when the file was written from observed defects rather than
  from a plan: what was seen, and the `file:line` each symptom traces to. Both
  `FIELD_FIXES.md` and `FIRST_RUN.md` do this, and it is the section that makes
  a defect file believable six weeks later. Include the build, the device and
  the date — and say what *held up*, not only what broke.
- **A data model delta**, when tasks add columns: only what is new, with a
  pointer to `DATA_MODEL.md` for the rest. **Claim your Room version number
  here, in writing, before you write the code** — two files in flight once
  collided on v9.
- **A suggested model per task**, if the file is long enough that it matters.

### 6 · Decisions already taken

A short list of things that are **closed**. Each one: what was decided, why, and
when. This is the cheapest section to write and the one that saves the most
time.

> *"No cover art. This is settled — don't re-propose it. Extracting covers is
> technically easy, which is exactly why this needs saying out loud."*

When a decision outlives your file — when it is about the app rather than about
this round — promote it to `GUARDRAILS.md` §J instead, and reference it.

### 7 · The tasks

Each task, in execution order, with this shape:

```markdown
### X3 · Short imperative title — Status: pending

**Depends on:** X1 (or "nothing")

**Why:** the reason this is worth a PR, in the user's terms.

**Files to touch:**
- `path/One.kt` — what changes there
- `path/Two.kt` (new) — what it is

<the spec: prose and code sketches, enough to build from and no more>

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `…` | `…` |

**Out of scope:** what belongs to a later task, named.

**Never:** what must not be done here, and would be tempting.

**Accept:** the observable outcomes, semicolon-separated; the README line if
anything visible changed; ending in "CI green".
```

Rules that matter:

- **One task is one PR.** If a task can't be, split it — except where splitting
  would leave the app in a half-corrected state, and then say so in the Why.
- **`Out of scope` and `Never` are different things, and both earn their space.**
  *Out of scope* is "this belongs to a later task" — it stops scope creep
  without a conversation. **`Never` is "this is a trap"** — the shortcut a
  reasonable session would take, and the reason it is wrong. Write a `Never`
  whenever you can name the wrong turn: overwriting what the user typed,
  deleting the thing rather than fixing it, making an optional field required,
  re-enabling something a guardrail forbids. A task with no plausible wrong turn
  can omit it; most tasks have one.
- **A task that changes what a user can see updates the repo-root `README.md` in
  the same PR** — the tab table, a feature section, the install steps, whatever
  it touched. Put it in that task's `Accept`. This is not paperwork: the READMEs
  went ~40 tasks without mentioning that the app had become a reader, and a
  README that describes half the app is worse than none, because it is believed.
  If a task genuinely changes nothing visible, say so in the `Accept` rather
  than leaving it ambiguous.
- **A new task *file* is added to `docs/README.md`** in the PR that creates it,
  and moved to `docs/archive/` in the PR that finishes its last task — with a
  row in `archive/README.md` and any still-binding section lifted out first.
- **Name the files.** A session that has to go looking will go looking somewhere
  else.
- **`Accept` is observable.** "Works well" is not acceptance; "concluding a
  session by hand asks for a percentage and typing 100 means finished" is. If
  the defect was found at a text scale or an orientation, **name that scale and
  that orientation in the Accept** — otherwise the fix is verified in the one
  configuration where the bug never appeared.
- **Sketch code, don't write it.** Signatures and shapes, not implementations —
  the session will write better code than the spec author guessed at.

### 8 · Leftovers, and amendments to other files

Two optional sections that stop small things from being lost:

- **Leftovers** — findings too small to be a task. Say where each one lives and
  that they may be folded into the next PR touching that file. **Anything not
  done is struck from the list with a reason in the Log**, so the list cannot
  quietly become a graveyard.
- **Amendments to other files** — if creating this file changed a task
  elsewhere (a scope line, an Accept clause), record what and why here. The
  other file gets the edit; this one keeps the reason.

### 9 · Order (and dependencies)

If the order is strict, say so and show it:

```
X1 → X2 → X3 → …
```

If tasks are independent, an ASCII dependency graph earns its space — it is how
a session knows what is safe to do next. Name anything **blocked on a decision**
here as well as in the task, so it is visible without reading to the bottom.

### 10 · Log

One entry per shipped task, appended, newest first. **This is the most valuable
section in the file** and the one most likely to be written lazily.

```markdown
YYYY-MM-DD · X3 · #PR · <a dense paragraph: what shipped, and the reasoning
behind the decisions that weren't obvious — including the ones that were
rejected and why> · Verified: <what was actually exercised, and where>
```

What makes a good entry:

- **It explains a choice, not a change.** *"the peek guard is `durationMs` plus
  'did the page change', so it needs no memory of who started the session"* —
  that sentence saves an hour six weeks later.
- **It records what was rejected.** The second-best option and why it lost.
- **It says what was verified.** JVM tests, CI, or a device — and if a device
  never saw it, say that. The `Verified:` clause exists because the gap
  between a green gate and a working app is where every entry in
  `FIELD_FIXES.md` and `FIRST_RUN.md` came from.
- **It is one paragraph.** Not bullets. The prose forces the reasoning to
  connect.

---

## Naming

- One file per **coherent body of work**, named for what it is:
  `BOOK_READER.md`, `POLISH.md`, `FIELD_FIXES.md`.
- Task IDs are a letter plus a number (`R4`, `U2`, `F11`, `N1`) — the letter
  matches the file so a bare ID is unambiguous across files. **Check the letter
  is free**, including in `archive/`.
- Never renumber a shipped task. Append; if the order must change, say so in the
  Order section rather than rewriting IDs that PRs and Log lines already
  reference.

## Files that are not task files

Four documents in `docs/` are not task files and do not take this shape. They
have their own rules, and each carries a Log for the same reason task files do:

| File | Shape | Who updates it |
|---|---|---|
| `GUARDRAILS.md` | rules, by section, plus a never-do list | any PR that adds or retires a rule |
| `CONTEXT.md` | state of the work, plus what has been run | **every PR that ships a task** |
| `DATA_MODEL.md` | tables and migration history | any PR that adds a column |
| `README.md` | the index | any PR that adds or archives a file |

---

## The skeleton

Copy this.

```markdown
# <Name> — task file

> **Concept.** …
>
> Ships as N self-contained tasks (X1…Xn). Each task is one PR.

## How to use

> Do the next one in `docs/<FILE>.md`.

<binding rules for Claude: first pending task, only that one, the progress
bullet, ship via CLAUDE.md's workflow, Status + Log + CONTEXT.md in the same PR,
stop if blocked>

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Guardrails

**`docs/GUARDRAILS.md` applies in full.** The ones that bite hardest here: …

**Extra, specific to this file:** …

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## <Shared context: the evidence / the data model delta>

## Decisions already taken — do not re-open these

---

## X1 · … — Status: pending

**Depends on:** nothing

**Why:**

**Files to touch:**

**Out of scope:**

**Never:**

**Accept:** … ; README updated (or: nothing user-visible changed) ; CI green.

---

## Leftovers — too small to be tasks

## Amendments to other files

---

## Order

X1 → X2 → …

---

## Log (append one line per shipped task · … · Verified: …)
```

---

## Log (append when this format changes)

<!-- YYYY-MM-DD · #PR · <what changed in the format, and what it replaced> -->
2026-08-03 · — · guardrails, data model and the security model moved out to `GUARDRAILS.md` / `DATA_MODEL.md`, so a new file inherits rather than restates them; added the **`Never:`** field per task (distinct from `Out of scope`), the progress-bullet requirement, the `CONTEXT.md` update rule, the Leftovers and Amendments sections, the "name the text scale and orientation in Accept" rule, and the note that Room version numbers are claimed in writing before code.
