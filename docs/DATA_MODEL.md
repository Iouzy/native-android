# Data model — the persistent shape of the app

> **What this is.** Every table, column and migration a task might need to know
> about, in one place. It was previously split across the Data model sections of
> `docs/archive/BOOK_MODE.md` (the entities) and `docs/archive/BOOK_READER.md` (the file
> columns) — two finished task files that later work still had to open. This is
> the canonical copy.
>
> **Read this before any task that adds a column, a migration or a query.** You
> should not need to open an archived task file to know what a column means.

Source paths are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

**Room is at version 14** (`data/AppDatabase.kt`), with migrations 1→2
through 13→14 registered. The next migration a task writes is **14 → 15**.

---

## The rule that governs all of it

**`pauta.v4` is frozen.** Its shape does not change and its round-trip stays
lossless; `data/WebBackup.kt` and its tests are the gate. Everything added since
the web app is **native-only**, marked `// native-only` on the field, and is
excluded from that export explicitly rather than by accident.

Book data has its own export — **`pauta.books.v1`** (`data/BookBackup.kt`,
shipped in L2) — a separate file in its own format, merging by id on import.
Attached documents are in neither: they are device-local, and a restored backup
brings back the book, not the file.

---

## Entities

### `BookEntity` (table: `books`)

| Column | Type | Default | Notes |
|---|---|---|---|
| `id` | String PK | — | prefix `bk_` + UUID |
| `title` | String | — | required |
| `author` | String | `""` | |
| `series` | String | `""` | |
| `seriesNumber` | Int? | null | null = standalone |
| `format` | String | `"physical"` | `physical` / `ebook` / `audiobook` |
| `totalPages` | Int | `0` | 0 = unknown; for audiobooks = total **minutes** |
| `currentPage` | Int | `0` | for audiobooks = current minute; for an attached EPUB = **percentage point** |
| `status` | String | `"tbr"` | `tbr` / `reading` / `done` / `dnf` / `paused` |
| `startedAt` | Long? | null | ms epoch; null until the first session |
| `finishedAt` | Long? | null | ms epoch; null until done/dnf |
| `rating` | Int? | null | 1–5; null = unrated |
| `genre` | String | `""` | free text, comma-separated tags; split by `BookMath.genreTags` and shown on the detail sheet — L7 |
| `position` | Int | `0` | ordering within the status shelf. **Stale after a move** — see L3 |
| `createdAt` | Long | — | ms epoch |
| `filePath` | String? | null | absolute path inside `filesDir/books/`; null = no file |
| `fileKind` | String? | null | `pdf` / `epub`; null when `filePath` is null |
| `fileName` | String | `""` | the original display name, for the UI |
| `readPosition` | String | `""` | reader bookmark — page index (PDF) or `spineIndex:scrollPercent` (EPUB) |
| `wordCount` | Int | `0` | total words; counted for EPUB, estimated elsewhere |

Progress % = `currentPage.toFloat() / totalPages.coerceAtLeast(1)`.

> **The trap in this table.** `currentPage` means three different things
> depending on `format` and whether a file is attached: a page, a minute, or a
> percentage point. Every screen that *displays* it was taught this; several
> that *ask* for it were not, which is the whole of `FIELD_FIXES.md` F1. If you
> touch an input that writes `currentPage`, read F1 first.

### `BookNoteEntity` (table: `book_notes`)

| Column | Type | Default | Notes |
|---|---|---|---|
| `id` | String PK | — | prefix `bn_` + UUID |
| `bookId` | String | — | FK → `books.id` (not enforced, follows the web pattern) |
| `kind` | String | `"annotation"` | `quote` / `annotation` / `thought` |
| `text` | String | — | required |
| `page` | Int? | null | null = not recorded; unused for audiobooks |
| `createdAt` | Long | — | ms epoch |

### `PrefsEntity` — the native-only additions

| Column | Type | Default | Notes |
|---|---|---|---|
| `bookMode` | Boolean | `false` | the lens switch; one source of truth |
| `bookAnnualGoal` | Int | `0` | 0 = no goal set |
| `timerPresets` | String | `"pomodoro"` | `pomodoro` (25/50/90) / `simples` (15/30/45/60) — U2 |
| `notifAskedAt` | Long | `0` | 0 = we have never asked the OS for `POST_NOTIFICATIONS`; ms epoch once we have — N1 |
| `readerTextScale` | Float | `1.0` | 0.8–1.8; the reader's body size only — L5 |
| `readerLineHeight` | Float | `1.62` | 1.3–2.0 — L5 |
| `readerMargin` | Int | `22` | dp, 8–48 — L5 |
| `readerTheme` | String | `"app"` | `app` / `paper` / `sepia` / `night` — L5 |
| `readingReminderEnabled` | Boolean | `false` | gated on its own switch **and** `bookMode` — L10 |
| `readingReminderTime` | String | `"21:00"` | HH:MM — L10 |

