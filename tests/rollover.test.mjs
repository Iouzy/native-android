// rollOverDay() coverage — the day rollover shared by loadState (cold start)
// and the live midnight watcher in useStore. Pure, so it runs in the Babel
// sandbox like the other store tests.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const TODAY = "2026-06-07";
const YEST = "2026-06-06";
const nowTs = () => S.tsFromDayKey(TODAY) + 9 * 3600000; // 09:00 on TODAY

function stateOn(dayKey, today = {}) {
  return {
    today: { dayKey, intentions: [], reflection: "", ...today },
    days: {}, activeId: null, blocks: [], habits: [], goals: [], prefs: {},
  };
}

describe("rollOverDay", () => {
  it("returns the same reference when the day hasn't changed", () => {
    const s0 = stateOn(TODAY);
    expect(S.rollOverDay(s0, nowTs())).toBe(s0);
  });

  it("archives a stale today with content and opens a fresh empty today", () => {
    const s0 = stateOn(YEST, { intentions: [{ id: "i1", text: "ler", done: false }], reflection: "bom dia" });
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1).not.toBe(s0);
    expect(s1.today.dayKey).toBe(TODAY);
    expect(s1.today.intentions).toEqual([]);
    expect(s1.today.reflection).toBe("");
    expect(s1.days[YEST]).toEqual({
      intentions: [{ id: "i1", text: "ler", done: false }], reflection: "bom dia",
    });
  });

  it("does not archive an empty stale day", () => {
    const s0 = stateOn(YEST);
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.today.dayKey).toBe(TODAY);
    expect(s1.days[YEST]).toBeUndefined();
  });

  it("archives a stale day that has only a reflection (no intentions)", () => {
    const s0 = stateOn(YEST, { reflection: "valeu" });
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.days[YEST]).toEqual({ intentions: [], reflection: "valeu" });
  });

  it("handles a missing today (null) by opening a fresh one", () => {
    const s0 = { today: null, days: {}, blocks: [], habits: [], goals: [], prefs: {} };
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.today).toEqual({ dayKey: TODAY, intentions: [], reflection: "" });
  });

  it("does not mutate the input state", () => {
    const s0 = stateOn(YEST, { intentions: [{ id: "i1", text: "x", done: true }] });
    const snap = JSON.parse(JSON.stringify(s0));
    S.rollOverDay(s0, nowTs());
    expect(s0).toEqual(snap);
  });
});
