# Polish — UI modernisation task file

> **Concept.** The app reached full feature parity but the surface still feels
> blunt: tab switches hitch and lose state, the three tab headers don't line
> up, radii/spacing/type sizes are ad hoc per screen, and there is almost no
> motion language. This file drives a **polish + modernisation pass**: fix the
> tab-navigation jank first, then build small design foundations (motion,
> surface, type tokens), then sweep each screen against them, then add the
> micro-interaction layer. **The retired web app is NOT the reference** — the
> owner asked for a cleaner, more modern feel than the web ever had. Design
> forward from the paper/ink identity, not backward from web CSS.
>
> The pass ships as 10 self-contained tasks (P1…P10). Each task is one
> session/PR. Phases are priority order; tasks within a phase are independent
> unless "Depends on:" says otherwise.

> **How to use (human).** In a fresh Claude Code session, prompt:
>
> > Read `docs/POLISH.md`. Do ONLY task **P1** — follow its spec and the
> > Global guardrails. Ship it via the CLAUDE.md workflow (branch → PR → CI →
> > squash-merge), then set the task's Status and append one line to the Log.
>
> Or stateless: *"Read `docs/POLISH.md` and do the first task whose Status is
> `pending`."*
>
> **How to use (Claude).** This file + `CLAUDE.md` are your complete briefing.
> The "Current state" section below was written from a full read of the shell
> and screens (July 2026) — trust it, open only the files your task names, and
> don't re-survey. Always update Status + Log in the same PR as the code.

Source paths below are relative to
`app-native/app/src/main/kotlin/com/pauta/app/`.

---

## Global guardrails (every task)

All guardrails from `docs/NATIVE_IMPROVEMENTS.md` apply, with one amendment:

- **Web parity is retired for visuals.** Comments and specs that mirror web
  CSS ("0.08em of 10sp", `tab-mares.jsx`…) are historical context, not
  constraints. When a task's spec conflicts with an old web-derived value, the
  task's spec wins. Data shape, backup format and behaviour parity still hold.
- **The identity is untouchable:** paper/ink palette via `LocalPautaColors`
  only; `SerifFamily` (display) / `MonoFamily` (meta) / `SansFamily` (body);
  `clickableNoRipple` — the app must stay *quiet*. Polish means refinement,
  not Material-default ripples, elevations, or FABs.
- **Prefs are law:** every new animation goes through `prefs.reducedMotion`
  (snap/None when true); every new haptic through `prefs.haptics`. The
  existing pattern is `val animate = !prefs.reducedMotion` + `snap()` /
  `EnterTransition.None` fallbacks — follow it.
- **Both lenses must survive every task.** Each screen has a planner face and
  a book-mode face (`prefs.bookMode`, sepia palette via `bookPautaColors`).
  Any shared primitive you change is used by both. Acceptance always includes
  "book mode on and off both look right".
- **No new dependencies.** Pager, animation, haptics APIs are all in the
  androidx artifacts already present.
