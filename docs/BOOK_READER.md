# Book reader — implementation task file

> **Concept.** Book mode today is a *tracker*: you type "page 80 of 228" by
> hand. This file turns it into a *reader* — attach the PDF/EPUB to a book,
> read it inside Pauta, and let the app know where you are without being told.
> Everything else follows from that: progress updates itself, reading speed
> becomes measurable, and the third tab finally has real data to draw.
>
> It also finishes the two loose ends from the book-mode round one: the capture
> chip that overlaps the tab bar, and the third tab that never transformed.
>
> Ships as 8 self-contained tasks (R1…R8). Each task is one PR. Tasks within a
> phase are independent unless "Depends on:" says otherwise.

> **How to use (human).** In a fresh Claude Code session, prompt:
>
> > Read `docs/BOOK_READER.md`. Do ONLY task **R1** — follow its spec and the
> > Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR → CI →
> > squash-merge), then set the task's Status and append one line to the Log.
>
> Or stateless: *"Read `docs/BOOK_READER.md` and do the first task whose Status
> is `pending`."*
>
> **How to use (Claude).** This file + `CLAUDE.md` + `docs/BOOK_MODE.md` (the
> data model section) are your complete briefing. Don't re-survey the codebase
> beyond the files each task names. Always update Status + Log in the same PR
> as the code.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Global guardrails (every task)

All guardrails from `docs/NATIVE_IMPROVEMENTS.md`, `docs/BOOK_MODE.md` and
`docs/POLISH.md` apply unchanged. Additional constraints for the reader:

- **No new dependencies. None.** The reader is built on the Android framework
  and the Kotlin stdlib only:
  - PDF → `android.graphics.pdf.PdfRenderer` (API 21+, in the framework)
  - EPUB → `java.util.zip.ZipFile` + `android.webkit.WebView`
  - charts → Compose `Canvas`, like the existing `InsightsSheet`
  There is no Room/Compose artifact to add and no PDF/EPUB library to pull in.
- **Book mode is still a lens, not a fork.** With `bookMode` off, every screen
  behaves exactly as today. Attached files are book-mode-only.
- **Files are device-local and native-only.** Attached documents live in the
  app's private storage, are **never** written into the `pauta.v4` export, and
  the new columns are marked `// native-only`. A backup restores the *book*,
  not the file — the detail sheet says so when the file is missing.
- **The reader is quiet.** Same paper/ink identity, sepia palette in book mode,
  `SerifFamily` for body text. No Material chrome, no toolbars that don't fade,
  no page-curl animations. Reading is the app at its most minimal.
- **Reading position is derived, never nagged.** Once a file is attached the app
  stops asking "até que página chegaste?" — it knows.
- **Prefs are law:** `reducedMotion` and `haptics` gate every new animation and
  tick, `textScale` and `highContrast` still apply, TalkBack descriptions on
  every new control.

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Data model additions

Everything below extends the `BookEntity` defined in `docs/BOOK_MODE.md`.
Read that table first — it is unchanged; these are new columns only.

### `BookEntity` additions (Room v8 → v9)

| Column | Type | Default | Notes |
|---|---|---|---|
| `filePath` | String? | null | absolute path inside `filesDir/books/`; null = no file attached |
| `fileKind` | String? | null | `pdf` / `epub`; null when `filePath` is null |
| `fileName` | String | `""` | the original display name, for the UI |
| `readPosition` | String | `""` | reader bookmark — page index (pdf) or `spineIndex:scrollPercent` (epub) |
| `wordCount` | Int | `0` | total words; counted for EPUB, estimated for PDF/physical |

**`MIGRATION_8_9`:**

```sql
ALTER TABLE books ADD COLUMN filePath TEXT;
ALTER TABLE books ADD COLUMN fileKind TEXT;
ALTER TABLE books ADD COLUMN fileName TEXT NOT NULL DEFAULT '';
ALTER TABLE books ADD COLUMN readPosition TEXT NOT NULL DEFAULT '';
ALTER TABLE books ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0;
```

### Where files live

