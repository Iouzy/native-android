# Book library — implementation task file

> **Concept.** `docs/archive/BOOK_MODE.md` built the lens and
> `docs/archive/BOOK_READER.md`
> built the reader. This file is round three, and it starts from a full review
> of what those two shipped (August 2026). The reader itself is good: it opens
> a book, it counts honestly, it keeps the security model. What is missing sits
> on either side of it.
>
> **Behind the reader**, three promises the app makes are not kept. "Apagar
> tudo" does not delete the library. The backup contains the *titles of every
> book you have read* but not the library itself, so a reinstall loses the
> shelf permanently and the export leaks what the guardrail said it never
> would. And a book has five documented statuses of which the UI can reach
> three, one-way.
>
> **In front of the reader**, a reader expects three things this one has not
> got: a way to jump (no table of contents, no go-to-page), a way to set the
> type (size comes from the app-wide `textScale` and nothing else is settable),
> and a way to keep a line (quote capture exists, but not from inside the book).
>
> **Around both**, the shelf does not scale past a few dozen books, and one
> stored field — `genre` — has never been read by anything.
>
> Ships as 12 self-contained tasks (L1…L12). Each task is one PR. Tasks within
> a phase are independent unless "Depends on:" says otherwise.

> **How to use (human).** In a fresh session, prompt:
>
> > Read `docs/BOOK_LIBRARY.md`. Do ONLY task **L1** — follow its spec and the
> > Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR → CI →
> > squash-merge), then set the task's Status and append one line to the Log.
>
> Or stateless: *"Read `docs/BOOK_LIBRARY.md` and do the first task whose
> Status is `pending`."*
>
> **How to use (Claude).** This file + `CLAUDE.md` + `docs/GUARDRAILS.md` +
> `docs/CONTEXT.md` + `docs/DATA_MODEL.md` are your complete briefing. Don't
> re-survey the codebase beyond the files each task names, and don't open the
> archived task files for instructions — only for the reasoning in their Logs.
> Always update Status, the Log **and `docs/CONTEXT.md`** in the same PR as the
> code.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Guardrails

**`docs/GUARDRAILS.md` applies in full.** It is binding and it is not restated
here — including **§G, the Security model**, which governs anything touching an
attached file, the parser, the `:reader` process or the WebView. Column
definitions and the current Room version are in `docs/DATA_MODEL.md`. The ones
that bite hardest in this file:

- **§D — no new dependencies. Still none.** Everything here is framework +
  stdlib.
- **§B — book mode is still a lens, not a fork.** With `bookMode` off, every
  screen behaves exactly as today.
- **§C — device-local stays device-local.** L2 gave book data its own export,
  `pauta.books.v1`, a separate file in its own format. Nothing book-shaped may
  enter `pauta.v4`.
- **§E — prefs are law:** `reducedMotion` and `haptics` gate every new animation
  and tick, `textScale` and `highContrast` still apply, TalkBack descriptions on
  every new control.
- **§J — no cover art, and no highlights from a text selection.** Both settled,
  both on reasons rather than effort. L8 makes the shelf searchable
  *typographically*; L6 is the honest version of note capture.

**Extra, specific to this file:**

- **The reader stays quiet.** L4, L5 and L6 each add a control to the reader.
  They live in the chrome that already fades, in the app's paper/ink/serif
  identity, and they add no toolbars, FABs or Material chrome. **Whichever of
  the three ships first owns the new control row**; the other two join it.
- **Claim your Room version in the task before you write code.** L5 takes
  **12 → 13**; L10 takes the next free one. A collision on v9 once cost a
  rebase — and 11 → 12 went to `FIRST_RUN.md` N1, which shipped ahead of this
  file, which is why L5's number moved.

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Suggested model per task

| Model | Tasks |
|---|---|
| **Opus 5** | L1, L2, L3, L5, L6, L8 |
| **Sonnet 5** | L4, L7, L9, L10, L11, L12 |

---

## Deliberately not doing (decided in this review — don't re-propose)

- **Cover art.** Settled; now recorded in `docs/GUARDRAILS.md` §J. Unchanged.
- **Highlights from a text selection in an EPUB.** This is the one obviously
  desirable reader feature that is genuinely blocked. Reading a WebView's
  selection requires `window.getSelection()`, and §3 of the Security model
  turns JavaScript off in the only browser engine the app has. There is no
  public non-JS API for the selected text. Enabling scripting to render
  untrusted HTML in order to get a nicer note-taking flow is a bad trade, and
  it stays refused. **L6 is the honest version**: capture a note at the
  position the reader already knows, typed rather than selected.
- **A fourth tab, cloud sync, an account, a store/OPDS catalogue, importing
  from Goodreads/StoryGraph.** Out of scope for a private offline app.
- **Bundling the attached documents into the book export.** They are the
  user's own files, they can be hundreds of megabytes, and R3 already built
  the exact behaviour for a book whose file is missing. See L2.

---

## Phase L-0 — the promises already made (do these first)

These three are not features. They are the app failing to do what it says it
does, and they outrank everything in the later phases.

### L1 · "Apagar tudo" apaga tudo — Status: done (PR #181)

**Depends on:** nothing

**Why:** `PautaRepository.resetAll()` clears eleven tables and none of them are
the library. After the danger-zone wipe, every book, every note, every rating
and every attached PDF/EPUB in `filesDir/books/` is still on the device. For an
app whose strongest promise is that the data is yours and local, the wipe that
does not wipe is the most serious defect in book mode.

`reseed()` calls `resetAll()` and then seeds planner demo data, so it inherits
the same hole: loading the sample data leaves a stale library behind it.

**Files to touch:**
- `data/PautaRepository.kt` — `resetAll`
- `data/dao/Daos.kt` — `BookDao.clear()` / `BookNoteDao.clear()` already exist
- `data/BookFiles.kt` — a directory-level wipe
- `src/test/…` if any pure helper falls out of it

