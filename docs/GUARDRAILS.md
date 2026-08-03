# Guardrails — the rules that bind every task

> **What this is.** The constraints that apply to *all* work in this repo,
> gathered in one place. Until now they lived in the Global guardrails section
> of whichever task file happened to introduce them, and later files inherited
> them by reference — `docs/FIELD_FIXES.md` opens by importing four other files,
> two of which are finished work nobody should need to read. That indirection is
> what this file removes.
>
> **This file is binding, not advisory.** A PR that breaks a rule below is not
> done, whatever its task file says. Where a task's spec and this file disagree,
> **this file wins** — and the disagreement is a bug in the task, worth saying so
> in the PR.

**Read this before your first PR in a session.** With `CLAUDE.md`, your task
file, and `docs/CONTEXT.md`, it is the complete briefing. You should not need to
open a finished task file to know what you are allowed to do.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## How the rules are organised

| Section | Covers | Where it came from |
|---|---|---|
| **A · Identity** | how the app looks and feels | `NATIVE_IMPROVEMENTS`, `POLISH` |
| **B · Both lenses** | planner and book mode | `BOOK_MODE` |
| **C · Data and backup** | Room, `pauta.v4`, what is device-local | `NATIVE_IMPROVEMENTS`, `BOOK_MODE`, `BOOK_LIBRARY` |
| **D · Dependencies** | why there are none | `BOOK_READER` |
| **E · Accessibility and prefs** | the preferences that are law | `POLISH`, `UX_FIXES` |
| **F · i18n** | Portuguese is the source | `NATIVE_IMPROVEMENTS` |
| **G · Security** | an attached book is hostile input | `BOOK_READER` — **moved here in full** |
| **H · Recording and reversibility** | what the app may do unasked | `FIELD_FIXES` |
| **I · Workflow** | how a change ships | `CLAUDE.md` |
| **J · Closed decisions** | settled; do not re-propose | every file |
| **K · Never do this** | the short list, absolute | this file |

---

## A · Identity

The app is **quiet**. That is the product, not a preference.

- **Colour comes only from `LocalPautaColors`.** No literals in composables, no
  Material colour roles. Book mode's sepia wash is `bookPautaColors`.
- **Type is `SerifFamily` (display) / `MonoFamily` (meta) / `SansFamily`
  (body).** No fourth family. The reader's body face is the platform `serif`
  and stays that way — the CSP forbids `font-src`, so a face picker would have
  one entry.
- **`clickableNoRipple` everywhere.** No Material ripples, no elevation, no
  FABs, no snackbar defaults that don't match the app's own.
- **No imagery.** No cover art, no icons standing in for words where a word
  fits, no illustration beyond Pip. Charts are pure Compose `Canvas`.
- **No gamification** beyond the tide tiers that already exist. No streak
  warnings, no nagging, no confetti. The **Respiro** exists because breaking a
  streak must not be punished.
- **Comments are bilingual (PT/EN) and explain *why*,** at the density of the
  code around them.

**Web parity is retired for visuals.** Comments citing web CSS (`0.08em of
10sp`, `tab-mares.jsx`) are historical context, not constraints; where a spec
conflicts with an old web-derived value, the spec wins. Data shape, backup
format and behaviour parity still hold.

## B · Both lenses

**Book mode is a lens, not a fork.** One `bookMode` boolean re-routes three
tabs; it does not branch the app.

- With `bookMode` off, every screen behaves **exactly** as it does today.
- **Three tabs, fixed.** No fourth tab, no fourth `NavHost` destination at the
  top level.
- Every shared primitive you touch is used by both faces. **Acceptance always
  includes "book mode on and off both look right"** — and that clause is not
  decoration; it is the one that catches this class of regression.
- One boolean, one source of truth: the Settings switch, the header long-press
  and the second launcher icon all write the same pref. Last action wins.

## C · Data and backup

- **The `pauta.v4` export shape does not change, and round-trips stay
  lossless.** `data/WebBackup.kt` plus its tests are the gate.
- **Everything book-shaped is native-only and never enters `pauta.v4`.** Mark
  new pref fields and entity columns `// native-only`. The book library has its
  own export (`pauta.books.v1`, L2) — a separate file in its own format.
