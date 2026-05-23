package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.scheduler.domain.model.Schedule;
import io.pravah.scheduler.domain.model.ScheduleHistory;
import io.pravah.scheduler.domain.repository.ScheduleHistoryRepository;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.scheduler.infrastructure.client.ExecutionTriggerClient;
import io.pravah.scheduler.infrastructure.leader.LeaderElectionService;
import io.pravah.scheduler.infrastructure.persistence.SchedulerRlsHelper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleEvaluationJobTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID PIPELINE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID SCHEDULE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
  private static final UUID EXECUTION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

  @Mock private LeaderElectionService leaderElectionService;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private ScheduleHistoryRepository scheduleHistoryRepository;
  @Mock private ExecutionTriggerClient executionTriggerClient;
  @Mock private SchedulerRlsHelper schedulerRlsHelper;

  private ScheduleEvaluationJob scheduleEvaluationJob;

  @BeforeEach
  void setUp() {
    scheduleEvaluationJob =
        new ScheduleEvaluationJob(
            leaderElectionService,
            scheduleRepository,
            scheduleHistoryRepository,
            executionTriggerClient,
            schedulerRlsHelper,
            Clock.systemUTC(),
            32,
            java.time.Duration.ofDays(7));
  }

  @Test
  void evaluateDueSchedules_skipsWhenNotLeader() {
    when(leaderElectionService.isLeader()).thenReturn(false);

    scheduleEvaluationJob.evaluateDueSchedules();

    verify(scheduleRepository, never()).findDueSchedulesForUpdate(any());
    verify(schedulerRlsHelper, never()).enableEvaluation();
  }

  @Test
  void evaluateDueSchedules_noOpWhenNothingDue() {
    when(leaderElectionService.isLeader()).thenReturn(true);
    when(scheduleRepository.findDueSchedulesForUpdate(any())).thenReturn(List.of());

    scheduleEvaluationJob.evaluateDueSchedules();

    verify(executionTriggerClient, never()).triggerScheduledExecution(any(), any(), any());
  }

  @Test
  void evaluateDueSchedules_triggersExecutionAndRecordsHistory() {
    Instant now = Instant.parse("2026-05-16T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .id(SCHEDULE_ID)
            .tenantId(TENANT_ID)
            .pipelineId(PIPELINE_ID)
            .name("Daily")
            .cronExpression("0 9 * * *")
            .timezone("UTC")
            .nextRunAt(now)
            .createdBy(UUID.randomUUID())
            .build();

    when(leaderElectionService.isLeader()).thenReturn(true);
    when(scheduleRepository.findDueSchedulesForUpdate(any())).thenReturn(List.of(schedule));
    when(executionTriggerClient.triggerScheduledExecution(TENANT_ID, PIPELINE_ID, SCHEDULE_ID))
        .thenReturn(EXECUTION_ID);
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    scheduleEvaluationJob.evaluateDueSchedules();

    verify(executionTriggerClient).triggerScheduledExecution(TENANT_ID, PIPELINE_ID, SCHEDULE_ID);
    verify(scheduleHistoryRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                h ->
                    h.getStatus().equals(ScheduleHistory.STATUS_TRIGGERED)
                        && h.getExecutionId().equals(EXECUTION_ID)));
    verify(schedulerRlsHelper, org.mockito.Mockito.atLeastOnce()).enableEvaluation();
    verify(schedulerRlsHelper, org.mockito.Mockito.atLeastOnce()).disableEvaluation();
  }

  @Test
  void evaluateDueSchedules_recordsFailureWhenTriggerFails() {
    Instant now = Instant.parse("2026-05-16T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .id(SCHEDULE_ID)
            .tenantId(TENANT_ID)
            .pipelineId(PIPELINE_ID)
            .name("Daily")
            .cronExpression("0 9 * * *")
            .timezone("UTC")
            .nextRunAt(now)
            .createdBy(UUID.randomUUID())
            .build();

    when(leaderElectionService.isLeader()).thenReturn(true);
    when(scheduleRepository.findDueSchedulesForUpdate(any())).thenReturn(List.of(schedule));
    when(executionTriggerClient.triggerScheduledExecution(
            eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID)))
        .thenThrow(new RuntimeException("execution unavailable"));
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    scheduleEvaluationJob.evaluateDueSchedules();

    verify(scheduleHistoryRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                h -> h.getStatus().equals(ScheduleHistory.STATUS_FAILED)));
    verify(scheduleRepository).save(schedule);
    assertThat(schedule.getNextRunAt()).isEqualTo(now);
  }

  @Test
  void nextRunAfterSuccessfulTrigger_skipAdvancesFromScheduledSlot() {
    Schedule schedule = minimalSchedule("skip");
    Instant scheduled = Instant.parse("2026-05-14T09:00:00Z");
    Instant now = Instant.parse("2026-05-16T09:00:00Z");

    Instant next = ScheduleEvaluationJob.nextRunAfterSuccessfulTrigger(schedule, scheduled);

    assertThat(next).isEqualTo(Instant.parse("2026-05-15T09:00:00Z"));
  }

  @Test
  void nextRunAfterSuccessfulTrigger_runAllAdvancesFromScheduledSlot() {
    Schedule schedule = minimalSchedule("run_all");
    Instant scheduled = Instant.parse("2026-05-14T09:00:00Z");

    Instant next = ScheduleEvaluationJob.nextRunAfterSuccessfulTrigger(schedule, scheduled);

    assertThat(next).isEqualTo(Instant.parse("2026-05-15T09:00:00Z"));
  }

  @Test
  void processScheduleCoalesce_fallsBackToRunAllWhenIntervalExceedsThreshold() {
    Instant now = Instant.parse("2026-06-16T09:00:00Z");
    Instant firstMissed = Instant.parse("2026-05-14T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .id(SCHEDULE_ID)
            .tenantId(TENANT_ID)
            .pipelineId(PIPELINE_ID)
            .name("Daily")
            .cronExpression("0 9 * * *")
            .timezone("UTC")
            .catchupPolicy("coalesce")
            .nextRunAt(firstMissed)
            .createdBy(UUID.randomUUID())
            .build();

    ScheduleEvaluationJob jobWithShortThreshold =
        new ScheduleEvaluationJob(
            leaderElectionService,
            scheduleRepository,
            scheduleHistoryRepository,
            executionTriggerClient,
            schedulerRlsHelper,
            Clock.systemUTC(),
            32,
            java.time.Duration.ofDays(7));

    when(executionTriggerClient.triggerScheduledExecution(
            eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID)))
        .thenReturn(EXECUTION_ID);
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    org.springframework.test.util.ReflectionTestUtils.invokeMethod(
        jobWithShortThreshold, "processScheduleCoalesce", schedule, now);

    verify(executionTriggerClient, org.mockito.Mockito.atLeastOnce())
        .triggerScheduledExecution(eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID));
    verify(executionTriggerClient, never())
        .triggerScheduledExecution(eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID), any());
  }

  @Test
  void triggerCoalescedSlot_advancesNextRunPastAllMissedSlots() {
    Instant now = Instant.parse("2026-05-16T09:00:00Z");
    Instant firstMissed = Instant.parse("2026-05-14T09:00:00Z");
    Instant lastMissed = Instant.parse("2026-05-16T09:00:00Z");
    Schedule schedule =
        Schedule.builder()
            .id(SCHEDULE_ID)
            .tenantId(TENANT_ID)
            .pipelineId(PIPELINE_ID)
            .name("Daily")
            .cronExpression("0 9 * * *")
            .timezone("UTC")
            .catchupPolicy("coalesce")
            .nextRunAt(firstMissed)
            .createdBy(UUID.randomUUID())
            .build();

    when(executionTriggerClient.triggerScheduledExecution(
            eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID), any()))
        .thenReturn(EXECUTION_ID);
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    boolean triggered =
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            scheduleEvaluationJob,
            "triggerCoalescedSlot",
            schedule,
            firstMissed,
            now,
            lastMissed,
            3);

    assertThat(triggered).isTrue();
    assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-05-17T09:00:00Z"));
    verify(executionTriggerClient)
        .triggerScheduledExecution(eq(TENANT_ID), eq(PIPELINE_ID), eq(SCHEDULE_ID), any());
  }

  private static Schedule minimalSchedule(String catchupPolicy) {
    return Schedule.builder()
        .tenantId(TENANT_ID)
        .pipelineId(PIPELINE_ID)
        .name("Daily")
        .cronExpression("0 9 * * *")
        .timezone("UTC")
        .catchupPolicy(catchupPolicy)
        .createdBy(UUID.randomUUID())
        .build();
  }
}
