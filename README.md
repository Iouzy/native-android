# Pauta

**Intentions, focus blocks, habits — and the book you're reading.**

A free, private, offline-first daily planner and reading companion for Android.
Write what matters today, run focus blocks with a real timer, keep habits
("marés") with a maritime, no-guilt streak system — and, in book mode, read a
PDF or EPUB inside the app with a progress that keeps itself. No account, no
server, no tracking; your data lives on your device and exports to a single JSON
file.

## Two modes, one app

Pauta is a planner and a reading companion wearing the same three tabs. A switch
in Settings (or a long press on the header) changes which one you get, and a
second launcher icon opens straight into book mode.

| | Planner | Book mode |
|---|---|---|
| **1** | **Hoje** — today's intentions + nightly reflection | **Estante** — the shelf: reading, up next, finished |
| **2** | **Pauta** — focus blocks with a start/pause/resume/conclude timer | **Sessão** — reading sessions on the same timer |
| **3** | **Marés** — habits with daily/weekly/monthly cadence and tide levels | **Hábitos** — the reading rhythm: days read, charts, annual goal |

## Reading

Attach a **PDF** or **EPUB** to a book and read it in the app.

- **PDF** — pages rendered by the Android framework, pinch to zoom, position
  remembered.
- **EPUB** — reflowable text in Pauta's own paper and ink, one chapter at a
  time, progress weighted by words rather than by chapters.
- **The session is the reading.** Opening a book starts the timer; closing it
  records where you got to. Nothing asks what page you reached.
- **Reading speed** in real words per minute for a counted EPUB — estimated,
  and marked as such, for anything else.

Attached files live in the app's private storage, are never uploaded, and are
never written into the backup. A book is untrusted input: it is parsed in a
separate process, and an EPUB is rendered with scripting off, the network
blocked and every navigation refused — see the Security model in
[`docs/GUARDRAILS.md`](docs/GUARDRAILS.md) §G.

## Notifications

Pauta asks for notification permission **once**, at the moment it first needs it
— when you start your first focus or reading block — and never at launch. Say no
and nothing stops working: the timer runs, the block records, and Settings →
Foco e lembretes tells you notifications are blocked and opens the system screen
that can undo it.

What it posts, and only this: the ongoing notification for a running block, the
three daily reminders you set yourself, and a per-tide reminder if you give a
tide a time. All local, all from the device.

## Install

Download `pauta-native-v<N>.apk` from the rolling
[`latest-native`](../../releases/tag/latest-native) release and install it.
The app checks that same release for updates and installs them in place —
data preserved.

## Build

```bash
cd app-native
./gradlew :app:assembleDebug      # requires JDK 17 + Android SDK (compileSdk 35)
./gradlew :app:testDebugUnitTest  # domain + backup unit tests
```

Native Kotlin + Jetpack Compose — sources in [`app-native/`](app-native/) (see
its README for the module's non-negotiables).

## How work happens here

Every change ships as one task from one of the files in [`docs/`](docs/): a
spec, one PR, and a log entry explaining why it was built that way.
[`docs/README.md`](docs/README.md) is the index.

**Keeping this file true is part of the job.** Any task that adds, removes or
changes something a user can see updates this README in the same PR — the tab
table, the reading section, the install steps, whatever it touched. A README
that describes half the app is worse than none, because it is believed. The rule
is written into [`docs/TASK_FILE_FORMAT.md`](docs/TASK_FILE_FORMAT.md) so it
outlives whoever remembered it.

## History

Pauta started as a no-build React web app wrapped with Capacitor. The native
rewrite reached full parity and replaced it in June 2026 — the entire web-era
tree is preserved on the [`web-legacy-final`](../../tree/web-legacy-final)
branch. Book mode and the reader came after, on the native tree only.

## Licence

[CC BY-NC 4.0](LICENSE)
