package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pravah.common.domain.ExecutionState;
import io.pravah.common.runner.RemoteJobSpecEnv;
import io.pravah.common.runner.RemoteJobSpecSecretStripper;
import io.pravah.execution.application.port.ConnectionCatalog;
import io.pravah.execution.application.port.ResolvedJdbcConnection;
import io.pravah.execution.application.port.SecretCatalog;
import io.pravah.execution.infrastructure.persistence.entity.ExecutionEntity;
import io.pravah.execution.infrastructure.persistence.entity.JobEntity;
import io.pravah.execution.infrastructure.persistence.repository.ExecutionEntityRepository;
import io.pravah.execution.infrastructure.persistence.repository.JobEntityRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobEnvironmentSecretServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID EXECUTION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID JOB_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Mock private JobEntityRepository jobEntityRepository;
  @Mock private ExecutionEntityRepository executionEntityRepository;
  @Mock private SecretCatalog secretCatalog;
  @Mock private ConnectionCatalog connectionCatalog;

  private JobEnvironmentSecretService service;

  @BeforeEach
  void setUp() {
    service =
        new JobEnvironmentSecretService(
            jobEntityRepository, executionEntityRepository, secretCatalog, connectionCatalog);
  }

  @Test
  void resolve_tenantSecret_returnsCatalogValue() {
    stubJobAndExecution("extract", Map.of("config", Map.of()));
    when(secretCatalog.resolve(
            eq(TENANT_ID), eq(EXECUTION_ID), org.mockito.ArgumentMatchers.any(), eq("api_key")))
        .thenReturn("resolved-key");

    Map<String, String> resolved =
        service.resolve(TENANT_ID, EXECUTION_ID, JOB_ID, Map.of("API_KEY", "api_key"));

    assertThat(resolved).containsEntry("API_KEY", "resolved-key");
  }

  @Test
  void resolve_rejectsInvalidSecretName() {
    stubJobAndExecution("extract", Map.of("config", Map.of()));

    assertThatThrownBy(
            () -> service.resolve(TENANT_ID, EXECUTION_ID, JOB_ID, Map.of("API_KEY", "__reserved")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reserved");
  }

  @Test
  void resolve_sqlPasswordFromNamedConnection() {
    stubJobAndExecution("sql-stage", Map.of("config", Map.of("connection", "warehouse")));
    when(connectionCatalog.resolve(TENANT_ID, "warehouse"))
        .thenReturn(new ResolvedJdbcConnection("warehouse", "jdbc:pg", "user", "pw"));

    Map<String, String> resolved =
        service.resolve(
            TENANT_ID,
            EXECUTION_ID,
            JOB_ID,
            Map.of(
                RemoteJobSpecEnv.SQL_PASSWORD, RemoteJobSpecSecretStripper.RUNTIME_SQL_PASSWORD));

    assertThat(resolved).containsEntry(RemoteJobSpecEnv.SQL_PASSWORD, "pw");
  }

  @Test
  void resolve_rejectsTenantMismatch() {
    JobEntity job = mock(JobEntity.class);
    when(job.getExecutionId()).thenReturn(EXECUTION_ID);
    when(jobEntityRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    ExecutionEntity execution = mock(ExecutionEntity.class);
    when(execution.getTenantId()).thenReturn(UUID.randomUUID());
    when(executionEntityRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));

    assertThatThrownBy(
            () -> service.resolve(TENANT_ID, EXECUTION_ID, JOB_ID, Map.of("K", "api_key")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant mismatch");
  }

  private void stubJobAndExecution(String stageId, Map<String, Object> stageFields) {
    Map<String, Object> stageDefinition = new java.util.LinkedHashMap<>(stageFields);
    stageDefinition.putIfAbsent("id", stageId);
    JobEntity job = mock(JobEntity.class);
    when(job.getExecutionId()).thenReturn(EXECUTION_ID);
    when(job.getStageId()).thenReturn(stageId);
    when(jobEntityRepository.findById(JOB_ID)).thenReturn(Optional.of(job));

    ExecutionEntity execution =
        new ExecutionEntity.Builder()
            .tenantId(TENANT_ID)
            .pipelineId(UUID.randomUUID())
            .pipelineVersion(1)
            .status(ExecutionState.RUNNING)
            .triggerType("manual")
            .parameters(Map.of())
            .definitionSnapshot(Map.of("stages", List.of(stageDefinition)))
            .build();
    when(executionEntityRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));
  }
}
