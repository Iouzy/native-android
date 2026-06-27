# Book mode — implementation task file

> **Concept.** When `prefs.bookMode` is `true`, the app's three tabs transform
> into a focused reading companion: **Hoje → Estante** (library shelf: TBR /
> reading now / finished), **Pauta → Sessão** (reading-session timer), and
> **Marés → Hábitos** (reading habits + annual book goal). The toggle is fully
> reversible — planner data (intentions, focus blocks, habits) is untouched and
> restored the moment book mode is off. Book-mode data (books, notes) persists
> independently and is never shown in planner mode.
>
> The feature ships across 9 self-contained tasks (K1–K9) plus one optional
> extra. Each task is one PR. Tasks within a phase are independent of each other
> unless "Depends on:" says otherwise.

> **How to use (human).** In a fresh Claude Code session, prompt:
>
> > Read `docs/BOOK_MODE.md`. Do ONLY task **K1** — follow its spec and the
> > Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR → CI →
> > squash-merge), then set the task's Status and append one line to the Log.
>
> Or stateless: *"Read `docs/BOOK_MODE.md` and do the first task whose Status
> is `pending`."* One task per session keeps context small and PRs reviewable.
>
> **How to use (Claude).** This file + `CLAUDE.md` are your complete briefing.
> Don't re-survey the codebase beyond the files each task names. The Data model
> section below is the ground truth for all new entities — don't re-derive it.
> Always update Status + Log in the same PR as the code.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Global guardrails (every task)

All guardrails from `docs/NATIVE_IMPROVEMENTS.md` apply unchanged. These are
additional constraints specific to book mode:

- **Book mode is a lens, not a fork.** When `bookMode` is false, every tab must
  behave exactly as it does today. The planner and the library never share
  tables or flows.
- **Three tabs, fixed.** The tab count stays at 3. All book-mode UI lives inside
  the existing slots — don't add a 4th tab or a 4th NavHost destination.
- **Native-only, not exported.** All book entities and the `bookMode`/
  `bookAnnualGoal` prefs columns are device-local. They must **not** appear in
  the `pauta.v4` export and must be explicitly excluded from `WebBackup.kt`.
  Mark every new pref field and entity with `// native-only`.
- **Reading sessions reuse the existing focus-block tables.** A reading session
  is a `FocusBlockEntity` with `project = "book:<bookId>"`. This gives the
  timer, session history and backup round-trip for free. To keep the normal
  Pauta tab clean, the `AppViewModel.blocks` flow (used by normal Pauta) must
  filter out `project LIKE 'book:%'` blocks. Book blocks have their own
  ViewModel flow (`bookSessionBlocks`).
- **Format-aware throughout.** `BookEntity.format` is `physical`, `ebook`, or
  `audiobook`. Audiobooks track **minutes** (not pages) in `totalPages` /
  `currentPage`. Every progress UI, every conclude prompt, and every stat must
  branch on format. Physical and ebook use "página"; audiobook uses "minuto".
- **No new external dependencies** beyond the androidx artifacts already present.

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Suggested model per task

| Model | Tasks |
|---|---|
| **Opus 4.8** | K1, K2, K5, K6, K7, K8, K9, K-extra |
| **Sonnet 4.6** | K3, K4 |

---

## Data model at a glance

**Read this before implementing any task.** These are the only new persistent
structures introduced by book mode. All other tasks reference this table rather
than re-specifying the shape.

### `BookEntity` (table: `books`)

| Column | Type | Default | Notes |
|---|---|---|---|
| `id` | String PK | — | prefix `bk_` + UUID |
| `title` | String | — | required |
| `author` | String | `""` | |
| `series` | String | `""` | |
| `seriesNumber` | Int? | null | null = standalone |
| `format` | String | `"physical"` | `physical` / `ebook` / `audiobook` |
| `totalPages` | Int | `0` | 0 = unknown; for audiobooks = totalMinutes |
| `currentPage` | Int | `0` | for audiobooks = currentMinute |
| `status` | String | `"tbr"` | `tbr` / `reading` / `done` / `dnf` / `paused` |
| `startedAt` | Long? | null | ms epoch; null until first session |
| `finishedAt` | Long? | null | ms epoch; null until done/dnf |
| `rating` | Int? | null | 1–5; null = unrated |
| `genre` | String | `""` | free text (comma-separated tags) |
| `position` | Int | `0` | ordering within status shelf |
| `createdAt` | Long | — | ms epoch |

Progress % = `currentPage.toFloat() / totalPages.coerceAtLeast(1)`.

### `BookNoteEntity` (table: `book_notes`)

