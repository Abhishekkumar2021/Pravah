import { describe, expect, it } from "vitest";
import { buildCronFromPreset } from "./schedulePresets";

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
});
