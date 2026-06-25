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
- **Native-only, not in v4.** All book entities and the `bookMode`/
  `bookAnnualGoal` prefs columns are device-local. They must **not** appear in
  the `pauta.v4` export and must be explicitly excluded from `WebBackup.kt`.
  Mark every new pref field and entity with `// native-only`. Book data *is*
  backed up — but via its own `pauta-native.v1` companion file (**K10**), never
  by changing the v4 shape.
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
  This explicitly includes images: covers/photos are decoded and rendered with
  the platform `Bitmap`/`PdfRenderer` and a small in-house helper, **not** an
  image-loading library. (Coil would be the easy path; it's the documented
  opt-in alternative in K11 if the maintainer ever relaxes this — but the
  default is no-dep.)
- **Images: files on disk, paths in the DB, base64 in the native backup.**
  Picked/extracted images are downscaled and copied into app storage
  (`filesDir/book_covers/`, `filesDir/note_images/`); the DB stores only the
  filename (`coverPath`/`imagePath`). They are **device-local** like all book
  data, and are carried in the `pauta-native.v1` backup as base64 (**K10/K11**)
  — never in the v4 file. No internet is ever contacted for images (extracting
  a cover from an uploaded EPUB/PDF is fully offline; online cover lookup is
  out of scope — it would break the offline-first identity).

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Suggested model per task

| Model | Tasks |
|---|---|
| **Opus 4.8** | K1, K2, K5, K6, K7, K8, K9, K10, K11, K12, K-extra |
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
| `coverPath` | String? | null | filename under `filesDir/book_covers/`; set by **K11** (null = no cover) |
| `position` | Int | `0` | ordering within status shelf |
| `createdAt` | Long | — | ms epoch |

Progress % = `currentPage.toFloat() / totalPages.coerceAtLeast(1)`.

`coverPath` is added in the K1 schema now (nullable, default null) so the image
tasks (K11/K12) need no further migration — it just stays null until K11.

### `BookNoteEntity` (table: `book_notes`)

| Column | Type | Default | Notes |
|---|---|---|---|
| `id` | String PK | — | prefix `bn_` + UUID |
| `bookId` | String | — | FK → `books.id` (not enforced, follows web pattern) |
| `kind` | String | `"annotation"` | `quote` / `annotation` / `thought` |
| `text` | String | — | required (may be blank if `imagePath` is set) |
| `page` | Int? | null | null = page not recorded; unused for audiobooks |
| `imagePath` | String? | null | filename under `filesDir/note_images/`; set by **K12** (null = no photo) |
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
(set at conclude time). An optional reading target works normally: the entity
stores `targetMs` (Long), but you set it by passing `targetMin` (Int minutes)
to `startBlock` — see the Verified API surface table.

The `AppViewModel` must expose two separate block flows:
- `blocks` (existing) — filtered to **exclude** `project LIKE 'book:%'` so
  normal Pauta stays clean.
- `bookSessionBlocks` (new) — only blocks where `project LIKE 'book:%'`.

This filtering change is part of **K2**, along with the `resetAll()` clear
(see K2) — together these are the only modifications to existing
repository/ViewModel behaviour.

### Verified API surface (checked against code on 2026-06-25)

These signatures were read from the live tree; build against them rather than
re-deriving. Line numbers are approximate anchors, not guarantees.

