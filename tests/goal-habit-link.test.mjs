// sanitizeGoal() — optional habitId link (goal ↔ tide "living pulse").
// The field must survive the backup round-trip and default cleanly.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("sanitizeGoal — habitId", () => {
  it("preserves a string habitId", () => {
    expect(S.sanitizeGoal({ text: "Correr 5 km", habitId: "h_run" }).habitId).toBe("h_run");
  });
  it("defaults a missing habitId to null", () => {
    expect(S.sanitizeGoal({ text: "Ler 12 livros" }).habitId).toBeNull();
  });
  it("rejects a non-string habitId", () => {
    expect(S.sanitizeGoal({ text: "x", habitId: 42 }).habitId).toBeNull();
  });
  it("survives a full import round-trip", () => {
    const imported = S.normalizeImported({
      goals: [{ id: "g1", text: "Correr 5 km", quarter: "2026-Q2", habitId: "h_run" }],
    });
    expect(imported.goals[0].habitId).toBe("h_run");
  });
});
