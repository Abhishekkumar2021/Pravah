import { useEffect, useState } from "react";
import { Download, File, FileArchive, FileCode, FileSpreadsheet, FileText, Folder } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { listExecutionArtifacts, generateArtifactDownloadUrl, type ArtifactMetadata, type ArtifactType, type JobSummary } from "@/lib/api";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function getFileIcon(filename: string) {
  const lower = filename.toLowerCase();
  if (lower.endsWith(".json") || lower.endsWith(".json.gz")) return FileCode;
  if (lower.endsWith(".csv")) return FileSpreadsheet;
  if (lower.endsWith(".log") || lower.endsWith(".log.gz") || lower.endsWith(".txt")) return FileText;
  if (lower.endsWith(".gz") || lower.endsWith(".zip") || lower.endsWith(".tar")) return FileArchive;
  if (lower.endsWith(".parquet") || lower.endsWith(".avro")) return FileSpreadsheet;
  return File;
}

function ArtifactTypeIcon({ type }: { type: ArtifactType }) {
  switch (type) {
    case "OUTPUT":
      return <Folder className="h-4 w-4 text-blue-500" />;
    case "LOG":
      return <FileText className="h-4 w-4 text-amber-500" />;
    case "PROFILE":
      return <FileCode className="h-4 w-4 text-purple-500" />;
    case "CHECKPOINT":
      return <FileArchive className="h-4 w-4 text-emerald-500" />;
    default:
      return <File className="h-4 w-4 text-neutral-500" />;
  }
}

type Props = {
  executionId: string;
  jobs: JobSummary[];
};

export function RunArtifacts({ executionId, jobs }: Props) {
  const [artifacts, setArtifacts] = useState<ArtifactMetadata[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    listExecutionArtifacts(executionId)
      .then((data) => {
        if (!cancelled) {
          setArtifacts(data);
        }
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [executionId]);

  async function handleDownload(artifact: ArtifactMetadata) {
    const keyParts = artifact.key.split("/");
    let jobId: string | null = null;
    for (let i = 0; i < keyParts.length; i++) {
      if (keyParts[i] === "jobs" && i + 1 < keyParts.length) {
        jobId = keyParts[i + 1];
        break;
      }
    }

    if (!jobId) {
      console.error("Could not extract job ID from artifact key:", artifact.key);
      return;
    }

    setDownloading(artifact.key);
    try {
      const presigned = await generateArtifactDownloadUrl(
        executionId,
        jobId,
        artifact.type,
        artifact.filename
      );
      window.open(presigned.url, "_blank");
    } catch (e) {
      console.error("Failed to generate download URL:", e);
    } finally {
      setDownloading(null);
    }
  }

  const groupedByJob = artifacts.reduce(
    (acc, artifact) => {
      const keyParts = artifact.key.split("/");
      let jobId = "unknown";
      for (let i = 0; i < keyParts.length; i++) {
        if (keyParts[i] === "jobs" && i + 1 < keyParts.length) {
          jobId = keyParts[i + 1];
          break;
        }
      }
      if (!acc[jobId]) {
        acc[jobId] = [];
      }
      acc[jobId].push(artifact);
      return acc;
    },
    {} as Record<string, ArtifactMetadata[]>
  );

  const jobIdToName = jobs.reduce(
    (acc, job) => {
      acc[job.id] = job.stageName || job.stageId;
      return acc;
    },
    {} as Record<string, string>
  );

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="text-sm text-neutral-500">Loading artifacts…</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 dark:border-rose-900/50 dark:bg-rose-950/30">
        <p className="text-sm text-rose-700 dark:text-rose-300">
          Failed to load artifacts: {error}
        </p>
      </div>
    );
  }

  if (artifacts.length === 0) {
    return (
      <div className="flex flex-col items-center py-12 text-center">
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 dark:bg-neutral-800">
          <Folder className="h-6 w-6 text-neutral-400" />
        </div>
        <h3 className="text-sm font-medium text-neutral-900 dark:text-neutral-100">
          No artifacts
        </h3>
        <p className="mt-1 text-sm text-neutral-500">
          No artifacts were produced by this execution. Stages that write large outputs
          will have their artifacts listed here.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {Object.entries(groupedByJob).map(([jobId, jobArtifacts]) => (
        <div key={jobId} className="space-y-3">
          <h4 className="flex items-center gap-2 text-sm font-medium text-neutral-700 dark:text-neutral-300">
            <Folder className="h-4 w-4" />
            {jobIdToName[jobId] || `Job ${jobId.slice(0, 8)}…`}
            <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500 dark:bg-neutral-800">
              {jobArtifacts.length} file{jobArtifacts.length !== 1 ? "s" : ""}
            </span>
          </h4>

          <div className="rounded-lg border border-neutral-200 bg-white dark:border-neutral-700 dark:bg-neutral-900">
            <div className="divide-y divide-neutral-100 dark:divide-neutral-800">
              {jobArtifacts.map((artifact) => {
                const Icon = getFileIcon(artifact.filename);
                return (
                  <div
                    key={artifact.key}
                    className="flex items-center justify-between px-4 py-3 transition-colors hover:bg-neutral-50 dark:hover:bg-neutral-800/50"
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-100 dark:bg-neutral-800">
                        <Icon className="h-4 w-4 text-neutral-500" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm text-neutral-900 dark:text-neutral-100">
                            {artifact.filename}
                          </span>
                          <ArtifactTypeIcon type={artifact.type} />
                        </div>
                        <div className="flex items-center gap-2 text-xs text-neutral-500">
                          <span>{formatFileSize(artifact.sizeBytes)}</span>
                          {artifact.contentType && (
                            <>
                              <span>·</span>
                              <span>{artifact.contentType}</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    <Button
                      type="button"
                      variant="secondary"
                      size="sm"
                      disabled={downloading === artifact.key}
                      onClick={() => void handleDownload(artifact)}
                    >
                      <Download className="h-4 w-4" />
                      {downloading === artifact.key ? "Loading…" : "Download"}
                    </Button>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ))}

      <div className="rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 dark:border-neutral-700 dark:bg-neutral-800/50">
        <p className="text-xs text-neutral-500">
          Total: {artifacts.length} artifact{artifacts.length !== 1 ? "s" : ""},{" "}
          {formatFileSize(artifacts.reduce((sum, a) => sum + a.sizeBytes, 0))}
        </p>
      </div>
    </div>
  );
}
