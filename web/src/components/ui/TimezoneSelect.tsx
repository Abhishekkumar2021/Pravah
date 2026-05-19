import { useMemo } from "react";
import { Combobox, type ComboboxOption } from "./Combobox";

const ALL_TIMEZONES = [
  // Americas
  { value: "America/New_York", label: "Eastern Time (US)", region: "Americas" },
  { value: "America/Chicago", label: "Central Time (US)", region: "Americas" },
  { value: "America/Denver", label: "Mountain Time (US)", region: "Americas" },
  { value: "America/Los_Angeles", label: "Pacific Time (US)", region: "Americas" },
  { value: "America/Anchorage", label: "Alaska Time", region: "Americas" },
  { value: "Pacific/Honolulu", label: "Hawaii Time", region: "Americas" },
  { value: "America/Phoenix", label: "Arizona (no DST)", region: "Americas" },
  { value: "America/Toronto", label: "Toronto", region: "Americas" },
  { value: "America/Vancouver", label: "Vancouver", region: "Americas" },
  { value: "America/Mexico_City", label: "Mexico City", region: "Americas" },
  { value: "America/Sao_Paulo", label: "São Paulo", region: "Americas" },
  { value: "America/Buenos_Aires", label: "Buenos Aires", region: "Americas" },
  { value: "America/Lima", label: "Lima", region: "Americas" },
  { value: "America/Bogota", label: "Bogotá", region: "Americas" },
  
  // Europe
  { value: "Europe/London", label: "London (UK)", region: "Europe" },
  { value: "Europe/Paris", label: "Paris (CET)", region: "Europe" },
  { value: "Europe/Berlin", label: "Berlin", region: "Europe" },
  { value: "Europe/Amsterdam", label: "Amsterdam", region: "Europe" },
  { value: "Europe/Brussels", label: "Brussels", region: "Europe" },
  { value: "Europe/Madrid", label: "Madrid", region: "Europe" },
  { value: "Europe/Rome", label: "Rome", region: "Europe" },
  { value: "Europe/Vienna", label: "Vienna", region: "Europe" },
  { value: "Europe/Zurich", label: "Zurich", region: "Europe" },
  { value: "Europe/Stockholm", label: "Stockholm", region: "Europe" },
  { value: "Europe/Oslo", label: "Oslo", region: "Europe" },
  { value: "Europe/Copenhagen", label: "Copenhagen", region: "Europe" },
  { value: "Europe/Helsinki", label: "Helsinki", region: "Europe" },
  { value: "Europe/Warsaw", label: "Warsaw", region: "Europe" },
  { value: "Europe/Prague", label: "Prague", region: "Europe" },
  { value: "Europe/Athens", label: "Athens", region: "Europe" },
  { value: "Europe/Moscow", label: "Moscow", region: "Europe" },
  { value: "Europe/Istanbul", label: "Istanbul", region: "Europe" },
  
  // Asia & Pacific
  { value: "Asia/Dubai", label: "Dubai (GST)", region: "Asia" },
  { value: "Asia/Kolkata", label: "India (IST)", region: "Asia" },
  { value: "Asia/Dhaka", label: "Dhaka", region: "Asia" },
  { value: "Asia/Bangkok", label: "Bangkok", region: "Asia" },
  { value: "Asia/Jakarta", label: "Jakarta", region: "Asia" },
  { value: "Asia/Singapore", label: "Singapore", region: "Asia" },
  { value: "Asia/Hong_Kong", label: "Hong Kong", region: "Asia" },
  { value: "Asia/Shanghai", label: "Shanghai (China)", region: "Asia" },
  { value: "Asia/Taipei", label: "Taipei", region: "Asia" },
  { value: "Asia/Seoul", label: "Seoul", region: "Asia" },
  { value: "Asia/Tokyo", label: "Tokyo", region: "Asia" },
  
  // Oceania
  { value: "Australia/Perth", label: "Perth", region: "Oceania" },
  { value: "Australia/Adelaide", label: "Adelaide", region: "Oceania" },
  { value: "Australia/Sydney", label: "Sydney", region: "Oceania" },
  { value: "Australia/Brisbane", label: "Brisbane", region: "Oceania" },
  { value: "Australia/Melbourne", label: "Melbourne", region: "Oceania" },
  { value: "Pacific/Auckland", label: "Auckland (NZ)", region: "Oceania" },
  
  // Africa & Middle East
  { value: "Africa/Cairo", label: "Cairo", region: "Africa" },
  { value: "Africa/Johannesburg", label: "Johannesburg", region: "Africa" },
  { value: "Africa/Lagos", label: "Lagos", region: "Africa" },
  { value: "Africa/Nairobi", label: "Nairobi", region: "Africa" },
  
  // UTC
  { value: "UTC", label: "UTC (Coordinated Universal Time)", region: "UTC" },
  { value: "Etc/GMT+0", label: "GMT", region: "UTC" },
];

function getBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return "UTC";
  }
}

function getTimezoneOffset(tz: string): string {
  try {
    const now = new Date();
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      timeZoneName: "shortOffset",
    });
    const parts = formatter.formatToParts(now);
    const offsetPart = parts.find((p) => p.type === "timeZoneName");
    return offsetPart?.value ?? "";
  } catch {
    return "";
  }
}

type TimezoneSelectProps = {
  value: string;
  onValueChange: (value: string) => void;
  className?: string;
  id?: string;
  "aria-label"?: string;
};

export function TimezoneSelect({
  value,
  onValueChange,
  className,
  id,
  "aria-label": ariaLabel,
}: TimezoneSelectProps) {
  const options = useMemo<ComboboxOption[]>(() => {
    const browserTz = getBrowserTimezone();
    const seen = new Set<string>();
    const result: ComboboxOption[] = [];

    const addOption = (tz: { value: string; label: string; region?: string }) => {
      if (seen.has(tz.value)) return;
      seen.add(tz.value);
      
      const offset = getTimezoneOffset(tz.value);
      result.push({
        value: tz.value,
        label: `${tz.label}${offset ? ` (${offset})` : ""}`,
        description: tz.value,
      });
    };

    // Add browser timezone first if known
    if (browserTz && browserTz !== "UTC") {
      const existing = ALL_TIMEZONES.find((t) => t.value === browserTz);
      addOption({
        value: browserTz,
        label: existing ? `${existing.label} · Your device` : `${browserTz} · Your device`,
      });
    }

    // Add UTC prominently
    addOption({ value: "UTC", label: "UTC (Coordinated Universal Time)" });

    // Add all other timezones
    for (const tz of ALL_TIMEZONES) {
      addOption(tz);
    }

    return result;
  }, []);

  return (
    <Combobox
      id={id}
      aria-label={ariaLabel}
      options={options}
      value={value}
      onValueChange={onValueChange}
      placeholder="Select timezone..."
      searchPlaceholder="Search timezone..."
      emptyText="No timezone found."
      className={className}
    />
  );
}
