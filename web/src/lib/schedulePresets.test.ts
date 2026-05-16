import { describe, expect, it } from "vitest";
import {
  buildCronFromPreset,
  defaultTimeForPreset,
  parseTime24,
  timezoneOptions,
} from "./schedulePresets";

describe("buildCronFromPreset", () => {
  it("builds daily at 9:30", () => {
    expect(
      buildCronFromPreset({
        preset: "daily",
        time: "09:30",
        dayOfWeek: "1",
        customCron: "",
      }),
    ).toBe("30 9 * * *");
  });

  it("builds weekdays", () => {
    expect(
      buildCronFromPreset({
        preset: "weekdays",
        time: "08:00",
        dayOfWeek: "1",
        customCron: "",
      }),
    ).toBe("0 8 * * 1-5");
  });

  it("builds hourly", () => {
    expect(
      buildCronFromPreset({
        preset: "hourly",
        time: "09:00",
        dayOfWeek: "1",
        customCron: "",
      }),
    ).toBe("0 * * * *");
  });

  it("builds weekly on chosen day", () => {
    expect(
      buildCronFromPreset({
        preset: "weekly",
        time: "14:15",
        dayOfWeek: "3",
        customCron: "",
      }),
    ).toBe("15 14 * * 3");
  });

  it("builds monthly on the first", () => {
    expect(
      buildCronFromPreset({
        preset: "monthly",
        time: "06:00",
        dayOfWeek: "1",
        customCron: "",
      }),
    ).toBe("0 6 1 * *");
  });

  it("uses custom cron when preset is custom", () => {
    expect(
      buildCronFromPreset({
        preset: "custom",
        time: "09:00",
        dayOfWeek: "1",
        customCron: "*/15 * * * *",
      }),
    ).toBe("*/15 * * * *");
  });

  it("uses default cron when custom expression is blank", () => {
    expect(
      buildCronFromPreset({
        preset: "custom",
        time: "09:00",
        dayOfWeek: "1",
        customCron: "   ",
      }),
    ).toBe("0 9 * * *");
  });
});

describe("parseTime24", () => {
  it("parses valid times", () => {
    expect(parseTime24("09:30")).toEqual({ hour: 9, minute: 30 });
  });

  it("clamps out-of-range values", () => {
    expect(parseTime24("99:99")).toEqual({ hour: 23, minute: 59 });
  });

  it("falls back when format is invalid", () => {
    expect(parseTime24("not-a-time")).toEqual({ hour: 9, minute: 0 });
  });
});

describe("timezoneOptions", () => {
  it("always includes common zones", () => {
    const options = timezoneOptions();
    expect(options.some((o) => o.value === "UTC")).toBe(true);
    expect(options.some((o) => o.value === "Asia/Kolkata")).toBe(true);
  });
});

describe("defaultTimeForPreset", () => {
  it("returns 09:00", () => {
    expect(defaultTimeForPreset()).toBe("09:00");
  });
});