**How:**

`resetAll()` gains, alongside the eleven existing `clear()` calls:

```kotlin
bookNoteDao.clear()
bookDao.clear()
BookFiles.clearAll(context)   // the whole filesDir/books/ directory
```

`resetAll` currently takes no `Context`; give it one (the ViewModel has the
application context) rather than reaching for a stored field. `BookFiles`
already knows the directory and already owns `isOurs` — `clearAll` deletes the
directory's contents and nothing outside it, and is a no-op when the directory
does not exist.

**Details that matter:**
- Delete the **files** as well as the rows. A row-only wipe leaves the
  documents, which is the part a user would most mind.
- `BookFiles.clearAll` must refuse to walk out of `filesDir/books/` — no
  symlink following, no `..`; assert the resolved canonical path is still
  inside the directory, the same check `isOurs` makes.
- Prefs stay intact, as the existing doc comment says — `bookMode` and
  `bookAnnualGoal` are the user's settings, not their data.

**Out of scope:** the backup (L2), the confirm dialog's wording.

**Accept:** after "Apagar tudo" the shelf is empty in book mode, no notes
survive, `filesDir/books/` is empty, and planner mode is exactly as before;
"Carregar dados de exemplo" leaves no stale books; CI green.

---

### L2 · The library in a backup — and out of `pauta.v4` — Status: done (PR #182)

**Depends on:** nothing (do it after L1 — they touch adjacent code)

**Why:** two failures that are mirror images of each other.

**(a) Reading sessions are being exported, and they shouldn't be.**
`snapshot()` takes `focusBlockDao.getAll()` wholesale. A reading session is a
`FocusBlockEntity` with `project = "book:<id>"` **and the block's `title` is
the book's title**. So every `pauta.v4` file the app has ever written contains
a list of the books its owner has been reading. `docs/GUARDRAILS.md` §C says book
data "must **not** appear in the `pauta.v4` export and must be explicitly
excluded from `WebBackup.kt`". It is not excluded. `AppViewModel.blocks`
already filters `project LIKE 'book:%'` for the planner UI; the export never
learned the same rule. Automatic backups (`service/BackupWorker.kt`) write the
same file, so this is not limited to manual exports.

**(b) The library cannot be backed up at all.** `books` and `book_notes` are in
no export, so a reinstall, a factory reset or a lost phone loses the shelf, the
ratings, the notes and the reading history permanently. "Offline-first" cannot
also mean "unrecoverable".

**Files to touch:**
- `data/WebBackup.kt` — exclude book blocks from the v4 export
- `data/BookBackup.kt` — **new**, the book-side format
- `data/PautaRepository.kt` — `snapshot`, `importJson`, the new export/import
- `data/dao/Daos.kt` — a planner-only clear for focus blocks
- `ui/screens/SettingsScreen.kt` — two rows in the Dados section
- `ui/viewmodel/AppViewModel.kt`
- `i18n/I18n.kt`
- `src/test/BookBackupTest.kt` — **new**

**How — (a) the v4 export:**

`snapshot()` filters book blocks out, and their sessions with them:

```kotlin
val plannerBlocks = focusBlockDao.getAll().filter { it.project?.startsWith("book:") != true }
val ids = plannerBlocks.mapTo(HashSet()) { it.id }
… blocks = plannerBlocks, sessions = focusSessionDao.getAll().filter { it.blockId in ids }
```

The same rule has to hold on the way back in. `importJson` clears
`focus_blocks` entirely before repopulating, which **deletes every reading
session on the device** when a planner backup is restored. Add
`FocusBlockDao.clearPlanner()` (`DELETE FROM focus_blocks WHERE project IS NULL
OR project NOT LIKE 'book:%'`) and its session equivalent, and use those in
`importJson`. Restoring a planner backup must leave the library untouched —
including its sessions.

> Do the filtering in `snapshot()`, not inside `WebBackup.export`. `WebBackup`
> is the format; deciding what is planner data is the repository's job, and
> `WebBackupExportTest` should get a case proving a `book:` block handed to
> `export` still round-trips (the format is unchanged) *plus* a repository-level
> case proving `snapshot` never hands it one.

**How — (b) the book export:**

A **separate file, in its own format**, written and read through SAF exactly as
the v4 export already is:

- Filename `pauta-livros-<YYYY-MM-DD>.json`, format id `pauta.books.v1`.
- Contents: every `BookEntity` row, every `BookNoteEntity` row, and the book
  `FocusBlockEntity` rows with their `FocusSessionEntity` segments — including
  `pagesDelta`, which R5 stores and v4 has no field for.
- **`filePath` and `fileKind` are not exported.** They are this device's paths.
  `fileName` *is* exported, because it is what the user called the file and it
  makes the re-attach prompt meaningful.
- Import **merges by id** (upsert), it does not wipe. Restoring a library onto
  a device that already has one should add to it, not replace it: this file is
  a rescue path, not a sync protocol, and a wipe-on-import is how someone loses
  the shelf they were trying to protect.

A restored book has no file, which is a state R3 already handles: the reader
shows *"O ficheiro já não está aqui."* and offers "Anexar de novo". That is why
the documents themselves stay out of the export — they are the user's own
files, they can be hundreds of megabytes, and the missing-file path is built,
tested and honest.

Two rows in Settings → Dados, book mode only (the section is already
conditional elsewhere in that file):

```
Exportar biblioteca        Livros, notas e sessões de leitura.
Importar biblioteca        Junta à biblioteca atual; nada é apagado.
```

**Details that matter:**
- The book export is native-only in the strict sense: nothing reads it but this
  app. Version it (`"format": "pauta.books.v1"`) and refuse an unknown format
  string rather than guessing.
