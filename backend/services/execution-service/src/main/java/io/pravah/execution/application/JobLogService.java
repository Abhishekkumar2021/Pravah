package io.pravah.execution.application;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.execution.api.dto.JobLogLineResponse;
import io.pravah.execution.api.dto.JobLogsResponse;
import io.pravah.execution.domain.JobLogLevel;
import io.pravah.execution.infrastructure.persistence.entity.JobLogEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobLogEntityRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists and queries per-job execution logs (US-02.03). */
@Service
public class JobLogService {

  private static final int MAX_LINES_PER_REQUEST = 2000;

  private final JobLogEntityRepository jobLogEntityRepository;
  private final JobEntityRepository jobEntityRepository;

  public JobLogService(
      JobLogEntityRepository jobLogEntityRepository, JobEntityRepository jobEntityRepository) {
    this.jobLogEntityRepository = jobLogEntityRepository;
    this.jobEntityRepository = jobEntityRepository;
  }

  @Transactional
  public void append(UUID jobId, JobLogLevel level, String message) {
    append(jobId, level, message, null);
  }

  @Transactional
  public void append(
      UUID jobId, JobLogLevel level, String message, Map<String, Object> attributes) {
    if (message == null || message.isBlank()) {
      throw ValidationException.of("message", "Log message must not be blank");
    }
    jobLogEntityRepository.save(new JobLogEntity(jobId, Instant.now(), level, message, attributes));
  }

  @Transactional(readOnly = true)
  public JobLogsResponse listLogs(UUID executionId, UUID jobId, String levelFilter) {
    jobEntityRepository
        .findById(jobId)
        .filter(job -> job.getExecutionId().equals(executionId))
        .orElseThrow(() -> new EntityNotFoundException("Job", jobId));

    JobLogLevel level = null;
    if (levelFilter != null && !levelFilter.isBlank()) {
      level = JobLogLevel.fromDatabaseValue(levelFilter);
    }

    var page = PageRequest.of(0, MAX_LINES_PER_REQUEST, Sort.by(Sort.Direction.ASC, "logTime"));
    List<JobLogLineResponse> lines =
        jobLogEntityRepository.findByJobIdAndOptionalLevel(jobId, level, page).stream()
            .map(
                row ->
                    new JobLogLineResponse(
                        row.getId(),
                        row.getLogTime(),
                        row.getLevel().asDatabaseValue(),
                        row.getMessage()))
            .toList();

    return new JobLogsResponse(jobId, executionId, lines);
  }
}