`context.filesDir/books/<bookId>.<ext>`. One file per book. Deleting a book
deletes its file. Nothing is ever written outside `filesDir`.

### Words-per-page estimate

Physical books and PDFs have no extractable word count, so the app uses a
single constant, `BookMath.WORDS_PER_PAGE = 280`, and says "≈" everywhere it
shows a derived word figure. EPUBs get a real count and drop the "≈".

---

## Phase R-0 — the small stuff (do this first)

### R1 · Quick fixes: capture chip + reading durations — Status: pending

**Depends on:** nothing

**Why:** two visible annoyances, both one-file changes, both worth clearing
before the big work starts.

**Files to touch:**
- `ui/MainScaffold.kt` — the K9 capture chip
- `ui/screens/BookShelfScreen.kt` — the shelf header
- `ui/screens/BookSessionScreen.kt` — the duration pills
- `i18n/I18n.kt`

**(a) The capture chip overlaps the tab bar.** Today it floats bottom-left at
`bottom = 84.dp` (`MainScaffold.kt`, the `AnimatedVisibility` block) and sits
on the tab bar's 1dp hairline. Three floating things share that strip already
(chip left, Pip right, snackbar centre). Don't nudge it — **remove the floating
chip entirely** and put the capture affordance in the shelf header instead,
beside `"Adicionar livro +"`, as a second quiet mono action:

```
Estante                    ✎ Nota +   ·   Adicionar livro +
```

If the two actions don't fit on one line at `textScale = 1.3`, wrap them with
`ChipFlow`. The sheet it opens (`QuoteCaptureSheet`) is unchanged.

> **Note:** this removes the chip from the Sessão and Hábitos tabs. That is
> intended — quick capture belongs where the books are. If a later task wants
> it back globally, it goes in `StatusRow`, not the bottom strip.

**(b) Reading duration presets are Pomodoro numbers.** `BookSessionScreen`
offers `Sem limite · 25 · 50 · 90`. Reading sessions aren't Pomodoros. Change
the book-mode set to:

```
Sem limite · 15 min · 30 min · 45 min · 60 min · Outro…
```

`Outro…` reveals an inline minutes field (`BoxedField`, numeric, 1–600,
`FieldError` outside that range) below the pills. Selecting a preset clears it;
typing a value deselects the presets.

> The planner's own presets and the shared "Outro…" behaviour are **U2** in
> `docs/UX_FIXES.md` — don't change `PautaSheets.kt` here. If U2 shipped first,
> reuse the primitive it introduced instead of writing a second one.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Nota` | `Note` |
| `Outro…` | `Custom…` |
| `Minutos` | `Minutes` |

**Out of scope:** the reader, the Hábitos tab, anything in `PautaSheets.kt`.

**Accept:** nothing floats over the tab bar in book mode; capture still works
from the shelf; reading presets are 15/30/45/60 + custom and the custom value
starts a session with that target; planner mode untouched; CI green.

---

## Phase R-1 — attaching files

### R2 · Data layer: attached files — Status: pending

**Depends on:** R1 (not strictly — but ship R1's small stuff first)

**Why:** the columns, the file storage and the picker must exist before either
reader can be built.

**Files to touch:**
- `data/entity/Entities.kt` — the five new `BookEntity` columns
- `data/AppDatabase.kt` — version 9, `MIGRATION_8_9`, register it
- `data/PautaRepository.kt` — attach / detach / resolve
- `data/BookFiles.kt` (new) — the storage helper
- `ui/viewmodel/AppViewModel.kt` — thin delegates
- `ui/screens/BookFormSheet.kt` — the attach row
- `ui/screens/BookDetailSheet.kt` — show the attached file
- `i18n/I18n.kt`

**`BookFiles`** (new, pure-ish helper over `Context`):

```kotlin
object BookFiles {
    fun dir(context: Context): File                    // filesDir/books, mkdirs
    fun fileFor(context: Context, bookId: String, ext: String): File
    /** Copies a picked SAF uri into private storage. Returns the file, or null. */
    suspend fun importFrom(context: Context, uri: Uri, bookId: String): ImportedFile?
    fun delete(context: Context, book: BookEntity)
    /** "pdf" / "epub" / null — sniffed from the display name AND magic bytes. */
    fun kindOf(context: Context, uri: Uri): String?
}

