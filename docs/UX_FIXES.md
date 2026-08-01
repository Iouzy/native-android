# UX fixes — task file

> **Concept.** `docs/POLISH.md` (P1–P10, all shipped) modernised the app's
> *surface*: motion, tokens, tab bar, per-screen sweeps. What it didn't touch is
> a handful of things that are still **awkward to use**, in both lenses:
>
> - opening a sheet with a text field is visually a mess — the keyboard arrives
>   mid-animation and shoves the half-drawn sheet
> - every timer offers 25/50/90 and nothing else; there is no way to type
>   "40 minutes"
> - the Hoje intention composer sprouts three rows of unlabeled grey pills the
>   moment you start typing
> - Settings is 10 sections and ~30 rows in one flat scroll, with no search and
>   an "Aparência" section that has become a junk drawer
>
> These are ordinary usability fixes, not a redesign. The identity, the palette
> and the type stay exactly as they are.
>
> Ships as 7 self-contained tasks (U1…U7). Each task is one PR.

> **How to use (human).** In a fresh Claude Code session, prompt:
>
> > Read `docs/UX_FIXES.md`. Do ONLY task **U1** — follow its spec and the
> > Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR → CI →
> > squash-merge), then set the task's Status and append one line to the Log.
>
> Or stateless: *"Read `docs/UX_FIXES.md` and do the first task whose Status is
> `pending`."*

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Global guardrails (every task)

All guardrails from `docs/NATIVE_IMPROVEMENTS.md` and `docs/POLISH.md` apply
unchanged. The ones that bite hardest here:

- **The identity is untouchable:** `LocalPautaColors` only, `SerifFamily` /
  `MonoFamily` / `SansFamily`, `clickableNoRipple`. No Material ripples, no
  elevation, no FABs. Quiet is the point.
- **Both lenses survive every task.** Every shared primitive touched here is
  used by the planner *and* by book mode (sepia). Acceptance always includes
  "book mode on and off both look right".
- **Prefs are law:** `reducedMotion`, `haptics`, `textScale`, `highContrast`,
  the 1/2/3 keyboard shortcuts and TalkBack descriptions must all still work.
- **No new dependencies.**
- **No state-management restructuring.** `AppViewModel` and the repo stay as
  they are — except where a task explicitly adds one pref.
- **No row is deleted from Settings.** U5 re-groups and re-styles; it does not
  remove functionality.

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

---

## Phase U-0 — the bug and the two annoyances

### U1 · Sheets: stop the keyboard fighting the open animation — Status: done (PR #156)

**Depends on:** nothing

**Why:** the single worst-looking moment in the app. Open "Novo bloco" (or any
sheet with an autofocused field) and the sheet is caught halfway up when the
keyboard appears, `imePadding()` yanks it, and the content visibly jumps and
clips. It reads as a bug, because it is one.

**The cause.** `PautaSheets.kt` → `rememberAutoFocusRequester()`:

```kotlin
internal fun rememberAutoFocusRequester(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)                       // ← a magic number, not a signal
        runCatching { fr.requestFocus() }
    }
    return fr
}
```

A fixed 120ms delay races the `ModalBottomSheet` expand animation, which is
longer than that on most devices (and much longer under a slow animator scale).
The keyboard therefore opens while the sheet is still sliding, and
`PautaSheet`'s `imePadding()` (`ui/PautaSheet.kt`) re-lays-out a sheet that
hasn't finished arriving.

**Files to touch:**
- `ui/PautaSheet.kt` — expose the settled signal
- `ui/screens/PautaSheets.kt` — `rememberAutoFocusRequester`
- every call site only if its signature must change (prefer not changing it)

**The fix.** Wait for the sheet to actually be open, not for a guessed duration:

1. In `PautaSheet`, provide the sheet's settled state through a
   `CompositionLocal` (e.g. `LocalSheetSettled: State<Boolean>`), set from
   `sheetState.currentValue == SheetValue.Expanded` — `currentValue`, not
   `targetValue`, so it flips when the animation *finishes*.
2. `rememberAutoFocusRequester()` reads that local and requests focus on the
   first frame where it is true. Keep the current behaviour as a fallback when
   the local is absent (the function is also used outside sheets — e.g.
   `AnnualGoalSheet`, `PinScreen`) so no call site has to change.
3. Under `reducedMotion` the sheet has no animation to wait for, so focus is
   requested immediately.

