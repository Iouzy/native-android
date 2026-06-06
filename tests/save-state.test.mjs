// saveState() persistence-health signal.
//
// saveState used to swallow every error (a bare try/catch), so a failed write —
// most importantly QuotaExceededError when localStorage is full — was invisible
// and the user could lose data with no warning. It now returns a boolean the
// useStore persist effect reads to surface a banner. These tests lock the
// contract: true on success, false when the underlying write throws. The
// loadStore() sandbox shares one localStorage object between `localStorage`
// (what saveState calls) and `__localStorage` (what we stub), so overriding
// setItem here is exactly what saveState sees.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("saveState", () => {
  it("returns true when the write succeeds", () => {
    expect(S.saveState({ ok: 1 })).toBe(true);
  });

  it("returns false when localStorage throws, true again once it recovers", () => {
    const ls = S.__localStorage;
    const orig = ls.setItem;
    ls.setItem = () => {
      const e = new Error("QuotaExceededError");
      e.name = "QuotaExceededError";
      throw e;
    };
    try {
      expect(S.saveState({ big: "x" })).toBe(false);
    } finally {
      ls.setItem = orig; // restore so the recovery assertion is meaningful
    }
    expect(S.saveState({ ok: 1 })).toBe(true);
  });
});
