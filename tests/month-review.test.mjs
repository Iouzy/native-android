// monthReview() (#7) — calendar-month aggregate for the Insights sheet.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

const NOW = () => S.tsFromDayKey("2026-06-20") + 12 * 3600000;
function block(dayKey, minutes, id) {
  const start = S.tsFromDayKey(dayKey) + 10 * 3600000;
  return { id, title: "x", linkedToId: null, project: null, targetMs: null,
    sessions: [{ startedAt: start, endedAt: start + minutes * 60000, note: "" }], status: "done", reflection: "", createdAt: start };
}

describe("monthReview", () => {
  it("aggregates focus / intentions / reflections / tide-days within the month, up to today", () => {
    const state = {
      today: { dayKey: "2026-06-20", intentions: [{ id: "i1", text: "a", done: true }], reflection: "hoje" },
      days: {
        "2026-06-03": { intentions: [{ id: "i2", text: "b", done: true }, { id: "i3", text: "c", done: false }], reflection: "x" },
        "2026-05-30": { intentions: [{ id: "i4", text: "d", done: true }], reflection: "y" }, // other month → excluded
      },
      blocks: [ block("2026-06-03", 50, "b1"), block("2026-06-20", 25, "b2"), block("2026-05-15", 40, "b3") ],
      habits: [
        { id: "h1", name: "Ler", cadence: "daily", anchor: null, recurrence: "forever", endsAt: null,
          createdAt: S.tsFromDayKey("2026-01-01"),
          log: { "2026-06-03": 1, "2026-06-04": 1, "2026-05-31": 1 }, respiros: {}, counts: {}, target: null, weekdays: [] },
      ],
      goals: [], routines: [], prefs: {},
    };
    const r = S.monthReview(state, 2026, 5, NOW()); // June = monthIdx 5
    expect(Math.round(r.focusMs / 60000)).toBe(75); // 50 + 25 (June); May excluded
    expect(r.activeDays).toBe(2);
    expect(r.intDone).toBe(2);   // i1 + i2
    expect(r.intTotal).toBe(3);  // i1, i2, i3
    expect(r.reflections).toBe(2); // today + 06-03
    expect(r.tideDoneDays).toBe(2); // 06-03 + 06-04 (05-31 excluded)
    expect(r.topKey).toBe("2026-06-03"); // 50m is the peak day
  });

  it("does not count days after today within the current month", () => {
    const state = {
      today: { dayKey: "2026-06-20", intentions: [] }, days: {},
      blocks: [ block("2026-06-25", 99, "bfuture") ], // after 'now' (06-20) → excluded
      habits: [], goals: [], routines: [], prefs: {},
    };
    const r = S.monthReview(state, 2026, 5, NOW());
    expect(r.focusMs).toBe(0);
  });
});
