package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.scheduler.api.dto.CreateScheduleRequest;
import io.pravah.scheduler.api.dto.CronPreviewRequest;
import io.pravah.scheduler.domain.model.Schedule;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID PIPELINE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID SCHEDULE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

  @Mock private ScheduleRepository scheduleRepository;

  private ScheduleService scheduleService;

  @BeforeEach
  void setUp() {
    scheduleService = new ScheduleService(scheduleRepository);
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(USER_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void previewCron_returnsNextRuns() {
    var preview = scheduleService.previewCron(new CronPreviewRequest("0 9 * * *", "UTC", null, 5));
    assertThat(preview.description()).isNotBlank();
    assertThat(preview.nextRuns()).hasSize(5);
  }

  @Test
  void previewCron_capsCountAtTen() {
    var preview =
        scheduleService.previewCron(new CronPreviewRequest("0 9 * * *", "UTC", null, 25));
    assertThat(preview.nextRuns()).hasSize(10);
  }

  @Test
  void previewCron_rejectsInvalidCron() {
    assertThatThrownBy(
            () -> scheduleService.previewCron(new CronPreviewRequest("not-cron", "UTC", null, 5)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void createSchedule_setsNextRunAt() {
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        scheduleService.createSchedule(
            new CreateScheduleRequest(PIPELINE_ID, "Daily ETL", "0 9 * * *", "UTC"));

    assertThat(response.name()).isEqualTo("Daily ETL");
    assertThat(response.nextRunAt()).isNotNull();
    assertThat(response.active()).isTrue();
  }

  @Test
  void createSchedule_rejectsInvalidCron() {
    assertThatThrownBy(
            () ->
                scheduleService.createSchedule(
                    new CreateScheduleRequest(PIPELINE_ID, "Bad", "invalid", "UTC")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void listSchedules_returnsMappedResponses() {
    Schedule schedule = savedSchedule();
    when(scheduleRepository.findByTenantIdAndPipelineIdOrderByCreatedAtDesc(TENANT_ID, PIPELINE_ID))
        .thenReturn(List.of(schedule));

    var list = scheduleService.listSchedules(PIPELINE_ID);

    assertThat(list).hasSize(1);
    assertThat(list.getFirst().id()).isEqualTo(schedule.getId());
  }

  @Test
  void getSchedule_returnsWhenFound() {
    Schedule schedule = savedSchedule();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));

    assertThat(scheduleService.getSchedule(SCHEDULE_ID).id()).isEqualTo(SCHEDULE_ID);
  }

  @Test
  void getSchedule_throwsWhenMissing() {
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> scheduleService.getSchedule(SCHEDULE_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void pauseSchedule_deactivatesActiveSchedule() {
    Schedule schedule = savedSchedule();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = scheduleService.pauseSchedule(SCHEDULE_ID);

    assertThat(response.active()).isFalse();
    verify(scheduleRepository).save(schedule);
  }

  @Test
  void pauseSchedule_rejectsAlreadyPaused() {
    Schedule schedule = savedSchedule();
    schedule.pause();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));

    assertThatThrownBy(() -> scheduleService.pauseSchedule(SCHEDULE_ID))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ValidationException) ex).getFieldErrors())
                    .anyMatch(fe -> fe.message().contains("already paused")));
  }

  @Test
  void resumeSchedule_reactivatesPausedSchedule() {
    Schedule schedule = savedSchedule();
    schedule.pause();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    var response = scheduleService.resumeSchedule(SCHEDULE_ID);

    assertThat(response.active()).isTrue();
    assertThat(response.nextRunAt()).isNotNull();
  }

  @Test
  void resumeSchedule_rejectsAlreadyActive() {
    Schedule schedule = savedSchedule();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));

    assertThatThrownBy(() -> scheduleService.resumeSchedule(SCHEDULE_ID))
        .isInstanceOf(ValidationException.class)
        .satisfies(
            ex ->
                assertThat(((ValidationException) ex).getFieldErrors())
                    .anyMatch(fe -> fe.message().contains("already active")));
  }

  @Test
  void deleteSchedule_removesEntity() {
    Schedule schedule = savedSchedule();
    when(scheduleRepository.findByIdAndTenantId(SCHEDULE_ID, TENANT_ID))
        .thenReturn(Optional.of(schedule));

    scheduleService.deleteSchedule(SCHEDULE_ID);

    verify(scheduleRepository).delete(schedule);
  }

  @Test
  void createSchedule_requiresTenantContext() {
    TenantContext.clear();
    assertThatThrownBy(
            () ->
                scheduleService.createSchedule(
                    new CreateScheduleRequest(PIPELINE_ID, "Daily", "0 9 * * *", "UTC")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");
  }

  private static Schedule savedSchedule() {
    return Schedule.builder()
        .id(SCHEDULE_ID)
        .tenantId(TENANT_ID)
        .pipelineId(PIPELINE_ID)
        .name("Daily ETL")
        .cronExpression("0 9 * * *")
        .timezone("UTC")
        .nextRunAt(Instant.parse("2026-05-17T09:00:00Z"))
        .createdBy(USER_ID)
        .build();
  }
}
