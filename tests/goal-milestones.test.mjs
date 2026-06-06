// Quarterly-goal milestones schema coverage (P2-13).
//
// goal.milestones is [{id,text,done}]; sanitizeGoal must accept a well-formed
// list, drop junk entries, and tolerate a missing field (older goals).
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("sanitizeMilestone", () => {
  it("keeps id/text/done, coercing done to boolean", () => {
    expect(S.sanitizeMilestone({ id: "m1", text: "draft", done: 1 }))
      .toEqual({ id: "m1", text: "draft", done: true });
  });
  it("mints an id when missing and defaults done to false", () => {
    const m = S.sanitizeMilestone({ text: "outline" });
    expect(m.text).toBe("outline");
    expect(m.done).toBe(false);
    expect(typeof m.id).toBe("string");
  });
  it("rejects empty/invalid entries", () => {
    expect(S.sanitizeMilestone({ text: "   " })).toBe(null);
    expect(S.sanitizeMilestone("nope")).toBe(null);
    expect(S.sanitizeMilestone(null)).toBe(null);
  });
});

describe("sanitizeGoal — milestones", () => {
  it("defaults a missing milestones list to []", () => {
    const g = S.sanitizeGoal({ id: "g1", text: "ship v1", quarter: "2026-Q1" });
    expect(g.milestones).toEqual([]);
  });
  it("sanitizes and filters the milestones array", () => {
    const g = S.sanitizeGoal({
      id: "g1", text: "ship v1", quarter: "2026-Q1",
      milestones: [
        { id: "m1", text: "spec", done: true },
        { text: "" },          // dropped (no text)
        "garbage",             // dropped (not an object)
        { text: "build", done: false },
      ],
    });
    expect(g.milestones.length).toBe(2);
    expect(g.milestones[0]).toEqual({ id: "m1", text: "spec", done: true });
    expect(g.milestones[1].text).toBe("build");
  });
  it("survives a backup round-trip via normalizeImported", () => {
    const imported = S.normalizeImported({
      goals: [{ id: "g1", text: "ship v1", quarter: "2026-Q1", milestones: [{ id: "m1", text: "spec", done: true }] }],
    });
    expect(imported.goals[0].milestones[0]).toEqual({ id: "m1", text: "spec", done: true });
  });
});
