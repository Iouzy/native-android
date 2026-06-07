// yearReview() (#13) — the calendar-year retrospective behind the share poster.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const NOW = () => S.tsFromDayKey("2026-06-07") + 12 * 3600000;
function block(dayKey, minutes, id) {
  const start = S.tsFromDayKey(dayKey) + 10 * 3600000;
  return { id, title: "x", linkedToId: null, project: null, targetMs: null,
    sessions: [{ startedAt: start, endedAt: start + minutes * 60000, note: "" }], status: "done", reflection: "", createdAt: start };
}

describe("yearReview", () => {
  it("aggregates focus, intentions, reflections and tide-days within the year only", () => {
    const state = {
      today: { dayKey: "2026-03-02", intentions: [{ id: "i1", text: "a", done: true }, { id: "i2", text: "b", done: false }], reflection: "valeu" },
      days: {
        "2026-01-05": { intentions: [{ id: "i3", text: "c", done: true }], reflection: "" },
        "2025-12-31": { intentions: [{ id: "i4", text: "d", done: true }], reflection: "x" }, // prior year → excluded
      },
      blocks: [ block("2026-01-05", 30, "b1"), block("2026-01-05", 15, "b2"), block("2025-06-01", 60, "b3") ],
      habits: [
        { id: "h1", name: "Ler", cadence: "daily", anchor: null, recurrence: "forever", endsAt: null,
          createdAt: S.tsFromDayKey("2026-01-01"),
          log: { "2026-01-05": 1, "2026-01-06": 1, "2025-12-30": 1 }, respiros: {}, counts: {}, target: null, weekdays: [] },
      ],
      goals: [], routines: [], prefs: {},
    };
    const r = S.yearReview(state, 2026, NOW());
    expect(r.year).toBe(2026);
    expect(Math.round(r.focusMs / 60000)).toBe(45);  // 30+15 (2026); the 2025 block excluded
    expect(r.activeDays).toBe(1);                     // only 2026-01-05
    expect(r.blockCount).toBe(2);
    expect(r.intDone).toBe(2);                        // i1 + i3
    expect(r.intTotal).toBe(3);                       // i1, i2, i3
    expect(r.reflections).toBe(1);                    // today "valeu"
    expect(r.tideDoneDays).toBe(2);                   // 2026-01-05 + 01-06 (2025-12-30 excluded)
    expect(r.bestStreakDays).toBe(2);
    expect(r.topTide).toEqual({ name: "Ler", days: 2 });
    expect(r.level).toBeTruthy();
  });

  it("is empty for a year with no data", () => {
    const r = S.yearReview({ today: { dayKey: "2026-01-01", intentions: [] }, days: {}, blocks: [], habits: [], goals: [], routines: [], prefs: {} }, 2024, NOW());
    expect(r.focusMs).toBe(0);
    expect(r.tideDoneDays).toBe(0);
    expect(r.topTide).toBeNull();
  });
});
