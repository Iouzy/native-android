// buildManualBlock() (#5) — a retroactive/manual focus block from a time entry.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

let S;
beforeEach(() => { S = loadStore(); });

describe("buildManualBlock", () => {
  it("builds a completed block with one closed session at the given span", () => {
    const start = S.tsFromDayKey("2026-05-10") + 9 * 3600000;
    const end = start + 45 * 60000;
    const b = S.buildManualBlock({ title: "Leitura", startTs: start, endTs: end });
    expect(b.title).toBe("Leitura");
    expect(b.status).toBe("done");
    expect(b.sessions).toHaveLength(1);
    expect(b.sessions[0]).toMatchObject({ startedAt: start, endedAt: end });
    expect(b.createdAt).toBe(start);
    expect(S.blockFocusMs(b)).toBe(45 * 60000);
    expect(S.dayKeyOf(b.createdAt)).toBe("2026-05-10");
  });
  it("trims the title and keeps optional project/linkedToId", () => {
    const start = 1000, end = 2000;
    const b = S.buildManualBlock({ title: "  x ", startTs: start, endTs: end, project: " p ", linkedToId: "i_1" });
    expect(b.title).toBe("x");
    expect(b.project).toBe("p");
    expect(b.linkedToId).toBe("i_1");
  });
  it("rejects empty title or a non-positive span", () => {
    expect(S.buildManualBlock({ title: "", startTs: 1, endTs: 2 })).toBeNull();
    expect(S.buildManualBlock({ title: "x", startTs: 5, endTs: 5 })).toBeNull();
    expect(S.buildManualBlock({ title: "x", startTs: 9, endTs: 1 })).toBeNull();
    expect(S.buildManualBlock({ title: "x", startTs: NaN, endTs: 10 })).toBeNull();
  });
  it("survives the backup round-trip as a valid block", () => {
    const start = 1700000000000, end = start + 60000;
    const b = S.buildManualBlock({ title: "x", startTs: start, endTs: end });
    const imported = S.normalizeImported({ blocks: [b] });
    expect(imported.blocks).toHaveLength(1);
    expect(imported.blocks[0].sessions[0].startedAt).toBe(start);
  });
});
