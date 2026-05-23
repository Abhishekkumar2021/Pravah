import type { StageDefinition } from "@/lib/api";

function yamlQuote(value: string): string {
  if (/^[a-zA-Z0-9_-]+$/.test(value)) {
    return value;
  }
  return JSON.stringify(value);
}

function indentBlock(text: string, spaces: number): string {
  const pad = " ".repeat(spaces);
  return text
    .split("\n")
    .map((line) => (line.length > 0 ? pad + line : line))
    .join("\n");
}

function stageToYaml(stage: StageDefinition): string {
  const lines: string[] = [];
  lines.push(`  - id: ${yamlQuote(stage.id)}`);
  if (stage.name && stage.name !== stage.id) {
    lines.push(`    name: ${yamlQuote(stage.name)}`);
  }
  if (stage.type) {
    lines.push(`    type: ${stage.type}`);
  }
  const deps = stage.dependsOn ?? stage.depends_on ?? [];
  if (deps.length > 0) {
    lines.push("    dependsOn:");
    for (const d of deps) {
      lines.push(`      - ${yamlQuote(d)}`);
    }
  }
  if (stage.config && Object.keys(stage.config).length > 0) {
    lines.push("    config:");
    lines.push(indentBlock(configToYaml(stage.config, 6), 0));
  }
  return lines.join("\n");
}

function configToYaml(config: Record<string, unknown>, indent: number): string {
  const pad = " ".repeat(indent);
  const lines: string[] = [];
  for (const [key, value] of Object.entries(config)) {
    if (value === null || value === undefined) continue;
    if (typeof value === "string") {
      lines.push(`${pad}${key}: ${yamlQuote(value)}`);
    } else if (typeof value === "number" || typeof value === "boolean") {
      lines.push(`${pad}${key}: ${String(value)}`);
    } else if (Array.isArray(value)) {
      lines.push(`${pad}${key}:`);
      for (const item of value) {
        if (typeof item === "string") {
          lines.push(`${pad}  - ${yamlQuote(item)}`);
        } else {
          lines.push(`${pad}  - ${JSON.stringify(item)}`);
        }
      }
    } else if (typeof value === "object") {
      lines.push(`${pad}${key}:`);
      lines.push(configToYaml(value as Record<string, unknown>, indent + 2));
    }
  }
  return lines.join("\n");
}

export function stagesToYaml(
  pipelineName: string,
  stages: StageDefinition[],
  description?: string | null,
  retry?: { maxAttempts: number },
): string {
  const header: string[] = [`name: ${yamlQuote(pipelineName)}`];
  if (description?.trim()) {
    header.push(`description: ${yamlQuote(description.trim())}`);
  }
  if (retry) {
    header.push("retry:");
    header.push(`  max_attempts: ${retry.maxAttempts}`);
  }
  header.push("stages:");
  if (stages.length === 0) {
    header.push("  []");
  } else {
    for (const stage of stages) {
      header.push(stageToYaml(stage));
    }
  }
  return `${header.join("\n")}\n`;
}

const RETRY_BLOCK_RE = /^retry:\s*\n(?:  .*\n)*/m;

export type PipelineRetrySettings = {
  autoRetry: boolean;
  maxAttempts: number;
};

/** Reads pipeline-level retry from draft/published YAML (defaults match backend RetryPolicy.DEFAULT). */
export function parseRetryFromYaml(yaml: string): PipelineRetrySettings {
  const match = yaml.match(/(?:^|\n)retry:\s*\n(?:  .*\n)*?  max_attempts:\s*(\d+)/);
  if (!match) {
    return { autoRetry: true, maxAttempts: 3 };
  }
  const maxAttempts = Math.max(1, parseInt(match[1], 10) || 1);
  return { autoRetry: maxAttempts > 1, maxAttempts };
}

/** Updates or removes the top-level retry block without touching stages. */
export function applyRetryToYaml(yaml: string, settings: PipelineRetrySettings): string {
  const withoutRetry = yaml.replace(RETRY_BLOCK_RE, "").trimEnd();
  if (!settings.autoRetry) {
    const lines = withoutRetry.split("\n");
    const insertAt = lines.findIndex((line) => line.startsWith("stages:"));
    if (insertAt <= 0) {
      return `${withoutRetry}\nretry:\n  max_attempts: 1\n`;
    }
    const before = lines.slice(0, insertAt).join("\n");
    const after = lines.slice(insertAt).join("\n");
    return `${before}\nretry:\n  max_attempts: 1\n${after}\n`;
  }
  const maxAttempts = Math.max(2, settings.maxAttempts);
  const lines = withoutRetry.split("\n");
  const insertAt = lines.findIndex((line) => line.startsWith("stages:"));
  if (insertAt <= 0) {
    return `${withoutRetry}\nretry:\n  max_attempts: ${maxAttempts}\n`;
  }
  const before = lines.slice(0, insertAt).join("\n");
  const after = lines.slice(insertAt).join("\n");
  return `${before}\nretry:\n  max_attempts: ${maxAttempts}\n${after}\n`;
}