| Column | Type | Default | Notes |
|---|---|---|---|
| `id` | String PK | — | prefix `bn_` + UUID |
| `bookId` | String | — | FK → `books.id` (not enforced, follows web pattern) |
| `kind` | String | `"annotation"` | `quote` / `annotation` / `thought` |
| `text` | String | — | required |
| `page` | Int? | null | null = page not recorded; unused for audiobooks |
| `createdAt` | Long | — | ms epoch |

### `PrefsEntity` additions (part of Room v7 → v8 migration)

| Column | Type | Default | Notes |
|---|---|---|---|
| `bookMode` | Boolean | `false` | `// native-only` |
| `bookAnnualGoal` | Int | `0` | 0 = no goal set; `// native-only` |

### Reading sessions (no new tables)

A reading session is a `FocusBlockEntity` where `project = "book:<bookId>"`.
The `title` field holds the book title (for display in normal block history).
`linkedToId` stays null. Session notes go in `FocusBlockEntity.reflection`
(set at conclude time). `targetMs` works normally (optional reading target).

The `AppViewModel` must expose two separate block flows:
- `blocks` (existing) — filtered to **exclude** `project LIKE 'book:%'` so
  normal Pauta stays clean.
- `bookSessionBlocks` (new) — only blocks where `project LIKE 'book:%'`.

This filtering change is part of **K2** and is the only modification to
existing ViewModel/DAO behaviour.

---

## Phase K-0 — foundation

### K1 · Data layer: entities, DAOs, migration — Status: done (PR #134)

**Depends on:** nothing (first task; everything else depends on this)

**Why:** the new Room tables and the two pref columns must exist before any
UI or repository work can start.

**Files to touch:**
- `data/entity/Entities.kt` — append `BookEntity`, `BookNoteEntity`; add
  `bookMode` + `bookAnnualGoal` to `PrefsEntity`
- `data/dao/Daos.kt` — append `BookDao`, `BookNoteDao`
- `data/AppDatabase.kt` — add both entity classes to `@Database(entities=[…])`,
  bump `version` to `8`, add `MIGRATION_7_8`, register it in `addMigrations(…)`

**`MIGRATION_7_8`** must execute exactly these statements:

```sql
ALTER TABLE prefs ADD COLUMN bookMode INTEGER NOT NULL DEFAULT 0;
ALTER TABLE prefs ADD COLUMN bookAnnualGoal INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS books (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    author TEXT NOT NULL DEFAULT '',
    series TEXT NOT NULL DEFAULT '',
    seriesNumber INTEGER,
    format TEXT NOT NULL DEFAULT 'physical',
    totalPages INTEGER NOT NULL DEFAULT 0,
    currentPage INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'tbr',
    startedAt INTEGER,
    finishedAt INTEGER,
    rating INTEGER,
    genre TEXT NOT NULL DEFAULT '',
    position INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS book_notes (
    id TEXT PRIMARY KEY NOT NULL,
    bookId TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'annotation',
    text TEXT NOT NULL,
    page INTEGER,
    createdAt INTEGER NOT NULL
);
```

**`BookDao`** — minimum required methods:

```kotlin
@Upsert suspend fun upsert(book: BookEntity)
@Query("DELETE FROM books WHERE id = :id") suspend fun deleteById(id: String)
@Query("SELECT * FROM books WHERE status = :status ORDER BY position")
    fun observeByStatus(status: String): Flow<List<BookEntity>>
@Query("SELECT * FROM books WHERE id = :id") suspend fun getById(id: String): BookEntity?
@Query("SELECT COUNT(*) FROM books WHERE status = 'done' AND finishedAt >= :fromMs")
    suspend fun countFinishedSince(fromMs: Long): Int
@Query("SELECT * FROM books") suspend fun getAll(): List<BookEntity>
@Query("DELETE FROM books") suspend fun clear()
```

**`BookNoteDao`** — minimum required methods:

```kotlin
@Insert suspend fun insert(note: BookNoteEntity): Long
@Query("DELETE FROM book_notes WHERE id = :id") suspend fun deleteById(id: String)
@Query("SELECT * FROM book_notes WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookNoteEntity>>
@Query("SELECT * FROM book_notes") suspend fun getAll(): List<BookNoteEntity>
@Query("DELETE FROM book_notes") suspend fun clear()
```

**Out of scope:** no repository methods, no ViewModel, no UI.

**Backup note:** `BookEntity`, `BookNoteEntity`, `bookMode`, `bookAnnualGoal`
must not appear anywhere in `WebBackup.kt`. The existing `exportJson` /
`importJson` and their tests must pass unchanged.

**Accept:**
- `./gradlew :app:compileDebugKotlin` — zero errors
- `./gradlew :app:testDebugUnitTest` — all green (WebBackup tests unaffected)