All `// native-only`. Every value in the reader group clamps **in the
setter**, not at the point of use — a prefs row is data, and data that can be out
of range is data something downstream has to keep re-checking.

### Reading sessions — no new tables

A reading session **is** a `FocusBlockEntity` with `project = "book:<bookId>"`.
That reuse bought the timer, the history and the backup round-trip for free.

- `title` holds the book title, for display in normal block history.
- `linkedToId` stays null. Session notes go in `reflection`, set at conclude
  time. `targetMs` works normally.
- `AppViewModel.blocks` (the planner's flow) **excludes** `project LIKE 'book:%'`
  so the Pauta tab stays clean; `bookSessionBlocks` is the book-mode flow.

> **Both of the consequences this used to warn about are fixed.** Reading
> sessions no longer inherit the *focus* notification — the service reads the
> block's `project` and uses its own channel and wording (L9) — and `deleteBook`
> cascades to the blocks and their spans, so a deleted book leaves no orphans
> counting in the statistics (F2).

---

## Where files live

`context.filesDir/books/<bookId>.<ext>` — one file per book. Deleting a book
deletes its file (`BookFiles`). **Nothing is ever written outside `filesDir`.**
The security rules that govern how a file gets there are section G of
`docs/GUARDRAILS.md`, and they are binding.

## Derived numbers, and their honesty

| Quantity | How it is obtained | Marked |
|---|---|---|
| EPUB word count | counted from the spine | exact |
| PDF / physical word count | `BookMath.WORDS_PER_PAGE = 280` × pages | **`≈` everywhere** |
| Reading speed (WPM) | words ÷ minutes | exact only for a counted EPUB |
| ETA to finish | `BookMath.etaDays`, at the daily minutes measured from the book's own sessions (60 min/day when there are none) | the assumption is **printed beside the estimate** — L12 |

**An estimate says so.** A derived figure presented as measured is the defect
`FIELD_FIXES.md` exists to remove; see `GUARDRAILS.md` K.11.

---

## Migration history

| Version | What it added | Shipped in |
|---|---|---|
| 1 → 7 | the planner: intentions, blocks, habits, prefs, goals, routines | `NATIVE_IMPROVEMENTS` |
| 7 → 8 | `books`, `book_notes`, `bookMode`, `bookAnnualGoal` | `BOOK_MODE` K1 |
| 8 → 9 | `timerPresets` | `UX_FIXES` U2 |
| 9 → 10 | `filePath`, `fileKind`, `fileName`, `readPosition`, `wordCount` | `BOOK_READER` R2 |
| 10 → 11 | `pagesDelta` on `focus_blocks` | `BOOK_READER` R5 |
| 11 → 12 | `notifAskedAt` | `FIRST_RUN` N1 |
| 12 → 13 | `readerTextScale`, `readerLineHeight`, `readerMargin`, `readerTheme` | `BOOK_LIBRARY` L5 |
| 13 → 14 | `readingReminderEnabled`, `readingReminderTime` | `BOOK_LIBRARY` L10 |
| **14 → 15** | **next free** | — |

**Never rewrite a shipped migration.** If a shipped one was wrong, the fix is a
new migration that repairs the data, plus a Log line saying what it repairs.

Version 9 was taken by `UX_FIXES` U2 while `BOOK_READER` R2 was in flight, which
is why R2 is 9→10 and not 8→9. If two task files are open at once, **claim the
version number in the task file before you write the code** — that collision
cost a rebase.

---

## Log (append when the model changes)

<!-- YYYY-MM-DD · <what changed> · #PR · <why, and what it replaced> -->
2026-08-06 · the reading reminder on `prefs`, Room 13 → 14 · #187 · L10. Two gates, not one: its own switch and `bookMode`, and the lens is a key of the reschedule flow so turning it off cancels the alarm rather than hiding the switch.
2026-08-06 · the reader's four settings on `prefs`, Room 12 → 13 · #187 · L5. All four default to what the reader already did, so an existing install reads exactly as before until someone opens the sheet. `readerTextScale` **replaces** the density font scale for a book's body; the app-wide `textScale` still governs the reader's chrome.
2026-08-06 · `notifAskedAt` on `prefs`, Room 11 → 12 · #187 · N1 needed one bit of state — have we ever asked for `POST_NOTIFICATIONS`? — because Android shows that dialog once and a second request is silent. Stored as a timestamp rather than a Boolean so a later task can tell *when*, at no cost. **This took the 11 → 12 slot `BOOK_LIBRARY.md` L5 had claimed**; L5 moves to 12 → 13 and L10 to the next free one after it.
2026-08-03 · file created · — · consolidated from the Data model sections of `BOOK_MODE.md` (entities, sessions-as-blocks) and `BOOK_READER.md` (file columns, `filesDir/books`, the words-per-page constant), so those files could be archived without later work losing its reference; the migration history table, the "derived numbers" table and the version-collision note are new.
