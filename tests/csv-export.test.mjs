// buildCSV() coverage — the flat CSV export (P2-12).
//
// buildCSV(state) is a pure helper (the downloadCSV side that touches the DOM
// is not exercised here). We assert the header, one row per record across the
// three data types, and RFC-4180 escaping of awkward values.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

function rows(csv) { return csv.split("\r\n"); }

describe("buildCSV — structure", () => {
  it("emits a header and a row per intention / block / habit mark", () => {
    const state = {
      today: { dayKey: "2026-02-02", intentions: [{ id: "a", text: "ship", done: true }, { id: "b", text: "rest", done: false }], reflection: "" },
      days: { "2026-02-01": { intentions: [{ id: "c", text: "plan", done: true }], reflection: "" } },
      blocks: [{
        id: "blk", title: "Deep work", project: "Pauta", status: "done", reflection: "good",
        createdAt: S.tsFromDayKey("2026-02-02"),
        sessions: [{ startedAt: S.tsFromDayKey("2026-02-02"), endedAt: S.tsFromDayKey("2026-02-02") + 25 * 60000 }],
      }],
      habits: [{ id: "h", name: "água", log: { "2026-02-01": 3 }, respiros: { "2026-02-02": true } }],
    };
    const r = rows(S.buildCSV(state));
    expect(r[0]).toBe("type,date,title,project,minutes,count,status,note");
    expect(r).toContain("intention,2026-02-02,ship,,,,done,");
    expect(r).toContain("intention,2026-02-02,rest,,,,open,");
    expect(r).toContain("intention,2026-02-01,plan,,,,done,"); // archived day
    expect(r).toContain("block,2026-02-02,Deep work,Pauta,25,,done,good");
    expect(r).toContain("habit,2026-02-01,água,,,3,done,"); // count carried
    expect(r).toContain("habit,2026-02-02,água,,,,respiro,");
  });

  it("handles empty state — just the header row", () => {
    const csv = S.buildCSV({ today: { dayKey: "2026-01-01", intentions: [], reflection: "" }, days: {}, blocks: [], habits: [] });
    expect(rows(csv).length).toBe(1);
  });
});

describe("csvCell — escaping", () => {
  it("quotes and doubles quotes for commas, quotes and newlines", () => {
    expect(S.csvCell("plain")).toBe("plain");
    expect(S.csvCell("a,b")).toBe('"a,b"');
    expect(S.csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(S.csvCell("line1\nline2")).toBe('"line1\nline2"');
    expect(S.csvCell(null)).toBe("");
  });

  it("escapes an intention whose text contains a comma", () => {
    const state = {
      today: { dayKey: "2026-03-03", intentions: [{ id: "x", text: "buy milk, eggs", done: false }], reflection: "" },
      days: {}, blocks: [], habits: [],
    };
    expect(S.buildCSV(state)).toContain('intention,2026-03-03,"buy milk, eggs",,,,open,');
  });
});