- **No state-management restructuring.** `AppViewModel` flows and repo stay
  as-is; polish is UI-layer work (P8's memoisation is in-composable only).
- **Don't regress T-tasks:** `textScale`, `highContrast`, `immersive`,
  keyboard tab shortcuts (1/2/3), TalkBack content descriptions.

---

## Current state — read before any task (July 2026 survey)

### Shell anatomy (`ui/MainScaffold.kt`, ~660 lines)

`MainScaffold` hosts a `NavHost` (routes HOME / SETTINGS / GOALS /
YEAR_REVIEW / TIER_GUIDE / HISTORY; A8 push transitions, `tween(300)`,
predictive-back pop). Book mode wraps the NavHost in
`CompositionLocalProvider(LocalPautaColors provides bookPautaColors(...))`;
PIN/onboarding/what's-new overlays sit outside it.

`HomeShell` (inside HOME) is: `StatusRow` (gear only) → `HorizontalPager`
(3 pages: `HojeScreen` / `PautaScreen` / `MaresScreen`, each taking a
`bookMode` param) → `TabBar`. Floating: Pip bottom-right (`ParrotCompanion`,
`bottom = 80.dp`), K9 capture chip bottom-left (`bottom = 84.dp`), undo
snackbar bottom-centre (`bottom = 84.dp`). Hardware keys 1/2/3 switch tabs
via `onKeyEvent` on the shell Column.

### The tab-switch bug — diagnosis (this is what "buggs a bit" is)

1. **`HorizontalPager` uses the default `beyondViewportPageCount = 0`.**
   Neighbouring pages are composed *during* the drag and disposed when ≥1
   page away. Consequences: a visible hitch on the first frames of every
   swipe (Hoje and Marés are heavy — see below); hopping Hoje↔Marés composes
   and immediately disposes Pauta mid-flight; and a disposed page loses its
   `remember` state — LazyColumn scroll positions reset, Marés month
   navigation resets, in-progress form state in non-sheet UI resets.
2. **`TabBar(current = Tab.entries[pager.currentPage])`** — `currentPage`
   only flips once the settle passes the midpoint, so the highlighted tab
   lags the finger/tap by half a transition. There is no animated indicator;
   icon/label tint snaps between `accent` and `ink3` with no tween.
3. **First-composition weight:** `HojeScreen` collects ~16 StateFlows and
   builds several derived lists up front; `MaresScreen` builds a
   `HabitModel` per habit and runs `HabitCalculator` month stats per row on
   every recomposition (`modelOf(h)` is called in at least three places per
   pass with no memoisation). This work lands exactly on the swipe frames.

P1 fixes (1) and the tracking half of (2); P2 rebuilds the bar; P8 fixes (3).

### Design-token inventory (what exists to build on)

- **Colours** (`ui/theme/Color.kt`): `PautaColors` = `paper` `paper2`
  `paper3` `ink` `ink2` `ink3` `ink4` `rule` `accent` `accentSoft`
  `accentBg` `good` `surfaceDark` `onDark` `onDark2` `tabbarBg` `pageBg`
  `isDark`. Light/dark/high-contrast variants + `bookPautaColors(dark)`
  sepia. Exposed via `LocalPautaColors`. `ui/theme/AccentColor.kt` holds the
  user-accent presets; `ui/theme/Theme.kt` maps core tokens onto a Material3
  scheme and does edge-to-edge system bars.
- **Type** (`ui/theme/Type.kt`): Geist (variable, Sans), Geist Mono
  (variable), Instrument Serif (+italic). A `PautaTypography` (Material3
  `Typography`) exists but **screens ignore it** — every `Text` hardcodes
  `fontSize`/`lineHeight`/`letterSpacing` inline.
- **Primitives:** `PautaSheet` + `PautaButton` (Primary/InkPrimary/Ghost) +
  `SheetEyebrow` in `ui/PautaSheet.kt` (A5: bottom sheet on phones, centred
  dialog ≥600dp); `clickableNoRipple` / `combinedClickableNoRipple` in
  `ui/Modifiers.kt`; `UnderlineField` `BoxedField` `SelectPill` `ChipFlow`
  `FieldError` `DangerRed` `rememberAutoFocusRequester` (all `internal`) in
  `ui/screens/PautaSheets.kt`; `PeriodLabel` (accent month chip);
  `PautaIcons` (5 stroke icons: Hoje/Pauta/Mares/Gear/Check); Pip in
  `ui/Parrot.kt`; `CellState`/`cellStateFor` tide-cell logic in
  `ui/TideHelpers.kt`.

### Measured inconsistencies (the "blunt" feeling, concretely)

- **Screen titles:** Marés serif 38sp / Hoje has a different date-header
  treatment / book screens (Estante, Sessão) serif 34sp — the headline
  visibly jumps size and baseline as you swipe between tabs.
- **Eyebrow labels exist as three near-identical private copies:**
  `SectionLabel` (BookShelfScreen, mono 9sp ls 1.8), `MonoSectionLabel`
  (PautaScreen, internal), `SheetEyebrow` (public) — plus inline one-offs in
  MaresScreen/BookHabitsScreen. Sizes drift 9–10sp, letterSpacing 0.9–2.
- **Corner radii in use:** 4, 6, 8, 9, 10, 12, 14, 16, 20, 50, 999 dp — ad
  hoc per call site (~180 `RoundedCornerShape(` sites under `ui/`).
- **The card pattern** (`clip(RoundedCornerShape(n)) + border(1.dp, rule) +
  background(paper2) + padding`) is copy-pasted dozens of times with n ∈
  {10, 12, 14, 16} and paddings ∈ {12, 14, 16, 20}.
- **Motion is ad hoc:** `tween(150/220/300)`, assorted `spring()`s, A3 cell
  animations, A8 nav transitions — no shared durations/easings; several
  surfaces (sheets opening, tab switches, list reorders outside Hoje) have
  no motion at all.
- **Haptics** exist in exactly one place (Pauta conclude long-press).

---

## Status legend

`pending` · `in-progress (PR #n)` · `done (PR #n)` · `skipped (reason)`

## Suggested model per task

| Model | Tasks |
|---|---|
| **Fable 5** (design taste, cross-file judgement) | P2, P5, P6, P7 |
| **Opus 5** (well-specified builds & sweeps) | P1, P3, P4, P8, P9, P10 |

Either model may do any task; this is a cost/benefit hint, not a rule.

---

## Phase P-0 — the tab bug (do these first)

### P1 · Pager smoothness + honest tab highlight — Status: done (PR #142)

**Depends on:** nothing. **This is the bug fix; ship it before anything else.**

**Files to touch:** `ui/MainScaffold.kt` only.

**Spec:**

1. Keep all three tabs alive:
   ```kotlin
   HorizontalPager(
       state = pager,
       beyondViewportPageCount = 2, // all 3 pages stay composed; scroll state survives
       modifier = …,
   )
   ```
   Two extra resident LazyColumns are cheap; this removes the first-swipe
   composition hitch and makes scroll/month/nav state survive tab hops.
2. Drive the highlight from the *destination*, not the settled page:
   `Tab.entries[pager.targetPage]` (falls back to `currentPage` when idle).
   On tap and on fling the highlight moves immediately.
3. Tint transition: `animateColorAsState(tween(180))` for icon + label in
   `TabBar` (snap when `reducedMotion`).
4. Optional but wanted: one subtle haptic tick when the pager settles on a
   new page (`LaunchedEffect` on `settledPage`; `HapticFeedbackType.
   SegmentTick` or `LongPress` fallback; gated on `prefs.haptics`).

**Out of scope:** visual redesign of the bar (P2). Don't touch the screens.

**Accept:** swipe Hoje→Marés→Hoje preserves both lists' scroll positions and
Marés' viewed month; no hitch entering a tab for the second time; highlight
tracks tap/fling immediately; 1/2/3 keys still work; identical behaviour in
book mode; CI green.

### P2 · Tab bar redesign — Status: done (PR #143)

**Depends on:** P1.

**Files to touch:** `ui/MainScaffold.kt` (`TabBar`), `ui/theme/Motion.kt`
(only if P3 already landed — else inline specs and let P3 sweep them).

**Spec:** a modern, quiet bar that keeps the mono-uppercase identity:

- A single **sliding indicator** (accent, 3dp tall, rounded-full, width of
  the label) under the active tab. It glides with the finger: derive its
  offset from `pager.currentPage + pager.currentPageOffsetFraction` so a
  half-swipe shows the indicator halfway between tabs. Under `reducedMotion`
  it jumps to the settled tab instead.
- Icon gets a small settle nudge (spring scale 1 → 1.06 → 1 on becoming
  active; skip when reduced).
- Selected label: `FontWeight.SemiBold` + accent (as today); unselected
  `ink3`. Keep the 1dp top `rule` hairline and `tabbarBg`.
- Bar height/paddings unchanged (Pip/chip/snackbar offsets depend on it).

**Out of scope:** relocating tabs, adding a 4th item, bottom-bar hiding.

**Accept:** indicator tracks a slow drag pixel-for-pixel; tap animates it
across; book-mode labels (Estante/Sessão/Hábitos) size the indicator
correctly; reduced motion = instant jumps; CI green.

---

## Phase P-1 — foundations (P3, P4, P5 independent of each other)

### P3 · Motion tokens — Status: done (PR #144)

**Depends on:** P1 (so its specs can be folded in).

**Files to create/touch:** `ui/theme/Motion.kt` (new); mechanical sweep of
existing ad-hoc specs in `ui/MainScaffold.kt`, `ui/screens/MaresScreen.kt`
(A3 cell anims), `ui/screens/HojeScreen.kt`, `ui/PautaSheet.kt`.

**Spec:** one object, used everywhere:

```kotlin
object PautaMotion {
    const val Fast = 140    // tint changes, small fades
    const val Base = 240    // most transitions
    const val Slow = 380    // nav pushes, sheet entrances
    val Ease: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)   // decisive out
    val Spring = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> tween(ms: Int = Base): TweenSpec<T> = tween(ms, easing = Ease)
}
```

plus a `@Composable fun rememberMotionEnabled(): Boolean` reading
`prefs.reducedMotion` from the VM, so call sites stop re-deriving `animate`.
Replace existing `tween(150/180/220/300)` call sites with tokens (same
perceived speeds — this is consolidation, not retuning). A8 nav transitions
move to `Slow`.

**Accept:** no behavioural change visible except consistency; reduced motion
still snaps everywhere it did; CI green.

### P4 · Surface primitives + radius scale — Status: pending

**Depends on:** nothing.

**Files to create/touch:** `ui/PautaSurfaces.kt` (new); sweep call sites in
`ui/screens/*.kt` (largest: PautaScreen, HojeScreen, MaresScreen,
PautaSheets, the five Book* files).

**Spec:**

```kotlin
object PautaRadius { val Chip = 8.dp; val Field = 10.dp; val Card = 14.dp; val Sheet = 20.dp }

@Composable fun PautaCard(
    modifier: Modifier = Modifier,
    radius: Dp = PautaRadius.Card,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
)  // clip + 1dp rule border + paper2 + optional clickableNoRipple
```

and **one** `SectionEyebrow(label)` (mono 10sp, `ink3`, uppercase,
letterSpacing 1.6) that replaces `SectionLabel`, `MonoSectionLabel` and the
inline copies (keep `SheetEyebrow` delegating to it). Sweep the repeated
clip+border+background card pattern onto `PautaCard`; migrate radii to the
scale (nearest value; pills stay 999). Screens should end visually near-
identical — a 12→14 radius drift is fine, layout shifts are not.

**Accept:** grep shows no remaining private eyebrow duplicates; card sites
use `PautaCard`; both themes + sepia render correctly; CI green.

### P5 · Type & header rhythm — Status: pending

**Depends on:** nothing (coordinate with P4 if both touch the same lines).

**Files to create/touch:** `ui/theme/Type.kt` (add `PautaType` object);
headers in `ui/screens/HojeScreen.kt`, `PautaScreen.kt`, `MaresScreen.kt`,
`BookShelfScreen.kt`, `BookSessionScreen.kt`.

**Spec:** a small role set (not full Material adoption):

```kotlin
object PautaType {
    val ScreenTitle = TextStyle(fontFamily = SerifFamily, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val CardTitle   = TextStyle(fontFamily = SerifFamily, fontSize = 20.sp, lineHeight = 25.sp)
    val Body        = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 21.sp)
    val Label       = TextStyle(fontFamily = SansFamily,  fontSize = 14.sp, lineHeight = 18.sp)
    val Meta        = TextStyle(fontFamily = MonoFamily,  fontSize = 11.sp)
    val MetaSmall   = TextStyle(fontFamily = MonoFamily,  fontSize = 10.sp)
}
```

**The headline fix:** all five tab faces use `ScreenTitle` (36sp) at the
same top offset, so swiping tabs no longer jumps the headline size/baseline
(today: 38 / date-header / 34). Roll the roles through the headers and the
most-repeated text patterns; don't chase every `Text` in one PR — note
leftovers in the Log.

**Accept:** side-by-side swipe shows a stable headline line across all three
tabs (both lenses); `textScale` pref still scales; CI green.

---

## Phase P-2 — screen sweeps (independent; all depend on P3 + P4)

### P6 · Hoje sweep — Status: pending

**Files:** `ui/screens/HojeScreen.kt`, `PautaExtras.kt` (if shared rows live
there), `BookShelfScreen.kt` (same-slot book face).

**Spec:** apply P3/P4/P5 primitives; then: intention rows get a settle
animation on check (accent flash → ink, ~Base ms); the day-progress pulse
becomes one clean `PautaCard`; carry-over/memórias/week cards align to one
card rhythm (same radius, padding, eyebrow); shelf cards (book face) adopt
`PautaCard`. Trim visual noise: max one border style per card, no
double-nested borders.

**Accept:** no functional change; screenshots before/after show aligned
gutters (24dp) and consistent cards; both lenses; CI green.

### P7 · Pauta sweep — Status: pending

**Files:** `ui/screens/PautaScreen.kt`, `PautaSheets.kt`,
`BookSessionScreen.kt`.

**Spec:** the timer is the hero — give the active dark card tabular-figure
mono digits (`fontFeatureSettings "tnum"`), a very subtle accent progress
ring/underline toward `targetMs` when set, and `Base`-speed state changes
(start/pause/resume crossfade instead of snap). Start card, paused rows and
history rows onto `PautaCard`/type roles. Same treatment mirrored in
`BookSessionScreen` (it deliberately echoes these layouts).

**Accept:** timer digits don't jitter horizontally; pause/resume crossfades
(snaps under reduced motion); both lenses; CI green.

### P8 · Marés sweep + render performance — Status: pending

**Files:** `ui/screens/MaresScreen.kt` (+ `BookHabitsScreen.kt` header card).

**Spec:** two halves:

1. **Perf (part of the tab-bug story):** memoise the derived models —
   `remember(habits, logsByHabit, respByHabit) { habits.associate { it.id to modelOf(it) } }`
   and reuse; hoist per-row month stats (`pctInMonth`, `periodStats`,
   streaks) into a `remember`-ed per-habit computation keyed on
   (model, counts, year, month, today). Target: swiping into Marés with ~10
   habits shows no dropped frames on a mid-range device.
2. **Visual:** month navigation animates (strip slides ±, `Base`); rows
   adopt the shared eyebrow/type roles; the annual-goal card (book face)
   onto `PautaCard`.

**Accept:** no behaviour change (cells, respiros, counts all as before —
the HabitCalculator tests still pass untouched); measurably fewer
recompositions per frame while swiping (note method in PR); CI green.

### P9 · Sheets & Settings sweep — Status: pending

**Files:** `ui/PautaSheet.kt`, `ui/screens/SettingsScreen.kt`, spot fixes in
sheet files (`PautaSheets.kt`, `HabitFormSheet.kt`, `BookFormSheet.kt`,
`BookDetailSheet.kt`, `QuoteCaptureSheet.kt`).

**Spec:** one sheet anatomy everywhere: drag-handle/affordance, title row,
`Sheet` radius, consistent 24dp gutters and 18dp field spacing (several
sheets currently mix 16/18/20/22); `Slow` entrance via P3. Settings: group
rows into `PautaCard` sections with `SectionEyebrow` headers, consistent
toggle-row heights, and a visual break before the danger zone.

**Accept:** every sheet opens with the same motion + header anatomy; A5
dialog-on-tablet form still works; CI green.

---

## Phase P-3 — delight (after the sweeps)

### P10 · Micro-interactions, empty states, haptic map — Status: pending

**Depends on:** P3, P4; ideally last.

**Files:** `ui/Modifiers.kt` (add `pressScale`), new `ui/EmptyState.kt`,
call sites across screens.

**Spec:**

- `Modifier.pressScale(enabled = motionEnabled)` — 0.97 spring scale on
  press for cards/pills (the quiet alternative to ripple). Apply to
  `PautaButton`, `PautaCard(onClick)`, tab items, `SelectPill`.
- `EmptyState(icon | pip, title, line)` — one composable for the ~6 ad-hoc
  empty states (Hoje no-intentions, Pauta no-blocks, shelf sections, session
  history, notes list); serif-italic line, `ink4`, optional small Pip pose
  (respect `prefs.parrot`).
- **Haptic map** (all via `prefs.haptics`): tab settle (if P1 didn't),
  intention check, block start/conclude, habit day fill, 2-step delete arm.
  One helper `fun HapticFeedback.tick(prefs)` so call sites stay one line.
- List entrance stagger on first composition of each tab (items fade/rise
  12dp, 30ms stagger, `Fast`; skip under reduced motion and on subsequent
  visits — track "seen" per session, not per scroll).

**Accept:** interactions feel responsive with haptics off and motion
reduced (nothing depends on the delight layer); no new jank (stagger only on
first entry); CI green.

---

## Task dependency graph

```
P1 (pager fix)
 ├─ P2 (tab bar redesign)
 └─ P3 (motion tokens)
P4 (surfaces)  ──┐
P5 (type)     ──┤
P3 + P4 ─────────┼─ P6 (Hoje)  · P7 (Pauta) · P8 (Marés+perf) · P9 (sheets/settings)
                 └─ P10 (delight — last)
```

Minimum feel-better slice: **P1 alone** (the bug). First visible-polish
slice: P1 → P2 → P5.

---

## Log (append one line per shipped task: date · task · PR · note)

<!-- e.g. 2026-08-01 · P1 · #n · beyondViewportPageCount=2, targetPage highlight, tint tween, settle haptic -->
2026-07-31 · P1 · #142 · beyondViewportPageCount=2, targetPage highlight, tint tween(180) w/ reducedMotion snap, settle haptic (LongPress — no SegmentTick in UI 1.7)
2026-07-31 · P2 · #143 · sliding indicator drawn from live pager offset (drawBehind, measured label widths), icon spring nudge 1→1.06→1, reducedMotion pins to settledPage; motion specs inline pending P3
2026-07-31 · P3 · #144 · PautaMotion (140/240/380 + Ease/Spring/tween helper) + rememberMotionEnabled(); nav→Slow, tint/chip-exit→Fast, strike/hatch/saved/chip-enter→Base, pulse keeps 450 on house easing; deliberate springs untouched; PautaSheet had no ad-hoc specs (P9 adds Slow entrance); leftover ad-hoc specs in PautaScreen/FocusTide/Parrot/Onboarding for P6/P7/P9