Also verify the reverse direction: on dismiss, hide the keyboard *before* the
sheet animates out, so the sheet doesn't fall through the gap the keyboard
leaves. `LocalSoftwareKeyboardController.hide()` in the dismiss path.

**Out of scope:** changing which fields autofocus, changing sheet layout,
`skipPartiallyExpanded`.

**Accept:** opening every sheet with an autofocused field (Novo bloco, Nova
intenção-like sheets, Objetivo anual, Nota rápida, Adicionar livro, habit form)
shows the sheet arriving *then* the keyboard, with no jump or clipping;
dismissing is equally clean; with the developer "Animator duration scale" set
to 5× the sequence is still correct (this is the reliable way to test it); the
120ms magic number is gone; book mode and planner both correct; CI green.

### U2 · Timers: preset sets + custom minutes — Status: pending

**Depends on:** nothing

**Why:** every duration control in the app offers `Sem limite · 25 · 50 · 90`
and nothing else. Those are Pomodoro numbers. There is no way to say "40
minutes" anywhere a timer starts — the only free-typed minutes field in the app
is in the *manual add block* sheet, which is a different flow and easy to miss.

**Files to touch:**
- `ui/PautaSheet.kt` or `ui/screens/PautaSheets.kt` — one shared
  `DurationPicker` composable (put it wherever the other shared field
  primitives live; do not write two)
- `ui/screens/PautaSheets.kt` — the Novo bloco sheet
- `ui/screens/BookSessionScreen.kt` — the reading session card
- `ui/screens/SettingsScreen.kt` — the preset-set choice
- `data/entity/Entities.kt` + `data/AppDatabase.kt` — one pref column
- `ui/viewmodel/AppViewModel.kt`
- `i18n/I18n.kt`

**One shared `DurationPicker`:**

```kotlin
@Composable
internal fun DurationPicker(
    minutes: Int,                 // 0 = Sem limite
    presets: List<Int>,
    onChange: (Int) -> Unit,
)
```

- Renders `Sem limite` + one pill per preset + a final **`Outro…`** pill.
- `Outro…` reveals an inline numeric `BoxedField` below the pills, mono,
  autofocused, `ImeAction.Done` commits.
- Valid range **1–600**; outside it, `FieldError(tr("Entre 1 e 600 min."))` and
  the sheet's confirm button is disabled.
- Picking a preset clears the custom field; typing a custom value deselects the
  presets. A value that happens to equal a preset just selects that preset.
- Wraps with `ChipFlow`, so `textScale = 1.3` doesn't overflow.

**The preset set becomes a setting.** New pref column (Room migration; bump the
version and add the `ALTER TABLE` — coordinate with `docs/BOOK_READER.md` R2 if
that lands first, one migration per version):

| Column | Type | Default | Notes |
|---|---|---|---|
| `timerPresets` | String | `"pomodoro"` | `pomodoro` / `simples` · `// native-only` |

| Set | Presets |
|---|---|
| `pomodoro` | 25 · 50 · 90 |
| `simples` | 15 · 30 · 45 · 60 |

In Settings → Foco, a `SegmentedRow`: **`Tempos do temporizador`** ·
`Pomodoro` / `Simples`. Both sets always end in `Outro…`, so a custom time is
never more than one tap away regardless of the choice.