---

## Phase K-1 — wiring (K2 and K3 are independent; do either first after K1)

### K2 · Repository + ViewModel wiring — Status: done (PR #135)

**Depends on:** K1

**Why:** the data layer exists; now the repository and ViewModel need to expose
it so K3–K9 can be built against a stable API without touching data code again.

**Files to touch:**
- `data/PautaRepository.kt`
- `ui/viewmodel/AppViewModel.kt`
- `PautaApplication.kt` — inject `BookDao` + `BookNoteDao` into the repo

**Repository — add these methods to `PautaRepository`:**

```kotlin
// Books
fun booksReading(): Flow<List<BookEntity>>   // observeByStatus("reading")
fun booksTbr(): Flow<List<BookEntity>>        // observeByStatus("tbr")
fun booksDone(): Flow<List<BookEntity>>       // observeByStatus("done") + "dnf" ordered by finishedAt DESC
fun getBook(id: String): BookEntity?          // suspend, snapshot
suspend fun addBook(
    title: String, author: String, series: String, seriesNumber: Int?,
    format: String, totalPages: Int, genre: String, status: String,
): String  // returns the new id ("bk_" + UUID)
suspend fun updateBook(book: BookEntity)
suspend fun deleteBook(id: String)
suspend fun updateProgress(id: String, currentPage: Int)
suspend fun finishBook(id: String, rating: Int?)  // status→"done", finishedAt=now
suspend fun booksFinishedThisYear(yearStartMs: Long): Int  // countFinishedSince

// Book notes
fun notesForBook(bookId: String): Flow<List<BookNoteEntity>>
suspend fun addNote(bookId: String, kind: String, text: String, page: Int?): String
suspend fun deleteNote(id: String)
```

**ViewModel — add to `AppViewModel`:**

```kotlin
// Book mode prefs
val bookMode: StateFlow<Boolean>             // prefs.map { it.bookMode }
val bookAnnualGoal: StateFlow<Int>           // prefs.map { it.bookAnnualGoal }
fun setBookMode(on: Boolean)                 // repo.updatePrefs { it.copy(bookMode = on) }
fun setAnnualGoal(n: Int)                    // repo.updatePrefs { it.copy(bookAnnualGoal = n) }

// Book state
val booksReading: StateFlow<List<BookEntity>>
val booksTbr: StateFlow<List<BookEntity>>
val booksDone: StateFlow<List<BookEntity>>
val bookSessionBlocks: StateFlow<List<FocusBlockEntity>>  // project LIKE 'book:%'

// Book actions (thin delegates to repo)
fun addBook(title, author, series, seriesNumber, format, totalPages, genre, status)
fun updateBook(book: BookEntity)
fun deleteBook(id: String)
fun updateProgress(id: String, currentPage: Int)
fun finishBook(id: String, rating: Int?)
fun addNote(bookId, kind, text, page)
fun deleteNote(id: String)
fun booksFinishedThisYear(): Int  // blocking snapshot, call from coroutine scope
```

**Critical — modify `blocks` flow:**
The existing `AppViewModel.blocks` flow (used by `PautaScreen`) must be
filtered to **exclude** reading-session blocks:

```kotlin
// before: repo.blocks() or similar
// after:
val blocks = repo.blocks()
    .map { list -> list.filter { it.project?.startsWith("book:") != true } }
    .stateIn(…)
```

This is the only change to existing ViewModel behaviour. Existing tests must
still pass.

**Out of scope:** no UI, no Settings toggle.

**Accept:** `compileDebugKotlin` green; unit tests green; existing
`AppViewModel.blocks` no longer includes reading-session blocks.

### K3 · Settings toggle + tab label remapping — Status: done (PR #136)

**Depends on:** K2

**Why:** the mode switch is the entry point for the whole feature. It must
ship and be testable — even before any tab content changes — so the toggle
itself can be verified in isolation.

**Files to touch:**
- `ui/screens/SettingsScreen.kt` — add toggle in the Appearance section
- `ui/MainScaffold.kt` — read `prefs.bookMode`, remap tab labels in `TabBar`
- `i18n/I18n.kt` — new strings

**Settings:** add a `PautaToggleRow` (matching existing toggle rows) in the
Appearance section, after the theme/accent rows:

| PT (key) | EN (value) |
|---|---|
| `Modo livro` | `Book mode` |
| `Transforma as três tabs numa companheira de leitura` | `Turns the three tabs into a reading companion` |

Wire: `checked = prefs.bookMode`, `onCheckedChange = { vm.setBookMode(it) }`.

**Tab label remapping in `TabBar`:** pass `bookMode: Boolean` down from
`HomeShell` to `TabBar`. When `bookMode` is true, substitute labels:

