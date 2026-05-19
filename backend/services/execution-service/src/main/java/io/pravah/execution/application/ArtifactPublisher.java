package io.pravah.execution.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.artifact.ArtifactMetadata;
import io.pravah.execution.infrastructure.artifact.ArtifactStorageService;
import io.pravah.execution.infrastructure.artifact.ArtifactType;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Publishes artifacts from stage executions to object storage.
 *
 * Executors use this to:
 * - Upload large result sets (SQL query results as JSON/CSV)
 * - Upload Python script output files
 * - Upload container working directory contents
 * - Store execution logs
 *
 * @see io.pravah.execution.infrastructure.artifact.ArtifactStorageService
 */
@Component
public class ArtifactPublisher {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPublisher.class);

    private static final long JSON_OUTPUT_THRESHOLD_BYTES = 100 * 1024; // 100KB
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final String GZIP_CONTENT_TYPE = "application/gzip";

    private final ArtifactStorageService artifactStorageService;
    private final JobLogService jobLogService;
    private final ObjectMapper objectMapper;

    public ArtifactPublisher(
            ArtifactStorageService artifactStorageService,
            JobLogService jobLogService,
            ObjectMapper objectMapper) {
        this.artifactStorageService = artifactStorageService;
        this.jobLogService = jobLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a list of rows as a JSON artifact if the data exceeds the threshold.
     *
     * @return artifact metadata if published, null if data was small enough for inline output
     */
    public ArtifactMetadata publishJsonIfLarge(
            JobEntity job,
            ExecutionEntity execution,
            String filename,
            List<Map<String, Object>> rows) {

        if (rows == null || rows.isEmpty()) {
            return null;
        }

        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(rows);

            if (jsonBytes.length < JSON_OUTPUT_THRESHOLD_BYTES) {
                return null;
            }

            byte[] compressed = gzipCompress(jsonBytes);
            String gzippedFilename = filename.endsWith(".json") ? filename + ".gz" : filename + ".json.gz";

            ArtifactMetadata metadata = artifactStorageService.upload(
                    execution.getTenantId().toString(),
                    execution.getId(),
                    job.getId(),
                    ArtifactType.OUTPUT,
                    gzippedFilename,
                    compressed,
                    GZIP_CONTENT_TYPE
            );

            jobLogService.append(job.getId(), JobLogLevel.INFO,
                    "[artifact] Uploaded %d rows (%s compressed) → %s"
                            .formatted(rows.size(), metadata.humanReadableSize(), gzippedFilename));

            log.info("Published JSON artifact",
                    kv("job_id", job.getId()),
                    kv("execution_id", execution.getId()),
                    kv("filename", gzippedFilename),
                    kv("original_bytes", jsonBytes.length),
                    kv("compressed_bytes", compressed.length),
                    kv("row_count", rows.size()));

            return metadata;

        } catch (Exception e) {
            log.error("Failed to publish JSON artifact",
                    kv("job_id", job.getId()),
                    kv("filename", filename),
                    e);
            jobLogService.append(job.getId(), JobLogLevel.WARN,
                    "[artifact] Failed to upload artifact: " + e.getMessage());
            return null;
        }
    }

    /**
     * Publish raw bytes as an artifact.
     */
    public ArtifactMetadata publishBytes(
            JobEntity job,
            ExecutionEntity execution,
            ArtifactType type,
            String filename,
            byte[] content,
            String contentType) {

        try {
            ArtifactMetadata metadata = artifactStorageService.upload(
                    execution.getTenantId().toString(),
                    execution.getId(),
                    job.getId(),
                    type,
                    filename,
                    content,
                    contentType
            );

            jobLogService.append(job.getId(), JobLogLevel.INFO,
                    "[artifact] Uploaded %s (%s) → %s"
                            .formatted(type.name().toLowerCase(), metadata.humanReadableSize(), filename));

            return metadata;

        } catch (Exception e) {
            log.error("Failed to publish artifact",
                    kv("job_id", job.getId()),
                    kv("filename", filename),
                    kv("type", type),
                    e);
            jobLogService.append(job.getId(), JobLogLevel.WARN,
                    "[artifact] Failed to upload: " + e.getMessage());
            return null;
        }
    }

    /**
     * Publish a file from the filesystem.
     */
    public ArtifactMetadata publishFile(
            JobEntity job,
            ExecutionEntity execution,
            ArtifactType type,
            Path file) {

        try {
            String filename = file.getFileName().toString();
            byte[] content = Files.readAllBytes(file);
            String contentType = determineContentType(filename);

            return publishBytes(job, execution, type, filename, content, contentType);

        } catch (IOException e) {
            log.error("Failed to read file for artifact upload",
                    kv("job_id", job.getId()),
                    kv("file", file),
                    e);
            jobLogService.append(job.getId(), JobLogLevel.WARN,
                    "[artifact] Failed to read file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Publish all files in a directory as artifacts.
     */
    public List<ArtifactMetadata> publishDirectory(
            JobEntity job,
            ExecutionEntity execution,
            ArtifactType type,
            Path directory) {

        List<ArtifactMetadata> published = new ArrayList<>();

        try (var stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        ArtifactMetadata metadata = publishFile(job, execution, type, file);
                        if (metadata != null) {
                            published.add(metadata);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk directory for artifact upload",
                    kv("job_id", job.getId()),
                    kv("directory", directory),
                    e);
            jobLogService.append(job.getId(), JobLogLevel.WARN,
                    "[artifact] Failed to publish directory: " + e.getMessage());
        }

        return published;
    }

    /**
     * Publish execution logs (compressed).
     */
    public ArtifactMetadata publishLogs(
            JobEntity job,
            ExecutionEntity execution,
            String logs) {

        if (logs == null || logs.isBlank()) {
            return null;
        }

        try {
            byte[] compressed = gzipCompress(logs.getBytes(StandardCharsets.UTF_8));
            return publishBytes(job, execution, ArtifactType.LOG, "execution.log.gz", compressed, GZIP_CONTENT_TYPE);
        } catch (IOException e) {
            log.error("Failed to compress logs",
                    kv("job_id", job.getId()),
                    e);
            return null;
        }
    }

    /**
     * Generate artifact reference map for inclusion in stage output.
     * This allows downstream stages to reference artifacts via ${stages.X.artifact.filename}
     */
    public Map<String, Object> toOutputReference(List<ArtifactMetadata> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> refs = new LinkedHashMap<>();
        for (ArtifactMetadata artifact : artifacts) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("key", artifact.key());
            meta.put("size_bytes", artifact.sizeBytes());
            meta.put("content_type", artifact.contentType());
            refs.put(artifact.filename(), meta);
        }
        return refs;
    }

    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".json")) return JSON_CONTENT_TYPE;
        if (lower.endsWith(".json.gz") || lower.endsWith(".gz")) return GZIP_CONTENT_TYPE;
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".parquet")) return "application/vnd.apache.parquet";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return TEXT_CONTENT_TYPE;
        return "application/octet-stream";
    }
}