- An import must validate: unknown `status` values fall back to `tbr`, unknown
  `format` to `physical`, `currentPage` clamps at `totalPages` where one is
  known. Treat the file as untrusted input — it is.
- Do **not** import `filePath`/`fileKind` even if a hand-edited file carries
  them; a path from a JSON file is exactly the tampered-row case §5 of the
  Security model refuses.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Exportar biblioteca` | `Export library` |
| `Importar biblioteca` | `Import library` |
| `Livros, notas e sessões de leitura.` | `Books, notes and reading sessions.` |
| `Junta à biblioteca atual; nada é apagado.` | `Merges into the current library; nothing is deleted.` |
| `Biblioteca exportada` | `Library exported` |
| `{n} livros importados` | `{n} books imported` |
| `Este ficheiro não é uma biblioteca Pauta.` | `This file is not a Pauta library.` |

**Out of scope:** exporting the documents, any automatic scheduling of the book
export (`BackupWorker` stays planner-only for now), sync of any kind.

**Accept:** a `pauta.v4` export contains no block whose project starts with
`book:`; restoring a v4 backup leaves books, notes and reading sessions intact;
a library export re-imported on a wiped install restores the shelf, ratings,
notes, sessions and `pagesDelta`; the restored books show the missing-file
notice and re-attach correctly; an unknown/corrupt file is refused with the
message above and changes nothing; CI green.

---

### L3 · The five states a book can be in — Status: done (PR #186)

**Depends on:** nothing

**Why:** `BookEntity.status` documents `tbr` / `reading` / `done` / `dnf` /
`paused`. The UI can reach `tbr`, `reading` and `done`, and only forwards:

- **There is no way out of `done`.** `BookDetailSheet` shows "Começar a ler"
  for `tbr` and "Marcar como lido" for `reading`, and nothing at all for a
  finished book. `BookFormSheet` hides the status pills when editing ("Estado
  is only set on add"). A book marked finished by a mis-tap is finished
  forever, short of deleting it and losing its notes and sessions with it.
- **There is no "abandonar".** `booksDone()` merges `done` + `dnf` and sorts
  them together, so the shelf is *ready* for abandoned books — nothing can
  create one.
- **`paused` is invisible.** `booksReading` / `booksTbr` / `booksDone` cover
  four of the five statuses. A book that reaches `paused` — through an L2
  import, or a later feature — appears on **no shelf**, and the only way to
  find it is to know its id. Any status the entity admits must be reachable in
  the UI *and* visible on some shelf; that is the invariant this task installs.
- **`position` is stale after a move.** `addBook` sets `position` to the count
  of books in that status; `updateBook` never recomputes it, so a book moved
  from `tbr` to `reading` keeps its old index and shelves accumulate
  collisions under `ORDER BY position`.

**Files to touch:**
- `ui/screens/BookDetailSheet.kt` — the status actions
- `ui/screens/BookShelfScreen.kt` — the paused section
- `data/PautaRepository.kt` — one `setStatus` that owns the transition
- `ui/viewmodel/AppViewModel.kt`
- `i18n/I18n.kt`

**How:**

One repository method owns every transition, so the rules live in one place:

```kotlin
suspend fun setBookStatus(id: String, status: String)
```

It sets `startedAt` on the first move into `reading` (never overwriting an
existing one), sets `finishedAt` on `done`/`dnf` and clears it on any move back
out, and **recomputes `position`** as the count of books already in the
destination status. Every existing caller (`finishBook`, "Começar a ler") goes
through it.

The detail sheet's status block becomes complete rather than forward-only:

| Current | Primary | Secondary (quiet, in a row under it) |
|---|---|---|
| `tbr` | Começar a ler | Abandonar |
| `reading` | Marcar como lido | Pausar · Abandonar |
| `paused` | Retomar | Marcar como lido · Abandonar |
| `done` | — | Voltar a ler · Marcar como não lido |
| `dnf` | Recomeçar | Marcar como lido |

"Voltar a ler" moves to `reading` and **keeps `finishedAt`** cleared but leaves
the rating and the notes alone — a re-read is the same book, not a new one.
"Marcar como não lido" is the plain undo of a mis-tap: back to `reading`,
`finishedAt` null.

The secondary actions are quiet ink (`PautaButtonVariant.Ghost`, or the same
mono header-action treatment the shelf uses), not a row of primaries. Only the
destructive one — "Abandonar" — arms in two steps, reusing the same
`confirmDelete` pattern already in this sheet.

The shelf gains a fourth section, between "A ler agora" and "A seguir", shown
only when non-empty:

```
EM PAUSA
```

rendered with `BookListRow` (the "A seguir" treatment), because a paused book
is a title you are choosing between, not one you are in the middle of. Add
`booksPaused()` to the repository beside the other three.

**Details that matter:**
- The shelf's three flows plus the new one must cover **every** status the
  entity admits. Write it as a test if a pure helper falls out; otherwise a
  comment in `Entities.kt` next to the status list saying which flow shows
  which, so the next status added doesn't vanish.
- An abandoned book keeps its progress and its notes. `dnf` is a judgement
  about the book, not a deletion.
- `FinishBookSheet` (the rating prompt) belongs to "Marcar como lido" only.
  "Abandonar" does not ask for a rating.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Abandonar` | `Abandon` |
| `Pausar leitura` | `Pause reading` |
| `Retomar` | `Resume` |
| `Voltar a ler` | `Read again` |
| `Marcar como não lido` | `Mark as unread` |
| `Recomeçar` | `Start over` |
| `Em pausa` | `Paused` |
| `Abandonado` | `Abandoned` |

**Out of scope:** manual reordering within a shelf, re-read *counts* (a second
`startedAt` history), per-status sorting (L8).

