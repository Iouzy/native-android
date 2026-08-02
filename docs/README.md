# `docs/` — the task files

Every change to this app ships through one of the files below: a task with a
spec, one PR, and a Log line explaining why it was built that way. Nothing here
is documentation of the code — the code documents itself, bilingually. These are
**plans and their reasoning**.

**Writing a new one?** Read [`TASK_FILE_FORMAT.md`](TASK_FILE_FORMAT.md) first.

---

## Active — in this order

Two files are open at once, which is unusual here and deliberate. They were
written from different angles in the same week: one from a full code review of
book mode, one from using the app on a phone. **`BOOK_LIBRARY.md` Phase L-0
runs first** — those three are the app not keeping promises it already makes,
and they outrank every defect in the other file.

| Order | File | Scope | Tasks |
|---|---|---|---|
| **1** | [`BOOK_LIBRARY.md`](BOOK_LIBRARY.md) | Book mode round three, from a full review of K + R. **L-0 first**: the wipe that doesn't clear the library, the backup that leaks the reading list while the library is in no backup at all, the statuses no UI can reach. Then the reader controls a reader expects, then the shelf at scale | L1…L12 |
| **2** | [`FIELD_FIXES.md`](FIELD_FIXES.md) | Defects found by **using** the app — the unit collision that sent a book to 100%, sessions that cannot be deleted, the keyboard that eats a half-typed tide, counts with no ceiling, the launcher door, and the round of UI fixes that never shipped | F1…F13 |

`FIELD_FIXES.md` originally carried three more tasks — a chapter index, the
shelf at scale, notes anchored to a reading position. They were dropped in
favour of `BOOK_LIBRARY.md`'s **L4**, **L8** and **L6**, which cover the same
ground with more of it.

## Complete

| File | Scope | Tasks |
|---|---|---|
| [`NATIVE_IMPROVEMENTS.md`](NATIVE_IMPROVEMENTS.md) | The improvement roadmap after the web port reached parity | A1…T2 |
| [`BOOK_MODE.md`](BOOK_MODE.md) | Book mode as a *tracker* — the shelf, sessions, the detail sheet, quote capture. Also the **data model** every later book task builds on | K1–K9 + K-extra |
| [`POLISH.md`](POLISH.md) | UI modernisation — tab-switch jank, then motion/surface/type foundations, then per-screen sweeps | P1…P10 |
| [`BOOK_READER.md`](BOOK_READER.md) | Book mode from tracker to **reader** — attach a PDF/EPUB, read it in-app, progress that updates itself, reading speed, the second launcher icon. Carries the **Security model** binding on anything touching a parsed book | R1…R8 |
| [`UX_FIXES.md`](UX_FIXES.md) | Usability fixes POLISH didn't cover — the sheet/keyboard race, timer presets, the Hoje composer, the Settings information architecture | U1…U7 |

## Reference

| File | What it is |
|---|---|
| [`TASK_FILE_FORMAT.md`](TASK_FILE_FORMAT.md) | The shape every new task file follows, and why each section exists |

---

## Where to look for what

- **A book's columns, ids, or how progress is stored** → `BOOK_MODE.md`, data
  model section; extended by `BOOK_READER.md` (attached files, read position,
  word count).
- **Anything that parses or renders an attached book** → the **Security model**
  in `BOOK_READER.md`. It is binding, not advisory.
- **Why something looks the way it does** → the Log of the file that shipped it.
  The Logs carry the reasoning, including what was rejected.
- **A decision that keeps coming back** → the "Decisions already taken" section
  of the relevant file. No cover art, no pagination, one habit list, percent as
  the EPUB's unit: all closed, all with reasons.
