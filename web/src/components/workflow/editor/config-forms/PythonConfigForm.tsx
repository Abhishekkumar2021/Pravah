import { Plus, Trash2, FileCode2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { ExpandableTextarea } from "@/components/ui/ExpandableTextarea";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";

type PythonConfig = {
  script: string;
  requirements: string[];
  pythonVersion?: string;
};

type PythonConfigFormProps = {
  config: PythonConfig;
  onChange: (config: PythonConfig) => void;
  errors: { field?: string; message: string }[];
};

const PYTHON_VERSIONS = [
  { value: "3.11", label: "Python 3.11 (recommended)" },
  { value: "3.10", label: "Python 3.10" },
  { value: "3.9", label: "Python 3.9" },
  { value: "3.12", label: "Python 3.12" },
];

export function PythonConfigForm({ config, onChange, errors }: PythonConfigFormProps) {
  const scriptError = errors.find((e) => e.field === "config.script");
  const requirements = config.requirements ?? [];

  const addRequirement = () => {
    onChange({ ...config, requirements: [...requirements, ""] });
  };

  const updateRequirement = (index: number, value: string) => {
    const newReqs = [...requirements];
    newReqs[index] = value;
    onChange({ ...config, requirements: newReqs });
  };

  const removeRequirement = (index: number) => {
    onChange({ ...config, requirements: requirements.filter((_, i) => i !== index) });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-blue-600 dark:text-blue-400">
        <FileCode2 className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">Python Stage</span>
      </div>

      <div>
        <Label htmlFor="python-version">Python Version</Label>
        <Select
          id="python-version"
          aria-label="Python version"
          value={config.pythonVersion ?? "3.11"}
          onValueChange={(value) => onChange({ ...config, pythonVersion: value })}
          options={PYTHON_VERSIONS}
          className="mt-1.5 w-full"
        />
      </div>

      <div>
        <Label htmlFor="python-script">
          Script <span className="text-rose-500">*</span>
        </Label>
        <ExpandableTextarea
          id="python-script"
          value={config.script ?? ""}
          onChange={(e) => onChange({ ...config, script: e.target.value })}
          placeholder="# Your Python code here&#10;import json&#10;&#10;def main():&#10;    result = {'status': 'success'}&#10;    print(json.dumps(result))&#10;&#10;if __name__ == '__main__':&#10;    main()"
          rows={10}
          mono
          invalid={!!scriptError}
          expandTitle="Python script"
          expandDescription="Print JSON to stdout for structured stage outputs."
          minHeightClass="min-h-[14rem]"
        />
        {scriptError && (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{scriptError.message}</p>
        )}
        <p className="mt-1 text-[10px] text-neutral-500">
          Python script to execute. Print JSON to stdout for structured outputs.
        </p>
      </div>

      <div>
        <Label>Requirements</Label>
        <div className="mt-1.5 space-y-2">
          {requirements.map((req, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={req}
                onChange={(e) => updateRequirement(index, e.target.value)}
                placeholder="package==1.0.0"
                className="flex-1 font-mono text-sm"
              />
              <Button
                type="button"
                variant="ghost"
                className="h-9 w-9 shrink-0 p-0 text-neutral-400 hover:text-rose-600"
                onClick={() => removeRequirement(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            className="h-8 gap-1.5 text-xs"
            onClick={addRequirement}
          >
            <Plus className="h-3.5 w-3.5" />
            Add requirement
          </Button>
        </div>
        <p className="mt-1 text-[10px] text-neutral-500">
          pip packages to install. Use version pinning for reproducibility.
        </p>
      </div>
    </div>
  );
}