**Accept:** every one of the five statuses is reachable from the detail sheet
and appears on exactly one shelf section; a finished book can return to
`reading`; abandoning keeps notes, sessions and progress; `position` is
recomputed on every move; planner mode untouched; CI green.

---

## Phase L-1 — the reader a reader expects

### L4 · A table of contents, and a way to jump — Status: done (PR #187)

**Depends on:** nothing

**Why:** the reader can only move one step at a time. An EPUB turns one chapter
per edge-tap, so chapter 20 is nineteen taps and there is no way to see what
the chapters even are. A PDF is a `LazyColumn` of *n* pages with no go-to-page,
so returning to page 400 is a scroll.

And the chapter names **already exist**: `domain/Epub.EpubChapter` carries a
`title` parsed from the OPF, and `service/EpubInfo` keeps only `chapterWords`
and `chapterHrefs`. The titles are parsed and then dropped at the IPC boundary.
The reader shows "Capítulo 7 de 31" because it was never handed "Capítulo 7 ·
As Cidades e os Mortos".

**Files to touch:**
- `service/DocumentParse.kt` — `EpubInfo` carries titles
- `service/DocumentParseService.kt` — put them on the wire
- `ui/screens/ReaderScreen.kt` — the chrome action + the sheet
- `ui/screens/BookProgress.kt` or a new `ui/screens/ReaderToc.kt`
- `i18n/I18n.kt`

**How:**

`EpubInfo` gains `chapterTitles: List<String>` alongside `chapterWords` and
`chapterHrefs` — same length, same order, `""` where the OPF gave nothing. The
existing `open` reply already streams a small structured payload; extend it,
and keep the three lists' lengths validated on arrival (a mismatched reply is a
corrupt one).

The reader's top bar gets a third control between the title and `⋯` — `☰`,
described to TalkBack as `tr("Índice")` — opening a `PautaSheet`:

- **EPUB:** the chapter list. Each row is its title (falling back to
  `trf("Capítulo {n}", …)` when blank), quiet mono percentage on the right
  showing where that chapter starts (`Epub.percent(chapterWords, i, 0f)`), the
  current chapter marked in accent. Tapping turns to it and closes the sheet.
- **PDF:** no TOC exists to parse, so the sheet is a single go-to-page field —
  numeric, clamped to `1..pageCount`, `ImeAction.Go` — plus the current page as
  its placeholder. `listState.scrollToItem(n - 1)`.

The sheet opens the reader's own chrome, so it obeys the same fade and the
chrome stays visible while it is open.

**Details that matter:**
- A chapter title is untrusted text from the book. It is already decoded by the
  entity table in `Epub`, but it renders as a Compose `Text` — never as HTML,
  and `maxLines = 2` with ellipsis so a book cannot push the list apart.
- Jumping is a position change like any other: it goes through the same `mark`
  / `turn` path so the bookmark, the label and the session all follow. Do not
  write `state.position` directly from the sheet.
- The TOC list scrolls to the current chapter when it opens — on a 60-chapter
  book, opening at the top is opening in the wrong place.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Índice` | `Contents` |
| `Capítulo {n}` | `Chapter {n}` |
| `Ir para a página` | `Go to page` |

**Out of scope:** the EPUB `nav`/`ncx` document (the spine + OPF titles are
enough and parsing another XML file is another attack surface), nested TOC
levels, PDF outlines/bookmarks (`PdfRenderer` cannot read them).

**Accept:** an EPUB shows its real chapter names and jumps to any of them; a
PDF jumps to a typed page and refuses one out of range; the bookmark and the
session follow a jump; TalkBack announces both controls; CI green.

---

### L5 · Reading settings — Status: done (PR #187)

**Depends on:** nothing (independent of L4; both add to the reader chrome —
whichever ships second reuses the first's control row)

**Why:** the single most-expected feature of any reader, and the one this one
has least of. Body size comes from the app-wide `textScale` preference and
nothing else is adjustable at all: no line height, no margin, no reading theme,
no choice of face. Someone reading at night in a bright room has to leave the
book, open Settings, change a global preference that also resizes the whole
planner, and come back.

**Files to touch:**
- `data/entity/Entities.kt` + `data/AppDatabase.kt` — prefs columns, Room v12 → v13
- `ui/screens/ReaderScreen.kt` — the chrome action + the sheet
- `ui/screens/EpubReader.kt` — `rememberChapterCss` reads them
- `ui/screens/PdfReader.kt` — the theme half only
- `i18n/I18n.kt`

**How:**

Four new `PrefsEntity` columns, all `// native-only`, Room **v12 → v13** as
`MIGRATION_12_13`:

| Column | Type | Default | Notes |
|---|---|---|---|
| `readerTextScale` | Float | `1.0` | 0.8–1.8, multiplies the reader body size only |
| `readerLineHeight` | Float | `1.62` | 1.3–2.0 |
| `readerMargin` | Int | `22` | dp, 8–48 |
| `readerTheme` | String | `"app"` | `app` / `paper` / `sepia` / `night` |

