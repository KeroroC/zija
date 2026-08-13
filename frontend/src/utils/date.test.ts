import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  addCalendarMonths,
  futureDateShortcuts,
  pastDateShortcuts,
} from "./date"

describe("addCalendarMonths", () => {
  it("adds months within the same year", () => {
    const result = addCalendarMonths(new Date(2026, 0, 15), 3)
    expect(result.getFullYear()).toBe(2026)
    expect(result.getMonth()).toBe(3)
    expect(result.getDate()).toBe(15)
  })

  it("clamps to the last day of the target month", () => {
    const result = addCalendarMonths(new Date(2026, 0, 31), 1)
    expect(result.getFullYear()).toBe(2026)
    expect(result.getMonth()).toBe(1)
    expect(result.getDate()).toBe(28)
  })

  it("clamps across year boundary for +12 months", () => {
    const result = addCalendarMonths(new Date(2024, 1, 29), 12)
    expect(result.getFullYear()).toBe(2025)
    expect(result.getMonth()).toBe(1)
    expect(result.getDate()).toBe(28)
  })
})

describe("date picker shortcuts", () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 7, 13, 15, 30, 0))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("pastDateShortcuts: 今天 / 昨天 / 一周前", () => {
    expect(pastDateShortcuts.map((s) => s.text)).toEqual([
      "今天",
      "昨天",
      "一周前",
    ])
    const values = pastDateShortcuts.map((s) =>
      typeof s.value === "function" ? s.value() : s.value,
    )
    expect(values.map((d) => [d.getFullYear(), d.getMonth(), d.getDate()])).toEqual([
      [2026, 7, 13],
      [2026, 7, 12],
      [2026, 7, 6],
    ])
  })

  it("futureDateShortcuts: 3个月后 / 6个月后 / 1年后, clamped from today", () => {
    expect(futureDateShortcuts.map((s) => s.text)).toEqual([
      "3个月后",
      "6个月后",
      "1年后",
    ])
    const values = futureDateShortcuts.map((s) =>
      typeof s.value === "function" ? s.value() : s.value,
    )
    expect(values.map((d) => [d.getFullYear(), d.getMonth(), d.getDate()])).toEqual([
      [2026, 10, 13],
      [2027, 1, 13],
      [2027, 7, 13],
    ])
  })

  it("futureDateShortcuts clamps month-end from today", () => {
    vi.setSystemTime(new Date(2026, 0, 31, 10, 0, 0))
    const threeMonths = futureDateShortcuts[0].value
    const d = typeof threeMonths === "function" ? threeMonths() : threeMonths
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 3, 30])
  })
})
