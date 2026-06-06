// Injects the native focus-timer plugin into the Capacitor Android project
// that was just created by `npx cap add android`.  Run this after `cap add`
// and before `cap sync` / the Gradle build.
import { cpSync, readFileSync, writeFileSync, mkdirSync, existsSync, rmSync } from "node:fs";
import { join } from "node:path";

const ROOT    = new URL("..", import.meta.url).pathname;
const SRC     = join(ROOT, "native/android");
const JAVA    = join(ROOT, "android/app/src/main/java/com/pauta/app");
const DRAWABLE = join(ROOT, "android/app/src/main/res/drawable");
const XML      = join(ROOT, "android/app/src/main/res/xml");
const MANIFEST = join(ROOT, "android/app/src/main/AndroidManifest.xml");
const GRADLE   = join(ROOT, "android/app/build.gradle");
const ROOT_GRADLE = join(ROOT, "android/build.gradle");
const APP_DIR  = join(ROOT, "android/app");
const KEYSTORE_SRC = join(ROOT, "debug.keystore");   // the committed, stable signing key
// Kotlin Gradle plugin version compatible with Capacitor 6.2's AGP 8.2.1 / Gradle 8.2.1.
const KOTLIN_VERSION = "1.9.25";

// ── 1. Copy Kotlin source files ───────────────────────────────
mkdirSync(JAVA,     { recursive: true });
mkdirSync(DRAWABLE, { recursive: true });
mkdirSync(XML,      { recursive: true });

for (const file of [
  "FocusActivityPlugin.kt",
  "FocusActionReceiver.kt",
  "FocusService.kt",
  "AppUpdaterPlugin.kt",
  "MainActivity.kt",
  "ReminderScheduler.kt",
  "ReminderReceiver.kt",
  "BootReceiver.kt",
]) {
  cpSync(join(SRC, file), join(JAVA, file));
  console.log(`Copied ${file}`);
}

// Capacitor generates an empty Java MainActivity (`extends BridgeActivity {}`).
// Our MainActivity.kt is the one that actually registers the FocusActivity and
// AppUpdater plugins — and once Kotlin compiles it, the two would collide as a
// duplicate `com.pauta.app.MainActivity`. Remove the Java stub so ours wins.
// (Before Kotlin was enabled the .kt was silently ignored and this Java stub
// shipped, which is exactly why every native plugin was missing at runtime —
// "unable to find plugin : FocusActivity".)
const MAIN_JAVA = join(JAVA, "MainActivity.java");
if (existsSync(MAIN_JAVA)) {
  rmSync(MAIN_JAVA);
  console.log("Removed generated MainActivity.java (replaced by MainActivity.kt)");
}

// ── 2. Copy resources (notification icons + updater FileProvider paths) ──
for (const icon of [
  "ic_stat_focus.xml",       // status-bar / small icon
  "ic_focus_pause.xml",      // focus-notification action-button icons
  "ic_focus_resume.xml",
  "ic_focus_conclude.xml",
  "ic_focus_switch.xml",
]) {
  cpSync(join(SRC, icon), join(DRAWABLE, icon));
  console.log(`Copied ${icon}`);
}
cpSync(join(SRC, "update_file_paths.xml"), join(XML, "update_file_paths.xml"));
console.log("Copied update_file_paths.xml");

// ── 3. Patch AndroidManifest.xml ─────────────────────────────
let manifest = readFileSync(MANIFEST, "utf8");

const PERMISSIONS = `
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
    <uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>`;

const COMPONENTS = `
        <service
            android:name=".FocusService"
            android:foregroundServiceType="dataSync"
            android:exported="false"/>
        <receiver
            android:name=".FocusActionReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.pauta.app.FOCUS_PAUSE"/>
                <action android:name="com.pauta.app.FOCUS_RESUME"/>
                <action android:name="com.pauta.app.FOCUS_CONCLUDE"/>
                <action android:name="com.pauta.app.FOCUS_SWITCH"/>
                <action android:name="com.pauta.app.FOCUS_DISMISS_ALERT"/>
            </intent-filter>
        </receiver>
        <receiver
            android:name=".ReminderReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.pauta.app.REMINDER_FIRE"/>
            </intent-filter>
        </receiver>
        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
                <action android:name="android.intent.action.QUICKBOOT_POWERON"/>
            </intent-filter>
        </receiver>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.pauta.app.updateprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/update_file_paths"/>
        </provider>`;

// Guard: don't double-inject on repeated runs
if (!manifest.includes("FocusService")) {
  manifest = manifest.replace(
    /(<manifest\b[^>]*>)/,
    `$1${PERMISSIONS}`,
  );
  manifest = manifest.replace(
    /(<\/application>)/,
    `${COMPONENTS}\n    $1`,
  );
  writeFileSync(MANIFEST, manifest);
  console.log("Patched AndroidManifest.xml");
} else {
  console.log("AndroidManifest.xml already patched — skipped");
}

