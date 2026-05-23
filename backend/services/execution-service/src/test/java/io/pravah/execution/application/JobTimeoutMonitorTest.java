package io.pravah.execution.application;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.domain.JobState;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobTimeoutMonitorTest {

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private JobTimeoutProcessor jobTimeoutProcessor;
  @Mock private io.pravah.spring.multitenancy.SystemMaintenanceRlsHelper maintenanceRlsHelper;

  private JobTimeoutMonitor monitor;

  @BeforeEach
  void setUp() {
    when(maintenanceRlsHelper.runWithMaintenance(
            org.mockito.ArgumentMatchers.<java.util.function.Supplier<List<JobEntity>>>any()))
        .thenAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              var supplier = (java.util.function.Supplier<List<JobEntity>>) inv.getArgument(0);
              return supplier.get();
            });
    monitor = new JobTimeoutMonitor(jobEntityRepository, jobTimeoutProcessor, maintenanceRlsHelper);
  }

  @Test
  void checkRunningJobTimeouts_delegatesPerJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    JobEntity job =
        JobEntity.builder().executionId(UUID.randomUUID()).stageId("a").stageName("A").build();
    setId(job, jobId);
    job.queue();
    job.assign(EmbeddedRunnerIds.LOCAL);

    when(jobEntityRepository.findByStatus(JobState.RUNNING)).thenReturn(List.of(job));

    monitor.checkRunningJobTimeouts();

    verify(jobTimeoutProcessor)
        .processTimedOutJobSafely(eq(jobId), org.mockito.ArgumentMatchers.any());
  }

  private static void setId(Object entity, UUID id) throws Exception {
    Field f = entity.getClass().getDeclaredField("id");
    f.setAccessible(true);
    f.set(entity, id);
  }
}