The reader's chrome gets a `Aa` control opening a `PautaSheet` with four rows —
each a label and a stepper (`−` value `+`, the app's mono meta treatment), not
sliders: sliders are imprecise and Material. Changes apply live behind the
sheet, which is the whole point of putting them here.

`rememberChapterCss` reads all four instead of the density's font scale:

```kotlin
val body = (18f * prefs.readerTextScale).toInt().coerceIn(12, 40)
```

Note this **replaces** the current `density.fontScale` read. The app-wide
`textScale` accessibility preference should still influence the reader's
*chrome* (the labels, the sheet) as it does everywhere else — it just stops
being the only thing that sets the body size of a book.

`readerTheme` resolves to a paper/ink pair:

- `app` — `LocalPautaColors` as today, following the app's theme and the book
  mode sepia wash.
- `paper`, `sepia`, `night` — three fixed pairs, defined once in
  `ui/theme/Color.kt` beside the existing tokens, not literals in the CSS
  builder.

The PDF half honours `readerTheme` only in what it can: the page background
behind a rendered bitmap and the reader's own surface. **Do not invert or
recolour a rendered PDF page** — a scanned page inverted is unreadable and a
diagram inverted is wrong. `night` on a PDF dims the surround and leaves the
page as the document drew it.

**Details that matter:**
- Every value clamps in the setter, not in the CSS. A prefs row is data.
- The CSS is a `remember` keyed on all four values plus the colours; get the
  key right or the chapter reloads on every recomposition and throws the scroll
  away (see the `LoadState` comment in `EpubReader.kt`).
- Changing the type size changes the chapter's height, and therefore what the
  scroll fraction means. Re-`mark()` after a change so the bookmark and the
  percentage stay truthful.
- The CSP still forbids `font-src`, so the body face stays the platform
  `serif`. Don't add a face picker; it would be a picker with one entry. Say so
  in the code comment rather than leaving the next reader to rediscover it.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Leitura` | `Reading` |
| `Tamanho do texto` | `Text size` |
| `Entrelinha` | `Line height` |
| `Margens` | `Margins` |
| `Tema de leitura` | `Reading theme` |
| `Papel` | `Paper` |
| `Sépia` | `Sepia` |
| `Noite` | `Night` |
| `Como a app` | `Match the app` |

**Out of scope:** per-book settings (these are the reader's, not the book's),
justification and hyphenation (the engine's defaults are better than a toggle),
a font picker, screen brightness (a system control).

**Accept:** all four settings change the page live and persist across a
restart; a PDF page is never recoloured; the bookmark survives a size change;
`reducedMotion` and `highContrast` still behave; the migration is tested; CI
green.

---

### L6 · Keeping a line, from inside the book — Status: done (PR #187)

**Depends on:** nothing

**Why:** capturing a quote is the highest-value thing a reading app does, and
in this one it is unreachable at the moment you want it. `QuoteCaptureSheet`
opens from **one** place — the Estante header — and:

- from inside the reader there is no note action at all; `⋯` opens
  `BookDetailSheet`, which *lists* notes and cannot add one;
- it only offers books with `status == "reading"`, so a note on a book you
  just finished has nowhere to go;
- it asks you to type the page, which the reader already knows.

**Files to touch:**
- `ui/screens/ReaderScreen.kt` — the chrome action
- `ui/screens/QuoteCaptureSheet.kt` — a targeted mode
- `ui/screens/BookDetailSheet.kt` — an add action on the notes section
- `i18n/I18n.kt`

**How:**

`QuoteCaptureSheet` gains two optional parameters:

```kotlin
fun QuoteCaptureSheet(
    onClose: () -> Unit,
    bookId: String? = null,   // fixed target: no picker, no shelf filter
    atPage: Int? = null,      // pre-filled position
)
```

With `bookId` set it skips the book picker entirely and drops the
`status == "reading"` constraint — the caller has already chosen. With `atPage`
set the page field is pre-filled and still editable (the reader's page is right
far more often than not, and a quote spanning a page break is real).

Three callers:
- **The reader chrome:** a `✎` beside `☰` and `⋯`, passing `bookId` and the
  current position. For an EPUB pass the percentage — `BookNoteEntity.page` is
  the position in the unit the book counts in, exactly as `currentPage` is, and
  `BookDetailSheet` must render it the same way (`43%`, not `p. 43`). Reuse
  `bookProgressLabel`'s branch rather than writing a second one.
- **The detail sheet:** a quiet `+ Nota` on the "Notas & Citações" eyebrow row,
  passing `bookId` only. This is what makes a note on a finished book possible.
- **The shelf header:** unchanged, both parameters null.

**Details that matter:**
- A note captured from the reader must not disturb the reading session. The
  sheet composes over the reader; the reader stays composed, so `onDispose`
  does not run and the session keeps going. Verify this rather than assuming
  it — if the sheet is a navigation destination it would end the session, which
  is exactly the failure to avoid.
- The keyboard race U1 solved applies: the focus requester goes inside the
  sheet body.
- An EPUB percentage is not a page number, and calling it `p. 43` in the list
  would be the second time the app has had to learn this lesson (see R4).

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Nova nota` | `New note` *(exists — reuse)* |
| `+ Nota` | `+ Note` |

**Out of scope:** highlights from a text selection (refused above, with
reasons), exporting notes as a document, note search (L8 covers the shelf; a
notes search can follow if it earns its place).

**Accept:** a note can be captured without leaving the reader and lands on the
right book at the right position; the reading session is not interrupted; a
finished book can take a note; an EPUB note reads as a percentage everywhere;
the shelf header path is unchanged; CI green.

---

## Phase L-2 — the shelf at scale

### L7 · `genre` earns its keep, or goes — Status: done (PR #187)

**Depends on:** nothing (but L8 is where it becomes useful — ship L7 first and
L8 consumes it)

**Why:** `BookEntity.genre` is collected by `BookFormSheet`, trimmed, stored,
migrated across two Room versions — and **never read by anything**. It is
write-only data: the only references outside the entity and the migration are
the form that writes it and the repository parameter that carries it there. A
field the user fills in and the app never shows is a small dishonesty.

**Files to touch:**
- `ui/screens/BookDetailSheet.kt` — render it
- `ui/screens/BookShelfScreen.kt` — the card meta, optionally
- `i18n/I18n.kt`

**How:**

The field is documented as "free text (comma-separated tags)", so treat it as
tags: split on commas, trim, drop blanks, and render on the detail sheet beside
the format chip using the same bordered `MetaSmall` treatment — one small
bordered chip per tag, wrapping in a `FlowRow`. Nothing new is stored; the
split is a pure helper (`domain/BookMath.kt` or a small `genreTags(book)` next
to `bookProgressLabel`), which makes it testable and makes L8's filter reuse it
instead of re-splitting.

> **If this proves not worth it**, the alternative is legitimate and should be
> taken rather than half-done: remove the field from the form and stop writing
> it, leaving the column in place (dropping a column is a migration nobody
> needs). Record the choice in the Log either way.

**Out of scope:** a tag picker, autocomplete over existing genres, a genre
taxonomy.

**Accept:** a book's genres are visible where the book is; the split handles
`"ficção, ensaio"`, `"ficção,ensaio"`, `" "` and `""`; no new column; CI green.

---

### L8 · The shelf at a hundred books — Status: done (PR #187)

**Depends on:** L3 (the paused section), L7 (genre tags, if kept)

**Why:** the shelf is built for a small library and quietly stops working as it
grows. There is no search, no sort and no filter anywhere in book mode.
"Lidos" is an unbounded horizontal `LazyRow`, so a year's reading is a long
sideways scroll with no way to reach a title you remember; "A seguir" is a flat
vertical list in `position` order, which after L3's moves is close to arbitrary.
Finding a book you read in March is not possible except by scrolling.

**Files to touch:**
- `ui/screens/BookShelfScreen.kt`
- `domain/BookShelf.kt` — **new**, the pure filter/sort
- `src/test/BookShelfTest.kt` — **new**
- `i18n/I18n.kt`

**How:**

One search field in the shelf header — the same quiet treatment as U5's
Settings search, which already solved this exact problem in this codebase
(`SettingsScreen.kt`: one row index, two renderings). Reuse the pattern, not
the code, if it does not generalise cleanly.

Empty query: the shelf renders exactly as it does today, four sections. A
non-empty query: the sections collapse into one flat result list ordered by
relevance, each row showing title, author and a quiet status word — because
when you are searching, "which shelf is it on" is the answer, not the
navigation.

The matching is a pure function in `domain/BookShelf.kt`:

```kotlin
fun search(books: List<BookEntity>, query: String): List<BookEntity>
```

Case- and accent-insensitive (`java.text.Normalizer`, NFD, strip combining
marks — "Saramago" must match "saramago" and "sarámago"), matching title,
author, series and genre, with title matches ranked above author matches above
the rest. No fuzzy matching: a substring match over four fields is enough for a
personal library and it is explicable.

Sort is a small control beside the search field, applying to the "Lidos"
section (the only one long enough to need it) and to search results:

```
Recentes · Título · Autor · Classificação
```

And "Lidos" stops being a `LazyRow`: past a threshold (say 12) it renders as
rows in the "A seguir" treatment, which scrolls in the direction the page
already scrolls.

**Details that matter:**
- The four flows already collect separately. Combine them once, in one
  `remember`, rather than searching four lists in four places.
- Keep the search state out of the ViewModel — it is screen state, and the
  shelf is one screen.
- TalkBack: the field is labelled, and the result count is announced
  (`trf("{n} livros", …)`).
- An empty result is the shared `EmptyState`, not a bespoke message.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Procurar na estante` | `Search the shelf` |
| `Recentes` | `Recent` |
| `Título` | `Title` *(check for an existing key first)* |
| `Autor` | `Author` *(check for an existing key first)* |
| `Classificação` | `Rating` |
| `Nenhum livro encontrado` | `No books found` |

**Out of scope:** searching inside a book's text (a different feature, and a
much bigger one), searching notes, saved filters, shelf reordering by drag.

**Accept:** a hundred-book library is searchable by title, author, series and
genre, accent-insensitively; sort applies and persists for the session; "Lidos"
never scrolls sideways past the threshold; the empty-query shelf is
pixel-identical to today's; CI green.

---

## Phase L-3 — the edges

### L9 · A reading session is not a focus block — Status: pending

**Depends on:** nothing

**Why:** reading sessions reuse the focus-block machinery, which was the right
call and gave the timer, the history and the notification for free. But the
notification came through unedited: reading a novel raises a **"Foco"**
notification, on a channel called "Foco", offering "Pausar" and "Concluir".

There is also a real defect hiding behind it. Concluding a session **from the
notification while the reader is open** ends the block; the reader's
`onDispose` then calls `endReaderSession`, finds no active block, writes the
bookmark, leaves `currentPage` untouched and returns null. The block is stored
with `pagesDelta = null`. It is the one path where the reader knows exactly how
far you got and does not record it.

**Files to touch:**
- `service/FocusService.kt` — the channel and the wording
- `data/PautaRepository.kt` — `endReaderSession`'s null-block branch
- `i18n/I18n.kt`

**How:**

The service already receives the block's title; give it the project too, and
when it starts with `book:` use the reading wording — channel name `tr("Leitura")`,
and "Concluir" reads "Terminar sessão". A second `NotificationChannel` is
acceptable here (a different kind of ongoing activity, and the user may well
want to silence one and not the other); a second *notification* is not.

For the defect: `endReaderSession` should record the reader's position even
when the block has already been concluded elsewhere. When `block == null` but
the position moved (`ReaderMath.sessionOutcome` would have saved), write the
progress and back-fill `pagesDelta` on the most recent `done` block for this
book **if it was concluded within the last few seconds** — otherwise leave it,
because an old block is not this sitting. If that heuristic feels too clever
when you get there, the acceptable alternative is to write the progress and
leave `pagesDelta` null; what is not acceptable is losing the page as well as
the delta.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Leitura` | `Reading` *(L5 may add this — reuse)* |
| `Terminar sessão` | `End session` |

**Out of scope:** a separate reading timer implementation, changing the block
schema.

**Accept:** a reading session's notification says reading, not focus;
concluding from the notification mid-read no longer loses the page; the planner's
focus notification is unchanged; CI green.

---

### L10 · A reminder to read — Status: pending

**Depends on:** nothing

**Why:** the app can remind you about habits (`HabitReminderScheduler`) and
about the day (`ReminderScheduler`), and book mode — whose whole subject is a
daily practice — has no reminder of its own. The scheduling machinery is built,
tested and boot-persistent; this is a third caller, not a new system.

**Files to touch:**
- `data/entity/Entities.kt` + `data/AppDatabase.kt` — two prefs columns (v13 → v14,
  or fold into L5's migration if that ships first and hasn't merged)
- `service/ReminderScheduler.kt` / `ReminderReceiver.kt` — a reading kind
- `ui/screens/SettingsScreen.kt` — a row in the reminders section, book mode only
- `i18n/I18n.kt`

**How:** `readingReminderEnabled: Boolean = false` and
`readingReminderTime: String = "21:00"`, scheduled through the existing
`AlarmManager` path, with the notification naming the book currently being read
when there is exactly one and staying generic otherwise. Nothing fires when
`bookMode` is off.

**Out of scope:** streak-warning notifications ("don't break the chain" is a
different app's idea of motivation), per-book reminders.

**Accept:** the reminder fires at the set time, survives a reboot, is silent in
planner mode and when disabled; CI green.

---

### L11 · The widget and the tile know about book mode — Status: pending

**Depends on:** nothing

**Why:** `MaresWidget` draws tides and `FocusTileService` starts a focus block,
in both modes. Someone using the book launcher icon has a home screen that
still talks about the planner.

**Files to touch:** `service/MaresWidget.kt`, `service/FocusTileService.kt`,
`i18n/I18n.kt`

**How:** both read `prefs.bookMode` and switch content — the widget to the
reading streak and this week's minutes (`domain/ReadingStats` already derives
both), the tile to "start a reading session" for the book being read. Neither
gains a second widget or a second tile: one of each, two faces, exactly like
the tabs.

**Out of scope:** a book-specific widget, a widget configuration screen.

**Accept:** with book mode on, the widget shows reading data and the tile starts
a reading session; with it off both are exactly as today; CI green.

---

### L12 · The review's leftovers — Status: pending

**Depends on:** everything above (do it last, or drop it)

**Why:** a small basket of things this review found that are each too small for
a task of their own. Do them together or not at all.

- **`ProgressEditor` accepts six digits** (`.take(6)`) then clamps to
  `bookProgressMax`. For a percentage that is 100, so typing `999999` silently
  becomes 100 with no feedback. Show the clamp, or cap the input length at the
  maximum's digit count.
- **`BookDoneCard` shows a rating but no finish date.** "Lidos" is sorted by
  `finishedAt` and never shows it; a quiet year on the card would make the
  shelf legible at a glance.
- **The detail sheet's ETA assumes 60 minutes of reading a day**
  (`BookMath.etaDays`'s default). It is a reasonable constant but it is
  invisible — either say it ("a 1 h/dia") or derive it from the last few weeks
  of sessions, which `ReadingStats.minutesLastDays` already computes.
- **`QuoteCaptureSheet`'s empty state says "adiciona um na Estante"** while
  being reachable only *from* the Estante. Point it at the add action instead.
- **A book with `totalPages == 0` and no file gets no progress bar** and no ETA
  — correct, but the detail sheet never suggests filling the length in, which
  is the one thing that would fix it.

**Accept:** whichever of these are done are done properly and the rest are
struck from this list with a reason in the Log; CI green.

---

## Task dependency graph

```
L1 (the wipe wipes)            ─┐
L2 (the library in a backup)   ─┼─ Phase L-0: do these first, in this order
L3 (the five statuses)         ─┘

L4 (contents + go-to-page)     ─┐
L5 (reading settings)          ─┼─ independent of each other and of L-0
L6 (capture from the book)     ─┘   (whichever of L4/L5/L6 ships first owns
                                     the reader's new chrome control row)

L7 (genre) ── L8 (shelf search + sort)  ── needs L3's paused shelf

L9 (reading ≠ focus)  ·  L10 (reminder)  ·  L11 (widget + tile)  ·  L12 (leftovers)
```

Minimum shippable slice: **L1 → L2 → L3**. At that point the app keeps the
promises it already made, which is worth more than any feature below it.
The most-missed *feature* is **L5**, and the cheapest good one is **L4** —
its data is already parsed and thrown away.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-08-03 · L1 · #n · resetAll now clears books, notes and filesDir/books -->
2026-08-02 · L1 · #181 · resetAll now clears book_notes, books and filesDir/books/ via BookFiles.clearAll; reseed inherits the fix
2026-08-02 · L2 · #182 · snapshot()/importJson() filter book blocks both ways (rule now single-sourced in BookBackup); new pauta.books.v1 export/import merges by id, no filePath/fileKind, two rows in Settings → Dados (book mode only)
2026-08-06 · L8 · #187 · `domain/BookShelf` is the whole of it — search, sort and the carousel threshold, pure and covered by 15 cases including a hundred-book library, because a shelf that stops working at scale is not a thing to find out on a device. Accent- and case-insensitive over title, author, series and genre, with title matches above author above the rest and a **prefix** match above a match in the middle; ties break by title so results never shuffle between keystrokes. No fuzzy matching: a substring match over four fields is enough for a personal library and it is *explicable*, and a result you cannot explain is a result you cannot trust. **It reuses L7's `genreTags`** rather than splitting the string again — which is why L7 put it in `domain/`. Two judgements in the sort: `Recentes` falls back from `finishedAt` to `createdAt`, because a book with no finish date is not undated; and an **unrated book sorts last under `Classificação` rather than as a zero** — unrated is not bad. The search state is screen state, deliberately not in the ViewModel. The four flows are combined once. "Lidos" stops being a `LazyRow` past twelve — about a year of ordinary reading, the point at which sideways stops being a gesture — and becomes rows that scroll the way the page already does. A result row says which shelf its book is on, because when you are searching that *is* the answer; `BookStatus.label` lives beside the statuses so a sixth one cannot be added without someone seeing it needs a name. An empty result is the shared `EmptyState`. · **Verified:** the pure half thoroughly. **The screen not at all** — no SDK here, and "the empty-query shelf is pixel-identical to today's" is an argument from the diff (the four sections' code is untouched; only "Lidos" gained a sort and a threshold) rather than something anyone has looked at. **Kept, not dropped** — and the choice is worth recording because the task allowed either. Dropping it would have meant a column that stays behind forever plus a form that quietly stopped collecting something people had already filled in; keeping it costs one `split` and makes an existing field honest. `BookMath.genreTags` is that split, pure and tested (`"ficção, ensaio"`, `"ficção,ensaio"`, `" "`, `""`, `",,, "`), and **L8's filter will reuse it** rather than splitting the string a second time — which was the reason to put it in `domain/` rather than inline in the sheet. Rendered on the detail sheet beside the format, sharing one `MetaChip` so a tag never reads as a different kind of thing from the format next to it, in a `FlowRow` so five tags wrap instead of pushing the sheet sideways. No new column and no migration. · **Verified:** the split is covered. The chips have not been drawn — no SDK here, nothing ran locally. `QuoteCaptureSheet` gains `bookId` and `atPage`; three callers now, and the shelf header's is byte-for-byte what it was. With a target the sheet drops the picker *and* the `status == "reading"` filter — the caller has already chosen, and that filter is exactly why a note on a book you had just finished had nowhere to go. **The thing to get right, and it was verified by reading rather than assumed:** the reader's `✎` composes the sheet *over* the reader, so the reader stays composed, its `onDispose` does not run and the reading session keeps going. A navigation destination would have ended the session every time someone wrote a line down, which is the failure this task exists to avoid. The position passed in is `state.unit`, which is already the unit the book counts in — a percentage point for an EPUB, exactly as `currentPage` is — and the detail sheet's note list now renders it that way too, reusing `bookProgressMark`/`countsPercent` rather than writing a second branch: calling a percentage "p. 43" would be the second time the app had to learn R4's lesson. · **Verified:** nothing was run. No SDK; the "session is not interrupted" property is an argument from how the sheet is composed, not an observation, and the honest test for it is a device. Four prefs columns, Room **12 → 13** (the 11 → 12 slot this task had claimed went to `FIRST_RUN.md` N1, which shipped first — recorded here and in `DATA_MODEL.md` when N1 landed). Steppers rather than sliders, and the `Aa` control joins the chrome row F5(a) opened rather than adding a bar. **Three things a later session should not re-derive.** The stylesheet is baked into the document at fetch time, so `css` had to become a **key** of the chapter-loading effect — without it the sheet would write a new stylesheet nobody ever loaded and appear to do nothing. Because the chapter then reloads, a size change would throw the reader's place away, so `reader` changing sets `restoreScroll` to where they are and re-`mark()`s — the bookmark and the percentage stay truthful across a nudge. And the parameter is named `reader`, not `settings`, because `WebView.settings` is an implicit receiver a few lines below and two things called the same thing in one function is how a lockdown line silently stops applying. `readerTextScale` **replaces** the density's font scale for the body only; the app-wide `textScale` still governs the reader's chrome, as everywhere else. A rendered PDF page is never recoloured — `night` dims the surround and leaves the page as the document drew it. No face picker, and the reason is in the code: the CSP forbids `font-src`, so it would have one entry. · **Verified:** nothing. No SDK here, so no compile or test run locally, and **the migration has never been executed** — this repo has no instrumentation tests and a JVM test cannot open a Room database. Nobody has seen a stepper move, a theme change, or the scroll survive one. `EpubInfo` carries `chapterTitles` and `KEY_TITLES` puts them on the wire — the names were always parsed (`Epub.EpubChapter.title`) and dropped at the `:reader` boundary, which is the whole reason the chrome could only count. The three lists' lengths are validated on arrival and a mismatch is refused as a corrupt reply, not treated as a book missing its names; titles absent entirely are fine, so an older reply still opens. **The jump does not write `state.position`.** The shell owns the chrome and the half owns the position, so `☰` sets a request on `ReaderState` and whichever half is mounted answers — the EPUB through its own `turn`, the PDF through `scrollToItem` — which is what keeps the bookmark, the label and the session following a jump. Writing the position from a sheet would bypass all three. The list opens scrolled to the current chapter; a title is untrusted text and renders as a Compose `Text` at two lines, never as HTML. The PDF half is a clamped go-to-page, since `PdfRenderer` cannot read outlines and inventing one would be inventing a number. **Worth knowing:** OPF *manifest* items rarely carry a `title` attribute, so in practice many books will fall back to "Capítulo {n}" — that is the designed fallback, and reading the `nav`/`ncx` document is explicitly out of scope (another XML parser is another attack surface). The chrome now reads "43% · Capítulo 7 · As Cidades e os Mortos" where a name exists and keeps "de 31" where it doesn't. · **Verified:** `EpubInfoTest` covers the shape titles arrive in. **Nothing else** — no SDK here, the wire itself needs a Bundle and therefore a device, and no book has been opened to see whether its OPF names anything.
2026-08-05 · L3 · #186 · new domain/BookStatus single-sources the five statuses + which shelf shows each (BookStatusTest asserts total cover, BookBackup reuses it); repo setBookStatus owns startedAt/finishedAt/position on every move (finishBook goes through it); the detail sheet reaches all five and reverses them, "Abandonar" arms in two steps; EM PAUSA shelf section; the reader, Sessão and Hábitos lookups now search all four shelves; position appends after the shelf maximum, not its size, so pause-then-resume no longer ties with the last book
