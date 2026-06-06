// Bulk respiro / catch-up coverage (P2-11).
//
// applyRespiro is the pure per-day helper shared by markRespiro and the new
// markRespiroRange. We exercise it directly across a date range for a daily
// tide, and confirm it clears any `done` on the days it touches and refuses
// future days.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

// A daily habit created well before the test window so every day is "active".
function dailyHabit(extra = {}) {
  return {
    id: "h", name: "água", cadence: "daily", recurrence: "forever",
    createdAt: S.tsFromDayKey("2020-01-01"), endsAt: null,
    log: {}, respiros: {}, counts: {}, anchor: null, ...extra,
  };
}

// Apply applyRespiro across [a,b] inclusive — the core of markRespiroRange.
function range(h, a, b, reason = "") {
  let out = h;
  for (let k = a; k <= b; k = S.addDaysToKey(k, 1)) out = S.applyRespiro(out, k, reason, S.tsFromDayKey("2026-06-30"));
  return out;
}

describe("applyRespiro — single day", () => {
  it("marks a respiro and clears any done log on that day", () => {
    const h = dailyHabit({ log: { "2026-06-10": 1 } });
    const out = S.applyRespiro(h, "2026-06-10", "doente", S.tsFromDayKey("2026-06-30"));
    expect(out.respiros["2026-06-10"].reason).toBe("doente");
    expect(out.log["2026-06-10"]).toBeUndefined();
  });
  it("is a no-op for a future day", () => {
    const h = dailyHabit();
    const out = S.applyRespiro(h, "2026-07-15", "", S.tsFromDayKey("2026-06-30"));
    expect(out).toBe(h);
  });
});

describe("markRespiroRange — daily tide", () => {
  it("marks every day in the inclusive range", () => {
    const h = dailyHabit({ log: { "2026-06-11": 1 } });
    const out = range(h, "2026-06-10", "2026-06-13", "férias forçadas");
    expect(Object.keys(out.respiros).sort()).toEqual(["2026-06-10", "2026-06-11", "2026-06-12", "2026-06-13"]);
    // a pre-existing done inside the range is cleared
    expect(out.log["2026-06-11"]).toBeUndefined();
    expect(out.respiros["2026-06-12"].reason).toBe("férias forçadas");
  });
  it("handles a single-day range", () => {
    const out = range(dailyHabit(), "2026-06-10", "2026-06-10");
    expect(Object.keys(out.respiros)).toEqual(["2026-06-10"]);
  });
});
