// habitDayStatus() / habitActionableToday() coverage — the single source of
// "is this tide due today?" behind the Marés-de-hoje strip in the Hoje tab. It
// must mirror the per-cell logic of the Marés grid, so this pins the contract:
// daily tides are always actionable in-window; weekly/monthly only on an
// eligible, still-open day; a locked / non-anchor / out-of-window day → null.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const CREATED = "2026-01-01";
const DAY = "2026-06-07";

function daily(over = {}) {
  return {
    id: "h", name: "Ler", cadence: "daily", anchor: null,
    recurrence: "forever", endsAt: null, createdAt: S.tsFromDayKey(CREATED),
    log: {}, respiros: {}, counts: {}, target: null, ...over,
  };
}
function weekly(over = {}) {
  return { ...daily(), id: "hw", name: "Limpeza", cadence: "weekly", ...over };
}

describe("habitDayStatus — daily", () => {
  it("empty in-window day → empty, not countable", () => {
    expect(S.habitDayStatus(daily(), DAY)).toEqual({ state: "empty", isCount: false, count: 0, target: null });
  });
  it("logged day → done", () => {
    expect(S.habitDayStatus(daily({ log: { [DAY]: 1 } }), DAY).state).toBe("done");
  });
  it("respiro day → respiro", () => {
    expect(S.habitDayStatus(daily({ respiros: { [DAY]: { reason: "", at: 0 } } }), DAY).state).toBe("respiro");
  });
  it("day before the tide existed → null", () => {
    expect(S.habitDayStatus(daily(), "2025-12-31")).toBeNull();
  });
});

describe("habitDayStatus — countable (daily)", () => {
  it("count below target → partial, carrying count + target", () => {
    const h = daily({ target: 3, counts: { [DAY]: 2 } });
    expect(S.habitDayStatus(h, DAY)).toEqual({ state: "partial", isCount: true, count: 2, target: 3 });
  });
  it("count reaching target (log synced) → done", () => {
    const h = daily({ target: 3, counts: { [DAY]: 3 }, log: { [DAY]: 1 } });
    expect(S.habitDayStatus(h, DAY).state).toBe("done");
  });
});

describe("habitDayStatus — weekly", () => {
  it("manual weekly, empty period → empty (any day eligible)", () => {
    expect(S.habitDayStatus(weekly(), DAY).state).toBe("empty");
  });
  it("done on another day of the same week → null on the queried day, done on the marked day", () => {
    const start = S.weekStartKey(DAY);
    const marked = S.addDaysToKey(start, 1); // another day in the same Monday-week
    const h = weekly({ log: { [marked]: 1 } });
    expect(S.habitDayStatus(h, marked).state).toBe("done");
    if (marked !== DAY) expect(S.habitDayStatus(h, DAY)).toBeNull();
  });
  it("fixed-day weekly → empty on the anchor day, null on other days", () => {
    const start = S.weekStartKey(DAY);
    const anchorDow = new Date(S.tsFromDayKey(start)).getDay(); // the week's Monday
    const other = S.addDaysToKey(start, 1);
    const h = weekly({ anchor: anchorDow });
    expect(S.habitDayStatus(h, start).state).toBe("empty");
    expect(S.habitDayStatus(h, other)).toBeNull();
  });
});

describe("habitActionableToday", () => {
  it("matches habitDayStatus for today's key", () => {
    const h = daily();
    const tk = S.dayKeyOf(Date.now());
    expect(S.habitActionableToday(h)).toEqual(S.habitDayStatus(h, tk));
  });
});
