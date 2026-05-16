import { useEffect, useMemo, useState } from "react";
import {
  CalendarClock,
  Check,
  Clock,
  Globe,
  Loader2,
  Plus,
  Repeat,
  Sparkles,
} from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/Skeleton";
import { TimePicker } from "@/components/ui/TimePicker";
import { useToast } from "@/components/ui/Toast";
import { ApiError, createSchedule, previewCron } from "@/lib/api";
import {
  buildCronFromPreset,
  defaultTimeForPreset,
  SCHEDULE_PRESETS,
  timezoneOptions,
  WEEKDAY_OPTIONS,
  type SchedulePresetId,
} from "@/lib/schedulePresets";
import { formatShortDateTime } from "@/lib/format";
import { cn } from "@/lib/cn";

type ScheduleFormProps = {
  pipelineId: string;
  onCreated: () => void;
  onError: (message: string | null) => void;
};

const PRESET_ICONS: Record<SchedulePresetId, string> = {
  hourly: "⏱️",
  daily: "📅",
  weekdays: "💼",
  weekly: "📆",
  monthly: "🗓️",
  custom: "⚙️",
};

export function ScheduleForm({ pipelineId, onCreated, onError }: ScheduleFormProps) {
  const { addToast } = useToast();
  const tzOptions = useMemo(() => timezoneOptions(), []);

  const [name, setName] = useState("");
  const [preset, setPreset] = useState<SchedulePresetId>("daily");
  const [time, setTime] = useState(defaultTimeForPreset());
  const [dayOfWeek, setDayOfWeek] = useState("1");
  const [customCron, setCustomCron] = useState("0 9 * * *");
  const [timezone, setTimezone] = useState(() => tzOptions[0]?.value ?? "UTC");

  const [saving, setSaving] = useState(false);

  const [previewDescription, setPreviewDescription] = useState<string | null>(null);
  const [previewRuns, setPreviewRuns] = useState<string[]>([]);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  const cronExpression = useMemo(
    () => buildCronFromPreset({ preset, time, dayOfWeek, customCron }),
    [preset, time, dayOfWeek, customCron],
  );

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void (async () => {
        setPreviewLoading(true);
        setPreviewError(null);
        try {
          const res = await previewCron({ cronExpression, timezone, count: 5 });
          setPreviewDescription(res.description);
          setPreviewRuns(res.nextRuns.map((t) => formatShortDateTime(t)));
        } catch (e) {
          setPreviewDescription(null);
          setPreviewRuns([]);
          setPreviewError(e instanceof ApiError ? e.message : String(e));
        } finally {
          setPreviewLoading(false);
        }
      })();
    }, 400);
    return () => window.clearTimeout(handle);
  }, [cronExpression, timezone]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    const savedName = name.trim();
    setSaving(true);
    onError(null);
    try {
      await createSchedule({ pipelineId, name: savedName, cronExpression, timezone });
      setName("");
      addToast({
        type: "success",
        title: "Schedule created",
        description: `"${savedName}" has been scheduled successfully.`,
      });
      onCreated();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : String(err);
      addToast({
        type: "error",
        title: "Failed to create schedule",
        description: message,
      });
      onError(message);
    } finally {
      setSaving(false);
    }
  }

  const showTime = preset !== "hourly" && preset !== "custom";
  const showWeekday = preset === "weekly";

  return (
    <Card className="overflow-hidden">
      <CardHeader className="border-b border-neutral-100 bg-gradient-to-b from-neutral-50/80 to-white pb-4 dark:border-neutral-800 dark:from-neutral-900/50 dark:to-neutral-950">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-lg shadow-blue-500/25">
            <Repeat className="h-5 w-5" aria-hidden />
          </div>
          <div>
            <CardTitle className="text-[15px]">Create schedule</CardTitle>
            <CardDescription className="mt-0.5">
              Automate this workflow with recurring runs
            </CardDescription>
          </div>
        </div>
      </CardHeader>

      <form className="p-5" onSubmit={(e) => void handleSubmit(e)}>
        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          {/* Left column: form fields */}
          <div className="space-y-6">
            {/* Schedule name */}
            <div className="space-y-2">
              <Label htmlFor="schedule-name" className="flex items-center gap-1.5 text-[13px]">
                <Sparkles className="h-3.5 w-3.5 text-amber-500" aria-hidden />
                Schedule name
              </Label>
              <Input
                id="schedule-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Daily sync, Hourly refresh"
                required
                autoComplete="off"
                className="max-w-md"
              />
            </div>

            {/* Frequency preset cards */}
            <div className="space-y-3">
              <Label className="flex items-center gap-1.5 text-[13px]">
                <Clock className="h-3.5 w-3.5 text-blue-500" aria-hidden />
                Frequency
              </Label>
              <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3">
                {SCHEDULE_PRESETS.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setPreset(p.id)}
                    className={cn(
                      "group relative flex items-start gap-3 rounded-xl border px-4 py-3.5 text-left transition-all duration-200",
                      "hover:shadow-md",
                      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2",
                      preset === p.id
                        ? "border-blue-500 bg-blue-50 shadow-sm ring-1 ring-blue-500/20 dark:border-blue-500 dark:bg-blue-950/50"
                        : "border-neutral-200 bg-white hover:border-blue-200 hover:bg-blue-50/30 dark:border-neutral-800 dark:bg-neutral-950 dark:hover:border-blue-900 dark:hover:bg-blue-950/20",
                    )}
                  >
                    <span className="text-xl" role="img" aria-hidden>
                      {PRESET_ICONS[p.id]}
                    </span>
                    <div className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block text-[13px] font-semibold",
                          preset === p.id
                            ? "text-blue-700 dark:text-blue-300"
                            : "text-neutral-900 dark:text-neutral-100",
                        )}
                      >
                        {p.label}
                      </span>
                      <span className="mt-0.5 block text-[11px] leading-snug text-neutral-500 dark:text-neutral-400">
                        {p.description}
                      </span>
                    </div>
                    {preset === p.id && (
                      <div className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-white shadow-sm">
                        <Check className="h-3 w-3" strokeWidth={3} />
                      </div>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* Time and day picker */}
            {(showTime || showWeekday) && (
              <section
                className="rounded-xl border border-neutral-200/80 bg-white p-5 shadow-sm dark:border-neutral-800 dark:bg-neutral-950/50"
                aria-label="Schedule timing"
              >
                <div className="grid gap-6 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
                  {showTime && (
                    <div className="flex min-w-0 items-center gap-4">
                      <Label htmlFor="schedule-time" className="w-16 shrink-0">
                        Run at
                      </Label>
                      <TimePicker id="schedule-time" value={time} onChange={setTime} />
                    </div>
                  )}
                  {showWeekday && (
                    <div className="flex min-w-0 items-center gap-4 sm:min-w-[11rem]">
                      <Label htmlFor="schedule-weekday" className="w-16 shrink-0">
                        On
                      </Label>
                      <Select
                        id="schedule-weekday"
                        aria-label="Day of week"
                        value={dayOfWeek}
                        onValueChange={setDayOfWeek}
                        options={WEEKDAY_OPTIONS}
                      />
                    </div>
                  )}
                </div>
              </section>
            )}

            {/* Hourly info */}
            {preset === "hourly" && (
              <div className="flex items-center gap-3 rounded-xl border border-blue-100 bg-blue-50/50 px-4 py-3 dark:border-blue-900/50 dark:bg-blue-950/30">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/50">
                  <Clock className="h-4 w-4 text-blue-600 dark:text-blue-400" aria-hidden />
                </div>
                <p className="text-[13px] text-blue-800 dark:text-blue-200">
                  Runs at minute <span className="font-mono font-semibold">:00</span> of every hour
                </p>
              </div>
            )}

            {/* Custom cron input */}
            {preset === "custom" && (
              <div className="space-y-3 rounded-xl border border-neutral-100 bg-neutral-50/50 p-4 dark:border-neutral-800 dark:bg-neutral-900/30">
                <div className="space-y-2">
                  <Label htmlFor="schedule-cron" className="text-[12px]">
                    Cron expression
                  </Label>
                  <Input
                    id="schedule-cron"
                    value={customCron}
                    onChange={(e) => setCustomCron(e.target.value)}
                    placeholder="0 9 * * *"
                    className="max-w-xs font-mono text-[14px]"
                    required
                    spellCheck={false}
                  />
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-[11px] text-neutral-500">Format:</span>
                  {["minute", "hour", "day", "month", "weekday"].map((field) => (
                    <span
                      key={field}
                      className="rounded-md bg-neutral-200/80 px-2 py-0.5 font-mono text-[10px] text-neutral-600 dark:bg-neutral-700 dark:text-neutral-400"
                    >
                      {field}
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Timezone */}
            <div className="space-y-2">
              <Label htmlFor="schedule-timezone" className="flex items-center gap-1.5 text-[13px]">
                <Globe className="h-3.5 w-3.5 text-emerald-500" aria-hidden />
                Timezone
              </Label>
              <Select
                id="schedule-timezone"
                aria-label="Timezone"
                value={timezone}
                onValueChange={setTimezone}
                options={tzOptions.map((t) => ({ value: t.value, label: t.label }))}
                className="max-w-xs"
              />
            </div>

            {/* Generated cron display */}
            {preset !== "custom" && (
              <div className="flex items-center gap-2 text-[12px]">
                <span className="text-neutral-500 dark:text-neutral-400">Generated cron:</span>
                <code className="rounded-lg bg-neutral-900 px-3 py-1.5 font-mono text-[12px] text-emerald-400">
                  {cronExpression}
                </code>
              </div>
            )}
          </div>

          {/* Right column: preview panel */}
          <div className="flex flex-col rounded-2xl border border-neutral-100 bg-gradient-to-br from-neutral-50 via-white to-blue-50/30 dark:border-neutral-800 dark:from-neutral-900/80 dark:via-neutral-950 dark:to-blue-950/20">
            <div className="flex items-center gap-2 border-b border-neutral-100 px-4 py-3 dark:border-neutral-800">
              <CalendarClock className="h-4 w-4 text-blue-500" aria-hidden />
              <span className="text-[13px] font-semibold text-neutral-800 dark:text-neutral-200">
                Schedule Preview
              </span>
            </div>

            <div className="flex-1 p-4" aria-live="polite">
              {previewLoading && (
                <div className="space-y-4" aria-busy="true">
                  <Skeleton className="h-6 w-4/5" />
                  <div className="space-y-2.5 pt-2">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <Skeleton key={i} className="h-5 w-full" />
                    ))}
                  </div>
                </div>
              )}

              {!previewLoading && previewError && (
                <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-[13px] text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300">
                  {previewError}
                </div>
              )}

              {!previewLoading && !previewError && previewDescription && (
                <div className="space-y-4">
                  <div className="rounded-xl bg-gradient-to-r from-blue-600 to-blue-500 px-4 py-3 text-white shadow-lg shadow-blue-500/20">
                    <p className="text-[14px] font-medium leading-relaxed">
                      {previewDescription}
                    </p>
                  </div>

                  {previewRuns.length > 0 && (
                    <div>
                      <p className="mb-3 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wider text-neutral-500">
                        <CalendarClock className="h-3.5 w-3.5" />
                        Next scheduled runs
                      </p>
                      <ol className="space-y-2">
                        {previewRuns.map((run, i) => (
                          <li
                            key={`${run}-${i}`}
                            className="flex items-center gap-3 rounded-lg border border-neutral-100 bg-white px-3 py-2.5 dark:border-neutral-800 dark:bg-neutral-900/50"
                          >
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-100 text-[11px] font-bold text-blue-600 dark:bg-blue-900/50 dark:text-blue-400">
                              {i + 1}
                            </span>
                            <span className="text-[13px] font-medium text-neutral-800 dark:text-neutral-200">
                              {run}
                            </span>
                          </li>
                        ))}
                      </ol>
                    </div>
                  )}
                </div>
              )}

              {!previewLoading && !previewError && !previewDescription && (
                <div className="flex flex-col items-center justify-center py-8 text-center">
                  <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
                    <CalendarClock className="h-6 w-6 text-neutral-400" />
                  </div>
                  <p className="text-[13px] text-neutral-500">
                    Configure your schedule to preview runs
                  </p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Submit button */}
        <div className="mt-6 flex items-center gap-3 border-t border-neutral-100 pt-5 dark:border-neutral-800">
          <Button type="submit" disabled={saving || !name.trim()} className="gap-2 px-5">
            {saving ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                Creating…
              </>
            ) : (
              <>
                <Plus className="h-4 w-4" aria-hidden />
                Create schedule
              </>
            )}
          </Button>
          <span className="text-[12px] text-neutral-400">
            {name.trim() ? "Ready to create" : "Enter a name to continue"}
          </span>
        </div>
      </form>
    </Card>
  );
}
