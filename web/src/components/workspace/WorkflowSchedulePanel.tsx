import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  ApiError,
  createSchedule,
  deleteSchedule,
  getDevBearerToken,
  listSchedules,
  pauseSchedule,
  previewCron,
  resumeSchedule,
  type ScheduleResponse,
} from "@/lib/api";
import { formatShortDateTime } from "@/lib/format";

type WorkflowSchedulePanelProps = {
  pipelineId: string;
};

export function WorkflowSchedulePanel({ pipelineId }: WorkflowSchedulePanelProps) {
  const [schedules, setSchedules] = useState<ScheduleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [cronExpression, setCronExpression] = useState("0 9 * * *");
  const [timezone, setTimezone] = useState("UTC");
  const [previewDescription, setPreviewDescription] = useState<string | null>(null);
  const [previewRuns, setPreviewRuns] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  const loadSchedules = useCallback(async () => {
    if (!getDevBearerToken()) {
      setSchedules([]);
      setError("Add a development JWT (Runs → Dev token) to manage schedules.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const list = await listSchedules(pipelineId);
      setSchedules(list);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [pipelineId]);

  useEffect(() => {
    void loadSchedules();
  }, [loadSchedules]);

  async function handlePreview() {
    setError(null);
    setPreviewing(true);
    try {
      const res = await previewCron({ cronExpression, timezone, count: 10 });
      setPreviewDescription(res.description);
      setPreviewRuns(res.nextRuns.map((t) => formatShortDateTime(t)));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setPreviewing(false);
    }
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await createSchedule({ pipelineId, name, cronExpression, timezone });
      setName("");
      await loadSchedules();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  async function togglePause(schedule: ScheduleResponse) {
    setError(null);
    try {
      if (schedule.active) {
        await pauseSchedule(schedule.id);
      } else {
        await resumeSchedule(schedule.id);
      }
      await loadSchedules();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    }
  }

  async function handleDelete(scheduleId: string, scheduleName: string) {
    if (!window.confirm(`Delete schedule "${scheduleName}"? This cannot be undone.`)) {
      return;
    }
    setError(null);
    try {
      await deleteSchedule(scheduleId);
      await loadSchedules();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>New cron schedule</CardTitle>
          <CardDescription>
            Standard 5-field cron (minute hour day month weekday). Timezone uses IANA names (e.g. UTC,
            America/New_York).
          </CardDescription>
        </CardHeader>
        <form className="space-y-3 px-6 pb-6" onSubmit={(e) => void handleCreate(e)}>
          <label className="block text-sm">
            <span className="text-muted-foreground">Name</span>
            <Input
              className="mt-1"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Daily ETL"
              required
            />
          </label>
          <label className="block text-sm">
            <span className="text-muted-foreground">Cron expression</span>
            <Input
              className="mt-1 font-mono text-[13px]"
              value={cronExpression}
              onChange={(e) => setCronExpression(e.target.value)}
              placeholder="0 9 * * *"
              required
            />
          </label>
          <label className="block text-sm">
            <span className="text-muted-foreground">Timezone</span>
            <Input
              className="mt-1"
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
              placeholder="UTC"
              required
            />
          </label>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="secondary"
              disabled={previewing}
              onClick={() => void handlePreview()}
            >
              {previewing ? "Loading…" : "Preview"}
            </Button>
            <Button type="submit" disabled={saving || !name.trim()}>
              {saving ? "Creating…" : "Create schedule"}
            </Button>
          </div>
          {previewDescription && (
            <p className="text-sm text-muted-foreground">
              <span className="font-medium text-foreground">{previewDescription}</span>
              {previewRuns.length > 0 && (
                <>
                  {" "}
                  — next runs: {previewRuns.slice(0, 5).join(", ")}
                  {previewRuns.length > 5 ? "…" : ""}
                </>
              )}
            </p>
          )}
        </form>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Schedules</CardTitle>
          <CardDescription>
            {loading ? "Loading…" : schedules.length === 0 ? "No schedules for this workflow." : null}
          </CardDescription>
        </CardHeader>
        {schedules.length > 0 && (
          <ul className="divide-y border-t px-6 pb-4">
            {schedules.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
                <div>
                  <p className="font-medium">{s.name}</p>
                  <p className="font-mono text-[12px] text-muted-foreground">
                    {s.cronExpression} ({s.timezone})
                  </p>
                  <p className="text-[12px] text-muted-foreground">
                    {s.active ? "Active" : "Paused"}
                    {s.nextRunAt ? ` · next ${formatShortDateTime(s.nextRunAt)}` : ""}
                    {s.lastRunAt ? ` · last ${formatShortDateTime(s.lastRunAt)}` : ""}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    className="h-8 px-3 text-[12px]"
                    onClick={() => void togglePause(s)}
                  >
                    {s.active ? "Pause" : "Resume"}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-8 px-3 text-[12px]"
                    onClick={() => void handleDelete(s.id, s.name)}
                  >
                    Delete
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {error && (
        <p className="text-sm text-rose-600 dark:text-rose-400" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
