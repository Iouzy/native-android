// Per-habit colour schema coverage (P2-10).
//
// color is a new optional habit field: null means "follow the live accent".
// migrateHabit defaults it; sanitizeHabit accepts a #rgb/#rrggbb hex and drops
// anything else; isHexColor is the shared validator.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("isHexColor", () => {
  it("accepts #rgb and #rrggbb, rejects everything else", () => {
    expect(S.isHexColor("#fff")).toBe(true);
    expect(S.isHexColor("#B8533A")).toBe(true);
    expect(S.isHexColor("#b8533a")).toBe(true);
    expect(S.isHexColor("red")).toBe(false);
    expect(S.isHexColor("#12")).toBe(false);
    expect(S.isHexColor("#12345")).toBe(false);
    expect(S.isHexColor("B8533A")).toBe(false);
    expect(S.isHexColor(0xB8533A)).toBe(false);
    expect(S.isHexColor(null)).toBe(false);
  });
});

describe("migrateHabit — color default", () => {
  it("defaults missing color to null", () => {
    const h = S.migrateHabit({ id: "h", name: "água" });
    expect(h.color).toBe(null);
  });
  it("leaves an existing color value in place (validation happens in sanitize)", () => {
    const h = S.migrateHabit({ id: "h", name: "água", color: "#5A6B3E" });
    expect(h.color).toBe("#5A6B3E");
  });
});

describe("sanitizeHabit — color validation", () => {
  it("keeps a valid hex (trimmed)", () => {
    expect(S.sanitizeHabit({ id: "h", name: "x", color: "#B8533A" }).color).toBe("#B8533A");
    expect(S.sanitizeHabit({ id: "h", name: "x", color: "  #abc  " }).color).toBe("#abc");
  });
  it("drops an invalid or missing color to null", () => {
    expect(S.sanitizeHabit({ id: "h", name: "x", color: "tomato" }).color).toBe(null);
    expect(S.sanitizeHabit({ id: "h", name: "x", color: 123 }).color).toBe(null);
    expect(S.sanitizeHabit({ id: "h", name: "x" }).color).toBe(null);
  });
});
