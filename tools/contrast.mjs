// WCAG contrast audit for Pauta's design tokens (P4-20). No dependencies.
//
//   node tools/contrast.mjs        # print the report
//   node tools/contrast.mjs --ci   # exit non-zero if any check fails
//
// WCAG 2.1 contrast ratio = (L1 + 0.05) / (L2 + 0.05), L = relative luminance.
// AA targets: 4.5:1 for normal text, 3:1 for large text (≥18.66px bold / 24px)
// and for UI components / graphical objects. The accent is used mostly for
// large headings, icons and chips, so it's judged at the 3:1 UI/large bar;
// body ink colours are judged at 4.5:1.
//
// Tokens mirror index.html's :root and html[data-theme="dark"] blocks — keep
// them in sync if those change.

const THEMES = {
  light: {
    paper: "#F5F1EA", paper2: "#EDE7DC", ink: "#1A1815", ink2: "#4A453C",
    ink3: "#8A8275", rule: "#D9D2C5", surfaceDark: "#1A1815", onDark: "#F5F1EA",
  },
  dark: {
    paper: "#1B1A17", paper2: "#232220", ink: "#ECE6DA", ink2: "#C3BCAD",
    ink3: "#8A8275", rule: "#322F2A", surfaceDark: "#2A2824", onDark: "#F2ECE0",
  },
};

// Selectable accent presets (app.jsx Tweaks panel).
const ACCENTS = ["#B8533A", "#5A6B3E", "#3D5A80", "#8E5A8E", "#1A1815"];

function hexToRgb(hex) {
  let h = hex.replace("#", "").trim();
  if (h.length === 3) h = h.split("").map(c => c + c).join("");
  return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16));
}
function srgbToLinear(c) {
  c /= 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}
function luminance(hex) {
  const [r, g, b] = hexToRgb(hex).map(srgbToLinear);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function ratio(fg, bg) {
  const a = luminance(fg), b = luminance(bg);
  const hi = Math.max(a, b), lo = Math.min(a, b);
  return (hi + 0.05) / (lo + 0.05);
}
function fmt(n) { return n.toFixed(2).padStart(5); }

// One audited pair. `bar` is the required ratio. Returns a row + pass flag.
function check(label, fg, bg, bar) {
  const r = ratio(fg, bg);
  const pass = r >= bar;
  return { label, fg, bg, bar, r, pass };
}

const rows = [];
for (const [name, t] of Object.entries(THEMES)) {
  // Body / secondary text on the two paper surfaces — 4.5 (normal text).
  rows.push(check(`[${name}] ink   on paper`,  t.ink,  t.paper,  4.5));
  rows.push(check(`[${name}] ink   on paper-2`, t.ink, t.paper2, 4.5));
  rows.push(check(`[${name}] ink-2 on paper`,  t.ink2, t.paper,  4.5));
  rows.push(check(`[${name}] ink-3 on paper`,  t.ink3, t.paper,  4.5));
  rows.push(check(`[${name}] on-dark on surface-dark`, t.onDark, t.surfaceDark, 4.5));
  // Each accent preset as a large/UI foreground on paper + dark surface — 3.0.
  for (const a of ACCENTS) {
    rows.push(check(`[${name}] accent ${a} on paper`,        a, t.paper,       3.0));
    rows.push(check(`[${name}] accent ${a} on surface-dark`, a, t.surfaceDark, 3.0));
  }
}

let failures = 0;
console.log("WCAG contrast audit — Pauta tokens\n");
for (const row of rows) {
  if (!row.pass) failures++;
  const tag = row.pass ? "ok  " : "FAIL";
  console.log(`${tag}  ${fmt(row.r)}:1  (need ${row.bar.toFixed(1)})  ${row.label}`);
}
console.log(`\n${rows.length - failures}/${rows.length} pass, ${failures} fail`);

if (process.argv.includes("--ci") && failures > 0) process.exit(1);
