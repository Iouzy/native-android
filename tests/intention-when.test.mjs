// Time-of-day bucket on intentions (#6) — sanitize guards + import round-trip.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("sanitizeIntention — when", () => {
  it("keeps a valid bucket", () => {
    expect(S.sanitizeIntention({ text: "Ler", when: "manha" }).when).toBe("manha");
    expect(S.sanitizeIntention({ text: "Treino", when: "noite" }).when).toBe("noite");
  });
  it("drops an invalid or missing bucket", () => {
    expect("when" in S.sanitizeIntention({ text: "x", when: "madrugada" })).toBe(false);
    expect("when" in S.sanitizeIntention({ text: "x" })).toBe(false);
  });
  it("survives a full import round-trip", () => {
    const imp = S.normalizeImported({
      today: { dayKey: S.dayKeyOf(Date.now()), intentions: [{ id: "i1", text: "Ler", when: "tarde" }], reflection: "" },
    });
    expect(imp.today.intentions[0].when).toBe("tarde");
  });
  it("exposes the bucket vocabulary", () => {
    expect(S.WHEN_VALUES).toEqual(["manha", "tarde", "noite"]);
  });
});
