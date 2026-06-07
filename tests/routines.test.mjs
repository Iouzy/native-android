// Routines (#6) — reusable intention templates. sanitizeRoutine guards the shape
// and the import round-trip; applying instantiates fresh intentions (carry-over).
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("sanitizeRoutine", () => {
  it("keeps id/name and only valid items (with valid planning fields)", () => {
    const r = S.sanitizeRoutine({ id: "r1", name: "Manhã", items: [
      { text: "Ler", priority: 1, targetMin: 20 },
      { text: "   " },                                  // empty → dropped
      { text: "Caminhar", priority: 9, targetMin: -5 }, // bad prio/target stripped, text kept
    ]});
    expect(r.id).toBe("r1");
    expect(r.name).toBe("Manhã");
    expect(r.items).toEqual([
      { text: "Ler", priority: 1, targetMin: 20 },
      { text: "Caminhar" },
    ]);
  });
  it("drops a routine with no usable items", () => {
    expect(S.sanitizeRoutine({ name: "x", items: [{ text: "" }] })).toBeNull();
  });
  it("defaults a blank name", () => {
    expect(S.sanitizeRoutine({ items: [{ text: "a" }] }).name).toBe("Rotina");
  });
});

describe("normalizeImported — routines", () => {
  it("preserves routines through a round-trip", () => {
    const imp = S.normalizeImported({ routines: [{ id: "r1", name: "Noite", items: [{ text: "Diário" }] }] });
    expect(imp.routines).toEqual([{ id: "r1", name: "Noite", items: [{ text: "Diário" }] }]);
  });
  it("defaults routines to []", () => {
    expect(S.normalizeImported({}).routines).toEqual([]);
  });
});
