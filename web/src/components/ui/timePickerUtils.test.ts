import { describe, expect, it } from "vitest";
import {
  formatTime12,
  formatTimeValue,
  isPm,
  parseTime,
  snapMinute,
  toHour12,
  toHour24,
} from "./timePickerUtils";

describe("timePickerUtils", () => {
  it("parses HH:mm values", () => {
    expect(parseTime("09:30")).toEqual({ hour: 9, minute: 30 });
    expect(parseTime("23:59")).toEqual({ hour: 23, minute: 59 });
  });

  it("falls back for invalid input", () => {
    expect(parseTime("")).toEqual({ hour: 9, minute: 0 });
    expect(parseTime("invalid")).toEqual({ hour: 9, minute: 0 });
  });

  it("formats 12-hour display", () => {
    expect(formatTime12(9, 0)).toBe("9:00 AM");
    expect(formatTime12(12, 0)).toBe("12:00 PM");
    expect(formatTime12(0, 15)).toBe("12:15 AM");
    expect(formatTime12(18, 45)).toBe("6:45 PM");
  });

  it("converts between 12h and 24h", () => {
    expect(toHour12(0)).toBe(12);
    expect(toHour12(13)).toBe(1);
    expect(isPm(14)).toBe(true);
    expect(isPm(8)).toBe(false);
    expect(toHour24(9, false)).toBe(9);
    expect(toHour24(9, true)).toBe(21);
    expect(toHour24(12, false)).toBe(0);
    expect(toHour24(12, true)).toBe(12);
  });

  it("formats 24-hour value string", () => {
    expect(formatTimeValue(6, 5)).toBe("06:05");
  });

  it("snaps minutes to five-minute steps", () => {
    expect(snapMinute(0)).toBe(0);
    expect(snapMinute(7)).toBe(5);
    expect(snapMinute(58)).toBe(0);
  });
});
