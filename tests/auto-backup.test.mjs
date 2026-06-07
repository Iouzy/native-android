// Auto-backup ring buffer — listAutoBackups / writeAutoBackup / readAutoBackup.
// The rolling local snapshot keeps a few timestamped copies (not one) so a bad
// auto-save can't bury the last good copy. Runs against the sandbox's in-memory
// localStorage stub, like the other store tests.
import { describe, it, expect, beforeEach } from "vitest";
import { loadStore } from "./load-store.mjs";

const KEY = "pauta.autobackup";
let S;
beforeEach(() => { S = loadStore(); });

describe("auto-backup ring buffer", () => {
  it("starts empty", () => {
    expect(S.listAutoBackups()).toEqual([]);
    expect(S.readAutoBackup()).toBeNull();
  });

  it("keeps snapshots newest-first; readAutoBackup returns the newest", () => {
    S.writeAutoBackup({ n: 1 });
    S.writeAutoBackup({ n: 2 });
    S.writeAutoBackup({ n: 3 });
    const list = S.listAutoBackups();
    expect(list.length).toBe(3);
    expect(list.map(s => s.backup.n)).toEqual([3, 2, 1]);
    expect(S.readAutoBackup().backup.n).toBe(3);
  });

  it("trims to AUTOBACKUP_KEEP, dropping the oldest", () => {
    const keep = S.AUTOBACKUP_KEEP;
    const total = keep + 2;
    for (let n = 1; n <= total; n++) S.writeAutoBackup({ n });
    const list = S.listAutoBackups();
    expect(list.length).toBe(keep);
    expect(list[0].backup.n).toBe(total);              // newest kept
    expect(list[keep - 1].backup.n).toBe(total - keep + 1); // oldest still kept
  });

  it("migrates the legacy single-slot shape and prepends new copies", () => {
    S.__localStorage.setItem(KEY, JSON.stringify({ ts: 123, backup: { n: "legacy" } }));
    expect(S.listAutoBackups()).toEqual([{ ts: 123, backup: { n: "legacy" } }]);
    S.writeAutoBackup({ n: "fresh" });
    const list = S.listAutoBackups();
    expect(list.length).toBe(2);
    expect(list[0].backup.n).toBe("fresh");
    expect(list[1].backup.n).toBe("legacy");
  });

  it("ignores a corrupt stored value", () => {
    S.__localStorage.setItem(KEY, "{not json");
    expect(S.listAutoBackups()).toEqual([]);
  });
});
