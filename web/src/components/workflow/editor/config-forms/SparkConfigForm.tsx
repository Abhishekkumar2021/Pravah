import { Plus, Trash2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";

type SparkConfig = {
  mainClass: string;
  jarPath?: string;
  args?: string[];
  sparkConf?: Record<string, string>;
};

type SparkConfigFormProps = {
  config: SparkConfig;
  onChange: (config: SparkConfig) => void;
  errors: { field?: string; message: string }[];
};

export function SparkConfigForm({ config, onChange, errors }: SparkConfigFormProps) {
  const mainClassError = errors.find((e) => e.field === "config.mainClass");
  const args = config.args ?? [];
  const sparkConf = config.sparkConf ?? {};
  const confEntries = Object.entries(sparkConf);

  const addArg = () => {
    onChange({ ...config, args: [...args, ""] });
  };

  const updateArg = (index: number, value: string) => {
    const newArgs = [...args];
    newArgs[index] = value;
    onChange({ ...config, args: newArgs });
  };

  const removeArg = (index: number) => {
    onChange({ ...config, args: args.filter((_, i) => i !== index) });
  };

  const addConfEntry = () => {
    onChange({
      ...config,
      sparkConf: { ...sparkConf, "spark.key": "value" },
    });
  };

  const updateConfEntry = (oldKey: string, newKey: string, value: string) => {
    const newConf = { ...sparkConf };
    if (oldKey !== newKey) {
      delete newConf[oldKey];
    }
    newConf[newKey] = value;
    onChange({ ...config, sparkConf: newConf });
  };

  const removeConfEntry = (key: string) => {
    const newConf = { ...sparkConf };
    delete newConf[key];
    onChange({ ...config, sparkConf: newConf });
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-rose-600 dark:text-rose-400">
        <Sparkles className="h-4 w-4" />
        <span className="text-xs font-medium uppercase tracking-wide">Spark Stage</span>
      </div>

      <div>
        <Label htmlFor="spark-main-class">
          Main Class <span className="text-rose-500">*</span>
        </Label>
        <Input
          id="spark-main-class"
          value={config.mainClass ?? ""}
          onChange={(e) => onChange({ ...config, mainClass: e.target.value })}
          placeholder="com.example.SparkApp"
          invalid={!!mainClassError}
          className="mt-1.5 font-mono text-sm"
        />
        {mainClassError && (
          <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{mainClassError.message}</p>
        )}
        <p className="mt-1 text-[10px] text-neutral-500">
          Fully qualified class name of the Spark application entry point.
        </p>
      </div>

      <div>
        <Label htmlFor="spark-jar">JAR Path</Label>
        <Input
          id="spark-jar"
          value={config.jarPath ?? ""}
          onChange={(e) => onChange({ ...config, jarPath: e.target.value })}
          placeholder="s3://bucket/path/to/app.jar"
          className="mt-1.5 font-mono text-sm"
        />
        <p className="mt-1 text-[10px] text-neutral-500">
          Path to the application JAR. Supports S3, GCS, HDFS, or local paths.
        </p>
      </div>

      <div>
        <Label>Application Arguments</Label>
        <div className="mt-1.5 space-y-2">
          {args.map((arg, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={arg}
                onChange={(e) => updateArg(index, e.target.value)}
                placeholder={`--arg${index + 1}=value`}
                className="flex-1 font-mono text-sm"
              />
              <Button
                type="button"
                variant="ghost"
                className="h-9 w-9 shrink-0 p-0 text-neutral-400 hover:text-rose-600"
                onClick={() => removeArg(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            className="h-8 gap-1.5 text-xs"
            onClick={addArg}
          >
            <Plus className="h-3.5 w-3.5" />
            Add argument
          </Button>
        </div>
      </div>

      <div>
        <Label>Spark Configuration</Label>
        <div className="mt-1.5 space-y-2">
          {confEntries.map(([key, value], index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={key}
                onChange={(e) => updateConfEntry(key, e.target.value, value)}
                placeholder="spark.executor.memory"
                className="w-1/2 font-mono text-sm"
              />
              <span className="text-neutral-400">=</span>
              <Input
                value={value}
                onChange={(e) => updateConfEntry(key, key, e.target.value)}
                placeholder="4g"
                className="flex-1 font-mono text-sm"
              />
              <Button
                type="button"
                variant="ghost"
                className="h-9 w-9 shrink-0 p-0 text-neutral-400 hover:text-rose-600"
                onClick={() => removeConfEntry(key)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            className="h-8 gap-1.5 text-xs"
            onClick={addConfEntry}
          >
            <Plus className="h-3.5 w-3.5" />
            Add configuration
          </Button>
        </div>
        <p className="mt-1 text-[10px] text-neutral-500">
          Additional Spark configuration properties.
        </p>
      </div>
    </div>
  );
}
