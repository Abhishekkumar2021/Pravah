package io.pravah.scheduler.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.pravah.scheduler.api.dto.CreateScheduleRequest;
import io.pravah.scheduler.api.dto.CronPreviewRequest;
import io.pravah.scheduler.domain.model.Schedule;
import io.pravah.scheduler.domain.repository.ScheduleRepository;
import io.pravah.spring.multitenancy.TenantContext;
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
  void createSchedule_setsNextRunAt() {
    when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        scheduleService.createSchedule(
            new CreateScheduleRequest(PIPELINE_ID, "Daily ETL", "0 9 * * *", "UTC"));

    assertThat(response.name()).isEqualTo("Daily ETL");
    assertThat(response.nextRunAt()).isNotNull();
    assertThat(response.active()).isTrue();
  }
}