| Tab | Normal | Book mode |
|---|---|---|
| HOJE | `tr("Hoje")` | `tr("Estante")` |
| PAUTA | `tr("Pauta")` | `tr("Sessão")` |
| MARES | `tr("Marés")` | `tr("Hábitos")` |

Icons stay the same (icon remapping is optional; not in scope here).

New strings (`// native-only`):

| PT | EN |
|---|---|
| `Estante` | `Shelf` |
| `Sessão` | `Session` |
| `Hábitos` | `Habits` |

**Out of scope:** actual tab content changes (K5–K7), sepia theme (K4).

**Accept:** toggle appears in Settings → Aparência; turning it on changes the
three tab labels; turning it off restores them; tab content is unchanged;
CI green.

---

## Phase K-2 — theme (independent; can run any time after K3)

### K4 · Sepia/parchment theme — Status: done (PR #137)

**Depends on:** K3 (toggle must exist to test; needs `bookMode` in the scaffold)

**Why:** book mode should feel visually distinct — warm parchment, not the
cool-neutral cream. The change must be invisible when book mode is off.

**Files to touch:**
- `ui/theme/Color.kt` — add `bookPautaColors(dark: Boolean): PautaColors`
- `ui/MainScaffold.kt` — wrap the `NavHost` with sepia colors when `bookMode`

**`bookPautaColors(dark: Boolean)`** returns a `PautaColors` where:

| Token | Light-sepia value | Dark-sepia value |
|---|---|---|
| `paper` | `#F2E8D5` (parchment) | `#28190F` |
| `paper2` | `#E8D9BC` | `#332010` |
| `ink` | `#2C1A0E` (dark sepia) | `#EBD9C0` |
| `ink2` | `#5C3A1E` | `#C8A882` |
| `ink3` | `#8C6540` | `#9A7855` |
| `ink4` | `#B89870` | `#6B5038` |
| `rule` | `#D4B896` | `#4A3020` |
| `tabbarBg` | same as `paper` | same as `paper` |
| `pageBg` | same as `paper` | same as `paper` |

All other tokens (`accent`, `accentSoft`, `accentBg`, `good`, `surfaceDark`,
`onDark`, `onDark2`) stay identical to the base palette (pass them through
from the current `LocalPautaColors`).

**In `MainScaffold`**, just before the `NavHost`:

```kotlin
val baseColors = LocalPautaColors.current
val effectiveColors = if (prefs.bookMode)
    bookPautaColors(dark = baseColors.isDark)
else
    baseColors

CompositionLocalProvider(LocalPautaColors provides effectiveColors) {
    NavHost(…)
}
```

**Out of scope:** no font changes, no new composables, no icon changes.

**Accept:** book mode on + light system theme → parchment everywhere; book
mode on + dark system theme → dark sepia; book mode off → colours identical to
today; no flicker on toggle; CI green.

---

## Phase K-3 — tab screens (K5, K6, K7 are independent; all depend on K2 + K3)

### K5 · Hoje → library shelf — Status: done (PR #138)

**Depends on:** K2, K3

**Why:** the first tab in book mode is the personal library — a quick overview
of what's being read, what's queued, and what's been finished.

**Files to touch / create:**
- `ui/screens/HojeScreen.kt` — add `if (bookMode) BookShelfScreen() else <existing>`
  at the top level; pass `bookMode: Boolean` in from `HomeShell`
- `ui/screens/BookShelfScreen.kt` (new file)
- `ui/screens/BookFormSheet.kt` (new file — add + edit book form)
- `i18n/I18n.kt` — new strings

**`BookShelfScreen`** is a `LazyColumn` with three sections:

**Section 1 — A ler agora (status = "reading"):**
Horizontal `LazyRow` of `BookProgressCard` composables. Each card:
- Title (SerifFamily, ~16sp)
- Author (MonoFamily, ~11sp, ink3)
- Progress bar (full-width, accent fill, same visual weight as the tide-fill
  cells) showing `currentPage / totalPages`. If `totalPages == 0`, show
  `"p. $currentPage"` with no bar.
- `"$currentPage / $totalPages p."` or `"$currentPage / $totalPages min."` for
  audiobooks (MonoFamily, 10sp, ink3)
- Tapping → opens `BookDetailSheet` (K8; wire as a no-op stub `TODO()` for now)

Empty state: quiet ink4 Mono text `"Nenhum livro em curso"`.

**Section 2 — A seguir (status = "tbr"):**
Vertical list. Each row: title (SerifFamily) + author (Mono ink3). Tapping →
`BookDetailSheet` stub.