| Symbol | Signature / fact | Location |
|---|---|---|
| `PautaColors` | data class, 18 fields incl. `paper3`, `onDark2`, `isDark` | `ui/theme/Color.kt:20` |
| `repo.startBlock` | `(title, linkedToId?, project?, targetMin: Int?)` → converts min→ms | `PautaRepository.kt:301` |
| `vm.startBlock` | same params; `targetMin` (NOT ms) | `AppViewModel.kt:188` |
| `vm.concludeActive` | `(reflection: String, markIntentionDone = false)` | `AppViewModel.kt:195` |
| `vm.blocks` | `StateFlow`, `SharingStarted.Eagerly` | `AppViewModel.kt:171` |
| `FocusBlockEntity.project` | `String? = null`; trimmed/null'd on insert | `Entities.kt:51` |
| `repo.resetAll` | clears each table by hand (add book clears here) | `PautaRepository.kt:754` |
| `repo.importJson` | clears tables then `prefsDao.upsert(s.prefs)` | `PautaRepository.kt:764` |
| `parsePrefs` | named-arg `PrefsEntity(...)`; new defaulted fields are safe | `WebBackup.kt:380` |
| Room version | currently **7**; book mode bumps to **8** | `AppDatabase.kt:58` |
| `minSdk` / `compileSdk` | **26** / 35 — `PdfRenderer` (API 21) + photo picker OK | `app/build.gradle.kts` |
| `activity-compose` | `1.9.2` present → `PickVisualMedia` / `OpenDocument` contracts, no new dep | `app/build.gradle.kts` |
| `PdfRenderer` | `android.graphics.pdf`, built-in, renders page→Bitmap (K11) | platform |
| EPUB parse | `java.util.zip.ZipFile` + `XmlPullParser`, built-in (K11) | platform |

---

## Phase K-0 — foundation

