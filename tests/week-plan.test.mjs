// Week-ahead planning (#6) — plans live in their own map and rollOverDay promotes
// the plan for the new day into `today`, dropping consumed/stale plans. Plus the
// import path keeps only future plans.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const TODAY = "2026-06-07";
const TOM = "2026-06-08";
const YEST = "2026-06-06";
const nowTs = () => S.tsFromDayKey(TODAY) + 9 * 3600000;

function base(todayKey, plans = {}) {
  return {
    today: { dayKey: todayKey, intentions: [], reflection: "" },
    days: {}, activeId: null, blocks: [], habits: [], goals: [], routines: [], plans, prefs: {},
  };
}

describe("rollOverDay — week-ahead plans", () => {
  it("promotes the new day's plan into today and removes it from plans", () => {
    // It's still 'yesterday' in state; rolling to TODAY should pull plans[TODAY].
    const s0 = base(YEST, { [TODAY]: { intentions: [{ id: "p1", text: "Correr", done: false }] }, [TOM]: { intentions: [{ id: "p2", text: "Ler", done: false }] } });
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.today.dayKey).toBe(TODAY);
    expect(s1.today.intentions).toEqual([{ id: "p1", text: "Correr", done: false }]);
    expect(s1.plans[TODAY]).toBeUndefined();   // consumed
    expect(s1.plans[TOM]).toBeTruthy();        // future plan kept
  });

  it("drops stale plans (<= today) even with no plan for today", () => {
    const s0 = base(YEST, { [YEST]: { intentions: [{ id: "x", text: "old" }] } });
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.today.intentions).toEqual([]);   // no plan for today
    expect(s1.plans[YEST]).toBeUndefined();    // stale dropped
  });

  it("is a no-op (same ref) on the same day with no stale plans", () => {
    const s0 = base(TODAY, { [TOM]: { intentions: [{ id: "p2", text: "Ler" }] } });
    expect(S.rollOverDay(s0, nowTs())).toBe(s0);
  });

  it("still archives yesterday's content while promoting today's plan", () => {
    const s0 = base(YEST, { [TODAY]: { intentions: [{ id: "p1", text: "Correr" }] } });
    s0.today.intentions = [{ id: "iy", text: "ontem", done: true }];
    const s1 = S.rollOverDay(s0, nowTs());
    expect(s1.days[YEST].intentions).toEqual([{ id: "iy", text: "ontem", done: true }]);
    expect(s1.today.intentions).toEqual([{ id: "p1", text: "Correr" }]);
  });
});

describe("normalizeImported — plans", () => {
  it("keeps only future plans with valid intentions", () => {
    const future = S.addDaysToKey(S.dayKeyOf(Date.now()), 3);
    const past = S.addDaysToKey(S.dayKeyOf(Date.now()), -3);
    const imp = S.normalizeImported({
      plans: {
        [future]: { intentions: [{ id: "p1", text: "Futuro" }, { text: "" }] },
        [past]: { intentions: [{ id: "p2", text: "Passado" }] },
        "not-a-day": { intentions: [{ text: "x" }] },
      },
    });
    expect(Object.keys(imp.plans)).toEqual([future]);
    expect(imp.plans[future].intentions).toHaveLength(1); // empty-text dropped
    expect(imp.plans[future].intentions[0].text).toBe("Futuro");
  });
  it("defaults plans to {}", () => {
    expect(S.normalizeImported({}).plans).toEqual({});
  });
});