**Book mode overrides the default:** reading sessions use `simples` unless the
user has explicitly chosen `pomodoro`. Reading isn't Pomodoro work. (If
`docs/BOOK_READER.md` R1 already changed the reading presets, this task
replaces that hard-coded list with `DurationPicker` — one primitive, not two.)

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Outro…` | `Custom…` |
| `Entre 1 e 600 min.` | `Between 1 and 600 min.` |
| `Tempos do temporizador` | `Timer presets` |
| `Pomodoro` | `Pomodoro` |
| `Simples` | `Simple` |

**Out of scope:** changing what `targetMs` does once a block runs, per-project
default durations, a countdown mode.

**Accept:** a 40-minute block can be started from Novo bloco and from a reading
session; the setting switches both preset sets; the custom value survives
rotation while the sheet is open; an invalid value can't be submitted; existing
blocks and their targets are unaffected; the `pauta.v4` export round-trip is
unchanged; CI green.

---

## Phase U-1 — the Hoje tab

### U3 · The intention composer — Status: pending

**Depends on:** nothing (U1 first is nicer to test against)

**Why:** type one character into "Nova intenção…" and three rows of unlabeled
grey pills unfold — `1 2 3`, then `manhã tarde noite`, then a bare `min` box
next to "Adicionar". Nothing says what `1 2 3` means. Nothing groups them.
It's the least finished surface in the planner, and it's the first thing you
touch every morning.

**The current shape** — `HojeScreen.kt` → `AddIntentionForm`: a raw Material
`TextField` (not the app's own field primitives, which is why it doesn't match
the sheets), then `if (expanded)` three stacked `Row`s of `Pill`s plus a second
raw `TextField` for minutes.

**Files to touch:**
- `ui/screens/HojeScreen.kt` — `AddIntentionForm`, `Pill`, `HeaderChip`
- `i18n/I18n.kt`

**The redesign — one line by default, details on request:**

1. **Resting state stays exactly as it is:** a single underlined field reading
   `"Nova intenção…"`. This is already right; don't touch it.
2. **On typing, show one row, not three:**

   ```
   [ Nova intenção… ______________________________ ]
     prioridade 1 2 3   ·   manhã tarde noite   ·   ⏱ __     Adicionar
   ```

   All three groups on one wrapped `ChipFlow` line, each preceded by a small
   `MonoFamily` 9sp ink4 label so the pills mean something:
   `PRIORIDADE` · `QUANDO` · `MIN`. The labels are the fix — the pills
   themselves are fine.
3. **Replace both raw `TextField`s** with the app's own `UnderlineField` /
   `BoxedField`, so the composer matches every sheet in the app.
4. **`Adicionar` stays right-aligned** and becomes a proper `PautaButton`
   (Primary, compact) rather than a bare accent `Text` — it's the commit
   action for the whole form.
5. **Collapse cleanly:** committing or clearing the field returns to the single
   resting line, animated with `PautaMotion` (snap under `reducedMotion`).

**Alternative if the one-line version still crowds at `textScale = 1.3`:** keep
the labels and let `ChipFlow` wrap to two lines. Do **not** go back to three
fixed rows.

**Also in this task — the header actions.** Four buttons of four different
widths stacked in a ragged right-hand column (`HeaderChip` × 4, lines 295–298:
`dias anteriores ↗` / `a semana ↗` / `Rotinas ↗` / `revisão ↗`). Make them a
single wrapped `ChipFlow` of equal-height chips aligned to the right, so the
column stops looking like a staircase. Casing is currently inconsistent too
(`Rotinas` capitalised, the rest lowercase) — the eyebrow style uppercases
them all anyway, so normalise the source strings to lowercase.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `prioridade` | `priority` |
| `quando` | `when` |

**Out of scope:** changing what priority/when/minutes *do*, the intention list
below the composer, the reflection card, adding new intention fields.

**Accept:** the resting composer is unchanged; typing reveals one labelled row
instead of three unlabeled ones; every field uses the app's own primitives;
the header chips align; `textScale = 1.3` and `highContrast` both hold up;
priority / when / minutes still save exactly as before; CI green.

---

## Phase U-2 — Settings (U5 → U6 → U7 in order; each depends on the last)

### U4 · Settings: regroup + row hierarchy — Status: pending

**Depends on:** nothing

**Why:** `docs/POLISH.md` P9 already unified the *anatomy* — one eyebrow, one
card, one row height. The rhythm is fine. What's dated is the **information
architecture**: 10 sections and ~30 rows in one flat 500-line scroll, ordered
by historical accretion rather than by use. And "Aparência" has become a junk
drawer holding Língua, Tema and Cor de destaque (fine) alongside Vibração,
Papagaio, Ecrã inteiro and Modo livro (not appearance at all) — which is
exactly why the book-mode toggle is buried six rows down.

Don't restyle. **Re-organise.**

**Files to touch:**
- `ui/screens/SettingsScreen.kt`
- `i18n/I18n.kt`

**(a) Regroup — 10 sections → 7. No row is deleted.**

| New section | Contains |
|---|---|
| **Modo** | the lens switcher (placeholder here; U7 makes it the real control) + the annual book goal when `bookMode` is on |
| **Aparência** | Língua · Tema · Cor de destaque · Tamanho do texto · Alto contraste · Reduzir movimento *(absorbs Acessibilidade — same mental category to a user)* |
| **Foco e lembretes** | Manter ecrã ligado · Som ao concluir · Tempos do temporizador (U2) · Notificações + the three times · Testar notificação |
| **Companhia** | Vibração · Papagaio ajudante · Ecrã inteiro |
| **Análise e objetivos** | Revisão semanal · Retrospetiva do ano · Como funcionam as marés · Objetivos trimestrais |
| **Dados e privacidade** | Bloqueio por PIN · Desbloqueio biométrico · Cópia automática + frequência + pasta · Exportar · Enviar para a nuvem · Importar · Marés arquivadas |
| **Sobre** | version + build date · the update state (see U6) · source-code link |
| **Zona perigosa** | unchanged — same extra gap, same rule, same red header |

**(b) Row hierarchy.** Today `ActionRow` paints *every* label in `colors.accent`
at 16sp, so Revisão semanal, Exportar dados, Escolher pasta and Tentar outra
vez all shout in the same terracota. Accent everywhere is accent nowhere.

- Action-row **label** → `colors.ink` (matching `ToggleRow`, which already does
  this).
- Action-row **value**, when there is one → right-aligned, `colors.accent`
  (the chosen folder, the version, "Nova versão").
- A quiet `›` in `ink4` on the right of every navigating row, so "tappable" is
  shown rather than inferred from colour.
- Danger rows keep the red — and use the existing `DangerRed` constant instead
  of the inline `Color(0xFFE53935)` currently hard-coded in `ActionRow`.

**(c) Values inline.** Where a `SegmentedRow` exists only to pick one of 3+
options and isn't frequently changed (Tamanho do texto, Frequência da cópia),
collapse it to `label — value ›` opening a small picker sheet. Keep the
segmented control for Língua and Tema (2–3 options, changed often, worth the
space).

**(d) Two small correctness fixes while in here:**
- The hero subtitle is hard-coded `"Hoje · Pauta · Marés"` and stays wrong in
  book mode — make it read `"Estante · Sessão · Hábitos"` when `bookMode`.
- The footer links to `github.com/Iouzy/psychic-guide`, which is not this
  repository. Point it at the real one.

**Out of scope:** search (U5), the update sheet (U6), the mode switcher control
itself (U7).

**Accept:** every setting that existed still exists and still works; seven
sections in the order above; Zona perigosa unchanged; only values are accent;
the hero subtitle follows the mode; the source link is correct; book mode and
planner both look right; CI green.

### U5 · Settings search — Status: pending

**Depends on:** U4

**Why:** thirty rows across seven sections is past the point where scanning
works. Search is the single change that most makes settings stop being a hunt.

**Files to touch:**
- `ui/screens/SettingsScreen.kt`
- `i18n/I18n.kt`

**Spec:**

- An `UnderlineField` pinned directly under the "Definições" header,
  placeholder `"Procurar definições…"`. Not autofocused — the resting screen
  must still be the list.
- Typing filters to matching rows, each still inside its section card, with the
  section eyebrow retained above it so the result keeps its context.
- Match on the label **and** the subtitle, accent- and case-insensitively
  (`"acao"` finds `"Ação"`, `"pin"` finds `"Bloqueio por PIN"`).
- Matching is done against the current language's strings via `tr(...)`, so it
  works in both PT and EN.
- No results → a quiet `EmptyState` line: `"Nada encontrado."`
- Clearing the field restores the full list at the previous scroll position.
- **Zona perigosa is searchable but never matches an empty query** — it stays
  where it is, below its rule, and never floats to the top of a result list.

Implementation note: build a flat `List<SettingsRow>` index of
`(section, label, subtitle, content)` once, and render either the grouped list
or the filtered one from it. Do not maintain the row definitions twice.

**Out of scope:** fuzzy matching, search history, deep-linking into sheets.

**Accept:** typing "pin", "cópia", "vibra", "backup" each finds the right rows
in both languages; filtered rows are fully functional (toggles toggle, actions
act); clearing restores the list; CI green.

### U6 · Update state as a sheet + the Sobre section — Status: pending

**Depends on:** U4

**Why:** the Atualizações block is a seven-branch `when` rendered inline in the
main scroll — checking, failed, downloading, progress, available, notes,
up-to-date, plus a permissions hint and a paragraph of conflict advice, at
three different font sizes with inconsistent padding. It's the least finished
block in the file and it occupies the most vertical space for something you
look at once a month.

**Files to touch:**
- `ui/screens/SettingsScreen.kt`
- `i18n/I18n.kt` (no new strings expected — reuse the existing ones)

**Spec:**

- **Sobre** becomes three rows: `Versão` (value = `v1.<run> · YYYY-MM-DD`),
  `Atualizações` (value = `"Está atualizado."` in ink3, or `"Nova versão"` in
  accent when one is available), `Código-fonte ↗`.
- Tapping `Atualizações` opens a `PautaSheet` holding the whole state machine:
  the check/download actions, the progress line, the failure messages, the
  release notes, the install-permission hint and the conflict paragraph.
- The **only** thing that stays on the main list is the one-line status, so an
  available update is still visible without opening anything.
- Behaviour is unchanged — this is a relocation, not a rewrite of the updater.
  `AppViewModel`'s update flows and `service/AppUpdater` are untouched.

**Out of scope:** changing how updates are checked, downloaded or installed;
touching `service/AppUpdater`.

**Accept:** every update state still reachable and correct (test at minimum:
idle, checking, up-to-date, available with notes, network failure); an
available update is visible from the list without opening the sheet; ~80 lines
leave the main scroll; CI green.

### U7 · The mode switcher — Status: pending

**Depends on:** U4

**Why:** switching between the planner and book mode currently costs a tap on
the gear, a scroll past three sections, and a hunt for the last row of the
Aparência card. It is the app's biggest state change and its most buried
control.

**Files to touch:**
- `ui/screens/SettingsScreen.kt` — the header switcher
- `ui/MainScaffold.kt` — the gear long-press, the palette crossfade
- `i18n/I18n.kt`

**(a) A switcher in the settings header.** The header row is `←` + "Definições"
with the entire right side empty. Put a two-state pill there:

```
←  Definições                              [ Pauta │ Livro ]
```

`MonoFamily`, uppercase, accent fill on the active side, `clickableNoRipple`.
Not a bare `Switch` — a naked toggle in a header doesn't say what it toggles.
Remove the `Modo livro` row from Aparência; the Modo section from U4 keeps a
descriptive line pointing at the header control (and the annual goal, when book
mode is on).

**(b) Long-press the gear** in `StatusRow` toggles the mode directly, with a
haptic tick (gated on the `haptics` pref) and an undo snackbar:
`"Modo livro ligado · Anular"`. Reuse the existing snackbar host. A single tap
still opens Settings, unchanged. This is the fast path; the header pill is what
teaches it exists.

**(c) Crossfade the palette.** `MainScaffold.kt` swaps `bookPautaColors` in one
frame, so the whole app flashes. Wrap the swap in a `Crossfade` /
`animateColorAsState` over `PautaMotion.Slow` so it reads as a page turn.
`EnterTransition.None` / instant under `reducedMotion`.

**(d) Note for `docs/BOOK_READER.md` R8.** The second launcher icon sets the
same `bookMode` boolean this control does — the icon is a shortcut to this
toggle, not a parallel system. Whichever acted last wins, and there is exactly
one source of truth. Nothing here needs to know the alias exists.

**New i18n strings (`// native-only`):**

