# `docs/` — how work happens here

Every change to this app ships through one task file: a spec, one PR, and a Log
line explaining why it was built that way. Nothing here documents the code — the
code documents itself, bilingually. These are **plans and their reasoning**.

---

## Read these first, in this order

Four files, and together they are a cold session's complete briefing. You should
not need to open anything else unless a task names it.

| # | File | What it is |
|---|---|---|
| 1 | [`../CLAUDE.md`](../CLAUDE.md) | The repo: stack, architecture, commands, workflow, conventions |
| 2 | [`GUARDRAILS.md`](GUARDRAILS.md) | **Binding.** What you may and may not do — identity, both lenses, data and backup, dependencies, accessibility, i18n, **the reader's security model**, closed decisions, and the never-do list |
| 3 | [`CONTEXT.md`](CONTEXT.md) | The state of the world: what shipped, what is active, what has actually been run on a device, and what is still an open question |
| 4 | [`TIDE_GOALS.md`](TIDE_GOALS.md) | **The active file** — M1…M3: a mínimo that closes the tide and a meta above it, the surplus said out loud, and a door to the numbers |

Two more, when you need them:

| File | When |
|---|---|
| [`DATA_MODEL.md`](DATA_MODEL.md) | Any task that adds a column, a migration or a query. Tracks the current Room version and what each field actually means |
| [`TASK_FILE_FORMAT.md`](TASK_FILE_FORMAT.md) | Before writing a **new** task file |

---

## Active — [`TIDE_GOALS.md`](TIDE_GOALS.md)

`SHAKEDOWN.md` (S1…S6) finished on **2026-08-19** and moved to
[`archive/`](archive/README.md). It was the file that followed #187 — thirty
tasks in one PR that **no device ever ran** — and it did the device pass, fixed
the three defects ten minutes of real use turned up, and built the block↔maré
link the owner reached for and did not find.

`TIDE_GOALS.md` is the file that follows it, written from a conversation rather
than a defect. A countable maré's single number is both its ceiling and what
marks the day done, so aiming for five litres of water and drinking three reads
as 60%, a half-filled cell and a broken streak — ambition punished. M1 splits it
into a **mínimo** that closes the tide and an optional **meta** above it, M2 says
the surplus out loud as one global figure, and M3 gives the numbers a door that
isn't inside Settings.

**Still owed, and larger than the active file:** five of `SHAKEDOWN`'s six tasks
were compiled and merged **without a device seeing any of them**.
[`CONTEXT.md`](CONTEXT.md) §4 lists exactly what a device pass would have to
cover, and it does not get shorter on its own.

## Complete

The nine finished task files live in [`archive/`](archive/README.md), with an
index explaining what each built and what moved out of it. **Their Logs are the
record of why the app is the way it is** — read them for reasoning, never for
instructions, and never follow their guardrails or data-model sections
(consolidated into `GUARDRAILS.md` and `DATA_MODEL.md`).

---

## Where to look for what

- **What am I allowed to do?** → `GUARDRAILS.md`. It is binding, and it wins
  over a task file that disagrees with it.
- **A book's columns, ids, or how progress is stored** → `DATA_MODEL.md`.
  Note what `currentPage` means: a page, a minute, or a percentage point,
  depending on the book.
- **Anything that parses or renders an attached book** → `GUARDRAILS.md` §G.
  Binding, not advisory.
- **Why something looks the way it does** → the Log of the file that shipped it,
  in `archive/`. The Logs carry the reasoning, including what was rejected.
- **A decision that keeps coming back** → `GUARDRAILS.md` §J. No cover art, no
  pagination, one habit list, percent as the EPUB's unit, no highlights from a
  selection: all closed, all with reasons.
- **What has and hasn't been tested on a device** → `CONTEXT.md` §4.

---

## Keeping this true is part of the job

- A task that changes what a user can see **updates the repo-root
  [`README.md`](../README.md) in the same PR** — the tab table, the reading
  section, the install steps, whatever it touched.
- A task that changes the state of the work **updates `CONTEXT.md` in the same
  PR** — status, order, what was verified.
- A new task **file** is added to the table above in the PR that creates it, and
  moved to `archive/` in the PR that finishes its last task.

The rules are written into [`TASK_FILE_FORMAT.md`](TASK_FILE_FORMAT.md) so they
outlive whoever remembered them.
