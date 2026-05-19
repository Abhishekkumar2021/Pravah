import { Circle } from "lucide-react";
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
        <textarea
          id="echo-message"
          value={config.message ?? ""}
          onChange={(e) => onChange({ ...config, message: e.target.value })}
          placeholder="Hello from echo stage!"
          rows={4}
          className="mt-1.5 w-full rounded-lg border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-400 hover:border-neutral-300 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 dark:border-neutral-700 dark:bg-neutral-900 dark:text-neutral-100 dark:hover:border-neutral-600"
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
