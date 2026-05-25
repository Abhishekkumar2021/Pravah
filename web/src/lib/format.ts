/** Terminal execution states where the run has completed (matches backend ExecutionState). */
export const TERMINAL_STATUSES = ["succeeded", "failed", "cancelled"] as const;

export function formatShortDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null || ms < 0 || !Number.isFinite(ms)) return "—";
  const sTotal = Math.floor(ms / 1000);
  const h = Math.floor(sTotal / 3600);
  const m = Math.floor((sTotal % 3600) / 60);
  const s = sTotal % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export type ExecutionTiming = {
  status: string;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
};

/** Wall-clock duration from start to completion (or now while non-terminal). */
export function formatExecutionWallDuration(row: ExecutionTiming): string {
  const now = Date.now();
  const startedMs = row.startedAt ? new Date(row.startedAt).getTime() : null;
  const completedMs = row.completedAt ? new Date(row.completedAt).getTime() : null;
  const st = row.status.toLowerCase();
  const terminal = (TERMINAL_STATUSES as readonly string[]).includes(st);
  if (startedMs != null && completedMs != null) return formatDurationMs(completedMs - startedMs);
  if (startedMs != null && !terminal) return formatDurationMs(now - startedMs);
  return "—";
}