- **Attached documents are device-local.** They live in `filesDir/books/`, are
  never uploaded, and are never written into any export. A restored backup
  brings back the *book*, not the file, and the detail sheet says so.
- **Room migrations are additive and tested.** Never rewrite a shipped
  migration. The current version and the full column tables live in
  `docs/DATA_MODEL.md`.
- **Nothing leaves the device** except the in-app updater's call to GitHub
  Releases. No account, no sync, no telemetry, no crash reporting.

## D · Dependencies

**No new dependencies. None.** Not in the app, not in the `:reader` process.

Everything is the Android framework plus the Kotlin stdlib plus the androidx
artifacts already in `build.gradle.kts`:

- PDF → `android.graphics.pdf.PdfRenderer`
- EPUB → `java.util.zip.ZipFile` + `android.webkit.WebView`
- charts and Pip → Compose `Canvas`

**Never vendor a parser to work around a limitation.** If a format cannot be
handled with the framework, the answer is to **refuse that file with a clear
message**. Adding a library is a separate, explicit decision by the owner, not
something a task may take on its way past.

## E · Accessibility and prefs

**Prefs are law.** Each of these gates real behaviour, and a new control that
ignores one is a regression:

| Pref | What it must do |
|---|---|
| `reducedMotion` | every animation becomes `snap()` / `EnterTransition.None` |
| `haptics` | every tick goes through the shared `tick(prefs)` |
| `textScale` | every layout survives it — test at **1.0 and 1.5** |
| `highContrast` | text and rules strengthen |
| `immersive` | the system bars stay hidden |

Plus: **TalkBack descriptions on every new control**, the `1`/`2`/`3` hardware
keyboard shortcuts keep working, and nothing that carries information sits
under a floating element (see `FIELD_FIXES.md` F11 for the rule and its scope).

**Layout is tested at two text scales and both orientations.** The app has
shipped defects visible at 1.5× and in landscape that no test could see.

## F · i18n

- **Portuguese is the source.** Every user-facing string goes through
  `tr()` / `trf()`; the PT string *is* the key.
- English values go in the `EN` map in `i18n/I18n.kt`, with `// native-only` on
  keys the web app never had.
- A task that adds strings lists them as a PT/EN table in its spec. Check for an
  existing key before adding a near-duplicate.

## G · Security — an attached book is untrusted input

> Moved here in full from `docs/archive/BOOK_READER.md`, which is finished work. This is
> the canonical copy. It is **binding on anything that touches an attached file,
> the parser, the `:reader` process or the WebView** — which today means the
> reader tasks in `docs/BOOK_LIBRARY.md` and `docs/FIELD_FIXES.md`, and anything
> that comes after them.

An attached book arrives as a file the user picked, it may have come from
anywhere, and it is parsed by native code and rendered in a browser engine.
**Treat every byte of it as hostile.** These are requirements, not suggestions;
a task's Accept restates the ones it owns, and a PR that skips one is not done.

### G.0 · Threat model — what actually applies to this stack

| Threat | Applies here? | Control |
|---|---|---|
| JS embedded in a PDF | **No** — `PdfRenderer` is native platform code with no script engine. There is no PDF.js and no `isEvalSupported` to set. | Don't add a JS-based PDF library. That *is* the control. |
| Malformed PDF → native memory corruption | **Yes, and it is the main PDF risk.** The parser is native and in-process; a crash there cannot be caught by Kotlin. | Process isolation (G.2) |
| JS embedded in EPUB XHTML | **Yes** — EPUB is HTML rendered by a real browser engine | WebView lockdown (G.3) |
| Exfiltration from document content | **Yes** — a remote `<img>`, a CSS `url()`, a beacon | Block network at the WebView, plus CSP |
| Zip bomb | **Yes** — EPUB is a zip | Ratio + total + count limits (G.1) |
| Zip Slip (`../` in entry names) | **Yes** — the classic EPUB vuln | Never resolve an entry to a path; read by name from the open archive |
| XXE / billion laughs in `container.xml` and the OPF | **Yes** — both are attacker-controlled XML | Disable DTDs and entity expansion (G.4) |
| `intent://` and custom-scheme links launching other apps | **Yes** | Refuse every navigation |
| DRM / encrypted EPUB rendering as garbage | Yes, minor | Detect and reject |
| Third-party library CVEs | **No libraries to pin** — section D forbids them. | See G.6 |