data class ImportedFile(val path: String, val kind: String, val name: String)
```

Sniff the kind from magic bytes, not just the extension: `%PDF` for PDF, `PK`
+ a `mimetype` entry of `application/epub+zip` for EPUB. Reject anything else
with a user-facing error rather than storing a file no reader can open.

**Repository:**

```kotlin
suspend fun attachFile(bookId: String, uri: Uri): AttachResult
suspend fun detachFile(bookId: String)
suspend fun setReadPosition(bookId: String, position: String)
suspend fun setWordCount(bookId: String, words: Int)
```

`AttachResult` is a sealed type: `Ok(kind, pageCount)` / `UnsupportedType` /
`CopyFailed`. On `Ok` for a PDF, also set `totalPages` from
`PdfRenderer.pageCount` **if the book's `totalPages` is still 0** — never
overwrite a number the user typed.

`deleteBook` must delete the attached file too.

**`BookFormSheet` — the attach row.** Below the format chips, a row:

- No file: `"Anexar ficheiro"` ghost action → `OpenDocument` launcher filtered
  to `application/pdf` and `application/epub+zip`.
- File attached: `"📄 <fileName>"` (mono, ink3) + a quiet `"remover"` action.
- While copying: `"A copiar…"`, the confirm button disabled.
- On `UnsupportedType`: `FieldError(tr("Só PDF e EPUB por agora."))`

**`BookDetailSheet`:** show the attached file name under the format chip. No
"open" button yet — that arrives with R3/R4.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Anexar ficheiro` | `Attach file` |
| `Só PDF e EPUB por agora.` | `PDF and EPUB only for now.` |
| `A copiar…` | `Copying…` |
| `Não foi possível copiar o ficheiro.` | `Could not copy the file.` |
| `Ficheiro` | `File` |
| `remover` | `remove` |

**Out of scope:** rendering anything. This task attaches and stores; it does
not open.

**Accept:** attaching a PDF stores it under `filesDir/books/`, sets `fileKind`
and `fileName`, and fills `totalPages` when it was 0; attaching a `.txt`
renamed to `.pdf` is rejected; deleting the book removes the file; a `pauta.v4`
export/import round-trip is byte-identical to before (the `WebBackup` tests
must pass untouched); CI green.

---

## Phase R-2 — the readers (R3 and R4 are independent; both need R2)

### R3 · PDF reader — Status: pending

**Depends on:** R2

**Why:** PDFs are the common case and the framework renders them, so this is
the shorter of the two readers and proves the reader shell.

**Files to create / touch:**
- `ui/screens/ReaderScreen.kt` (new) — the shared reader shell
- `ui/screens/PdfReader.kt` (new) — the PDF page source
- `ui/MainScaffold.kt` — a `READER` NavHost destination
- `ui/screens/BookDetailSheet.kt` / `BookShelfScreen.kt` — the "Ler" entry point
- `i18n/I18n.kt`

**The reader shell (`ReaderScreen`)** is a full-surface navigation destination
(not a sheet), shared by both formats:

- **Chrome auto-hides.** Tap the middle of the screen to toggle it. Hidden by
  default after 2s. When hidden: nothing but the page. When shown: a top row
  (`←` back, book title, `⋯`) and a bottom row (progress `"80 / 228"` + a thin
  accent progress line). Fades via `PautaMotion`; snaps under `reducedMotion`.
- **Immersive while reading:** hide the system bars regardless of the
  `immersive` pref, and restore the user's setting on exit.
- **Keep-awake** honours the existing `keepAwake` pref.
- **Position is saved** on every page change (debounced ~1s) and on dispose,
  via `setReadPosition`.
- **Back** exits to wherever it was opened from.

**`PdfReader`** renders with `PdfRenderer` over a `ParcelFileDescriptor`:

- A vertical `LazyColumn`, one item per page, each an `Image` of a rendered
  `Bitmap`. Render at the composable's measured width × device density, capped
  at 2048px on the long edge to stay off the bitmap ceiling.
