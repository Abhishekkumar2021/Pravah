export function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

export function parseTime(value: string): { hour: number; minute: number } {
  const match = /^(\d{1,2}):(\d{2})$/.exec(value.trim());
  if (!match) return { hour: 9, minute: 0 };
  return {
    hour: Math.min(23, Math.max(0, parseInt(match[1], 10))),
    minute: Math.min(59, Math.max(0, parseInt(match[2], 10))),
  };
}

export function formatTime12(hour: number, minute: number): string {
  const period = hour >= 12 ? "PM" : "AM";
  const h = hour % 12 || 12;
  return `${h}:${pad(minute)} ${period}`;
}

export function toHour12(hour24: number): number {
  const h = hour24 % 12;
  return h === 0 ? 12 : h;
}

export function isPm(hour24: number): boolean {
  return hour24 >= 12;
}

export function toHour24(hour12: number, pm: boolean): number {
  if (pm) {
    return hour12 === 12 ? 12 : hour12 + 12;
  }
  return hour12 === 12 ? 0 : hour12;
}

export function formatTimeValue(hour: number, minute: number): string {
  return `${pad(hour)}:${pad(minute)}`;
}

/** Snap to 5-minute steps for schedule-friendly minute selection. */
export function snapMinute(minute: number): number {
  return Math.round(minute / 5) * 5 % 60;
}

export const HOUR_OPTIONS = Array.from({ length: 12 }, (_, i) => {
  const h = i === 0 ? 12 : i;
  return { value: String(h), label: String(h) };
});

export const MINUTE_OPTIONS = Array.from({ length: 12 }, (_, i) => {
  const m = i * 5;
  return { value: pad(m), label: pad(m) };
});
