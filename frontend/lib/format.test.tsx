import { describe, expect, it } from "vitest";
import { forCount } from "./format";

describe("forCount", () => {
  it("reads the singular for exactly one and the plural, with the count filled in, otherwise", () => {
    expect(forCount(1, "1 file missing", "{n} files missing")).toBe("1 file missing");
    expect(forCount(2, "1 file missing", "{n} files missing")).toBe("2 files missing");
    expect(forCount(0, "1 file missing", "{n} files missing")).toBe("0 files missing");
  });
});