- **`PdfRenderer` is not thread-safe and allows one open page at a time.**
  Serialise every render through a single `Mutex` on `Dispatchers.IO`, and hold
  the renderer in a `remember` that closes it in `onDispose`.
- Cache the last ~5 rendered pages in an LRU keyed by `(pageIndex, widthPx)`;
  drop the cache on width change.
- Pinch-to-zoom per page (`graphicsLayer` scale 1f–4f, pan clamped to bounds),
  double-tap to reset. Zoom resets on page change.
- Position format: the page index as a plain string (`"79"`, zero-based).
- Restore: on open, `scrollToItem(readPosition)`.

**Entry point:** in `BookDetailSheet`, when `filePath != null`, the primary
action becomes **`"Ler"`** (accent `PautaButton`), above `Marcar como lido`.
On the shelf, tapping a *reading* book with a file goes straight to the reader;
tapping its author line still opens the detail sheet.

**When the file is missing** (restored backup, cleared storage): the reader
shows `"O ficheiro já não está aqui."` with a `"Anexar de novo"` action, and
the detail sheet's `Ler` button reverts to the manual progress editor.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Ler` | `Read` |
| `O ficheiro já não está aqui.` | `The file is no longer here.` |
| `Anexar de novo` | `Attach again` |
| `A abrir…` | `Opening…` |

**Out of scope:** EPUB, text selection, search inside the document,
annotations anchored to a page, night-mode inversion of PDF pages.

**Accept:** a real multi-page PDF opens, scrolls smoothly, zooms and restores
its position after closing and reopening; rotating the device doesn't leak the
renderer or crash; a 300-page PDF doesn't OOM; chrome hides and returns on tap;
CI green.

### R4 · EPUB reader — Status: pending

**Depends on:** R2 (and R3 should land first so the shell exists)

**Why:** EPUB is where the app can actually *be* a reading app — reflowable
text in Pauta's own type and palette, and a real word count that makes reading
speed honest.

**Files to create / touch:**
- `domain/Epub.kt` (new) — pure parsing, unit-tested
- `ui/screens/EpubReader.kt` (new) — the rendering half
- `ui/screens/ReaderScreen.kt` — branch on `fileKind`
- `i18n/I18n.kt`

**`domain/Epub.kt`** — no Android imports, so it is testable on the JVM:

```kotlin
data class EpubChapter(val href: String, val title: String, val words: Int)
data class EpubBook(val title: String, val author: String, val chapters: List<EpubChapter>)

/** Reads META-INF/container.xml → the OPF → the spine, in order. */
fun parseEpub(zip: ZipFile): EpubBook

/** Chapter XHTML with scripts, styles and event attributes stripped. */
fun chapterHtml(zip: ZipFile, href: String): String

/** Words in a chapter's text content — the real count, not an estimate. */
fun countWords(html: String): Int
```

Parse with `org.xmlpull` (framework) or a narrow regex over the OPF — either is
fine; the spine order and the `href`s are all that matter. Handle the common
shapes: OPF anywhere in the archive, relative hrefs, `<spine toc=…>` present or
absent. A malformed EPUB throws `EpubFormatException`, caught at the UI edge.

**Sanitising is mandatory.** The HTML comes from a file the user supplied, and
it renders in a WebView. Strip `<script>`, `<iframe>`, `<object>`, `<embed>`,
`on*=` attributes and `javascript:` URLs before rendering. In the WebView:
`javaScriptEnabled = false`, `allowFileAccess = false`,
`allowContentAccess = false`, no `addJavascriptInterface`, and a
`WebViewClient` that refuses every navigation except the initial load.

**`EpubReader`** — one `WebView` per chapter inside a `LazyColumn` is too heavy;
use **one WebView, one chapter at a time**, with the reader shell's swipe/tap
moving between chapters:

- Inject a stylesheet built from `LocalPautaColors` + the app's fonts so text
  is Pauta's serif on Pauta's parchment: background `paper`, text `ink`,
  links `accent`, `font-size` scaled by the `textScale` pref, generous
  `line-height` and side margins.