### G.1 · Import limits

Enforced in `BookFiles.importFrom` **before** the file is stored, streaming —
never read the whole thing into memory to measure it:

| Limit | Value | Why |
|---|---|---|
| Max file size | 200 MB | above this, refuse rather than fill the user's storage |
| Max total uncompressed (EPUB) | 500 MB | zip bomb |
| Max compression ratio, per entry and overall | 100:1 | zip bomb |
| Max entry count (EPUB) | 10 000 | zip bomb by entry count |
| Max single entry uncompressed | 50 MB | one huge chapter |

Read the declared sizes from the zip's central directory (`ZipEntry.getSize()` /
`getCompressedSize()`) **and** enforce the same ceilings against a running byte
counter while copying — a crafted archive can lie in its header, so the declared
size is a fast reject, not the guarantee. Abort and delete the partial file the
moment a counter trips.

**Entry names are validated, never resolved.** Reject any entry whose name
contains `..`, starts with `/`, contains a backslash, or is absolute. Chapter
content is read via `ZipFile.getEntry(name)` on the open archive — the reader
**never** extracts an EPUB to disk, so there is no directory to escape into.

**Sniff by magic bytes, not extension:** `%PDF-` at offset 0 for PDF; for EPUB,
the zip signature plus a `mimetype` entry whose content is exactly
`application/epub+zip`. Reject anything else with a user-facing error.

**Reject encrypted books:** if `META-INF/encryption.xml` exists, refuse with
`"Este EPUB está protegido por DRM."` rather than rendering mojibake.

### G.2 · Parsing isolation

`PdfRenderer` and the zip/XML parsing run in a **separate process** with no
exported surface:

```xml
<service
    android:name=".service.DocumentParseService"
    android:process=":reader"
    android:exported="false" />
```

A native crash in the PDF parser then kills `:reader`, not the app — the user
sees "Não foi possível abrir este ficheiro", and their planner data, running
focus block and unsaved state all survive. This is the single most valuable
control for PDFs, because a native crash is otherwise unhandleable.

The `:reader` process is `exported="false"`; receives an already-validated file
path and never opens a `content://` URI or resolves a path itself; returns
rendered bitmaps or parsed chapter HTML over the binder and nothing else; writes
no files and holds no permissions of its own. The main process detects its death
(`DeathRecipient` / a failed binding) and surfaces the friendly error rather than
retrying in a loop.

A separate `android:process` shares the app's UID and therefore its storage
permissions. It is a **crash and fault-containment boundary, not a privilege
boundary.** That is the honest guarantee; do not document it as a sandbox.

### G.3 · WebView lockdown

There is no iframe `sandbox` attribute at the WebView level. These settings are
the equivalent, and **all of them are mandatory**:

```kotlin
settings.javaScriptEnabled = false               // no allow-scripts
settings.allowFileAccess = false
settings.allowContentAccess = false
settings.allowFileAccessFromFileURLs = false
settings.allowUniversalAccessFromFileURLs = false
settings.blockNetworkLoads = true                // no exfiltration, full stop
settings.setGeolocationEnabled(false)
settings.domStorageEnabled = false
settings.databaseEnabled = false
settings.mediaPlaybackRequiresUserGesture = true
// never: addJavascriptInterface(...)  — not under any condition
```

**Opaque origin:** load with `loadDataWithBaseURL(null, html, "text/html",
"utf-8", null)`. A null base URL gives the document an opaque origin, so it
cannot read app storage, cannot reach other origins, and has no same-origin
privileges to abuse. Never load a chapter from a `file://` URL.

**Refuse every navigation and every subresource:**

```kotlin
webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
webChromeClient = null   // no JS dialogs, no fullscreen, no permission prompts
```

Internal chapter links (`href="chapter7.xhtml#p3"`) are **not** exceptions —
resolve them in Kotlin against the parsed spine and scroll the reader ourselves.
The WebView never navigates.

**CSP as defence in depth.** Inject into the `<head>` of every wrapped chapter:

```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; sandbox">
```

This is a **second** layer: the WebView settings above are the primary control
and must stand on their own if the CSP is ever dropped by an engine quirk.

