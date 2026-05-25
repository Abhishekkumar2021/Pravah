import { useState } from "react";
import {
  AlertTriangle,
  Calendar,
  Clock,
  Pause,
  Play,
  Trash2,
  Zap,
} from "lucide-react";
import { Pill } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { IconButton } from "@/components/ui/IconButton";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/Dialog";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/Tooltip";
import { DashboardWorkflowListSkeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/components/ui/Toast";
import {
  ApiError,
  deleteSchedule,
  pauseSchedule,
  resumeSchedule,
  type ScheduleResponse,
} from "@/lib/api";
import { formatShortDateTime } from "@/lib/format";
import { cn } from "@/lib/cn";

type ScheduleListProps = {
  schedules: ScheduleResponse[];
  loading: boolean;
  onChanged: () => void;
  onError: (message: string | null) => void;
};

export function ScheduleList({ schedules, loading, onChanged, onError }: ScheduleListProps) {
  const { addToast } = useToast();
  const [deleteTarget, setDeleteTarget] = useState<ScheduleResponse | null>(null);
  const [actionId, setActionId] = useState<string | null>(null);

  async function togglePause(schedule: ScheduleResponse) {
    setActionId(schedule.id);
    onError(null);
    try {
      if (schedule.active) {
        await pauseSchedule(schedule.id);
        addToast({
          type: "info",
          title: "Schedule paused",
          description: `"${schedule.name}" is now paused.`,
        });
      } else {
        await resumeSchedule(schedule.id);
        addToast({
          type: "success",
          title: "Schedule resumed",
          description: `"${schedule.name}" is now active.`,
        });
      }
      onChanged();
    } catch (e) {
      const message = e instanceof ApiError ? e.message : String(e);
      addToast({
        type: "error",
        title: "Action failed",
        description: message,
      });
      onError(message);
    } finally {
      setActionId(null);
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    const targetName = deleteTarget.name;
    setActionId(deleteTarget.id);
    onError(null);
    try {
      await deleteSchedule(deleteTarget.id);
      setDeleteTarget(null);
      addToast({
        type: "success",
        title: "Schedule deleted",
        description: `"${targetName}" has been removed.`,
      });
      onChanged();
    } catch (e) {
      const message = e instanceof ApiError ? e.message : String(e);
      addToast({
        type: "error",
        title: "Delete failed",
        description: message,
      });
      onError(message);
    } finally {
      setActionId(null);
    }
  }

  const activeCount = schedules.filter((s) => s.active).length;
  const pausedCount = schedules.length - activeCount;

  return (
    <TooltipProvider delayDuration={200}>
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-neutral-400" aria-hidden />
              Schedules
            </CardTitle>
            <CardDescription className="mt-1">
              {loading
                ? "Loading…"
                : schedules.length === 0
                  ? "No schedules configured"
                  : `${schedules.length} schedule${schedules.length !== 1 ? "s" : ""}`}
              {!loading && activeCount > 0 && (
                <span className="ml-1.5 text-emerald-600 dark:text-emerald-400">
                  ({activeCount} active)
                </span>
              )}
              {!loading && pausedCount > 0 && (
                <span className="ml-1.5 text-amber-600 dark:text-amber-400">
                  ({pausedCount} paused)
                </span>
              )}
            </CardDescription>
          </div>
        </CardHeader>

        {loading && (
          <div className="border-t border-neutral-100 px-5 pb-4 pt-3 dark:border-neutral-800">
            <DashboardWorkflowListSkeleton rows={2} />
          </div>
        )}

        {!loading && schedules.length === 0 && (
          <div className="flex flex-col items-center gap-4 border-t border-neutral-100 px-5 py-12 text-center dark:border-neutral-800">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-neutral-100 to-neutral-50 dark:from-neutral-800 dark:to-neutral-900">
              <Calendar className="h-7 w-7 text-neutral-400" aria-hidden />
            </div>
            <div className="max-w-sm">
              <p className="text-[14px] font-medium text-neutral-900 dark:text-neutral-100">
                No schedules yet
              </p>
              <p className="mt-1 text-[13px] text-neutral-500 dark:text-neutral-400">
                Create a schedule above to automatically run this workflow on a recurring basis.
              </p>
            </div>
          </div>
        )}

        {!loading && schedules.length > 0 && (
          <ul className="divide-y divide-neutral-100 border-t border-neutral-100 dark:divide-neutral-800 dark:border-neutral-800">
            {schedules.map((s) => (
              <li
                key={s.id}
                className={cn(
                  "group px-5 py-4 transition-colors",
                  s.active
                    ? "hover:bg-neutral-50/70 dark:hover:bg-neutral-900/40"
                    : "bg-neutral-50/50 dark:bg-neutral-900/30",
                )}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    {/* Header row */}
                    <div className="flex flex-wrap items-center gap-2">
                      <div
                        className={cn(
                          "flex h-6 w-6 shrink-0 items-center justify-center rounded-lg",
                          s.active
                            ? "bg-emerald-100 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-400"
                            : "bg-amber-100 text-amber-600 dark:bg-amber-950/60 dark:text-amber-400",
                        )}
                      >
                        {s.active ? (
                          <Zap className="h-3.5 w-3.5" aria-hidden />
                        ) : (
                          <Pause className="h-3.5 w-3.5" aria-hidden />
                        )}
                      </div>
                      <h3 className="text-[14px] font-semibold text-neutral-900 dark:text-neutral-50">
                        {s.name}
                      </h3>
                      <Pill
                        variant={s.active ? "green" : "amber"}
                        className="text-[10px] uppercase tracking-wide"
                      >
                        {s.active ? "Active" : "Paused"}
                      </Pill>
                    </div>

                    {/* Cron expression */}
                    <div className="mt-2 flex items-center gap-2">
                      <code className="rounded-md bg-neutral-100 px-2 py-1 font-mono text-[11px] text-neutral-600 dark:bg-neutral-800 dark:text-neutral-400">
                        {s.cronExpression}
                      </code>
                      <span className="text-[11px] text-neutral-400">·</span>
                      <span className="text-[12px] text-neutral-500 dark:text-neutral-400">
                        {s.timezone}
                      </span>
                    </div>

                    {/* Timing info */}
                    <div className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-[12px] text-neutral-600 dark:text-neutral-400">
                      {s.nextRunAt && (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span className="inline-flex cursor-default items-center gap-1.5">
                              <Clock className="h-3.5 w-3.5 text-blue-500" aria-hidden />
                              <span className="font-medium text-neutral-900 dark:text-neutral-100">
                                Next:
                              </span>{" "}
                              {formatShortDateTime(s.nextRunAt)}
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>Next scheduled run</TooltipContent>
                        </Tooltip>
                      )}
                      {s.lastRunAt && (
                        <span className="inline-flex items-center gap-1.5">
                          <span className="text-neutral-400">Last run:</span>{" "}
                          {formatShortDateTime(s.lastRunAt)}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Action buttons */}
                  <div className="flex shrink-0 items-center gap-1.5">
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <IconButton
                          aria-label={s.active ? "Pause schedule" : "Resume schedule"}
                          disabled={actionId === s.id}
                          onClick={() => void togglePause(s)}
                          className={cn(
                            "h-8 w-8",
                            s.active
                              ? "hover:bg-amber-50 hover:text-amber-600 dark:hover:bg-amber-950/40"
                              : "hover:bg-emerald-50 hover:text-emerald-600 dark:hover:bg-emerald-950/40",
                          )}
                        >
                          {s.active ? (
                            <Pause className="h-4 w-4" />
                          ) : (
                            <Play className="h-4 w-4" />
                          )}
                        </IconButton>
                      </TooltipTrigger>
                      <TooltipContent>{s.active ? "Pause" : "Resume"}</TooltipContent>
                    </Tooltip>

                    <Tooltip>
                      <TooltipTrigger asChild>
                        <IconButton
                          aria-label="Delete schedule"
                          disabled={actionId === s.id}
                          onClick={() => setDeleteTarget(s)}
                          className="h-8 w-8 hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/40"
                        >
                          <Trash2 className="h-4 w-4" />
                        </IconButton>
                      </TooltipTrigger>
                      <TooltipContent>Delete</TooltipContent>
                    </Tooltip>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* Delete confirmation dialog */}
      <Dialog open={deleteTarget !== null} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600 dark:bg-rose-950/60 dark:text-rose-400">
              <AlertTriangle className="h-6 w-6" />
            </div>
            <DialogTitle>Delete schedule?</DialogTitle>
            <DialogDescription className="pt-1">
              {deleteTarget && (
                <>
                  <span className="font-medium text-neutral-900 dark:text-neutral-100">
                    "{deleteTarget.name}"
                  </span>{" "}
                  will be permanently deleted. Future automatic runs will stop immediately.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="secondary">
                Cancel
              </Button>
            </DialogClose>
            <Button
              type="button"
              variant="danger"
              disabled={actionId !== null}
              onClick={() => void confirmDelete()}
            >
              Delete schedule
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </TooltipProvider>
  );
}
