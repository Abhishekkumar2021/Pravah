import { Circle } from "lucide-react";
import { ExpandableTextarea } from "@/components/ui/ExpandableTextarea";
import { Label } from "@/components/ui/Label";

type EchoConfig = {
  message: string;
};

type EchoConfigFormProps = {
  config: EchoConfig;
  onChange: (config: EchoConfig) => void;
  errors: { field?: string; message: string }[];
};

export function EchoConfigForm({ config, onChange }: EchoConfigFormProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400">
        <Circle className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">Echo Stage</span>
      </div>

      <div>
        <Label htmlFor="echo-message">Message</Label>
        <ExpandableTextarea
          id="echo-message"
          value={config.message ?? ""}
          onChange={(e) => onChange({ ...config, message: e.target.value })}
          placeholder="Hello from echo stage!"
          rows={4}
          expandTitle="Echo message"
          minHeightClass="min-h-[6rem]"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Message to echo. Use for debugging or as a placeholder stage.
        </p>
      </div>

      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 dark:border-amber-900/50 dark:bg-amber-950/30">
        <p className="text-xs text-amber-800 dark:text-amber-200">
          <strong>Tip:</strong> Echo stages are useful for testing workflow structure before adding real logic.
        </p>
      </div>
    </div>
  );
}
