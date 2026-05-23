package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.infrastructure.artifact.ArtifactStorageProperties;
import io.pravah.execution.infrastructure.artifact.ArtifactStorageService;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.spring.multitenancy.SystemMaintenanceRlsHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that cleans up expired artifacts based on retention policy.
 *
 * <p>Runs daily to delete artifacts from completed executions older than the configured retention
 * period (default 30 days).
 *
 * <p>Cleanup strategy:
 *
 * <ul>
 *   <li>Find all executions completed before (now - retentionDays)
 *   <li>Delete all artifacts for those executions from MinIO
 *   <li>Log cleanup statistics
 * </ul>
 *
 * @see ArtifactStorageProperties#retentionDays()
 */
@Component
@ConditionalOnProperty(
    prefix = "pravah.artifact",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ArtifactCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ArtifactCleanupJob.class);

  private final ExecutionEntityRepository executionRepository;
  private final ArtifactStorageService artifactStorageService;
  private final ArtifactStorageProperties props;
  private final SystemMaintenanceRlsHelper maintenanceRlsHelper;

  public ArtifactCleanupJob(
      ExecutionEntityRepository executionRepository,
      ArtifactStorageService artifactStorageService,
      ArtifactStorageProperties props,
      SystemMaintenanceRlsHelper maintenanceRlsHelper) {
    this.executionRepository = executionRepository;
    this.artifactStorageService = artifactStorageService;
    this.props = props;
    this.maintenanceRlsHelper = maintenanceRlsHelper;
  }

  /** Run artifact cleanup daily at 3 AM. */
  @Scheduled(cron = "${pravah.artifact.cleanup-cron:0 0 3 * * ?}")
  public void cleanupExpiredArtifacts() {
    log.info("Starting artifact cleanup job", kv("retention_days", props.retentionDays()));

    Instant cutoff = Instant.now().minus(props.retentionDays(), ChronoUnit.DAYS);
    AtomicInteger cleanedCount = new AtomicInteger(0);
    AtomicInteger failedCount = new AtomicInteger(0);

    try {
      var expiredExecutions =
          maintenanceRlsHelper.runWithMaintenance(
              () -> executionRepository.findCompletedBefore(cutoff));

      log.info(
          "Found {} executions with artifacts to clean", kv("count", expiredExecutions.size()));

      for (var execution : expiredExecutions) {
        try {
          artifactStorageService.deleteExecutionArtifacts(
              execution.getTenantId().toString(), execution.getId());
          cleanedCount.incrementAndGet();
        } catch (Exception e) {
          failedCount.incrementAndGet();
          log.warn(
              "Failed to clean artifacts for execution",
              kv("execution_id", execution.getId()),
              kv("error", e.getMessage()));
        }
      }

      log.info(
          "Artifact cleanup completed",
          kv("cleaned_executions", cleanedCount.get()),
          kv("failed_executions", failedCount.get()),
          kv("cutoff", cutoff));

    } catch (Exception e) {
      log.error("Artifact cleanup job failed", e);
    }
  }

  /** Manual trigger for cleanup (for testing/admin). */
  public CleanupResult triggerCleanup() {
    log.info("Manual artifact cleanup triggered");
    Instant cutoff = Instant.now().minus(props.retentionDays(), ChronoUnit.DAYS);
    int cleaned = 0;
    int failed = 0;

    var expiredExecutions = executionRepository.findCompletedBefore(cutoff);

    for (var execution : expiredExecutions) {
      try {
        artifactStorageService.deleteExecutionArtifacts(
            execution.getTenantId().toString(), execution.getId());
        cleaned++;
      } catch (Exception e) {
        failed++;
      }
    }

    return new CleanupResult(cleaned, failed, cutoff);
  }

  public record CleanupResult(int cleanedExecutions, int failedExecutions, Instant cutoff) {}
}
