import { useSyncExternalStore, useState } from "react";
import { Play } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/Button";
import { ApiError, createExecution, getDevBearerToken, subscribeDevBearerToken } from "@/lib/api";
import { cn } from "@/lib/cn";

type TriggerRunButtonProps = {
  pipelineId: string;
  pipelineVersion?: number | null;
  disabled?: boolean;
  className?: string;
  variant?: "primary" | "secondary" | "ghost";
  size?: "default" | "sm";
  label?: string;
};

export function TriggerRunButton({
  pipelineId,
  pipelineVersion,
  disabled,
  className,
  variant = "ghost",
  size = "sm",
  label = "Run",
}: TriggerRunButtonProps) {
  const navigate = useNavigate();
  const hasToken = useSyncExternalStore(
    subscribeDevBearerToken,
    () => Boolean(getDevBearerToken()),
    () => false,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRun() {
    setLoading(true);
    setError(null);
    try {
      const res = await createExecution(pipelineId, pipelineVersion);
      navigate(`/app/runs/${res.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  const title = !hasToken
    ? "Add a development JWT (Runs → Dev token) to start a run"
    : error ?? undefined;

  return (
    <div className={cn("inline-flex flex-col items-end gap-1", className)}>
      <Button
        type="button"
        variant={variant}
        className={cn(size === "sm" && "h-8 px-3 text-[12px]")}
        disabled={disabled || !hasToken || loading}
        aria-busy={loading}
        title={title}
        onClick={() => void handleRun()}
      >
        <Play className="h-3.5 w-3.5" aria-hidden />
        {loading ? "Starting…" : label}
      </Button>
      {error && (
        <span className="max-w-[14rem] text-right text-[11px] text-rose-600 dark:text-rose-400" role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
