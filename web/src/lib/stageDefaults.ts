import type { StageDefinition } from "@/lib/api";

export const STAGE_TYPE_OPTIONS = [
  { type: "sql", label: "SQL", description: "Query a database connection" },
  { type: "container", label: "Container", description: "Run a Docker image" },
  { type: "echo", label: "Echo", description: "Debug / placeholder stage" },
  { type: "python", label: "Python", description: "Execute a Python script" },
  { type: "dbt", label: "dbt", description: "Run dbt models" },
  { type: "spark", label: "Spark", description: "Submit a Spark job" },
] as const;

const STAGE_NAME_PREFIX: Record<string, string> = {
  sql: "Query",
  container: "Run",
  echo: "Echo",
  python: "Script",
  dbt: "Transform",
  spark: "Job",
};

export function defaultConfigForType(type: string): Record<string, unknown> {
  switch (type) {
    case "sql":
      return { query: "SELECT 1" };
    case "container":
      return { image: "alpine:3.19", command: ["echo", "hello"] };
    case "python":
      return { script: "print('hello')" };
    case "dbt":
      return { select: "tag:daily" };
    case "spark":
      return { main_class: "com.example.App" };
    default:
      return { message: "hello from echo" };
  }
}

export function createStage(type: string, index: number): StageDefinition {
  const prefix = STAGE_NAME_PREFIX[type] ?? type;
  const id = `${type}-${index}`;
  return {
    id,
    name: `${prefix} ${index}`,
    type,
    dependsOn: [],
    config: defaultConfigForType(type),
  };
}
