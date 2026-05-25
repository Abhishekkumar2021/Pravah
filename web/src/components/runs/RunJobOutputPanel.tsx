type RunJobOutputPanelProps = {
  output: Record<string, unknown> | null | undefined;
};

export function RunJobOutputPanel({ output }: RunJobOutputPanelProps) {
  if (!output || Object.keys(output).length === 0) {
    return null;
  }

  return (
    <div className="mb-3">
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-neutral-500">Stage output</p>
      <pre className="max-h-48 overflow-auto rounded-lg border border-neutral-200 bg-neutral-50 p-3 font-mono text-xs text-neutral-800 dark:border-neutral-800 dark:bg-neutral-900 dark:text-neutral-100">
        {JSON.stringify(output, null, 2)}
      </pre>
    </div>
  );
}
