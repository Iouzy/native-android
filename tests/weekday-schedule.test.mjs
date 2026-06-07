// Per-weekday daily schedule (#4) — the cadence math that decides which days a
// daily tide is "due" on. Off-days must drop out of observed / status / streak
// so a Mon/Wed/Fri tide is never "missed" on a Tuesday.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const CREATED = "2026-01-01";
function h(over = {}) {
  return {
    id: "h", name: "Ginásio", cadence: "daily", anchor: null,
    recurrence: "forever", endsAt: null, createdAt: S.tsFromDayKey(CREATED),
    log: {}, respiros: {}, counts: {}, target: null, weekdays: [], ...over,
  };
}

describe("habitDailyDueOn", () => {
  it("is always true with no weekday schedule", () => {
    expect(S.habitDailyDueOn(h(), "2026-06-09")).toBe(true);
  });
  it("matches weekday membership (Mon/Wed/Fri)", () => {
    const mon = S.weekStartKey("2026-06-10");           // Monday of that week
    const tue = S.addDaysToKey(mon, 1);
    const wed = S.addDaysToKey(mon, 2);
    const t = h({ weekdays: [1, 3, 5] });
    expect(S.habitDailyDueOn(t, mon)).toBe(true);
    expect(S.habitDailyDueOn(t, tue)).toBe(false);
    expect(S.habitDailyDueOn(t, wed)).toBe(true);
  });
});

describe("habitDayStatus — weekday schedule", () => {
  it("returns null on an off-day, a real status on a due day", () => {
    const mon = S.weekStartKey("2026-06-10");
    const tue = S.addDaysToKey(mon, 1);
    const t = h({ weekdays: [1, 3, 5] });
    expect(S.habitDayStatus(t, tue)).toBeNull();
    expect(S.habitDayStatus(t, mon).state).toBe("empty");
    expect(S.habitDayStatus(h({ weekdays: [1, 3, 5], log: { [mon]: 1 } }), mon).state).toBe("done");
  });
});

describe("habitPeriodStats — weekday schedule", () => {
  it("only counts scheduled days as observed", () => {
    const mon = S.weekStartKey("2026-06-10");
    const wed = S.addDaysToKey(mon, 2);
    const sun = S.addDaysToKey(mon, 6);
    const t = h({ weekdays: [1, 3, 5], log: { [mon]: 1, [wed]: 1 } });
    const stats = S.habitPeriodStats(t, mon, sun);
    expect(stats.observed).toBe(3);  // Mon, Wed, Fri
    expect(stats.done).toBe(2);      // Mon, Wed
  });
});

describe("habitCurrentStreak — weekday schedule", () => {
  it("counts consecutive scheduled completions, not broken by off-days", () => {
    const mon = S.weekStartKey("2026-06-10");
    const wed = S.addDaysToKey(mon, 2);
    const fri = S.addDaysToKey(mon, 4);
    const t = h({ weekdays: [1, 3, 5], log: { [mon]: 1, [wed]: 1, [fri]: 1 } });
    // "Today" is Friday — the off-days (Tue/Thu) between completions don't break it.
    const st = S.habitCurrentStreak(t, S.tsFromDayKey(fri) + 12 * 3600000);
    expect(st.days).toBe(3);
  });
});

describe("sanitizeHabit — weekdays", () => {
  it("dedupes, clamps to 0–6 and sorts", () => {
    expect(S.sanitizeHabit({ name: "x", weekdays: [3, 1, 1, 7, -1, 5] }).weekdays).toEqual([1, 3, 5]);
  });
  it("defaults to [] (every day)", () => {
    expect(S.sanitizeHabit({ name: "x" }).weekdays).toEqual([]);
  });
});