**Section 3 — Lidos (status = "done" or "dnf"):**
Horizontal `LazyRow`, most recent first (`finishedAt DESC`). Each card: title +
star rating display (filled / empty stars, if rated; nothing if null).
Tapping → `BookDetailSheet` stub.

**Add book action:** an `"Adicionar livro +"` text button (MonoFamily, 11sp,
accent colour) at the top of the shelf, right-aligned (echoes the `+` chips
elsewhere in the app). Tapping opens `BookFormSheet`.

**`BookFormSheet`** (used for both add and edit; `book: BookEntity? = null`):
- Campo: título (required, SerifFamily TextField, autofocus)
- Campo: autor
- Campo: série + nº (side by side; nº is optional Int)
- Selector: formato (3 chips: Físico / Ebook / Audiolivro)
- Campo: total de páginas / minutos (optional; Int; label changes with format)
- Campo: género (optional free text)
- Selector: estado (2 chips: A ler / A seguir — only on add; edit can change
  via the detail sheet)
- On confirm (add): `vm.addBook(…)`
- On confirm (edit): `vm.updateBook(book.copy(…))`
- On delete (edit only): 2-step confirm → `vm.deleteBook(id)`

Keyboard: autofocus título on open; IME Next chains fields; Done on last field
submits.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `A ler agora` | `Reading now` |
| `A seguir` | `Up next` |
| `Lidos` | `Finished` |
| `Nenhum livro em curso` | `No books in progress` |
| `Adicionar livro` | `Add book` |
| `Título` | `Title` |
| `Autor` | `Author` |
| `Série` | `Series` |
| `Nº na série` | `Series no.` |
| `Físico` | `Physical` |
| `Ebook` | `Ebook` |
| `Audiolivro` | `Audiobook` |
| `Total de páginas` | `Total pages` |
| `Total de minutos` | `Total minutes` |
| `Género` | `Genre` |
| `A ler` | `Reading` |

**Out of scope:** book detail sheet (K8 wires it up), drag-to-reorder TBR,
book search/lookup, K8 functionality behind the stub tap.

**Accept:** all three shelf sections render live DB data; add book flow saves
and the card appears immediately; format label adapts (pages vs minutes);
normal HojeScreen is pixel-identical when `bookMode` is false; CI green.

### K6 · Pauta → reading session — Status: done (PR #139)

**Depends on:** K2, K3 (and K5 should be done first so books exist to test with)

**Why:** the timer is the app's core loop. In book mode it becomes a
reading-session timer linked to a specific book, with a page-update prompt on
conclude.

**Files to touch / create:**
- `ui/screens/PautaScreen.kt` — add `if (bookMode) BookSessionScreen() else <existing>`
- `ui/screens/BookSessionScreen.kt` (new file)
- `i18n/I18n.kt` — new strings

**`BookSessionScreen`:**

*No active reading block:*
A "Iniciar sessão de leitura" card:
- Book selector: a `PautaSheet`-backed picker listing `booksReading`. If exactly
  one book is reading, pre-select it (show its title, tap to change). If none,
  show `"Adiciona um livro na Estante primeiro"` and disable the start button.
- Optional target: same duration input as the existing Start block sheet
  (minutes → `targetMs`).
- `Começar` button → `vm.startBlock(title = book.title, linkedToId = null, project = "book:${book.id}", targetMs = …)`

Below the card: a compact session history — focus blocks where `project LIKE 'book:%'`,
grouped by book title, showing date + duration (MonoFamily, ink3). Uses
`vm.bookSessionBlocks`.

*Active reading block (project starts with "book:"):*
Show the running timer using the existing `ActiveBlockCard` or a close
equivalent, with the book title displayed above the timer. Pause/resume work
exactly as today. Conclude button opens `BookConcludeSheet`.

**`BookConcludeSheet`:**
- Prompt: `"Até que página chegaste?"` (physical/ebook) or `"Quantos minutos ouviste?"` (audiobook)
- Number input, pre-filled with `book.currentPage`. MonoFamily keyboard.
- Optional: `"Nota da sessão"` multiline TextField (SerifFamily).
- Confirm: `vm.updateProgress(bookId, newPage)` → `vm.concludeActive()` with
  the note saved to the block's reflection field.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Iniciar sessão de leitura` | `Start reading session` |
| `Começar` | `Start` |
| `Até que página chegaste?` | `What page did you reach?` |
| `Quantos minutos ouviste?` | `How many minutes did you listen?` |
| `Nota da sessão` | `Session note` |
| `Sessões de leitura` | `Reading sessions` |
| `Nenhuma sessão ainda` | `No sessions yet` |
| `Adiciona um livro na Estante primeiro` | `Add a book on the Shelf first` |

**Out of scope:** pace/ETA stats (K-extra), session history in book detail (K8).

