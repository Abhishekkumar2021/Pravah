import { Box } from "lucide-react";
import { Checkbox } from "@/components/ui/Checkbox";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";

type DbtConfig = {
  select: string;
  exclude?: string;
  fullRefresh?: boolean;
  target?: string;
};

type DbtConfigFormProps = {
  config: DbtConfig;
  onChange: (config: DbtConfig) => void;
  errors: { field?: string; message: string }[];
};

export function DbtConfigForm({ config, onChange, errors }: DbtConfigFormProps) {
  const selectError = errors.find((e) => e.field === "config.select");

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-orange-600 dark:text-orange-400">
        <Box className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">dbt Stage</span>
      </div>

      <div>
        <Label htmlFor="dbt-select">
          Select <span className="text-rose-500">*</span>
        </Label>
        <Input
          id="dbt-select"
          value={config.select ?? ""}
          onChange={(e) => onChange({ ...config, select: e.target.value })}
          placeholder="tag:daily, model_name, path:models/staging"
          invalid={!!selectError}
          className="mt-1.5 font-mono text-sm"
        />
        {selectError && (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{selectError.message}</p>
        )}
        <p className="mt-1 text-[10px] text-neutral-500">
          dbt node selection syntax. Supports tags, model names, paths, and graph operators.
        </p>
      </div>

      <div>
        <Label htmlFor="dbt-exclude">Exclude</Label>
        <Input
          id="dbt-exclude"
          value={config.exclude ?? ""}
          onChange={(e) => onChange({ ...config, exclude: e.target.value })}
          placeholder="tag:skip, +model_to_exclude"
          className="mt-1.5 font-mono text-sm"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Models to exclude from the selection.
        </p>
      </div>

      <div>
        <Label htmlFor="dbt-target">Target</Label>
        <Input
          id="dbt-target"
          value={config.target ?? ""}
          onChange={(e) => onChange({ ...config, target: e.target.value })}
          placeholder="dev, prod"
          className="mt-1.5"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          dbt target profile. Leave empty for default.
        </p>
      </div>

      <div className="flex items-center gap-2">
        <Checkbox
          id="dbt-full-refresh"
          checked={config.fullRefresh ?? false}
          onCheckedChange={(checked) =>
            onChange({ ...config, fullRefresh: checked === true })
          }
        />
        <Label
          htmlFor="dbt-full-refresh"
          className="cursor-pointer text-sm font-normal text-neutral-700 dark:text-neutral-300"
        >
          Full refresh (rebuild incremental models from scratch)
        </Label>
      </div>

      <div className="rounded-lg border border-neutral-200 bg-neutral-50 p-3 dark:border-neutral-800 dark:bg-neutral-900/50">
        <p className="text-xs font-medium text-neutral-700 dark:text-neutral-300">
          Selection examples:
        </p>
        <ul className="mt-2 space-y-1 text-[10px] text-neutral-600 dark:text-neutral-400">
          <li><code className="rounded bg-neutral-200/60 px-1 dark:bg-neutral-800">tag:daily</code> — all models with the daily tag</li>
          <li><code className="rounded bg-neutral-200/60 px-1 dark:bg-neutral-800">+orders</code> — orders model and its upstream dependencies</li>
          <li><code className="rounded bg-neutral-200/60 px-1 dark:bg-neutral-800">path:models/marts</code> — all models in the marts folder</li>
        </ul>
      </div>
    </div>
  );
}