// ── 3b. Enable Kotlin so the injected .kt sources actually compile ──
// Capacitor's generated Android project is Java-only — no Kotlin Gradle plugin.
// So every .kt file copied above (the plugins, services, receivers, MainActivity)
// was silently NOT compiled: the build still succeeded using the empty Java
// MainActivity, and none of the native bridges existed at runtime — the JS side
// got "unable to find plugin : FocusActivity" and notifications/updates never
// worked. Add the Kotlin Android plugin (root classpath + app apply) and pin
// jvmTarget to Capacitor's Java 17 compileOptions to avoid a JVM-target mismatch.
let rootGradle = readFileSync(ROOT_GRADLE, "utf8");
if (!rootGradle.includes("kotlin-gradle-plugin")) {
  rootGradle = rootGradle.replace(
    /(classpath ['"]com\.android\.tools\.build:gradle:[^'"]+['"])/,
    `$1\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:${KOTLIN_VERSION}'`,
  );
  writeFileSync(ROOT_GRADLE, rootGradle);
  console.log(`Patched android/build.gradle: kotlin-gradle-plugin ${KOTLIN_VERSION}`);
} else {
  console.log("android/build.gradle already has kotlin-gradle-plugin — skipped");
}

let kgradle = readFileSync(GRADLE, "utf8");
if (!kgradle.includes("kotlin.android")) {
  kgradle = kgradle.replace(
    /apply plugin: ['"]com\.android\.application['"]/,
    `$&\napply plugin: 'org.jetbrains.kotlin.android'`,
  );
  // A second android{} block is legal — Gradle merges it into the same extension.
  kgradle += `\nandroid {\n    kotlinOptions { jvmTarget = '17' }\n}\n`;
  writeFileSync(GRADLE, kgradle);
  console.log("Patched android/app/build.gradle: applied kotlin.android + jvmTarget 17");
} else {
  console.log("android/app/build.gradle already has kotlin.android — skipped");
}

// ── 3c. Sign the debug build with the COMMITTED debug.keystore ──
// Capacitor's debug build relies on AGP's *default* debug signingConfig, which
// reads $HOME/.android/debug.keystore — and when AGP doesn't find a keystore at
// the exact path it expects, it SILENTLY GENERATES A NEW THROWAWAY KEY per build
// (you can spot it: the signing cert's "valid from" == the build timestamp).
// On CI that meant every release APK was signed with a *different* random key,
// so installing/updating over a previously-installed build failed with
// "the package conflicts with an existing package" — a signature mismatch that
// no amount of uninstalling fixes for good, because the next build's key differs
// again. (The workflow's `cp debug.keystore ~/.android/debug.keystore` step was
// not the keystore AGP actually used.) Fix: pin signing explicitly to the
// committed keystore, copied into the app module, so every build — local or CI —
// signs with the SAME stable key and updates install cleanly. Standard debug
// credentials (storepass/keypass "android", alias "androiddebugkey").
// IMPORTANT: never regenerate debug.keystore, or installed apps break on update.
cpSync(KEYSTORE_SRC, join(APP_DIR, "pauta-debug.keystore"));
console.log("Copied debug.keystore → android/app/pauta-debug.keystore");

let sgradle = readFileSync(GRADLE, "utf8");
if (!sgradle.includes("pauta-debug.keystore")) {
  // A further android{} block is legal — Gradle merges it into the same
  // extension. Reconfiguring the existing `debug` signingConfig is enough: the
  // implicit debug buildType already points at signingConfigs.debug.
  sgradle += `
android {
    signingConfigs {
        debug {
            storeFile file('pauta-debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }
}
`;
  writeFileSync(GRADLE, sgradle);
  console.log("Patched android/app/build.gradle: debug signingConfig → committed keystore");
} else {
  console.log("android/app/build.gradle already pins the committed keystore — skipped");
}

// ── 4. Bump versionCode/versionName in CI ────────────────────
// versionCode must be a monotonically increasing integer or Android refuses the
// update ("package conflicts with an existing package" / downgrade blocked).
// We previously used GITHUB_RUN_NUMBER — but that counter RESETS to 1 when the
// workflow is renamed or the repo is forked/transferred, which already happened
// here and produced a build with a HIGHER number than newer builds. Derive the
// code from wall-clock minutes since the epoch instead: always increasing,
// reset-proof, and well within the 2,147,483,647 ceiling for ~4000 years.
// versionName is a human date (no fake semver tied to the run number).
// Gate on CI (GITHUB_RUN_NUMBER present) so local builds keep the Capacitor default.
if (process.env.GITHUB_RUN_NUMBER) {
  const versionCode = Math.floor(Date.now() / 60000);   // epoch minutes — monotonic
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  const versionName = `${d.getUTCFullYear()}.${p(d.getUTCMonth() + 1)}.${p(d.getUTCDate())}`;
  let gradle = readFileSync(GRADLE, "utf8");
  gradle = gradle.replace(/versionCode\s+\d+/, `versionCode ${versionCode}`);
  gradle = gradle.replace(/versionName\s+"[^"]*"/, `versionName "${versionName}"`);
  writeFileSync(GRADLE, gradle);
  console.log(`Patched build.gradle: versionCode=${versionCode}, versionName=${versionName}`);
} else {
  console.log("No GITHUB_RUN_NUMBER set — leaving build.gradle versionCode at the Capacitor default.");
}

console.log("Native injection complete.");
