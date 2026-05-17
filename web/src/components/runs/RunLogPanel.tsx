import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, Download } from "lucide-react";
import type { JobSummary } from "@/lib/api";
import { ApiError, getJobLogs, type JobLogLine } from "@/lib/api";
import { isFailedJob } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";

type RunLogPanelProps = {
  executionId: string;
  job: JobSummary;
  /** When false, skip fetching (panel collapsed). */
  active?: boolean;
  className?: string;
};

const LEVELS = ["ALL", "INFO", "WARN", "ERROR"] as const;

const LEVEL_OPTIONS = LEVELS.map((level) => ({ value: level, label: level }));

function levelClass(level: string): string {
  switch (level.toUpperCase()) {
    case "ERROR":
      return "text-rose-400";
    case "WARN":
      return "text-amber-300";
    case "DEBUG":
      return "text-neutral-500";
    default:
      return "text-neutral-300";
  }
}

function formatLogTime(iso: string): string {
  try {
    return new Date(iso).toISOString().slice(11, 23);
  } catch {
    return iso;
  }
}

export function RunLogPanel({ executionId, job, active = true, className }: RunLogPanelProps) {
  const [lines, setLines] = useState<JobLogLine[]>([]);
  const [levelFilter, setLevelFilter] = useState<(typeof LEVELS)[number]>("ALL");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const errorLineRefs = useRef<Map<string, HTMLParagraphElement>>(new Map());
  const failed = isFailedJob(job.status);
  const isRunning = job.status.toLowerCase() === "running";

  useEffect(() => {
    if (!active) {
      return;
    }
    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const res = await getJobLogs(
          executionId,
          job.id,
          levelFilter !== "ALL" ? { level: levelFilter } : undefined,
        );
        if (!cancelled) {
          setLines(res.lines);
        }
      } catch (e: unknown) {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : "Could not load logs");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    if (!isRunning) {
      return () => {
        cancelled = true;
      };
    }
    const interval = window.setInterval(() => void load(), 3000);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [active, executionId, job.id, isRunning, levelFilter]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return lines.filter((line) => {
      if (levelFilter !== "ALL" && line.level.toUpperCase() !== levelFilter) {
        return false;
      }
      if (q && !line.message.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [lines, levelFilter, search]);

  const firstErrorId = useMemo(() => {
    const errorLine = filtered.find((l) => l.level.toUpperCase() === "ERROR");
    return errorLine?.id ?? null;
  }, [filtered]);

  const errorCount = useMemo(() => {
    return filtered.filter((l) => l.level.toUpperCase() === "ERROR").length;
  }, [filtered]);

  const jumpToError = useCallback(() => {
    if (!firstErrorId) return;
    const el = errorLineRefs.current.get(firstErrorId);
    if (el && scrollRef.current) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      el.classList.add("animate-pulse");
      setTimeout(() => el.classList.remove("animate-pulse"), 1500);
    }
  }, [firstErrorId]);

  useEffect(() => {
    if (active && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [active, filtered.length]);

  function downloadLogs() {
    const body = filtered
      .map((l) => `${formatLogTime(l.logTime)} [${l.level}] ${l.message}`)
      .join("\n");
    const blob = new Blob([body], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${job.stageId}-logs.txt`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className={cn("space-y-2", className)}>
      <div className="flex flex-wrap items-center gap-2">
        <Select
          id={`log-level-${job.id}`}
          aria-label="Filter by level"
          value={levelFilter}
          onValueChange={(value) => setLevelFilter(value as (typeof LEVELS)[number])}
          options={LEVEL_OPTIONS}
          className="w-[6.5rem] shrink-0"
        />
        <Input
          type="search"
          placeholder="Search logs…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search within logs"
          className="min-w-[8rem] flex-1"
        />
        {errorCount > 0 && (
          <Button
            type="button"
            variant="secondary"
            onClick={jumpToError}
            className="shrink-0 gap-1.5 text-rose-600 hover:text-rose-700 dark:text-rose-400 dark:hover:text-rose-300"
          >
            <AlertTriangle className="h-3.5 w-3.5" aria-hidden />
            Jump to error
            {errorCount > 1 && (
              <span className="ml-0.5 rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-medium text-rose-700 dark:bg-rose-900/50 dark:text-rose-300">
                {errorCount}
              </span>
            )}
          </Button>
        )}
        <IconButton
          type="button"
          aria-label="Download logs"
          onClick={downloadLogs}
          disabled={filtered.length === 0}
          className="shrink-0"
        >
          <Download className="h-4 w-4" aria-hidden />
        </IconButton>
      </div>
      <div
        ref={scrollRef}
        className={cn(
          "max-h-64 overflow-y-auto rounded-lg border border-neutral-200 bg-neutral-950 px-3 py-3 font-mono text-[12px] leading-relaxed dark:border-neutral-800",
          failed && "border-rose-800/60",
        )}
      >
        {loading && lines.length === 0 && <p className="text-neutral-500">Loading logs…</p>}
        {error && <p className="text-rose-400">{error}</p>}
        {!loading && !error && filtered.length === 0 && (
          <p className="text-neutral-500">No log lines yet for this stage.</p>
        )}
        {filtered.map((line) => {
          const isError = line.level.toUpperCase() === "ERROR";
          return (
            <p
              key={line.id}
              ref={isError ? (el) => { if (el) errorLineRefs.current.set(line.id, el); } : undefined}
              className={cn(
                "whitespace-pre-wrap break-words",
                isError && "rounded bg-rose-950/50 px-1 -mx-1",
              )}
            >
              <span className="text-neutral-500">{formatLogTime(line.logTime)} </span>
              <span className={levelClass(line.level)}>[{line.level}]</span>{" "}
              <span className="text-neutral-200">{line.message}</span>
            </p>
          );
        })}
      </div>
    </div>
  );
}
