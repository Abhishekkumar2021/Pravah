package io.pravah.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.common.domain.UserId;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.PipelineResponse;
import io.pravah.pipeline.api.dto.PipelineVersionDefinitionResponse;
import io.pravah.pipeline.api.dto.ValidatePipelineRequest;
import io.pravah.pipeline.api.dto.ValidatePipelineResponse;
import io.pravah.pipeline.domain.Pipeline;
import io.pravah.pipeline.domain.PipelineState;
import io.pravah.pipeline.domain.repository.PipelineRepository;
import io.pravah.pipeline.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEventEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineVersionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineEventRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineVersionRepository;
import io.pravah.spring.multitenancy.TenantContext;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PipelineApplicationServiceTest {

  @Mock private PipelineRepository pipelineRepository;
  @Mock private PipelineEventRepository pipelineEventRepository;
  @Mock private PipelineVersionRepository pipelineVersionRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private EntityManager entityManager;

  private PipelineApplicationService service;
  private ObjectMapper objectMapper;

  private UUID tenantId;
  private UUID userId;
  private UUID projectId;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    service =
        new PipelineApplicationService(
            pipelineRepository,
            pipelineEventRepository,
            pipelineVersionRepository,
            outboxRepository,
            objectMapper,
            entityManager);

    tenantId = UUID.randomUUID();
    userId = UUID.randomUUID();
    projectId = UUID.randomUUID();

    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createPipeline_validRequest_persistsAllEntities() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "my-pipeline", "desc", "stages:\n  - id: extract\n");

    when(pipelineRepository.save(any(Pipeline.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(pipelineEventRepository.save(any(PipelineEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxRepository.save(any(OutboxEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PipelineResponse response = service.createPipeline(request);

    assertThat(response.name()).isEqualTo("my-pipeline");
    assertThat(response.status()).isEqualTo("draft");
    assertThat(response.projectId()).isEqualTo(projectId);
    assertThat(response.id()).isNotNull();

    ArgumentCaptor<Pipeline> pipelineCaptor = ArgumentCaptor.forClass(Pipeline.class);
    verify(pipelineRepository).save(pipelineCaptor.capture());
    verify(entityManager).flush();
    Pipeline savedPipeline = pipelineCaptor.getValue();
    assertThat(savedPipeline.getTenantId()).isEqualTo(tenantId);
    assertThat(savedPipeline.getCreatedBy().value()).isEqualTo(userId);

    ArgumentCaptor<PipelineEventEntity> eventCaptor =
        ArgumentCaptor.forClass(PipelineEventEntity.class);
    verify(pipelineEventRepository).save(eventCaptor.capture());
    PipelineEventEntity savedEvent = eventCaptor.getValue();
    assertThat(savedEvent.getEventType()).isEqualTo("pipeline.created");
    assertThat(savedEvent.getTenantId()).isEqualTo(tenantId);
    assertThat(savedEvent.getPayload()).containsKey("occurredAt");

    ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
    verify(outboxRepository).save(outboxCaptor.capture());
    OutboxEntity savedOutbox = outboxCaptor.getValue();
    assertThat(savedOutbox.getEventType()).isEqualTo("pipeline.created");
    assertThat(savedOutbox.getAggregateType()).isEqualTo("Pipeline");
  }

  @Test
  void createPipeline_duplicateName_throwsDuplicatePipelineNameException() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "duplicate", null, "key: value\n");

    var sqlException =
        new SQLException(
            "duplicate key value violates unique constraint \"pipelines_project_id_name_key\"",
            "23505");
    when(pipelineRepository.save(any(Pipeline.class)))
        .thenThrow(new DataIntegrityViolationException("unique constraint", sqlException));

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(DuplicatePipelineNameException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void createPipeline_missingTenantContext_throwsIllegalStateException() {
    TenantContext.clear();

    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "test", null, "key: value\n");

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing tenant context");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_missingUserContext_throwsIllegalStateException() {
    TenantContext.setCurrentUserId(null);

    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "test", null, "key: value\n");

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing user context");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_invalidYamlSyntax_throwsIllegalArgumentException() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "test", null, ":\ninvalid yaml");

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid YAML");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_yamlNotMapping_throwsIllegalArgumentException() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "test", null, "- item1\n- item2\n");

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a non-empty YAML mapping");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_emptyYaml_throwsIllegalArgumentException() {
    CreatePipelineRequest request = new CreatePipelineRequest(projectId, "test", null, "");

    assertThatThrownBy(() -> service.createPipeline(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a non-empty YAML mapping");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_nullDescription_allowedAndSaved() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "no-desc", null, "key: value\n");

    when(pipelineRepository.save(any(Pipeline.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(pipelineEventRepository.save(any(PipelineEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxRepository.save(any(OutboxEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PipelineResponse response = service.createPipeline(request);

    assertThat(response.description()).isNull();
  }

  @Test
  void validatePipelineDefinition_validYaml_returnsValid() {
    ValidatePipelineRequest request = new ValidatePipelineRequest("stages: []\n");

    ValidatePipelineResponse response = service.validatePipelineDefinition(request);

    assertThat(response.valid()).isTrue();
    verify(pipelineRepository, never()).save(any());
    verify(entityManager, never()).flush();
  }

  @Test
  void validatePipelineDefinition_invalidYaml_throwsIllegalArgumentException() {
    ValidatePipelineRequest request = new ValidatePipelineRequest(":\nbad");

    assertThatThrownBy(() -> service.validatePipelineDefinition(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid YAML");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void validatePipelineDefinition_missingTenantContext_throwsIllegalStateException() {
    TenantContext.clear();
    TenantContext.setCurrentUserId(userId);
    ValidatePipelineRequest request = new ValidatePipelineRequest("key: value\n");

    assertThatThrownBy(() -> service.validatePipelineDefinition(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing tenant context");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void validatePipelineDefinition_missingUserContext_throwsIllegalStateException() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(null);
    ValidatePipelineRequest request = new ValidatePipelineRequest("key: value\n");

    assertThatThrownBy(() -> service.validatePipelineDefinition(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing user context");

    verify(pipelineRepository, never()).save(any());
  }

  @Test
  void createPipeline_eventPayloadContainsAllRequiredFields() {
    CreatePipelineRequest request =
        new CreatePipelineRequest(projectId, "full-event", "full desc", "stages: []\n");

    when(pipelineRepository.save(any(Pipeline.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(pipelineEventRepository.save(any(PipelineEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outboxRepository.save(any(OutboxEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createPipeline(request);

    ArgumentCaptor<PipelineEventEntity> eventCaptor =
        ArgumentCaptor.forClass(PipelineEventEntity.class);
    verify(pipelineEventRepository).save(eventCaptor.capture());

    var payload = eventCaptor.getValue().getPayload();
    assertThat(payload).containsKey("eventId");
    assertThat(payload).containsKey("eventType");
    assertThat(payload).containsKey("occurredAt");
    assertThat(payload).containsKey("aggregateType");
    assertThat(payload).containsKey("aggregateId");
    assertThat(payload).containsKey("tenantId");
    assertThat(payload).containsKey("projectId");
    assertThat(payload).containsKey("pipelineId");
    assertThat(payload).containsKey("name");
    assertThat(payload).containsKey("description");
    assertThat(payload).containsKey("status");
  }

  @Test
  void getPublishedVersionDefinition_storesJsonNode_convertsToPlainCollections() throws Exception {
    UUID pipelineUuid = UUID.randomUUID();
    PipelineId pipelineId = PipelineId.of(pipelineUuid);
    Instant now = Instant.parse("2026-05-14T12:00:00Z");
    Pipeline pipeline =
        Pipeline.builder()
            .id(pipelineId)
            .tenantId(tenantId)
            .projectId(ProjectId.of(projectId))
            .name("p")
            .description("d")
            .currentVersion(1)
            .state(PipelineState.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(UserId.of(userId))
            .build();
    when(pipelineRepository.findByIdAndTenantId(pipelineId, tenantId))
        .thenReturn(Optional.of(pipeline));

    var definitionJson =
        objectMapper.readTree("{\"stages\":[{\"id\":\"extract\",\"type\":\"sql\"}]}");

    when(pipelineVersionRepository.findByPipelineIdAndVersion(pipelineUuid, 1))
        .thenReturn(
            Optional.of(new PipelineVersionEntity(pipelineUuid, 1, definitionJson, now, userId)));

    PipelineVersionDefinitionResponse response =
        service.getPublishedVersionDefinition(pipelineUuid, 1);

    assertThat(response.pipelineId()).isEqualTo(pipelineUuid);
    assertThat(response.version()).isEqualTo(1);
    assertThat(response.definition().get("stages")).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> stages =
        (List<Map<String, Object>>) response.definition().get("stages");
    assertThat(stages.get(0).get("id")).isEqualTo("extract");
    assertThat(stages.get(0).get("type")).isEqualTo("sql");
  }
}