- Position format: `"<spineIndex>:<scrollPercent>"`, e.g. `"12:0.43"`.
- Progress: chapters are weighted by word count, so the progress line reflects
  reading position, not chapter count.
- On first open, sum `countWords` across the spine and store it via
  `setWordCount`. If `totalPages` is 0, leave it 0 — for an EPUB the app shows
  **percent**, not pages, everywhere.

**Format-aware display, extended.** `docs/BOOK_MODE.md` says physical/ebook use
"página" and audiobook uses "minuto". An *attached EPUB* is a third case: it
shows `"43%"`. Every progress display must handle it — shelf card, detail
sheet, conclude prompt, reader chrome.

**Unit tests** in `src/test/` for `parseEpub`, `countWords` and the sanitiser,
against small hand-built zip fixtures (a valid book, a book with no OPF, a
chapter containing a `<script>` and an `onclick=`).

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Não foi possível abrir este EPUB.` | `Could not open this EPUB.` |
| `Capítulo {n} de {total}` | `Chapter {n} of {total}` |

**Out of scope:** EPUB3 fixed-layout, embedded audio/video, the table of
contents as a navigable list (that's a later extra), text selection.

**Accept:** a real EPUB opens in Pauta's own type and palette; chapters advance
and the position restores exactly; the progress line tracks words not chapters;
a `<script>` in a chapter never executes; the parser's unit tests are green;
CI green.

---

## Phase R-3 — what the reader makes possible

### R5 · Reader ↔ session: progress that updates itself — Status: pending

**Depends on:** R3 (R4 too if it has landed — handle both kinds)

**Why:** this is the payoff. Opening the reader *is* starting a reading session,
and closing it records where you got to. No more typing page numbers.

**Files to touch:**
- `ui/screens/ReaderScreen.kt`
- `ui/screens/BookSessionScreen.kt`
- `ui/viewmodel/AppViewModel.kt`
- `i18n/I18n.kt`

**Opening the reader starts a session** — the same `FocusBlockEntity` with
`project = "book:<id>"` that K6 already creates, so the timer, the focus
notification and the session history all keep working unchanged. If a reading
session is *already* running for that book, the reader joins it rather than
starting a second one.

**Closing the reader concludes it**, writing:
- `currentPage` = the page/percent the reader was left at, derived from
  `readPosition` — **not** asked for.
- the session's page delta, so `BookMath` has real spans.

**`BookConcludeSheet` becomes optional.** With a file attached, closing the
reader concludes silently and shows a one-line snackbar: `"Sessão guardada · 24
págs em 38 min"` with an `"Anular"` action. Without a file, the existing
page-prompt sheet is unchanged — manual tracking still works exactly as today.

**Guard the accidental session:** a reader session shorter than 60 seconds with
no position change is discarded rather than saved. Opening a book to check a
quote shouldn't litter the history.

**`BookSessionScreen`** gains, for books with a file, a `"Continuar a ler"`
card that jumps straight into the reader at the saved position — that becomes
the primary action, above the manual start card.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Continuar a ler` | `Continue reading` |
| `Sessão guardada` | `Session saved` |
| `{n} págs em {min} min` | `{n} pages in {min} min` |

**Out of scope:** stats and charts (R6/R7).

**Accept:** opening the reader starts exactly one session; closing it concludes
that session and the book's progress matches where the reader was left; a
10-second peek saves nothing; books without a file still use the manual flow
unchanged; CI green.

### R6 · Reading speed (WPM) — Status: pending

**Depends on:** R5 (needs sessions carrying honest page deltas)

**Why:** "how fast do I read" is the stat a reading app owes you, and after R5
the app finally has the data to answer it truthfully.

**Files to touch:**
- `domain/BookMath.kt` — extend, keep pure
- `ui/screens/BookDetailSheet.kt`
- `src/test/…/BookMathTest.kt`
- `i18n/I18n.kt`

**`BookMath` additions:**

```kotlin
const val WORDS_PER_PAGE = 280