**Accept:** full session cycle (start → timer runs → conclude → page updated +
note saved); `project = "book:<id>"` set on the block; normal Pauta is
unchanged when `bookMode` is false; existing `vm.blocks` flow excludes reading
blocks (verified by checking the normal Pauta tab shows nothing new); CI green.

### K7 · Marés → reading habits + annual goal — Status: pending

**Depends on:** K2, K3

**Why:** the third tab in book mode anchors the habit layer of reading and the
annual goal — consistency over time, not just per-session tracking.

**Files to touch / create:**
- `ui/screens/MaresScreen.kt` — add `if (bookMode) BookHabitsScreen() else <existing>`
- `ui/screens/BookHabitsScreen.kt` (new file)
- `i18n/I18n.kt` — new strings

**`BookHabitsScreen`** is a `LazyColumn` with two sections:

**Section 1 — Objetivo anual (Annual goal):**
A card at the top of the screen.

- If `prefs.bookAnnualGoal == 0`: show `"N livros este ano"` (where N =
  `booksFinishedThisYear()`) with a quiet `"Definir objetivo →"` link (Mono,
  ink3) that opens `AnnualGoalSheet`.
- If goal is set: show `"N / M livros este ano"` + a progress bar (accent fill,
  same style as focus target) capped at 100%. A small edit icon lets them
  update the goal.

`AnnualGoalSheet`: single number input (`"Objetivo de livros por ano"`),
IME Done submits, saves via `vm.setAnnualGoal(n)`.

N is computed once on composition + on `books` state change via
`vm.booksFinishedThisYear()` (called from a `LaunchedEffect` into a local
`var booksThisYear by remember`).

**Section 2 — Hábitos de leitura:**
A section header `"HÁBITOS DE LEITURA"` (MonoFamily, 10sp, ink3, uppercase,
letterSpacing), followed by the **full normal `MaresScreen` content**,
embedded unchanged. No special reading-habit types are auto-created; the user
manages their own (e.g. "Ler 20 min por dia") with the existing habit engine.
This section reuses `MaresScreen`'s exact composables — extract them into a
shared internal composable if needed, but don't duplicate logic.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Objetivo anual` | `Annual goal` |
| `livros este ano` | `books this year` |
| `Definir objetivo` | `Set goal` |
| `Objetivo de livros por ano` | `Annual book goal` |
| `Hábitos de leitura` | `Reading habits` |

**Out of scope:** auto-creating reading habits, reading-streak tracking separate
from the existing habit system.

**Accept:** annual counter shows the correct count of books with `status = 'done'`
and `finishedAt ≥ Jan 1 of current year`; updating the goal persists and the
progress bar reflects it; the normal Marés content appears and functions fully
below the goal card; `bookMode` off → normal Marés identical to today; CI green.

---

## Phase K-4 — depth (K8 and K9 are independent of each other; both need K5)

### K8 · Book detail sheet — Status: pending

**Depends on:** K5 (shelf taps open this; needs books to exist)

**Why:** the shelf cards open here — it's where all book-level detail lives:
progress editing, rating, annotations and session history. This is also where
K5's `TODO()` stubs get replaced.

**Files to touch / create:**
- `ui/screens/BookDetailSheet.kt` (new file)
- `ui/screens/BookShelfScreen.kt` — replace `TODO()` stubs with real calls
- `i18n/I18n.kt` — new strings

**`BookDetailSheet(bookId: String, onDismiss: () -> Unit)`** — a
`PautaSheet`/`ModalBottomSheet` (follows the A5 pattern: bottom sheet on phones,
centred dialog ≥600dp). Observe `notesForBook(bookId)` and derive book from
`booksReading / booksTbr / booksDone` flows.

**Header block:**
- Title (SerifFamily, 20sp), author (MonoFamily, 11sp, ink3), series info if set
  (MonoFamily, 10sp, ink4: `"Série · Nº 3"`)
- Format chip (read-only, Mono ink3)
- **Progress editor:** tapping the progress display opens an inline number input
  (`"Página X de Y"` / `"Min X de Y"`). Confirm → `vm.updateProgress(id, n)`.
  A progress bar sits below (same accent style as tide cards).
- **Rating:** 5 star icons (tap to set 1–5; tap the current star to clear).
  Saves immediately via `vm.updateBook(book.copy(rating = n))`.
- **Status actions row:**
  - If `status == "reading"`: `"Marcar como lido"` PautaButton (accent, confirm
    sheet asking for rating before saving → `vm.finishBook(id, rating)`).
  - If `status == "tbr"`: `"Começar a ler"` → `vm.updateBook(book.copy(status = "reading", startedAt = now()))`.
  - `"Editar"` ghost button → opens `BookFormSheet` (from K5) in edit mode.
  - `"Eliminar"` ghost button (danger red) → 2-step confirm → `vm.deleteBook(id)` → dismiss.