### K1 · Data layer: entities, DAOs, migration — Status: pending

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
    coverPath TEXT,
    position INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS book_notes (
    id TEXT PRIMARY KEY NOT NULL,
    bookId TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'annotation',
    text TEXT NOT NULL,
    page INTEGER,
    imagePath TEXT,
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

**`BookNoteDao`** — minimum required methods (string PK ⇒ `@Upsert`, not an
autogen `@Insert`; the repo generates the `bn_` id):

```kotlin
@Upsert suspend fun upsert(note: BookNoteEntity)
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

### K2 · Repository + ViewModel wiring — Status: pending

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
filtered to **exclude** reading-session blocks. The real declaration is at
`AppViewModel.kt:171` — note it uses `SharingStarted.Eagerly`, keep that:

```kotlin
// AppViewModel.kt:171 — current:
val blocks: StateFlow<List<FocusBlockEntity>> =
    repo.blocks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

// change to (add a .map before .stateIn):
val blocks: StateFlow<List<FocusBlockEntity>> =
    repo.blocks()
        .map { list -> list.filterNot { it.project?.startsWith("book:") == true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

// and add a sibling flow for the book-session screen (K6):
val bookSessionBlocks: StateFlow<List<FocusBlockEntity>> =
    repo.blocks()
        .map { list -> list.filter { it.project?.startsWith("book:") == true } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

This is the only change to existing ViewModel behaviour. Existing tests must
still pass.

**Critical — reset vs import asymmetry (`PautaRepository.kt`):** the two
wipe paths must be treated *differently* because the library is native-only and
absent from the v4 backup.
- **`resetAll()` (~line 754)** — the explicit "apagar tudo" nuke. Books *are*
  user data, so **add** `bookDao.clear(); bookNoteDao.clear()` here. (Also flows
  through `reseed()`, which calls `resetAll()` — acceptable; no demo books.)
- **`importJson()` (~line 764)** — restoring a v4 backup. The backup carries no
  books, so **do NOT** add book clears to the import block: clearing them would
  silently destroy a library the backup cannot restore. Leave `books` /
  `book_notes` untouched on import, with a one-line PT/EN comment saying why.
- **Prefs on import:** `importJson` ends with `prefsDao.upsert(s.prefs)`, and
  `parsePrefs` defaults all native-only fields — so `bookMode`/`bookAnnualGoal`
  reset to defaults on import, exactly like `pinHash`/`backupFolderUri` already
  do. This is the established pattern: accept it, do **not** add them to the v4
  schema to "preserve" them. The library tables survive (per the bullet above),
  so the user just re-enables the toggle.

**Out of scope:** no UI, no Settings toggle.

**Accept:** `compileDebugKotlin` green; unit tests green (WebBackup unchanged);
existing `AppViewModel.blocks` no longer includes reading-session blocks;
`resetAll()` clears `books` + `book_notes`; a v4 `importJson` leaves them
intact.

### K3 · Settings toggle + tab label remapping — Status: pending

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

### K4 · Sepia/parchment theme — Status: pending

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
| `paper3` | `#DECBA8` | `#3D2814` |
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

### K5 · Hoje → library shelf — Status: pending

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

### K6 · Pauta → reading session — Status: pending

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
  (minutes, passed straight through as `targetMin`).
- `Começar` button → `vm.startBlock(title = book.title, linkedToId = null,
  project = "book:${book.id}", targetMin = …)`. **Note:** the param is
  `targetMin` (Int, minutes) — the repo converts it to `targetMs` internally
  (verified `AppViewModel.kt:188` / `PautaRepository.kt:301`). Do not pass ms.

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
- Confirm: `vm.updateProgress(bookId, newPage)` then
  `vm.concludeActive(reflection = note)` — the session note *is* the
  `reflection` arg (signature `concludeActive(reflection: String,
  markIntentionDone: Boolean = false)`, `AppViewModel.kt:195`). Pass `""` when
  the note is blank.

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

## Phase K-6 — durability

### K10 · Book-data backup (separate `pauta-native.v1` file) — Status: pending

**Depends on:** K1, K2. Can ship any time after K2 — it needs neither the tabs
(K5–K9) nor the toggle (K3), and giving the library its own backup early
protects the data before the rest is built. References the SAF + WorkManager
auto-backup shipped in **B1** (`docs/NATIVE_IMPROVEMENTS.md`).

**Why:** book data (books, notes, annual goal) is native-only and absent from
the `pauta.v4` web backup by design, so today it would die on uninstall or
device loss. The v4 file must stay byte-for-byte web-compatible (the lossless
round-trip is the gate test), so the answer is a **second, companion backup
file** — never a change to v4.

**Format — `pauta-native.v1` (a new JSON, written beside the v4 file):**
```json
{
  "schema": "pauta-native.v1",
  "exportedAt": 1700000000000,
  "books": [ { "id": "bk_…", "title": "…", "coverPath": "bk_x.jpg", … } ],
  "bookNotes": [ { "id": "bn_…", "bookId": "bk_…", "imagePath": null, … } ],
  "bookAnnualGoal": 24,
  "images": { "bk_x.jpg": "<base64 jpeg bytes>", … }
}
```
- `bookMode` (the toggle) is **deliberately excluded** — it's live UI state, not
  data; an import must never silently flip a planner user into book mode. They
  re-enable it. `bookAnnualGoal` *is* data and is restored.
- **Images = one combined file (the user's choice).** Every non-null
  `coverPath`/`imagePath` has its bytes base64-encoded into the `images` map,
  keyed by filename. Import decodes them back into `filesDir/book_covers/` and
  `filesDir/note_images/` before inserting the rows. An entry whose file is
  missing on disk is exported with the row but no `images` key (import tolerates
  a path with no matching blob → treats it as no image). When K10 ships before
  K11/K12, all paths are null and `images` is `{}` — forward-compatible.

**Files to create / touch:**
- `data/NativeBackup.kt` (new) — `export(snapshot): String` /
  `import(text): NativeSnapshot`, structured like `WebBackup.kt` but fully
  independent, so WebBackup and its gate test stay untouched. Same
  `kotlinx.serialization` approach WebBackup uses (no new deps).
- `data/PautaRepository.kt` — `exportNativeJson(): String` and
  `importNativeJson(text)` (full replace: clear `books` + `book_notes`, insert
  parsed rows, set `bookAnnualGoal` via `updatePrefs`). An empty library must
  export a valid file with empty arrays.
- `ui/viewmodel/AppViewModel.kt` — thin `exportNativeJson()` /
  `importNativeJson(text)` delegates.
- `ui/screens/SettingsScreen.kt` — fold book data into the existing Data
  export/import flow (see UX below), not a parallel set of buttons.
- The **B1** auto-backup writer (`service/BackupWorker` + the SAF/`filesDir`
  path in `PautaRepository`) — write a second `pauta-native-<stamp>.json` next
  to the v4 file, same cadence and same prune logic.
- `src/test/` — a `NativeBackup` round-trip test (the new gate for book data):
  books + notes + goal survive export→import losslessly, covering every
  nullable field (`seriesNumber`, `rating`, `page`, `startedAt`, `finishedAt`)
  and all three formats.

**Settings UX (Data section) — keep it one flow:**
- **Export:** the existing "Exportar" writes the v4 file as today **and**, when
  any book or note exists, the `pauta-native` file alongside it. Name both files
  in the confirmation so the user knows to keep the pair.
- **Import:** the existing picker routes by the file's top-level shape — a
  `schema == "pauta-native.v1"` goes to `importNativeJson`; anything else stays
  on the current v4 `importJson` path. One "Importar" handles both; a full
  restore is two picks (v4, then native). Say so in the helper text.

**Backup-rules note (B1):** native data lives inside `pauta.db`, which B1's
`backup_rules.xml` already excludes from cloud backup, so this task leaks
nothing new — the SAF folder copy is the off-device path. Confirm and note in
the PR; no rules change expected.

**Out of scope:** merging book data into the v4 file (forbidden); cloud sync;
partial/selective book export.

**Accept:** export produces a valid `pauta-native.v1` holding the full library
(incl. base64 `images` for any covers/photos); import on a fresh install
restores books + notes + annual goal + image files exactly; `NativeBackup`
round-trip test green (incl. an image blob); **WebBackup tests still green and
the v4 output byte-identical** to before this task; auto-backup writes both
files on schedule with the app closed.

---

## Phase K-7 — images

### K11 · Book covers: pick + auto-extract from EPUB/PDF — Status: pending

**Depends on:** K1 (`coverPath` column exists), K2, K5 (covers render on the
shelf cards + the add/edit form). All on-device; no new deps; no internet.

**Why:** a cover turns the shelf from a list into a library, and pulling it
automatically from a file the user already has is the magic moment.

**Files to create / touch:**
- `data/ImageStore.kt` (new) — the no-dep image plumbing, all on `Dispatchers.IO`:
  - `saveCover(bookId, src: Uri): String` — read bytes, **downscale** to ≤1024px
    long edge (`BitmapFactory` with `inSampleSize`), recompress JPEG ~85%, write
    to `filesDir/book_covers/<bookId>.jpg`, return the filename.
  - `delete(path)`, `fileFor(path): File`.
- `ui/BookImage.kt` (new) — `rememberLocalImage(path: String?): ImageBitmap?`,
  decodes off-main-thread (`LaunchedEffect` + IO) with a small in-memory
  `LruCache` keyed by path; returns null while loading / when absent. This is
  the **no-dep** replacement for Coil. *(Opt-in alternative: if the maintainer
  adds `io.coil-kt:coil-compose`, this file collapses to an `AsyncImage` —
  document the swap but default to the hand-rolled helper.)*
- `data/BookFileImport.kt` (new) — extract metadata + cover from a picked file:
  - **EPUB** (`application/epub+zip`): open as `ZipFile`; read
    `META-INF/container.xml` → OPF path; parse the OPF with `XmlPullParser` for
    `<dc:title>`, `<dc:creator>`, and the cover image (manifest item with
    `properties="cover-image"`, or `<meta name="cover">` → manifest id); extract
    that entry's bytes → `ImageStore`. Returns `(title?, author?, coverPath?)`.
  - **PDF** (`application/pdf`): `PdfRenderer` (built-in, minSdk 26 ✓) render
    page 0 to a Bitmap → `ImageStore`. Title guessed from the filename; no
    author. Returns `(titleGuess?, null, coverPath?)`.
  - Unknown type → all null (caller falls back to manual entry).
- `ui/screens/BookFormSheet.kt` (K5) — add two affordances:
  1. **"Escolher capa"** → `PickVisualMedia` photo picker (image/*) → `saveCover`.
  2. **"Importar de ficheiro (EPUB/PDF)"** → `ACTION_OPEN_DOCUMENT`
     (epub+zip, pdf) → `BookFileImport`; prefill title/author if blank and set
     the cover. Show the cover thumbnail once set, with a "remover" affordance.
- `ui/screens/BookShelfScreen.kt` (K5) + `BookDetailSheet.kt` (K8) — render the
  cover via `rememberLocalImage`; **fallback** when null: the current
  text-only card (title/author on `paper2`) — never a broken-image box.
- `i18n/I18n.kt` — new strings.

**Picker contracts** (use `androidx.activity.compose.rememberLauncherForActivityResult`):
`PickVisualMedia()` for photos; `OpenDocument()` for the EPUB/PDF (it returns a
readable `content://` Uri — read bytes immediately into `ImageStore`, don't
persist the Uri).

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Escolher capa` | `Choose cover` |
| `Importar de ficheiro` | `Import from file` |
| `Remover capa` | `Remove cover` |
| `Capa extraída` | `Cover extracted` |
| `Não foi possível ler o ficheiro` | `Couldn't read the file` |

**Backup note:** covers flow into the K10 `images` map automatically (K10 reads
`coverPath`). If K10 already shipped, no change needed; if not, K10 picks them
up. Note in the PR which order happened.

**Out of scope:** in-app ebook reading/opening the file to read; MOBI/AZW;
online cover lookup; cropping/rotation UI.

**Accept:** picking a photo sets a cover that shows on the shelf + detail +
survives app restart; importing an EPUB prefills title/author and sets the
cover; importing a PDF sets a page-1 cover; a book with no cover shows the
text fallback (no broken image); large images don't OOM (downscaled); CI green.

### K12 · Photos on quotes & annotations — Status: pending

**Depends on:** K11 (reuses `ImageStore` + `rememberLocalImage`), K9 (capture
sheet) and/or K8 (detail list). Build after K11.

**Why:** snapping a photo of a page or a margin note is the highest-fidelity
way to capture a passage — the power-user feature of a serious reading log.

**Files to touch:**
- `data/ImageStore.kt` (K11) — add `saveNoteImage(noteId, src: Uri): String`
  writing to `filesDir/note_images/<noteId>.jpg` (same downscale/compress).
- `ui/screens/QuoteCaptureSheet.kt` (K9) — add an **"Adicionar foto"** button
  (`PickVisualMedia`, image/*); show a thumbnail when set, removable. On
  confirm, save the image under the new note's id and store `imagePath`.
  `text` may be blank **iff** an image is attached (update K9's validation).
- `ui/screens/BookDetailSheet.kt` (K8) — render `note.imagePath` thumbnails in
  the notes list (`rememberLocalImage`); tap → full-screen viewer (a simple
  `Dialog` with the `ImageBitmap`, pinch-zoom optional/out of scope). Deleting a
  note also deletes its image file (`ImageStore.delete`).
- `data/PautaRepository.kt` (K2) — `addNote` gains an optional `imagePath:
  String? = null`; `deleteNote` also removes the file. Keep the existing
  signature working (default null) so K9 callers don't break.
- `i18n/I18n.kt` — new strings.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Adicionar foto` | `Add photo` |
| `Remover foto` | `Remove photo` |
| `Ver foto` | `View photo` |

**Backup note:** note images flow into the K10 `images` map via `imagePath` —
same mechanism as covers, no K10 change.

**Out of scope:** in-camera capture flow (the photo picker covers gallery; a
`TakePicture` camera contract can be a later add), OCR of the photo into text,
multiple photos per note (one per note for now).

**Accept:** attaching a photo to a quote saves it; the thumbnail shows in the
book detail and opens full-screen; a note may be image-only (blank text);
deleting the note removes the file; the image round-trips through a K10 backup;
CI green.

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
     ├─ K7 (Marés + annual goal)
     └─ K10 (book-data backup)  ← can ship right after K2; protects data early

K9  (quote capture)  depends on K5
K11 (book covers: pick + EPUB/PDF extract)  depends on K5
 └─ K12 (photos on notes)  depends on K11 (+ K9/K8)
```

Minimum shippable slice: **K1 → K2 → K3** (toggle works, labels change, no
book content yet). Each subsequent task adds one tab or one feature layer.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-06-25 · K1 · #n · data layer: books + book_notes tables, Room v7→v8, bookMode + bookAnnualGoal prefs -->
