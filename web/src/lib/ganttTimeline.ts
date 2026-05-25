import type { JobSummary } from "@/lib/api";
import { normalizeJobStatus } from "@/lib/jobStatus";

export type GanttSegmentKind = "queued" | "running" | "pending";

export type GanttSegment = {
  kind: GanttSegmentKind;
  startPct: number;
  widthPct: number;
};

export type GanttRow = {
  job: JobSummary;
  segments: GanttSegment[];
  waitMs: number | null;
  durationMs: number | null;
};

export type GanttTick = {
  pct: number;
  label: string;
};

export type GanttTimeline = {
  rows: GanttRow[];
  domainStartMs: number;
  domainEndMs: number;
  spanMs: number;
  hasTimingData: boolean;
  ticks: GanttTick[];
};

function parseTime(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = new Date(iso).getTime();
  return Number.isFinite(ms) ? ms : null;
}

function isActiveJob(status: string): boolean {
  const normalized = normalizeJobStatus(status);
  return normalized === "running" || normalized === "queued";
}

export function sortJobsForGantt(jobs: JobSummary[]): JobSummary[] {
  return [...jobs].sort((a, b) => {
    const aStart = parseTime(a.queuedAt) ?? parseTime(a.startedAt) ?? Number.POSITIVE_INFINITY;
    const bStart = parseTime(b.queuedAt) ?? parseTime(b.startedAt) ?? Number.POSITIVE_INFINITY;
    if (aStart !== bStart) return aStart - bStart;
    return a.stageId.localeCompare(b.stageId);
  });
}

function formatTickLabel(ms: number, domainStartMs: number, spanMs: number): string {
  if (spanMs <= 60_000) {
    const seconds = Math.round((ms - domainStartMs) / 1000);
    return `${seconds}s`;
  }
  if (spanMs <= 3_600_000) {
    const minutes = Math.floor((ms - domainStartMs) / 60_000);
    const seconds = Math.round(((ms - domainStartMs) % 60_000) / 1000);
    return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`;
  }
  return new Date(ms).toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function buildTicks(domainStartMs: number, domainEndMs: number, tickCount = 5): GanttTick[] {
  const spanMs = Math.max(domainEndMs - domainStartMs, 1);
  return Array.from({ length: tickCount + 1 }, (_, index) => {
    const pct = (index / tickCount) * 100;
    const ms = domainStartMs + (spanMs * index) / tickCount;
    return {
      pct,
      label: formatTickLabel(ms, domainStartMs, spanMs),
    };
  });
}

function pctOf(ms: number, domainStartMs: number, spanMs: number): number {
  return ((ms - domainStartMs) / spanMs) * 100;
}

function clampWidth(widthPct: number): number {
  return Math.max(widthPct, 0.75);
}

export function computeGanttTimeline(jobs: JobSummary[], nowMs = Date.now()): GanttTimeline {
  const ordered = sortJobsForGantt(jobs);
  if (ordered.length === 0) {
    return {
      rows: [],
      domainStartMs: nowMs,
      domainEndMs: nowMs + 1,
      spanMs: 1,
      hasTimingData: false,
      ticks: [],
    };
  }

  const timed = ordered.map((job) => ({
    job,
    queued: parseTime(job.queuedAt),
    started: parseTime(job.startedAt),
    completed: parseTime(job.completedAt),
  }));

  const hasTimingData = timed.some((entry) => entry.queued != null || entry.started != null);

  if (!hasTimingData) {
    return {
      rows: ordered.map((job) => ({
        job,
        segments: [{ kind: "pending", startPct: 0, widthPct: 100 }],
        waitMs: null,
        durationMs: null,
      })),
      domainStartMs: nowMs,
      domainEndMs: nowMs + ordered.length,
      spanMs: ordered.length,
      hasTimingData: false,
      ticks: buildTicks(nowMs, nowMs + ordered.length),
    };
  }

  let domainStartMs = Number.POSITIVE_INFINITY;
  let domainEndMs = Number.NEGATIVE_INFINITY;

  for (const entry of timed) {
    const rowStart = entry.queued ?? entry.started;
    let rowEnd = entry.completed;
    if (rowEnd == null && entry.started != null && isActiveJob(entry.job.status)) {
      rowEnd = nowMs;
    }
    if (rowEnd == null && entry.queued != null && !entry.started && isActiveJob(entry.job.status)) {
      rowEnd = nowMs;
    }
    if (rowStart != null) {
      domainStartMs = Math.min(domainStartMs, rowStart);
    }
    if (rowEnd != null) {
      domainEndMs = Math.max(domainEndMs, rowEnd);
    }
    if (entry.started != null && entry.completed == null && isActiveJob(entry.job.status)) {
      domainEndMs = Math.max(domainEndMs, nowMs);
    }
  }

  if (!Number.isFinite(domainStartMs)) {
    domainStartMs = nowMs;
  }
  if (!Number.isFinite(domainEndMs) || domainEndMs <= domainStartMs) {
    domainEndMs = domainStartMs + 1_000;
  }

  const spanMs = Math.max(domainEndMs - domainStartMs, 1);

  const rows: GanttRow[] = timed.map(({ job, queued, started, completed }) => {
    const segments: GanttSegment[] = [];

    if (queued != null && started != null && queued < started) {
      segments.push({
        kind: "queued",
        startPct: pctOf(queued, domainStartMs, spanMs),
        widthPct: clampWidth(pctOf(started, domainStartMs, spanMs) - pctOf(queued, domainStartMs, spanMs)),
      });
    } else if (queued != null && started == null && isActiveJob(job.status)) {
      segments.push({
        kind: "queued",
        startPct: pctOf(queued, domainStartMs, spanMs),
        widthPct: clampWidth(pctOf(nowMs, domainStartMs, spanMs) - pctOf(queued, domainStartMs, spanMs)),
      });
    }

    if (started != null) {
      const endMs = completed ?? (isActiveJob(job.status) ? nowMs : started);
      segments.push({
        kind: "running",
        startPct: pctOf(started, domainStartMs, spanMs),
        widthPct: clampWidth(pctOf(endMs, domainStartMs, spanMs) - pctOf(started, domainStartMs, spanMs)),
      });
    }

    if (segments.length === 0) {
      segments.push({ kind: "pending", startPct: 0, widthPct: 100 });
    }

    const waitMs = queued != null && started != null ? started - queued : null;
    const durationMs =
      started != null ? (completed ?? (isActiveJob(job.status) ? nowMs : started)) - started : null;

    return { job, segments, waitMs, durationMs };
  });

  return {
    rows,
    domainStartMs,
    domainEndMs,
    spanMs,
    hasTimingData: true,
    ticks: buildTicks(domainStartMs, domainEndMs),
  };
}

export function formatGanttDuration(ms: number | null): string {
  if (ms == null) return "";
  if (ms < 1000) return `${ms}ms`;
  const sec = Math.floor(ms / 1000);
  if (sec < 60) return `${sec}s`;
  const min = Math.floor(sec / 60);
  const remSec = sec % 60;
  return remSec > 0 ? `${min}m ${remSec}s` : `${min}m`;
}
