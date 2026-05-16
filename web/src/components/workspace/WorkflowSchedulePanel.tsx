import { useCallback, useEffect, useState } from "react";
import { ScheduleForm } from "@/components/workspace/ScheduleForm";
import { ScheduleList } from "@/components/workspace/ScheduleList";
import { ApiError, getDevBearerToken, listSchedules, type ScheduleResponse } from "@/lib/api";

type WorkflowSchedulePanelProps = {
  pipelineId: string;
};

export function WorkflowSchedulePanel({ pipelineId }: WorkflowSchedulePanelProps) {
  const [schedules, setSchedules] = useState<ScheduleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSchedules = useCallback(async () => {
    if (!getDevBearerToken()) {
      setSchedules([]);
      setError("Sign in or add a development JWT (Runs → Dev token) to manage schedules.");
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

  return (
    <div className="space-y-6">
      {error && (
        <p
          className="rounded-lg border border-rose-200/80 bg-rose-50/80 px-3 py-2 text-[13px] text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-200"
          role="alert"
        >
          {error}
        </p>
      )}

      <ScheduleForm
        pipelineId={pipelineId}
        onCreated={() => void loadSchedules()}
        onError={setError}
      />

      <ScheduleList
        schedules={schedules}
        loading={loading}
        onChanged={() => void loadSchedules()}
        onError={setError}
      />
    </div>
  );
}