| PT | EN |
|---|---|
| `Modo livro ligado` | `Book mode on` |
| `Modo livro desligado` | `Book mode off` |
| `Livro` | `Book` |

**Out of scope:** app shortcuts, a QS tile for the mode, the launcher alias
(that's `docs/BOOK_READER.md` R8).

**Accept:** the header pill switches modes and reflects the current one; the
Aparência row is gone and nothing else moved; long-pressing the gear toggles
with a haptic and an undo that actually reverts; a single tap still opens
Settings; the palette crossfades instead of flashing, and snaps under reduced
motion; CI green.

---

## Task dependency graph

```
U1 (sheet keyboard timing)      — independent, do first
U2 (timer presets + custom)     — independent
U3 (Hoje composer + header)     — independent

U4 (settings regroup + hierarchy)
 ├─ U5 (settings search)
 ├─ U6 (update sheet + Sobre)
 └─ U7 (mode switcher)
```

U1–U3 are three small independent PRs and can ship in any order. U4 must land
before U5–U7, which are then independent of each other.

**Cross-file note:** `docs/BOOK_READER.md` R1 changes the reading-session
duration pills. If U2 ships first, R1 should use U2's `DurationPicker` instead
of writing its own list. If R1 ships first, U2 replaces R1's hard-coded list
with the shared primitive. Either order works — just don't leave two.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-08-02 · U1 · #n · autofocus waits for the sheet to settle instead of a 120ms guess -->

2026-08-01 · U1 · #156 · `LocalSheetSettled` (from `sheetState.currentValue`) replaces the 120ms guess in `rememberAutoFocusRequester`; keyboard hidden before dismiss; the three other copies of the delay folded into the shared requester. Deviation: the settle signal is used regardless of `reducedMotion` — M3 still slides the sheet under that pref, so short-circuiting would have kept the bug for those users.
