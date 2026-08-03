# `docs/archive/` — the task files that finished

Five task files, every task shipped. They are kept because their **Logs** are
the only record of *why* the app is the way it is — what was decided, what was
rejected, and what was actually verified. Nothing else in the repository carries
that.

**These files are history, not instructions.**

- **Do not work from them.** The active files are `docs/FIRST_RUN.md`,
  `docs/BOOK_LIBRARY.md` and `docs/FIELD_FIXES.md`; `docs/README.md` indexes
  them.
- **Do not follow their Global guardrails sections.** Those were consolidated
  into `docs/GUARDRAILS.md`, which is now the binding copy. Where the two
  disagree, `GUARDRAILS.md` wins.
- **Do not follow their Data model sections.** Those were consolidated into
  `docs/DATA_MODEL.md`, which tracks the current Room version.
- **Do not retrofit them to `TASK_FILE_FORMAT.md`.** They predate it, they are
  already close, and rewriting shipped history to match a template destroys the
  one thing that makes them valuable.

| File | What it built | Tasks | Period |
|---|---|---|---|
| `NATIVE_IMPROVEMENTS.md` | The improvement roadmap after the web port reached parity: reminders, widget, QS tile, PIN lock, insights, goals, the updater, accessibility | A1…T2 | to mid-2026 |
| `BOOK_MODE.md` | Book mode as a **tracker** — the lens, the shelf, sessions, the detail sheet, quote capture. Introduced the book data model everything later builds on | K1…K9 + K-extra | 2026 |
| `POLISH.md` | UI modernisation: tab-switch jank, then the motion/surface/type foundations, then per-screen sweeps | P1…P10 | 2026 |
| `BOOK_READER.md` | Book mode from tracker to **reader** — attach a PDF/EPUB, read it in-app, progress that updates itself, reading speed, the second launcher icon. Carried the **Security model**, now in `GUARDRAILS.md` §G | R1…R8 | Aug 2026 |
| `UX_FIXES.md` | Usability fixes `POLISH` didn't cover: the sheet/keyboard race, timer presets, the Hoje composer, the Settings information architecture | U1…U7 | 2026 |

## What moved out of them, and where it went

| Was in | Now in | Why |
|---|---|---|
| `BOOK_READER.md` § Security model | `docs/GUARDRAILS.md` §G | Binding on active work. A finished file is the wrong home for a rule that still governs new code. |
| Six separate § Global guardrails | `docs/GUARDRAILS.md` §A–K | `FIELD_FIXES.md` used to open by importing four files, two of which nobody should need to read. |
| `BOOK_MODE.md` § Data model at a glance | `docs/DATA_MODEL.md` | Same reason. |
| `BOOK_READER.md` § Data model additions | `docs/DATA_MODEL.md` | Same reason. |
| Scattered "decided, don't re-open" notes | `docs/GUARDRAILS.md` §J | They were re-proposed because they were hard to find. |

The originals below are unedited apart from this move — the sections are still
in them, in their original wording, as part of the record.

## Reading them well

The **Log** at the foot of each file is the point. A good entry explains a
choice rather than a change, records the option that lost, and says what was
actually exercised. When you need to know why a thing is shaped the way it is —
why the peek guard is `durationMs` plus "did the page change", why there is no
`bookHabit` column, why version 9 was skipped — that is where the answer is.
