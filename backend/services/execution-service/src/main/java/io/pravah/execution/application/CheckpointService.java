package io.pravah.execution.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.execution.infrastructure.persistence.entity.CheckpointEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.CheckpointRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists and restores stage checkpoints (US-02.12). */
@Service
public class CheckpointService {

  private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

  private final CheckpointRepository checkpointRepository;

  public CheckpointService(CheckpointRepository checkpointRepository) {
    this.checkpointRepository = checkpointRepository;
  }

  @Transactional
  public void saveAfterJobSuccess(JobEntity job) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("jobId", job.getId().toString());
    state.put("status", job.getStatus().asDatabaseValue());
    state.put("exitCode", job.getExitCode());
    if (job.getOutput() != null) {
      state.put("output", job.getOutput());
    }
    if (job.getArtifacts() != null) {
      state.put("artifacts", job.getArtifacts());
    }
    if (job.getMetrics() != null) {
      state.put("metrics", job.getMetrics());
    }

    checkpointRepository
        .findByExecutionIdAndStageId(job.getExecutionId(), job.getStageId())
        .ifPresentOrElse(
            existing -> existing.updateState(state),
            () ->
                checkpointRepository.save(
                    new CheckpointEntity(job.getExecutionId(), job.getStageId(), state)));

    log.info(
        "Checkpoint saved",
        kv("execution_id", job.getExecutionId()),
        kv("stage_id", job.getStageId()));
  }

  @Transactional(readOnly = true)
  public Optional<Map<String, Object>> loadCheckpointState(UUID executionId, String stageId) {
    return checkpointRepository
        .findByExecutionIdAndStageId(executionId, stageId)
        .map(CheckpointEntity::getState);
  }

  @Transactional(readOnly = true)
  public List<CheckpointEntity> listForExecution(UUID executionId) {
    return checkpointRepository.findByExecutionId(executionId);
  }

  @Transactional
  public void clearForExecution(UUID executionId) {
    checkpointRepository.deleteByExecutionId(executionId);
    log.debug("Checkpoints cleared", kv("execution_id", executionId));
  }
}