**Sanitise before rendering anyway.** Strip `<script>`, `<iframe>`, `<object>`,
`<embed>`, `<link>`, `<base>`, `<form>`, every `on*=` attribute, and any
`javascript:` / `data:text/html` / `intent:` URL. Prefer an **allow-list** of
tags and attributes over a block-list — a block-list of HTML is a losing game,
and the set a book needs is small (`p`, headings, `em`, `strong`, `blockquote`,
`ul`/`ol`/`li`, `img`, `a`, `br`, `hr`, `table` and friends).

### G.4 · XML parsing

`container.xml` and the OPF are attacker-controlled XML:

```kotlin
setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)   // no DTD
setFeature(XmlPullParser.FEATURE_VALIDATION, false)
```

No `DocumentBuilderFactory` without `disallow-doctype-decl`; no external entity
resolution; no XInclude. Cap the parse at the entry-size limit so a deeply
nested document cannot exhaust the stack — catch `StackOverflowError` at the
parse boundary and treat it as a malformed file.

### G.5 · Only what the user imported

- Files enter **only** through the SAF `OpenDocument` picker. There is no intent
  filter for opening books from other apps, no `ACTION_VIEW` handler, no
  directory scan, no "recent books on device".
- The reader opens exactly one path: `BookEntity.filePath`, which always
  resolves inside `filesDir/books/`. Verify with
  `File.canonicalPath.startsWith(BookFiles.dir(context).canonicalPath)` before
  opening — cheap, and it closes off a tampered database row.
- Nothing in a document can cause a fetch: `blockNetworkLoads`, the request
  interceptor and the CSP each independently prevent it.

### G.6 · Versioning

There is nothing to pin, and that is deliberate — section D means there is no
PDF or EPUB library in the tree to carry a CVE. Both engines update outside our
release cycle: `PdfRenderer` with the OS, WebView through Google Play system
updates.

**Don't assume a current WebView.** Read
`WebViewCompat.getCurrentWebViewPackage()` and, on a very old or absent WebView,
refuse to render EPUBs with `"Atualiza o Android System WebView para ler
EPUBs."` rather than rendering in an engine whose posture is unknown. PDFs are
unaffected.

### G.7 · Security acceptance — every PR that touches a book

- No `addJavascriptInterface` anywhere in the tree.
- `javaScriptEnabled` is never set true, in any code path or debug flag.
- No new dependency in `build.gradle.kts`.
- The parse service is `exported="false"` and on `:reader`.
- Unit tests exist for: a zip bomb, a `../` entry name, an XXE payload in the
  OPF, a chapter containing `<script>` and `on*=`, a PDF-magic file that is not
  a PDF, and an `encryption.xml` present. Each must be **rejected or neutered,
  asserted** — not merely "doesn't crash".

## H · Recording and reversibility

**Anything the app records without being asked must be removable.**

This is the lesson of `FIELD_FIXES.md` F1/F2 — twelve junk reading sessions the
app wrote by itself and offered no way to reach — and it now applies to every
future feature. Automatic capture is only defensible when it is reversible.

Concretely, before shipping anything that writes a row the user did not type:

1. There is a UI path to **see** it.
2. There is a UI path to **edit or delete** it.
3. Deleting its parent does not orphan it.
4. A wrong value cannot be permanent (see F4: a count with no ceiling).

## I · Workflow

- Ship via the `CLAUDE.md` workflow: **branch → commit → PR → CI green →
  squash-merge**. Never push to `main` directly.
- **One task is one PR.** Split anything that cannot be — except where splitting
  would leave the app in a half-corrected state, and then say so in the Why.
- **Status + Log update in the same PR as the code.** Not after.
- **A task that changes what a user can see updates the repo-root `README.md` in
  the same PR.** The READMEs went ~40 tasks without mentioning the app had
  become a reader. A README that describes half the app is worse than none,
  because it is believed.
- **`docs/CONTEXT.md` is updated in the same PR** whenever a task file's state
  changes — a task shipped, a file completed, an order changed.
- **Say what was verified.** A green CI and a working app are different things;
  `FIELD_FIXES.md` exists entirely because of that gap. Every Log line ends with
  `Verified:` and what was actually exercised. If a device never saw it, say
  so.

## J · Closed decisions — do not re-open these

Each one has been decided, with a reason. Re-proposing them costs a session
every time.

