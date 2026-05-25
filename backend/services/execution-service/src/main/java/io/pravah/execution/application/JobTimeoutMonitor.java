package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.spring.multitenancy.SystemMaintenanceRlsHelper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scans RUNNING jobs and delegates timeout handling per job (US-02.07). */
@Component
public class JobTimeoutMonitor {

  private static final Logger log = LoggerFactory.getLogger(JobTimeoutMonitor.class);

  private final JobEntityRepository jobEntityRepository;
  private final JobTimeoutProcessor jobTimeoutProcessor;
  private final SystemMaintenanceRlsHelper maintenanceRlsHelper;

  public JobTimeoutMonitor(
      JobEntityRepository jobEntityRepository,
      JobTimeoutProcessor jobTimeoutProcessor,
      SystemMaintenanceRlsHelper maintenanceRlsHelper) {
    this.jobEntityRepository = jobEntityRepository;
    this.jobTimeoutProcessor = jobTimeoutProcessor;
    this.maintenanceRlsHelper = maintenanceRlsHelper;
  }

  @Scheduled(fixedDelayString = "${pravah.job.timeout.check-interval-ms:5000}")
  public void checkRunningJobTimeouts() {
    List<JobEntity> running =
        maintenanceRlsHelper.runWithMaintenance(
            () -> jobEntityRepository.findByStatus(JobState.RUNNING));
    if (running.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (JobEntity snapshot : running) {
      UUID jobId = snapshot.getId();
      try {
        jobTimeoutProcessor.processTimedOutJobSafely(jobId, now);
      } catch (Exception e) {
        log.warn("Timeout check failed for job", kv("job_id", jobId), kv("error", e.getMessage()));
      }
    }
  }
}