/** Words per minute across the given spans. null with < 2 usable spans. */
fun wordsPerMinute(spans: List<SessionSpan>, wordsPerUnit: Float): Float?

/** Words per unit of progress: real for EPUB (wordCount / units),
 *  WORDS_PER_PAGE for pages, null for audiobooks (minutes aren't words). */
fun wordsPerUnit(book: BookEntity): Float?
```

`SessionSpan` already exists from K-extra — reuse it, don't redefine it.

**Display** in `BookDetailSheet`, on the line already holding "Ritmo":

- EPUB with a real `wordCount` → `"Ritmo: 246 palavras/min"`
- PDF / physical → `"Ritmo: ≈ 240 palavras/min"` (the `≈` is required; the
  number is estimated from `WORDS_PER_PAGE`)
- Audiobook → keep the existing `min/hora` line; **no WPM**, and no `≈` fudge.
- Fewer than 2 spans → the line is hidden entirely, as today.

**Unit tests:** zero spans, one span, a span with zero duration, an audiobook
(must return null), an EPUB with a real word count vs a PDF estimate.

**Out of scope:** charts (R7), comparing your speed to anyone else's, per-genre
speed.

**Accept:** WPM appears for a book with ≥2 sessions and matches a hand
calculation; audiobooks never show WPM; estimates always carry `≈`; unit tests
green; CI green.

### R7 · Hábitos tab rebuild: the reading rhythm — Status: pending

**Depends on:** R5 (the day data comes from sessions), R6 (the speed chart)

**Why:** the third tab is the only one that never transformed — it's still the
planner's habit grid with a card glued on top, under a header claiming those
are reading habits when they're the same water-and-running tides as planner
mode. The header currently lies. This makes the tab honest and gives the
reading data somewhere to live.

**Files to create / touch:**
- `ui/screens/BookHabitsScreen.kt` — becomes a real screen (it is currently
  only `BookAnnualGoalCard`, 151 lines)
- `ui/screens/MaresScreen.kt` — remove the book-mode injection at the top of
  its `LazyColumn`; book mode early-returns to `BookHabitsScreen` instead,
  matching how `HojeScreen` and `PautaScreen` already branch
- `domain/ReadingStats.kt` (new) — pure, unit-tested
- `i18n/I18n.kt`

**`domain/ReadingStats.kt`** — derived entirely from existing data, no new
tables:

```kotlin
/** Local day keys on which any reading session was concluded. */
fun daysRead(sessions: List<FocusBlockEntity>): Set<String>
/** Current and best consecutive-day reading streaks. */
fun streaks(daysRead: Set<String>, today: String): Pair<Int, Int>
/** Minutes read per day for a month, for the day grid and the bar chart. */
fun minutesByDay(sessions: List<FocusBlockEntity>, year: Int, month: Int): Map<String, Int>
/** Books finished per month across a year. */
fun finishedByMonth(books: List<BookEntity>, year: Int): List<Int>
```

**`BookHabitsScreen` sections, top to bottom:**

1. **Objetivo anual** — the existing `BookAnnualGoalCard`, unchanged. It works.
2. **Dias de leitura** — a month grid using **the same 22dp day cells the tides
   draw**, but filled automatically from `daysRead`. Reuse the tide cell
   composables; do not write a second cell renderer. Cells are read-only (no
   tap to mark — reading is proven by sessions, not self-reported). Below it:
   `"Sequência atual: 6 dias · melhor: 21"`.
3. **Gráficos** — three Compose `Canvas` charts in the style of `InsightsSheet`
   (thin accent strokes on paper, mono axis labels, no gridlines):
   - minutes read per day, last 30 days (bars)
   - pages/percent per week, last 12 weeks (bars)
   - reading speed over time, one point per session (line) — hidden with < 3
     sessions
4. **Livros terminados** — a 12-cell year strip, one per month, from
   `finishedByMonth`.
5. **Hábitos** — the existing habit engine, embedded as today, under an honest
   eyebrow: **`HÁBITOS`**, not `HÁBITOS DE LEITURA`. These are the user's
   ordinary tides and the header must stop pretending otherwise.

**Empty states:** with no sessions yet, sections 2–4 collapse to a single quiet
line — `"Ainda sem leituras registadas."` — rather than rendering empty charts.

**Unit tests** for all four `ReadingStats` functions, including a streak broken
by one day, a month boundary, and an empty input.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Dias de leitura` | `Reading days` |
| `Sequência atual` | `Current streak` |
| `melhor` | `best` |
| `Minutos por dia` | `Minutes per day` |
| `Ritmo ao longo do tempo` | `Speed over time` |
| `Livros terminados` | `Books finished` |
| `Ainda sem leituras registadas.` | `No reading recorded yet.` |
| `Hábitos` | `Habits` |

