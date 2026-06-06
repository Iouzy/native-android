// withDayReflection() coverage — editing a reflection for *any* day.
//
// P1-7 lets you edit a past day's reflection from the Hoje history view, not
// just today's. The store action setDayReflection() is a thin setState wrapper
// around the pure withDayReflection(state, dayKey, text), which is what we test
// here (hooks can't run in the Babel sandbox; pure helpers can).
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

function baseState(todayKey) {
  return {
    today: { dayKey: todayKey, intentions: [], reflection: "hoje" },
    days: {
      "2026-01-01": { intentions: [{ id: "i1", text: "old", done: true }], reflection: "antigo" },
    },
    activeId: null, blocks: [], habits: [], goals: [], prefs: {},
  };
}

describe("withDayReflection — today", () => {
  it("updates today.reflection when the key is today", () => {
    const today = S.dayKeyOf(Date.now());
    const s0 = baseState(today);
    const s1 = S.withDayReflection(s0, today, "nova reflexão");
    expect(s1.today.reflection).toBe("nova reflexão");
    // archive untouched
    expect(s1.days["2026-01-01"].reflection).toBe("antigo");
  });
});

describe("withDayReflection — archived day", () => {
  it("updates an existing archived day's reflection, keeping its intentions", () => {
    const today = S.dayKeyOf(Date.now());
    const s0 = baseState(today);
    const s1 = S.withDayReflection(s0, "2026-01-01", "reescrita");
    expect(s1.days["2026-01-01"].reflection).toBe("reescrita");
    expect(s1.days["2026-01-01"].intentions).toEqual([{ id: "i1", text: "old", done: true }]);
    // today untouched
    expect(s1.today.reflection).toBe("hoje");
  });

  it("creates a day entry (empty intentions) when the archived day has none yet", () => {
    const today = S.dayKeyOf(Date.now());
    const s0 = baseState(today);
    const s1 = S.withDayReflection(s0, "2025-12-25", "véspera");
    expect(s1.days["2025-12-25"]).toEqual({ intentions: [], reflection: "véspera" });
  });
});

describe("withDayReflection — immutability", () => {
  it("does not mutate the input state", () => {
    const today = S.dayKeyOf(Date.now());
    const s0 = baseState(today);
    const snapshot = JSON.parse(JSON.stringify(s0));
    S.withDayReflection(s0, today, "x");
    S.withDayReflection(s0, "2026-01-01", "y");
    expect(s0).toEqual(snapshot);
  });
});
