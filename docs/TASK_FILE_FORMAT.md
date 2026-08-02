# Task file format — the shape every `docs/*.md` follows

> **What this is.** Six task files have now driven this app from a web port to a
> reader, one PR at a time, mostly by sessions that had never seen the codebase
> before. The format they converged on is *why* that worked. This file writes it
> down so the next one doesn't have to rediscover it.
>
> **Read this before creating a new task file.** Follow it for anything new; the
> files that predate it (`NATIVE_IMPROVEMENTS`, `BOOK_MODE`, `POLISH`,
> `BOOK_READER`, `UX_FIXES`) are not retrofitted — they are already close, and
> rewriting shipped history to match a template would destroy the one thing that
> makes them valuable.

---

## The one idea

A task file is written for **a session that knows nothing**. It opens cold, reads
the file plus `CLAUDE.md`, and must be able to ship a correct PR without asking
anything. Everything in the format below serves that: it is not bureaucracy, it
is the context a stranger needs.

Three consequences worth stating, because they are what people get wrong:

1. **Record the *why*, not the *what*.** The diff already says what changed. The
   file must say why it changed that way and what was rejected — otherwise the
   next session re-derives it, or worse, undoes it.
2. **Write decisions down as closed.** A decision that isn't recorded gets
   re-proposed every few sessions. Say "settled, don't re-open", with the reason
   and the date.
3. **Be honest about what wasn't verified.** A green CI and a working app are
   different things. Say which one you have.

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
something is broken, say what broke and how it was found.

### 2 · How to use

Both audiences, always — the human's prompt and the binding rules for Claude.
The prompt should carry **no task number** if the file is meant to be worked
through in order:

```markdown
> Faz o próximo em `docs/<FILE>.md`.
```

State explicitly: do the **first task whose Status is `pending`**, top to bottom;
only that one; ship via the CLAUDE.md workflow; update Status + Log in the same
PR; and stop rather than skip if a task is blocked on a decision.

If the file wants a progress report, specify its exact shape here — a format
described in prose comes back different every time.

### 3 · Global guardrails

The constraints that apply to *every* task, so no task has to restate them.
Inherit explicitly from the other files ("all guardrails from X apply
unchanged") and then list only what bites hardest here. Include the
non-negotiables: no new dependencies, both lenses survive, prefs are law, the
`pauta.v4` export stays lossless.

### 4 · Status legend

```markdown
`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`
```

### 5 · Shared context — the section that saves a session

Whatever the tasks *share* goes here once, not repeated per task:

- a **data model** section, when tasks add columns or migrations
  (`BOOK_READER.md` does this well: one table of new columns, one migration, one
  place to look)
- a **security model**, when the feature takes untrusted input, marked as binding
  on the tasks that own it
- **the evidence**, when the file was written from observed defects rather than
  from a plan: what was seen, and the file:line each symptom traces to
  (`FIELD_FIXES.md`)

### 6 · Decisions already taken

A short list of things that are **closed**. Each one: what was decided, why, and
when. This is the cheapest section to write and the one that saves the most
time.

> *"No cover art. This is settled — don't re-propose it. Extracting covers is
> technically easy, which is exactly why this needs saying out loud."*

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

**Accept:** the observable outcomes, semicolon-separated, ending in "CI green".
```

Rules that matter:

- **One task is one PR.** If a task can't be, split it — except where splitting
  would leave the app in a half-corrected state, and then say so in the Why.
- **Name the files.** A session that has to go looking will go looking somewhere
  else.
- **`Out of scope` is as important as the spec.** It is what stops scope creep
  without a conversation.
- **`Accept` is observable.** "Works well" is not acceptance; "concluding a
  session by hand asks for a percentage and typing 100 means finished" is.
- **Sketch code, don't write it.** Signatures and shapes, not implementations —
  the session will write better code than the spec author guessed at.

### 8 · Order (and dependencies)

If the order is strict, say so and show it:

```
X1 → X2 → X3 → …
```

If tasks are independent, an ASCII dependency graph earns its space — it is how
a session knows what is safe to do next.

### 9 · Log

One entry per shipped task, appended, newest first. **This is the most valuable
section in the file** and the one most likely to be written lazily.

```markdown
YYYY-MM-DD · X3 · #PR · <a dense paragraph: what shipped, and the reasoning
behind the decisions that weren't obvious — including the ones that were
rejected and why> · Verificado: <what was actually exercised, and where>
```

What makes a good entry:

- **It explains a choice, not a change.** *"the peek guard is `durationMs` plus
  'did the page change', so it needs no memory of who started the session"* —
  that sentence saves an hour six weeks later.
- **It records what was rejected.** The second-best option and why it lost.
- **It says what was verified.** JVM tests, CI, or a device — and if a device
  never saw it, say that. The `Verificado:` clause exists because the gap
  between a green gate and a working app is where every entry in
  `FIELD_FIXES.md` came from.
- **It is one paragraph.** Not bullets. The prose forces the reasoning to
  connect.

---

## Naming

- One file per **coherent body of work**, named for what it is:
  `BOOK_READER.md`, `POLISH.md`, `FIELD_FIXES.md`.
- Task IDs are a letter plus a number (`R4`, `U2`, `F11`) — the letter matches
  the file so a bare ID is unambiguous across files.
- Never renumber a shipped task. Append; if the order must change, say so in the
  Order section rather than rewriting IDs that PRs and Log lines already
  reference.

---

## The skeleton

Copy this.

```markdown
# <Name> — task file

> **Concept.** …
>
> Ships as N self-contained tasks (X1…Xn). Each task is one PR.

## How to use

> Faz o próximo em `docs/<FILE>.md`.

<binding rules for Claude: first pending task, only that one, ship via
CLAUDE.md's workflow, Status + Log in the same PR, stop if blocked>

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Global guardrails (every task)

- …

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## <Shared context: data model / security model / evidence>

## Decisions already taken — do not re-open these

---

## X1 · … — Status: pending

**Depends on:** nothing

**Why:**

**Files to touch:**

**Out of scope:**

**Accept:**

---

## Order

X1 → X2 → …

---

## Log (append one line per shipped task)
```
