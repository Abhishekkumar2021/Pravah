/** Preset-driven cron builders for the schedule UI (US-03.01). */

export type SchedulePresetId =
  | "hourly"
  | "daily"
  | "weekdays"
  | "weekly"
  | "monthly"
  | "custom";

export type SchedulePresetOption = {
  id: SchedulePresetId;
  label: string;
  description: string;
};

export const SCHEDULE_PRESETS: SchedulePresetOption[] = [
  { id: "hourly", label: "Every hour", description: "Runs at the start of each hour" },
  { id: "daily", label: "Every day", description: "Runs once per day at a chosen time" },
  { id: "weekdays", label: "Weekdays", description: "Monday through Friday" },
  { id: "weekly", label: "Weekly", description: "Runs on one day each week" },
  { id: "monthly", label: "Monthly", description: "Runs on the 1st of each month" },
  { id: "custom", label: "Custom cron", description: "Full 5-field cron expression" },
];

export const WEEKDAY_OPTIONS = [
  { value: "1", label: "Monday" },
  { value: "2", label: "Tuesday" },
  { value: "3", label: "Wednesday" },
  { value: "4", label: "Thursday" },
  { value: "5", label: "Friday" },
  { value: "6", label: "Saturday" },
  { value: "0", label: "Sunday" },
];

export type TimezoneOption = { value: string; label: string };

const COMMON_TIMEZONES: TimezoneOption[] = [
  { value: "UTC", label: "UTC" },
  { value: "America/New_York", label: "Eastern Time (US)" },
  { value: "America/Chicago", label: "Central Time (US)" },
  { value: "America/Denver", label: "Mountain Time (US)" },
  { value: "America/Los_Angeles", label: "Pacific Time (US)" },
  { value: "Europe/London", label: "London" },
  { value: "Europe/Paris", label: "Paris" },
  { value: "Asia/Kolkata", label: "India (IST)" },
  { value: "Asia/Singapore", label: "Singapore" },
  { value: "Asia/Tokyo", label: "Tokyo" },
  { value: "Australia/Sydney", label: "Sydney" },
];

/** Timezone select options, with the browser zone first when known. */
export function timezoneOptions(): TimezoneOption[] {
  let browserTz = "UTC";
  try {
    browserTz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    /* ignore */
  }
  const seen = new Set<string>();
  const out: TimezoneOption[] = [];
  const add = (value: string, label: string) => {
    if (seen.has(value)) return;
    seen.add(value);
    out.push({ value, label });
  };
  if (browserTz && browserTz !== "UTC") {
    add(browserTz, `${browserTz} (your device)`);
  }
  for (const tz of COMMON_TIMEZONES) {
    add(tz.value, tz.label);
  }
  return out;
}

export type BuildCronInput = {
  preset: SchedulePresetId;
  time: string;
  dayOfWeek: string;
  customCron: string;
};

export function parseTime24(time: string): { hour: number; minute: number } {
  const match = /^(\d{1,2}):(\d{2})$/.exec(time.trim());
  if (!match) {
    return { hour: 9, minute: 0 };
  }
  const hour = Math.min(23, Math.max(0, Number.parseInt(match[1], 10)));
  const minute = Math.min(59, Math.max(0, Number.parseInt(match[2], 10)));
  return { hour, minute };
}

/** Builds a standard 5-field UNIX cron from simple schedule inputs. */
export function buildCronFromPreset(input: BuildCronInput): string {
  if (input.preset === "custom") {
    return input.customCron.trim() || "0 9 * * *";
  }
  const { hour, minute } = parseTime24(input.time);
  switch (input.preset) {
    case "hourly":
      return "0 * * * *";
    case "daily":
      return `${minute} ${hour} * * *`;
    case "weekdays":
      return `${minute} ${hour} * * 1-5`;
    case "weekly":
      return `${minute} ${hour} * * ${input.dayOfWeek}`;
    case "monthly":
      return `${minute} ${hour} 1 * *`;
    default:
      return `${minute} ${hour} * * *`;
  }
}

export function defaultTimeForPreset(): string {
  return "09:00";
}