**Out of scope:** separate reading-only habit types (a `bookHabit` column was
considered and deliberately dropped — one habit list, honest label), exporting
charts as images.

**Accept:** book mode's third tab is a reading screen, not a decorated tide
grid; the day grid fills from real sessions with no tapping; all three charts
render and hide gracefully with thin data; the habits section keeps working
exactly as the planner's; `bookMode` off → Marés is pixel-identical to today;
`ReadingStats` tests green; CI green.

---

## Phase R-4 — packaging

### R8 · Second launcher icon — Status: pending

**Depends on:** nothing technically, but ship it last — it's the bow on top

**Why:** two icons on the home screen, one app, one database. Wanting a
"separate book app" is really wanting a separate *door*, not a separate app.

**Files to touch:**
- `app/src/main/AndroidManifest.xml` — an `activity-alias`
- `MainActivity.kt` — read the launch intent
- `res/mipmap-*` — the book icon (a Canvas-drawn or vector book mark in the
  app's ink/accent style, matching the existing icon's weight)

**How it works — and why it doesn't fight the in-app toggle:**

An `<activity-alias android:name=".BookLauncher" android:targetActivity=".MainActivity">`
with its own `LAUNCHER` intent filter, its own icon and its own label ("Livro").
`MainActivity` reads which component it was launched through
(`intent.component?.className`) and, on a **cold start only**, sets `bookMode`
accordingly — `true` from the book alias, `false` from the main one.

That means the launcher icon does exactly what the in-app toggle does: it sets
one boolean. It is a shortcut to the toggle, not a parallel mode system. If you
flip the mode inside the app, that sticks until you next launch from an icon.
Last action wins, and there is only ever one source of truth.

**Details that matter:**
- Apply on cold start only — reading `intent` on every resume would flip the
  mode when returning from a share sheet or the file picker.
- `launchMode` stays as-is; both entry points resolve to the same task, so the
  app never appears twice in recents.
- Both aliases must survive an OTA update (`android:enabled="true"`, no
  `PackageManager` toggling — a disabled component is a well-known way to lose
  a home-screen shortcut permanently).
- The main icon's label and icon are unchanged. Nothing about the planner
  experience shifts for someone who never taps the second icon.

**New i18n strings:** the alias label lives in `strings.xml`, not `I18n.kt`
(the launcher reads it before Compose exists). Add `app_name_book` in both
`values/` and `values-en/`.

**Out of scope:** app shortcuts (long-press menu), a QS tile for the mode,
per-icon themed-icon variants.

**Accept:** two icons appear after install; each opens the app in its own mode;
the in-app toggle still works and its choice persists; only one entry appears
in recents; an OTA update over an existing install keeps both icons and all
data; CI green.

---

## Task dependency graph

```
R1 (quick fixes — independent, do first)

R2 (attach files: Room v8→v9 + BookFiles + form row)
 ├─ R3 (PDF reader + reader shell)
 │   └─ R5 (reader ↔ session: auto-progress)
 │       ├─ R6 (WPM)
 │       └─ R7 (Hábitos rebuild) ── needs R6 for the speed chart
 └─ R4 (EPUB reader) ── needs R3's shell

R8 (second launcher icon — independent, ship last)
```

Minimum shippable slice: **R1 → R2 → R3**. At that point you can attach a PDF
and read it in the app; everything after makes it smarter.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-08-02 · R1 · #n · capture chip moved to the shelf header; reading presets 15/30/45/60 + custom -->
