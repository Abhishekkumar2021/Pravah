import { Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Label } from "@/components/ui/Label";
import { Select } from "@/components/ui/Select";
import {
  useEditorStore,
  selectSelectedNode,
  STAGE_TYPES,
  STAGE_TYPE_META,
  type StageType,
} from "./editorStore";
import { ContainerConfigForm } from "./config-forms/ContainerConfigForm";
import { DbtConfigForm } from "./config-forms/DbtConfigForm";
import { EchoConfigForm } from "./config-forms/EchoConfigForm";
import { PythonConfigForm } from "./config-forms/PythonConfigForm";
import { SparkConfigForm } from "./config-forms/SparkConfigForm";
import { SqlConfigForm } from "./config-forms/SqlConfigForm";

const typeOptions = STAGE_TYPES.map((type) => ({
  value: type,
  label: STAGE_TYPE_META[type].label,
}));

function defaultConfigForType(type: StageType): Record<string, unknown> {
  switch (type) {
    case "sql":
      return { query: "SELECT 1", connectionId: "" };
    case "container":
      return { image: "alpine:3.19", command: ["echo", "hello"], env: [] };
    case "python":
      return { script: "print('hello')", requirements: [], pythonVersion: "3.11" };
    case "dbt":
      return { select: "tag:daily", exclude: "", fullRefresh: false };
    case "spark":
      return { mainClass: "com.example.App", jarPath: "", args: [], sparkConf: {} };
    default:
      return { message: "hello from echo" };
  }
}

export function StageConfigPanel() {
  const selectedNode = useEditorStore(selectSelectedNode);
  const updateNode = useEditorStore((s) => s.updateNode);
  const deleteNode = useEditorStore((s) => s.deleteNode);
  const validationErrors = useEditorStore((s) => s.validationErrors);

  if (!selectedNode) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
          <svg
            className="h-6 w-6 text-neutral-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M15 15l-2 5L9 9l11 4-5 2zm0 0l5 5M7.188 2.239l.777 2.897M5.136 7.965l-2.898-.777M13.95 4.05l-2.122 2.122m-5.657 5.656l-2.12 2.122"
            />
          </svg>
        </div>
        <p className="text-sm font-medium text-neutral-600 dark:text-neutral-400">
          Select a stage to configure
        </p>
        <p className="mt-1 text-xs text-neutral-500 dark:text-neutral-500">
          Click on a stage node or drag one from the palette
        </p>
      </div>
    );
  }

  const { stage } = selectedNode.data;
  const stageType = (stage.type as StageType) ?? "echo";
  const nodeErrors = validationErrors.filter((e) => e.nodeId === selectedNode.id);

  const handleIdChange = (newId: string) => {
    updateNode(selectedNode.id, { id: newId });
  };

  const handleNameChange = (newName: string) => {
    updateNode(selectedNode.id, { name: newName });
  };

  const handleTypeChange = (newType: StageType) => {
    updateNode(selectedNode.id, {
      type: newType,
      config: defaultConfigForType(newType),
    });
  };

  const handleConfigChange = (newConfig: Record<string, unknown>) => {
    updateNode(selectedNode.id, { config: newConfig });
  };

  const handleDelete = () => {
    deleteNode(selectedNode.id);
  };

  const idError = nodeErrors.find((e) => e.field === "id");

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500 dark:text-neutral-400">
          Stage Configuration
        </p>
      </div>

      {/* Basic properties */}
      <div className="space-y-4">
        <div>
          <Label htmlFor="stage-id">
            Stage ID <span className="text-rose-500">*</span>
          </Label>
          <Input
            id="stage-id"
            value={stage.id}
            onChange={(e) => handleIdChange(e.target.value.trim())}
            invalid={!!idError}
            className="mt-1.5 font-mono text-sm"
          />
          {idError && (
            <p className="mt-1 text-xs text-rose-600 dark:text-rose-400">{idError.message}</p>
          )}
          <p className="mt-1 text-[10px] text-neutral-500">
            Unique identifier used in YAML and dependencies.
          </p>
        </div>

        <div>
          <Label htmlFor="stage-name">Display Name</Label>
          <Input
            id="stage-name"
            value={stage.name ?? ""}
            onChange={(e) => handleNameChange(e.target.value)}
            placeholder={stage.id}
            className="mt-1.5"
          />
          <p className="mt-1 text-[10px] text-neutral-500">
            Human-readable name shown in the UI.
          </p>
        </div>

        <div>
          <Label htmlFor="stage-type">Stage Type</Label>
          <Select
            id="stage-type"
            aria-label="Stage type"
            value={stageType}
            onValueChange={(v) => handleTypeChange(v as StageType)}
            options={typeOptions}
            className="mt-1.5 w-full"
          />
        </div>
      </div>

      {/* Divider */}
      <div className="border-t border-neutral-200 dark:border-neutral-800" />

      {/* Type-specific config form */}
      {stageType === "sql" && (
        <SqlConfigForm
          config={stage.config as Parameters<typeof SqlConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}
      {stageType === "container" && (
        <ContainerConfigForm
          config={stage.config as Parameters<typeof ContainerConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}
      {stageType === "python" && (
        <PythonConfigForm
          config={stage.config as Parameters<typeof PythonConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}
      {stageType === "echo" && (
        <EchoConfigForm
          config={stage.config as Parameters<typeof EchoConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}
      {stageType === "dbt" && (
        <DbtConfigForm
          config={stage.config as Parameters<typeof DbtConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}
      {stageType === "spark" && (
        <SparkConfigForm
          config={stage.config as Parameters<typeof SparkConfigForm>[0]["config"]}
          onChange={handleConfigChange}
          errors={nodeErrors}
        />
      )}

      {/* Divider */}
      <div className="border-t border-neutral-200 dark:border-neutral-800" />

      {/* Delete button */}
      <Button
        type="button"
        variant="secondary"
        className="w-full gap-2 text-rose-600 hover:bg-rose-50 hover:text-rose-700 dark:text-rose-400 dark:hover:bg-rose-950/30 dark:hover:text-rose-300"
        onClick={handleDelete}
      >
        <Trash2 className="h-4 w-4" />
        Delete Stage
      </Button>
    </div>
  );
}