**Notes section** (separated by a `rule`-coloured divider):
- Header `"NOTAS & CITAÇÕES"` (Mono, 10sp, uppercase, ink3)
- List of `BookNoteEntity` sorted by `createdAt DESC`. Each note:
  - Kind tag (Mono, 9sp, accent: `CITAÇÃO` / `ANOTAÇÃO` / `PENSAMENTO`)
  - Page if set: `"p. 42"` (Mono, 9sp, ink4) — hidden for audiobooks
  - Text (SerifFamily, 15sp, ink)
  - Long-press or swipe-to-dismiss → 2-step delete → `vm.deleteNote(id)`
- Empty state: `"Sem notas ainda"` (Mono, ink4)

**Sessions section:**
- Header `"SESSÕES"` (Mono, 10sp, uppercase, ink3)
- List from `vm.bookSessionBlocks` filtered to `project == "book:$bookId"`,
  sorted by `createdAt DESC`. Each row: date (Mono, 10sp, ink3) + duration in
  minutes (Mono, ink3) + reflection if non-blank (SerifFamily, italic, 13sp, ink3).
- Empty state: `"Nenhuma sessão ainda"`.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Notas & Citações` | `Notes & Quotes` |
| `Sem notas ainda` | `No notes yet` |
| `Sessões` | `Sessions` |
| `Nenhuma sessão ainda` | `No sessions yet` |
| `Marcar como lido` | `Mark as read` |
| `Começar a ler` | `Start reading` |
| `Editar` | `Edit` |
| `Eliminar livro` | `Delete book` |
| `CITAÇÃO` | `QUOTE` |
| `ANOTAÇÃO` | `ANNOTATION` |
| `PENSAMENTO` | `THOUGHT` |

**Out of scope:** sharing quotes as images, Kindle highlights import, pace/ETA
stats in the header (those are K-extra).

**Accept:** all sections display live data; progress edit, rating, status
changes, note delete, book edit/delete all work and persist; session history
shows the correct book's sessions; CI green.

### K9 · Quote & annotation capture — Status: pending

**Depends on:** K5 (needs books to exist for the book picker)

**Why:** quick capture is the highest-frequency action in book mode — jotting a
quote or thought while reading, without disrupting the flow.

**Files to touch:**
- `ui/MainScaffold.kt` — add the capture chip inside `HomeShell`, above the
  tab bar, visible only when `bookMode` is true
- `ui/screens/QuoteCaptureSheet.kt` (new file)
- `i18n/I18n.kt` — new strings

**Capture chip** in `HomeShell`: a small icon+text affordance (`PautaIcons`
pen or bookmark icon, or a Canvas-drawn pen matching the app icon style + `"+"`)
placed bottom-left, same vertical offset as Pip (bottom-right). It must:
- Only render when `bookMode` is true
- Use `clickableNoRipple`; no elevation, no FAB styling
- Respect `prefs.reducedMotion` (no entrance animation if true)
- Not interfere with Pip or the snackbar (Pip is bottom-right; chip is
  bottom-left; snackbar is centred above both)

Tapping opens **`QuoteCaptureSheet`:**
- **Tipo / Kind** chip selector: Citação · Anotação · Pensamento (default: Anotação)
- **Texto / Text** multiline TextField (SerifFamily, autofocus, IME fills remaining)
- **Página / Page** optional Int field (Mono, `"p."` prefix, hidden for audiobooks)
- **Livro / Book** selector:
  - If exactly 1 book is `status == "reading"`: show its title as a label, not a picker
  - If multiple: a compact picker of `booksReading`
  - If none: `"Sem livros em curso — adiciona um na Estante"` (ink3, sheet disabled)
- Confirm → `vm.addNote(bookId, kind, text, page)`; dismiss sheet

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Nova nota` | `New note` |
| `Citação` | `Quote` |
| `Anotação` | `Annotation` |
| `Pensamento` | `Thought` |
| `Página (opcional)` | `Page (optional)` |
| `Sem livros em curso — adiciona um na Estante` | `No books in progress — add one on the Shelf` |

**Out of scope:** note editing (add-only; delete via K8), image/photo capture.

**Accept:** chip appears on all 3 tabs only when `bookMode` is true; capture
saves correctly; note appears in K8 book detail for the selected book; no
layout interference with Pip or snackbar; CI green.

---

## Phase K-5 — extras (ship after K1–K9 are all done)

### K-extra · Pace stats + ETA — Status: pending

**Depends on:** K6 (needs sessions with page deltas), K8 (displayed in detail)

