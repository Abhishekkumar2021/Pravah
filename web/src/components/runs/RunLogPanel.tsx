import { useEffect, useMemo, useRef, useState } from "react";
import { Download } from "lucide-react";
import type { JobSummary } from "@/lib/api";
import { ApiError, getJobLogs, type JobLogLine } from "@/lib/api";
import { isFailedJob } from "@/lib/jobStatus";
import { cn } from "@/lib/cn";
import { Button } from "@/components/ui/Button";

type RunLogPanelProps = {
  executionId: string;
  job: JobSummary;
  /** When false, skip fetching (panel collapsed). */
  active?: boolean;
  className?: string;
};

const LEVELS = ["ALL", "INFO", "WARN", "ERROR"] as const;

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
        const res = await getJobLogs(executionId, job.id);
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
  }, [active, executionId, job.id, isRunning]);

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
        <label className="sr-only" htmlFor={`log-level-${job.id}`}>
          Filter by level
        </label>
        <select
          id={`log-level-${job.id}`}
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value as (typeof LEVELS)[number])}
          className="h-8 rounded-md border border-neutral-700 bg-neutral-900 px-2 text-[12px] text-neutral-200"
        >
          {LEVELS.map((l) => (
            <option key={l} value={l}>
              {l}
            </option>
          ))}
        </select>
        <input
          type="search"
          placeholder="Search logs…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="h-8 min-w-[8rem] flex-1 rounded-md border border-neutral-700 bg-neutral-900 px-2 text-[12px] text-neutral-200 placeholder:text-neutral-500"
          aria-label="Search within logs"
        />
        <Button
          type="button"
          variant="ghost"
          className="h-8 px-2 text-neutral-400"
          onClick={downloadLogs}
          disabled={filtered.length === 0}
          aria-label="Download logs"
        >
          <Download className="h-4 w-4" aria-hidden />
        </Button>
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
        {filtered.map((line) => (
          <p key={line.id} className="whitespace-pre-wrap break-words">
            <span className="text-neutral-500">{formatLogTime(line.logTime)} </span>
            <span className={levelClass(line.level)}>[{line.level}]</span>{" "}
            <span className="text-neutral-200">{line.message}</span>
          </p>
        ))}
      </div>
    </div>
  );
}
