package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.scheduler.domain.model.Schedule;
import io.pravah.scheduler.domain.model.ScheduleHistory;
import io.pravah.scheduler.domain.repository.ScheduleHistoryRepository;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.scheduler.infrastructure.client.ExecutionTriggerClient;
import io.pravah.scheduler.infrastructure.leader.LeaderElectionService;
import io.pravah.scheduler.infrastructure.persistence.SchedulerRlsHelper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Evaluates due cron schedules and triggers executions (US-03.01). */
@Component
public class ScheduleEvaluationJob {

  private static final Logger log = LoggerFactory.getLogger(ScheduleEvaluationJob.class);

  private final LeaderElectionService leaderElectionService;
  private final ScheduleRepository scheduleRepository;
  private final ScheduleHistoryRepository scheduleHistoryRepository;
  private final ExecutionTriggerClient executionTriggerClient;
  private final SchedulerRlsHelper schedulerRlsHelper;

  public ScheduleEvaluationJob(
      LeaderElectionService leaderElectionService,
      ScheduleRepository scheduleRepository,
      ScheduleHistoryRepository scheduleHistoryRepository,
      ExecutionTriggerClient executionTriggerClient,
      SchedulerRlsHelper schedulerRlsHelper) {
    this.leaderElectionService = leaderElectionService;
    this.scheduleRepository = scheduleRepository;
    this.scheduleHistoryRepository = scheduleHistoryRepository;
    this.executionTriggerClient = executionTriggerClient;
    this.schedulerRlsHelper = schedulerRlsHelper;
  }

  @Scheduled(fixedDelayString = "${pravah.scheduler.evaluation-interval-ms:60000}")
  @Transactional
  public void evaluateDueSchedules() {
    if (!leaderElectionService.isLeader()) {
      log.debug("Not scheduler leader; skipping evaluation");
      return;
    }

    Instant now = Instant.now();
    List<Schedule> due;
    try {
      schedulerRlsHelper.enableEvaluation();
      due = scheduleRepository.findDueSchedulesForUpdate(now);
    } finally {
      schedulerRlsHelper.disableEvaluation();
    }
    if (due.isEmpty()) {
      return;
    }

    log.info("Processing due schedules", kv("count", due.size()));

    for (Schedule schedule : due) {
      try {
        schedulerRlsHelper.enableEvaluation();
        processSchedule(schedule, now);
      } finally {
        schedulerRlsHelper.disableEvaluation();
      }
    }
  }

  private void processSchedule(Schedule schedule, Instant now) {
    Instant scheduledTime = schedule.getNextRunAt();
    try {
      UUID executionId =
          executionTriggerClient.triggerScheduledExecution(
              schedule.getTenantId(), schedule.getPipelineId(), schedule.getId());
      Instant nextRun =
          CronScheduleCalculator.nextRunAfter(
              schedule.getCronExpression(), schedule.getTimezone(), now);
      schedule.recordTriggeredRun(now, nextRun);
      scheduleRepository.save(schedule);
      scheduleHistoryRepository.save(
          ScheduleHistory.triggered(
              schedule.getTenantId(), schedule.getId(), scheduledTime, executionId));
      log.info(
          "Triggered scheduled execution",
          kv("schedule_id", schedule.getId()),
          kv("execution_id", executionId),
          kv("next_run_at", nextRun));
    } catch (Exception e) {
      log.warn(
          "Failed to trigger scheduled execution",
          kv("schedule_id", schedule.getId()),
          kv("pipeline_id", schedule.getPipelineId()),
          e);
      scheduleHistoryRepository.save(
          ScheduleHistory.failed(schedule.getTenantId(), schedule.getId(), scheduledTime));
      Instant retryAt =
          CronScheduleCalculator.nextRunAfter(
              schedule.getCronExpression(), schedule.getTimezone(), now);
      schedule.setNextRunAt(retryAt);
      scheduleRepository.save(schedule);
    }
  }
}
