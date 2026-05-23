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
import org.springframework.beans.factory.annotation.Value;
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
  private final int maxCatchupFires;

  public ScheduleEvaluationJob(
      LeaderElectionService leaderElectionService,
      ScheduleRepository scheduleRepository,
      ScheduleHistoryRepository scheduleHistoryRepository,
      ExecutionTriggerClient executionTriggerClient,
      SchedulerRlsHelper schedulerRlsHelper,
      @Value("${pravah.scheduler.max-catchup-fires:32}") int maxCatchupFires) {
    this.leaderElectionService = leaderElectionService;
    this.scheduleRepository = scheduleRepository;
    this.scheduleHistoryRepository = scheduleHistoryRepository;
    this.executionTriggerClient = executionTriggerClient;
    this.schedulerRlsHelper = schedulerRlsHelper;
    this.maxCatchupFires = maxCatchupFires;
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
        if ("run_all".equals(schedule.getCatchupPolicy())) {
          processScheduleRunAll(schedule, now);
        } else {
          processScheduleOnce(schedule, now);
        }
      } finally {
        schedulerRlsHelper.disableEvaluation();
      }
    }
  }

  private void processScheduleOnce(Schedule schedule, Instant now) {
    Instant scheduledTime = schedule.getNextRunAt();
    if (scheduledTime == null || scheduledTime.isAfter(now)) {
      return;
    }
    triggerSlot(schedule, scheduledTime, now);
  }

  /**
   * Fires every missed cron slot up to {@code pravah.scheduler.max-catchup-fires} (US-03.13).
   *
   * <p>Advances {@code next_run_at} from each fired slot so missed intervals are not skipped.
   */
  private void processScheduleRunAll(Schedule schedule, Instant now) {
    int fired = 0;
    while (schedule.getNextRunAt() != null
        && !schedule.getNextRunAt().isAfter(now)
        && fired < maxCatchupFires) {
      Instant slot = schedule.getNextRunAt();
      if (!triggerSlot(schedule, slot, now)) {
        return;
      }
      fired++;
    }
    if (fired >= maxCatchupFires
        && schedule.getNextRunAt() != null
        && !schedule.getNextRunAt().isAfter(now)) {
      log.warn(
          "Schedule catchup limit reached; remaining slots deferred",
          kv("schedule_id", schedule.getId()),
          kv("max_catchup_fires", maxCatchupFires),
          kv("next_run_at", schedule.getNextRunAt()));
    }
  }

  /**
   * @return true if trigger succeeded and schedule was updated; false if trigger failed
   */
  private boolean triggerSlot(Schedule schedule, Instant scheduledTime, Instant now) {
    try {
      UUID executionId =
          executionTriggerClient.triggerScheduledExecution(
              schedule.getTenantId(), schedule.getPipelineId(), schedule.getId());
      Instant nextRun = nextRunAfterSuccessfulTrigger(schedule, scheduledTime);
      schedule.recordTriggeredRun(now, nextRun);
      scheduleRepository.save(schedule);
      scheduleHistoryRepository.save(
          ScheduleHistory.triggered(
              schedule.getTenantId(), schedule.getId(), scheduledTime, executionId));
      log.info(
          "Triggered scheduled execution",
          kv("schedule_id", schedule.getId()),
          kv("execution_id", executionId),
          kv("scheduled_time", scheduledTime),
          kv("next_run_at", nextRun));
      return true;
    } catch (Exception e) {
      log.warn(
          "Failed to trigger scheduled execution",
          kv("schedule_id", schedule.getId()),
          kv("pipeline_id", schedule.getPipelineId()),
          e);
      scheduleHistoryRepository.save(
          ScheduleHistory.failed(schedule.getTenantId(), schedule.getId(), scheduledTime));
      schedule.setNextRunAt(scheduledTime);
      scheduleRepository.save(schedule);
      return false;
    }
  }

  /**
   * Computes the next fire time after a successful trigger.
   *
   * <ul>
   *   <li>{@code skip} — advance from the slot that fired, dropping missed intermediate fires
   *   <li>{@code run_all} — advance from the slot that fired (catchup loop handles additional
   *       slots)
   * </ul>
   */
  static Instant nextRunAfterSuccessfulTrigger(Schedule schedule, Instant scheduledTime) {
    return CronScheduleCalculator.nextRunAfter(
        schedule.getCronExpression(), schedule.getTimezone(), scheduledTime);
  }
}
