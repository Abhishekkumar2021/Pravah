import { Plus, Trash2, Container } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";

type EnvVar = { name: string; value: string };

type ContainerConfig = {
  image: string;
  command: string[];
  env: EnvVar[];
  resources?: {
    profile?: string;
    memory?: string;
    cpus?: number;
  };
};

type ContainerConfigFormProps = {
  config: ContainerConfig;
  onChange: (config: ContainerConfig) => void;
  errors: { field?: string; message: string }[];
};

const RESOURCE_PROFILES = [
  { value: "", label: "Default" },
  { value: "small", label: "Small (256MB, 0.25 CPU)" },
  { value: "medium", label: "Medium (512MB, 0.5 CPU)" },
  { value: "large", label: "Large (1GB, 1 CPU)" },
  { value: "xlarge", label: "XLarge (2GB, 2 CPU)" },
];

export function ContainerConfigForm({ config, onChange, errors }: ContainerConfigFormProps) {
  const imageError = errors.find((e) => e.field === "config.image");
  const command = config.command ?? [];
  const env = config.env ?? [];
  const resources = config.resources ?? {};

  const updateCommand = (index: number, value: string) => {
    const newCommand = [...command];
    newCommand[index] = value;
    onChange({ ...config, command: newCommand });
  };

  const addCommandArg = () => {
    onChange({ ...config, command: [...command, ""] });
  };

  const removeCommandArg = (index: number) => {
    onChange({ ...config, command: command.filter((_, i) => i !== index) });
  };

  const updateEnvVar = (index: number, field: "name" | "value", value: string) => {
    const newEnv = [...env];
    newEnv[index] = { ...newEnv[index], [field]: value };
    onChange({ ...config, env: newEnv });
  };

  const addEnvVar = () => {
    onChange({ ...config, env: [...env, { name: "", value: "" }] });
  };

  const removeEnvVar = (index: number) => {
    onChange({ ...config, env: env.filter((_, i) => i !== index) });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-violet-600 dark:text-violet-400">
        <Container className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">Container Stage</span>
      </div>

      <div>
        <Label htmlFor="container-image">
          Image <span className="text-rose-500">*</span>
        </Label>
        <Input
          id="container-image"
          value={config.image ?? ""}
          onChange={(e) => onChange({ ...config, image: e.target.value })}
          placeholder="alpine:3.19"
          invalid={!!imageError}
          className="mt-1.5 font-mono text-sm"
        />
        {imageError && (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{imageError.message}</p>
        )}
        <p className="mt-1 text-[10px] text-neutral-500">
          Docker image to run. Include registry and tag.
        </p>
      </div>

      <div>
        <Label>Command</Label>
        <div className="mt-1.5 space-y-2">
          {command.map((arg, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={arg}
                onChange={(e) => updateCommand(index, e.target.value)}
                placeholder={index === 0 ? "executable" : `arg ${index}`}
                className="flex-1 font-mono text-sm"
              />
              <Button
                type="button"
                variant="ghost"
                className="h-9 w-9 shrink-0 p-0 text-neutral-400 hover:text-rose-600"
                onClick={() => removeCommandArg(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            className="h-8 gap-1.5 text-xs"
            onClick={addCommandArg}
          >
            <Plus className="h-3.5 w-3.5" />
            Add argument
          </Button>
        </div>
        <p className="mt-1 text-[10px] text-neutral-500">
          Command and arguments to run inside the container.
        </p>
      </div>

      <div>
        <Label>Environment Variables</Label>
        <div className="mt-1.5 space-y-2">
          {env.map((envVar, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={envVar.name}
                onChange={(e) => updateEnvVar(index, "name", e.target.value)}
                placeholder="NAME"
                className="w-1/3 font-mono text-sm"
              />
              <span className="text-neutral-400">=</span>
              <Input
                value={envVar.value}
                onChange={(e) => updateEnvVar(index, "value", e.target.value)}
                placeholder="value"
                className="flex-1 font-mono text-sm"
              />
              <Button
                type="button"
                variant="ghost"
                className="h-9 w-9 shrink-0 p-0 text-neutral-400 hover:text-rose-600"
                onClick={() => removeEnvVar(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            className="h-8 gap-1.5 text-xs"
            onClick={addEnvVar}
          >
            <Plus className="h-3.5 w-3.5" />
            Add variable
          </Button>
        </div>
        <p className="mt-1 text-[10px] text-neutral-500">
          Use {"${secret.name}"} for sensitive values.
        </p>
      </div>

      <div>
        <Label htmlFor="container-resources">Resource Profile</Label>
        <Select
          id="container-resources"
          value={resources.profile ?? ""}
          onValueChange={(value) =>
            onChange({ ...config, resources: { ...resources, profile: value || undefined } })
          }
          options={RESOURCE_PROFILES}
          className="mt-1.5 w-full"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Pre-defined resource allocation for the container.
        </p>
      </div>
    </div>
  );
}
