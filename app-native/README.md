# Pauta — native (Kotlin + Jetpack Compose)

A from-scratch native Android rewrite of Pauta, replacing the Capacitor/WebView
build. Parity with the original web app was reached and the web build was retired
in June 2026 (its full history lives on the `web-legacy-final` branch). This
module **is** the shipping app, and it has since grown past the thing it
replaced: book mode, an in-app PDF/EPUB reader, and a second launcher icon.

## Non-negotiables preserved
- Name **Pauta**, appId **com.pauta.app**
- Three tabs **Hoje / Pauta / Marés** with swipe + hardware `1`/`2`/`3` shortcuts
- The design tokens, typography, maritime theme (light/dark/auto), live accent
- **Pip** the parrot + his animations
- Marés levels (Onda → Tsunami/Oceano) + the no-guilt **Respiro**
- i18n with **Portuguese as source** + an English dictionary (`com.pauta.app.i18n`)
- Native reminders via **AlarmManager**
- Offline-first: no account, no network, no tracking (only the in-app updater
  talks to GitHub Releases, exactly as the web build does)
- **Data compatibility**: imports backups exported by the web app (`pauta.v4`)
- Licence **CC BY-NC 4.0** (repo-root `LICENSE`)

## Book mode — the second half
A single `bookMode` preference re-routes the same three tabs to **Estante /
Sessão / Hábitos**. It is one boolean with one source of truth: the Settings
switch, the header long-press and the second launcher icon all write to it.

A book can carry an attached **PDF** or **EPUB**, read in a full-surface reader
that starts (and concludes) the same focus block the Sessão tab uses, so
progress, pace and reading speed are observed rather than reported.

Three constraints on that half, and they are not negotiable either:

- **No parsing library.** PDFs go through the framework's `PdfRenderer`; EPUBs
  through `java.util.zip` and a hand-written tag scanner. If a format cannot be
  handled with the framework, the answer is to refuse the file with a clear
  message, never to vendor a parser.
- **An attached book is untrusted input.** Parsing runs in the `:reader`
  process; the WebView has scripting off, no file or network access, an opaque
  origin and refuses every navigation. The full model is **§G of
  `docs/GUARDRAILS.md`**, and it is binding.
- **Attached files are device-local.** They live in `filesDir/books/`, never
  enter the `pauta.v4` export, and a restored backup brings back the book, not
  the file.

## Build
```bash
cd app-native
./gradlew :app:assembleDebug      # debug APK (signed with repo-root debug.keystore)
./gradlew :app:testDebugUnitTest  # JVM unit tests (domain math, backup converter, EPUB parser)
```
Requires JDK 17+ and the Android SDK (compileSdk 35). CI
(`.github/workflows/android-native.yml`) runs the tests and uploads the APK as a
validation artifact on every push that touches `app-native/`; on `main` it also
publishes the rolling `latest-native` release the in-app updater polls.

## Run it on an emulator (local dev)
CI can compile and test the app but never *sees* it. To look at a change, run it
on an emulator from a local checkout. // PT: o CI compila mas não vê o ecrã.

One-time setup: install Android Studio, open this folder with it once (it writes
`local.properties` with the SDK path), then Device Manager → **+** → any Pixel →
API 35. Or from the command line, with `ANDROID_HOME` set:

```bash
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" \
           "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n pauta -k "system-images;android-35;google_apis;x86_64"
```

Then, every time:

```bash
emulator -avd pauta &                     # boot it (Linux needs KVM, Windows WHPX)
adb wait-for-device
cd app-native && ./gradlew :app:installDebug
adb shell am start -n com.pauta.app/.MainActivity
adb exec-out screencap -p > /tmp/pauta.png   # what the screen actually shows
```

On Windows use `.\gradlew.bat` from PowerShell (`./gradlew` is the Unix wrapper),
and run the session outside WSL — `adb` and `emulator` are Windows binaries there.

The APK is signed with the repo-root `debug.keystore`, so an emulator install
upgrades in place and keeps its data — the same property OTA updates rely on.

## Stack
Kotlin 2.0 · Compose (BOM 2024.09.03, Material3) · Room + KSP · DataStore ·
Lifecycle / Navigation / Activity Compose · kotlinx.serialization (backup v4) ·
Coroutines · Splashscreen. Charts and Pip are pure Compose Canvas; the readers
are `PdfRenderer` and `WebView` from the framework. **No network, charting, PDF
or EPUB dependency** — the in-app updater is the only code that opens a socket.

## Status
Everything the retired web app did — all three tabs, sheets and forms, settings,
insights/reviews, quarterly goals, week-ahead planning, PIN lock, reminders, home
widget, QS tile, in-app updater and `pauta.v4` backups — plus book mode and the
reader.

Active work is `docs/FIELD_FIXES.md`; `docs/README.md` indexes every task file,
and `docs/TASK_FILE_FORMAT.md` is the shape a new one follows. **A task that
changes what a user can see updates the repo-root `README.md` in the same PR.**
