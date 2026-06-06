// weeklyReview() habit accounting — cadence-aware completion counting.
//
// weeklyReview summarises the 7-day window ending at endKey. Habits used to be
// counted one observed "slot" per active day, which badly under-reported
// weekly/monthly tides: a habit completed once for the week (1/1) was scored as
// 1-of-7 ≈ 14%. Everywhere else in the app uses the cadence-aware
// habitPeriodStats; this locks weeklyReview onto the same semantics — each
// weekly/monthly period overlapping the window counts ONCE, judged by its
// period-wide mark (done/respiro). Daily tides still count one slot per day.
import { describe, it, expect, beforeAll } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeAll(() => { S = loadStore(); });

// weeklyReview reads only state.habits / state.blocks / state.days / state.today,
// so a bare state with the habits slice replaced is enough to exercise it.
const withHabits = (habits) => ({ ...S.emptyState(), habits });

describe("weeklyReview — weekly/monthly tides count by period, not by day", () => {
  it("a once-a-week tide reads 1 slot / 100%, not 1-of-7", () => {
    // Window aligned to a single Mon–Sun week (endKey on a Sunday).
    const endKey = S.weekEndKey("2025-05-14");   // the Sunday closing that week
    const start = S.weekStartKey("2025-05-14");  // the Monday opening it
    const h = {
      id: "h1", name: "ler", cadence: "weekly", anchor: null,
      createdAt: S.tsFromDayKey(start),          // active across the whole window
      log: { [S.addDaysToKey(start, 2)]: 1 },    // completed once, midweek
      respiros: {},
    };
    const r = S.weeklyReview(withHabits([h]), endKey);
    expect(r.habitObservedSlots).toBe(1); // one period, not seven days
    expect(r.habitDone).toBe(1);
    expect(r.habitPct).toBe(100);         // was ~14% before the fix
  });

  it("a weekly tide taken as a respiro leaves the denominator empty (pct null)", () => {
    const endKey = S.weekEndKey("2025-05-14");
    const start = S.weekStartKey("2025-05-14");
    const h = {
      id: "h3", name: "meditar", cadence: "weekly", anchor: null,
      createdAt: S.tsFromDayKey(start),
      log: {},
      respiros: { [S.addDaysToKey(start, 1)]: { reason: "", at: 0 } },
    };
    const r = S.weeklyReview(withHabits([h]), endKey);
    expect(r.habitObservedSlots).toBe(1);
    expect(r.respiros).toBe(1);
    expect(r.habitDone).toBe(0);
    expect(r.habitPct).toBeNull(); // respiros leave the denominator (1 − 1 = 0)
  });

  it("a window straddling two weeks counts each period once", () => {
    // endKey mid-week (Sunday + 3 = Wednesday) so the 7-day window spans the tail
    // of week A (Thu–Sun) and the head of week B (Mon–Wed): two periods.
    const sundayA = S.weekEndKey("2025-05-14");
    const mondayA = S.weekStartKey("2025-05-14");
    const endKey = S.addDaysToKey(sundayA, 3);     // Wednesday of week B
    const thuA = S.addDaysToKey(sundayA, -3);
    const friA = S.addDaysToKey(sundayA, -2);
    const mondayB = S.addDaysToKey(sundayA, 1);
    const h = {
      id: "h4", name: "correr", cadence: "weekly", anchor: null,
      createdAt: S.tsFromDayKey(mondayA),
      // Two completions in week A (same period) + one in week B.
      log: { [thuA]: 1, [friA]: 1, [mondayB]: 1 },
      respiros: {},
    };
    const r = S.weeklyReview(withHabits([h]), endKey);
    expect(r.habitObservedSlots).toBeLessThanOrEqual(2);
    expect(r.habitObservedSlots).toBe(2);
    expect(r.habitDone).toBe(2); // once per period — NOT 3 (the two week-A days)
  });
});

describe("weeklyReview — daily tides unchanged", () => {
  it("counts one slot per active day (5 of 7 done → 71%)", () => {
    const endKey = "2025-05-20";
    const start = S.addDaysToKey(endKey, -6);
    const days = [];
    for (let i = 6; i >= 0; i--) days.push(S.addDaysToKey(endKey, -i));
    const log = {};
    for (let i = 0; i < 5; i++) log[days[i]] = 1; // 5 of the 7 days done
    const h = {
      id: "h2", name: "água", cadence: "daily",
      createdAt: S.tsFromDayKey(start), log, respiros: {},
    };
    const r = S.weeklyReview(withHabits([h]), endKey);
    expect(r.habitObservedSlots).toBe(7);
    expect(r.habitDone).toBe(5);
    expect(r.habitPct).toBe(71); // round(5 / 7 × 100)
  });
});