| Decision | Why | When |
|---|---|---|
| **No cover art.** | Identity, not effort. The app has no imagery anywhere; that *is* the look. Extracting covers is technically easy, which is exactly why this needs saying out loud. Shelf cards stay typographic. | Aug 2026, `BOOK_READER` |
| **No pagination in the EPUB reader.** | Offered and declined; the owner prefers continuous scroll by chapter. Technically possible without JS (CSS `column-width` + native horizontal scrolling), recorded so nobody re-derives it as new. `FIELD_FIXES` F7 gives the orientation pagination would have given, honestly. | `FIELD_FIXES` |
| **The reading session still starts on its own.** | An explicit "start reading" button was proposed and declined: forgetting to press it loses an hour unrecoverably, which is worse than junk sessions, which are removable. The fix is a guard with teeth plus the ability to delete. | `FIELD_FIXES` |
| **An attached EPUB counts in percent.** | It has no pages; text reflows with type size. Also the unit `BookMath.wordsPerUnit` already uses. | `FIELD_FIXES` |
| **One habit list.** | `BOOK_READER` R7 dropped a `bookHabit` column deliberately; `FIELD_FIXES` F13 removes the tides from the book-mode tab rather than splitting them in two. Twice decided. | `BOOK_READER`, `FIELD_FIXES` |
| **No highlights from a text selection in an EPUB.** | Reading a WebView's selection needs `window.getSelection()`, and G.3 turns JavaScript off. Enabling scripting to render untrusted HTML for a nicer note flow is a bad trade. `BOOK_LIBRARY` L6 is the honest version. | `BOOK_LIBRARY` |
| **No fourth tab, no cloud sync, no account, no store/OPDS catalogue, no Goodreads/StoryGraph import.** | Out of scope for a private offline app. | `BOOK_LIBRARY` |
| **Attached documents are not bundled into the book export.** | They are the user's own files, can be hundreds of megabytes, and the missing-file behaviour is already built. | `BOOK_LIBRARY` L2 |
| **Web parity is retired for visuals.** | Data shape, backup format and behaviour parity still hold; CSS-derived numbers do not. | `POLISH` |
| **No state-management restructuring as polish.** | `AppViewModel` and the repo stay as they are unless a task explicitly adds one pref. | `POLISH`, `UX_FIXES` |
| **No Settings row is ever deleted.** | `UX_FIXES` U5 re-grouped and re-styled; it removed no functionality, and neither may anything after it. | `UX_FIXES` |

**Adding to this table** is part of finishing a task that closed a question.
Write the decision, the reason and the date — not just the outcome.

## K · Never do this

The absolute list. No task, no spec, no deadline overrides these.

1. **Never set `javaScriptEnabled = true`.** Anywhere, including debug paths.
2. **Never call `addJavascriptInterface`.**
3. **Never add a dependency** to run a task. Refuse the file, or ask the owner.
4. **Never let anything book-shaped into `pauta.v4`,** or change its shape.
5. **Never write outside `filesDir`,** and never upload an attached file.
6. **Never extract an EPUB to disk.** Read entries by name from the open archive.
7. **Never resolve a zip entry name to a path.** Validate and reject.
8. **Never push to `main`,** never `push --force`, never `reset --hard` on
   shared history. `.claude/settings.json` denies these; do not work around it.
9. **Never renumber a shipped task,** and never rewrite a shipped migration or a
   Log line. Append.
10. **Never record something the user cannot delete** (section H).
11. **Never invent a number and present it as measured.** An estimate carries
    `≈` and says what it assumed. This is the rule `FIELD_FIXES` exists to
    restore.
12. **Never claim a green CI means it works.** Say what you actually exercised.

---

## Log (append when a rule is added, changed or retired)

Rules change. When one does, say which, why, and what it replaced — a guardrail
whose history is invisible gets quietly re-litigated.

<!-- YYYY-MM-DD · <rule> · #PR · <what changed and why> -->
2026-08-03 · file created · — · consolidated from the Global guardrails sections of `NATIVE_IMPROVEMENTS`, `BOOK_MODE`, `POLISH`, `BOOK_READER`, `UX_FIXES`, `BOOK_LIBRARY` and `FIELD_FIXES`; the reader's Security model moved here in full from `BOOK_READER.md` so that finished files could be archived without breaking a binding reference; sections H, K and this Log are new.
