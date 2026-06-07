// narrativeStats() — the locally-computed "Padrões" facts in the Insights sheet.
// Pure, so it runs in the Babel sandbox. We assert the math; the i18n layer owns
// the sentences.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const CREATED = "2026-01-01";

function habit(id, name, log) {
  return {
    id, name, cadence: "daily", anchor: null, recurrence: "forever", endsAt: null,
    createdAt: S.tsFromDayKey(CREATED), log: log || {}, respiros: {}, counts: {}, target: null,
  };
}
// A run of done days ending at `endKey` going back `n` days.
function streakLog(endKey, n) {
  const log = {};
  let k = endKey;
  for (let i = 0; i < n; i++) { log[k] = 1; k = S.addDaysToKey(k, -1); }
  return log;
}

describe("narrativeStats — top tide by streak", () => {
  it("picks the habit with the longest current streak", () => {
    const today = S.dayKeyOf(Date.now());
    const state = {
      today: { dayKey: today, intentions: [], reflection: "" }, days: {},
      blocks: [], goals: [], prefs: {},
      habits: [habit("a", "Ler", streakLog(today, 3)), habit("b", "Correr", streakLog(today, 9))],
    };
    const n = S.narrativeStats(state);
    expect(n.topHabit).toEqual({ name: "Correr", days: 9 });
  });
  it("returns null topHabit when no habit has a live streak", () => {
    const today = S.dayKeyOf(Date.now());
    const state = { today: { dayKey: today, intentions: [] }, days: {}, blocks: [], goals: [], habits: [habit("a", "Ler", {})], prefs: {} };
    expect(S.narrativeStats(state).topHabit).toBeNull();
  });
});

describe("narrativeStats — high-priority completion", () => {
  it("computes % only once there are at least 3 high-priority intentions", () => {
    const today = S.dayKeyOf(Date.now());
    const mk = (id, prio, done) => ({ id, text: id, priority: prio, done });
    const state = {
      today: { dayKey: today, intentions: [mk("i1", 1, true), mk("i2", 1, false), mk("i3", 2, true)], reflection: "" },
      days: { "2026-05-01": { intentions: [mk("i4", 1, true), mk("i5", 1, true)], reflection: "" } },
      blocks: [], goals: [], habits: [], prefs: {},
    };
    // High-priority: i1,i2,i4,i5 → 4 total, 3 done → 75%
    expect(S.narrativeStats(state).highPrioPct).toBe(75);
  });
  it("is null below the sample threshold", () => {
    const today = S.dayKeyOf(Date.now());
    const state = {
      today: { dayKey: today, intentions: [{ id: "i1", text: "x", priority: 1, done: true }], reflection: "" },
      days: {}, blocks: [], goals: [], habits: [], prefs: {},
    };
    expect(S.narrativeStats(state).highPrioPct).toBeNull();
  });
});

describe("narrativeStats — peak focus hour", () => {
  it("is null with no focus on record", () => {
    const today = S.dayKeyOf(Date.now());
    const state = { today: { dayKey: today, intentions: [] }, days: {}, blocks: [], goals: [], habits: [], prefs: {} };
    expect(S.narrativeStats(state).peakHour).toBeNull();
  });
});
