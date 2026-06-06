// Assembles the web assets Capacitor bundles into the native app (webDir).
// Everything is copied locally so the installed app has zero network/CDN deps.
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";

const OUT = "www";

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });

// index.html registers ./service-worker.js on load, so it must ship in the
// bundle — otherwise every static web deploy built from ./www silently has no
// offline cache (the registration 404s and the .catch() swallows the error).
// Native Capacitor bundles its assets and ignores the SW, so it's unaffected.
const files = ["index.html", "manifest.json", "service-worker.js"];
const dirs = ["icons", "src", "vendor"];

for (const f of files) cpSync(f, `${OUT}/${f}`);
for (const d of dirs) cpSync(d, `${OUT}/${d}`, { recursive: true });

// Stamp the build with run number + timestamp so the in-app update check can
// compare against the latest GitHub release. Locally, `run` stays 0 and the
// in-app check just treats any published release as newer.
const run = Number(process.env.GITHUB_RUN_NUMBER || 0);
const ts  = Math.floor(Date.now() / 1000);

// Point the in-app updater at the repo whose CI is building this APK. Releases
// publish to GITHUB_REPOSITORY ("owner/repo"), so stamping it here keeps the
// update check from ever polling a stale/forked repo. Locally (no env) the
// committed default in index.html stands.
const repo = process.env.GITHUB_REPOSITORY || "";

const indexPath = `${OUT}/index.html`;
let html = readFileSync(indexPath, "utf8").replace(
  /\/\*BUILD-INFO-BEGIN\*\/[\s\S]*?\/\*BUILD-INFO-END\*\//,
  `/*BUILD-INFO-BEGIN*/ ts: ${ts}, run: ${run} /*BUILD-INFO-END*/`,
);
if (repo) {
  html = html.replace(
    /window\.PAUTA_REPO\s*=\s*"[^"]*";\s*\/\*REPO-INFO\*\//,
    `window.PAUTA_REPO  = "${repo}"; /*REPO-INFO*/`,
  );
}
writeFileSync(indexPath, html);

console.log(`Web bundle assembled in ./${OUT}  (build run=${run}, ts=${ts}, repo=${repo || "default"})`);