**Why:** "how fast am I reading, and when will I finish?" is the most useful
derived stat a reading tracker can show. Intentionally deferred until the core
loop (K1–K9) is proven.

**Files to create:**
- `domain/BookMath.kt` — pure functions, tested

**`BookMath`** functions:
```kotlin
/** Pages (or minutes) read per hour across the last N sessions. */
fun pagesPerHour(sessions: List<SessionSpan>): Float?  // null if < 2 data points

/** Estimated days to finish at the current pace. */
fun etaDays(remaining: Int, pagesPerHour: Float, dailyReadingMinutes: Float = 60f): Int?
```

`SessionSpan` is a value class `(pagesDelta: Int, durationMs: Long)`, derived
from consecutive `FocusSessionEntity` pairs for the same block (or across the
last 5 blocks for the same book).

**Display in `BookDetailSheet` header** (K8 must be touched):
Below the progress bar, add two lines (MonoFamily, 10sp, ink3):
- `"Ritmo: ~N págs/hora"` (or `"min/hora"` for audiobooks)
- `"Conclusão estimada: em ~N dias"` — hidden when `totalPages == 0` or `etaDays` returns null

**Unit tests** in `src/test/` covering edge cases: 0 sessions, 1 session, mixed
duration/delta, zero remaining pages.

**Accept:** `BookMath` unit tests green; ETA shows sensible values in K8 for a
book with ≥ 2 sessions; hides gracefully with insufficient data; CI green.

---

## Task dependency graph

```
K1 (data)
 └─ K2 (repo + VM)
     ├─ K3 (settings toggle + labels)
     │   └─ K4 (sepia theme)
     ├─ K5 (Hoje shelf)  ──────────────── K8 (book detail)
     │   └─ (books exist)                 └─ K-extra (pace stats)
     ├─ K6 (Pauta sessions)
     │   └─ K-extra (pace stats)
     └─ K7 (Marés + annual goal)

K9 (quote capture) depends on K5
```

Minimum shippable slice: **K1 → K2 → K3** (toggle works, labels change, no
book content yet). Each subsequent task adds one tab or one feature layer.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-06-25 · K1 · #n · data layer: books + book_notes tables, Room v7→v8, bookMode + bookAnnualGoal prefs -->
2026-06-25 · K1 · #134 · data layer: BookEntity/BookNoteEntity + BookDao/BookNoteDao, bookMode + bookAnnualGoal prefs columns, Room v7→v8 MIGRATION_7_8; all native-only, WebBackup/v4 untouched
2026-06-25 · K3 · #136 · "Modo livro" toggle in Settings → Aparência; TabBar remaps labels to Estante/Sessão/Hábitos when on, restores Hoje/Pauta/Marés when off; 5 native-only i18n strings
2026-06-25 · K2 · #135 · repo + VM wiring: PautaRepository book/note CRUD + booksReading/Tbr/Done (done merges done+dnf, finishedAt DESC) + booksFinishedThisYear; AppViewModel bookMode/bookAnnualGoal StateFlows + setters, three shelf flows, book actions, booksFinishedThisYear() (year-start ms); blocks flow now excludes project "book:%", new bookSessionBlocks flow holds them. DAOs derived in-repo from AppDatabase so no PautaApplication change. native-only, WebBackup/v4 untouched
2026-06-27 · K4 · #137 · bookPautaColors(dark) in Color.kt with parchment tokens (light paper #F2E8D5 / dark #28190F, ink/rule/tabbarBg/pageBg all sepia); NavHost in MainScaffold wrapped in CompositionLocalProvider — sepia when bookMode on, base palette when off; PIN/onboarding overlays outside provider, unaffected
2026-06-27 · K5 · #138 · Hoje→Estante shelf (BookShelfScreen): A ler agora (LazyRow progress cards, pages/min format-aware) · A seguir (list rows) · Lidos (LazyRow cards w/ ★ rating); "Adicionar livro +" opens BookFormSheet (add+edit, format/status chips, 2-step delete); HojeScreen gains bookMode param + early return, MainScaffold passes prefs.bookMode; book tap a no-op stub pending K8; 22 native-only i18n strings; planner view untouched when off
2026-06-27 · K6 · #139 · Pauta→Sessão reading timer (BookSessionScreen): start card with book picker (booksReading, pre-selects single) + optional duration → startBlock project "book:<id>"; live dark active card (timer, Pausar, Concluir); BookConcludeSheet asks page/min (format-aware) + session note → updateProgress + concludeActive (note→reflection); paused-session resume + history grouped by book from bookSessionBlocks; PautaScreen gains bookMode param + early return, MainScaffold passes prefs.bookMode; 8 native-only i18n strings; planner Pauta untouched when off
