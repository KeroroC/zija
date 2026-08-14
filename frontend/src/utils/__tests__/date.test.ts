import { describe, it, expect } from "vitest";
import { dateRangeToIsoBounds } from "../date";

describe("dateRangeToIsoBounds", () => {
  it("returns empty when no range given", () => {
    expect(dateRangeToIsoBounds(null)).toEqual({});
    expect(dateRangeToIsoBounds(undefined)).toEqual({});
    expect(dateRangeToIsoBounds([])).toEqual({});
    expect(dateRangeToIsoBounds(["2026-08-13"])).toEqual({});
  });

  it("returns empty on malformed input", () => {
    expect(dateRangeToIsoBounds(["not-a-date", "2026-08-14"])).toEqual({});
    expect(dateRangeToIsoBounds(["2026-08-13", "bad"])).toEqual({});
  });

  it("produces ISO instants covering the whole day range", () => {
    const { from, to } = dateRangeToIsoBounds(["2026-08-13", "2026-08-14"]);
    expect(from).toBeDefined();
    expect(to).toBeDefined();
    // both are valid ISO-8601 UTC instants the backend can bind
    expect(() => new Date(from as string)).not.toThrow();
    expect(() => new Date(to as string)).not.toThrow();
    // parse back and assert local-day interpretation without timezone assumptions:
    // start at 00:00 local, end at 23:59:59.999 local — so end > start, same 2-day span
    const startMs = new Date(from as string).getTime();
    const endMs = new Date(to as string).getTime();
    expect(endMs).toBeGreaterThan(startMs);
    expect(endMs - startMs).toBeGreaterThanOrEqual(24 * 60 * 60 * 1000);
    expect(endMs - startMs).toBeLessThanOrEqual(48 * 60 * 60 * 1000);
  });

  it("single-day range still has a non-zero window", () => {
    const { from, to } = dateRangeToIsoBounds(["2026-08-13", "2026-08-13"]);
    const endMs = new Date(to as string).getTime();
    const startMs = new Date(from as string).getTime();
    expect(endMs).toBeGreaterThan(startMs);
    expect(endMs - startMs).toBeLessThanOrEqual(24 * 60 * 60 * 1000);
  });
});
