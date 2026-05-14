package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.PipelineDetailResponse;
import io.pravah.pipeline.api.dto.PipelineListResponse;
import io.pravah.pipeline.api.dto.PipelineResponse;
import io.pravah.pipeline.api.dto.UpdatePipelineRequest;
import io.pravah.pipeline.domain.PipelineEventTypes;
import io.pravah.pipeline.domain.PipelineState;
import io.pravah.pipeline.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEventEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineVersionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.JpaPipelineRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineEventRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineVersionRepository;
import io.pravah.pipeline.infrastructure.security.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Service
public class PipelineApplicationService {

  private static final Logger log = LoggerFactory.getLogger(PipelineApplicationService.class);

  private static final String AGGREGATE_TYPE = "Pipeline";

  private final JpaPipelineRepository pipelineRepository;
  private final PipelineEventRepository pipelineEventRepository;
  private final PipelineVersionRepository pipelineVersionRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public PipelineApplicationService(
      JpaPipelineRepository pipelineRepository,
      PipelineEventRepository pipelineEventRepository,
      PipelineVersionRepository pipelineVersionRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.pipelineRepository = pipelineRepository;
    this.pipelineEventRepository = pipelineEventRepository;
    this.pipelineVersionRepository = pipelineVersionRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PipelineResponse createPipeline(CreatePipelineRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID userId = TenantContext.getCurrentUserId();
    if (tenantId == null || userId == null) {
      throw new IllegalStateException("Missing tenant or user context");
    }

    log.info(
        "Creating pipeline",
        kv("tenant_id", tenantId),
        kv("project_id", request.projectId()),
        kv("pipeline_name", request.name()));

    Map<String, Object> definition = parseYamlDefinition(request.definitionYaml());
    Instant now = Instant.now();
    UUID pipelineId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("eventType", PipelineEventTypes.PIPELINE_CREATED);
    payload.put("occurredAt", now.toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", pipelineId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("projectId", request.projectId().toString());
    payload.put("pipelineId", pipelineId.toString());
    payload.put("name", request.name());
    payload.put("description", request.description());
    payload.put("definition", definition);
    payload.put("status", PipelineState.DRAFT.asDatabaseValue());
    payload.put("createdBy", userId.toString());

    PipelineEntity pipeline =
        new PipelineEntity(
            pipelineId,
            tenantId,
            request.projectId(),
            request.name(),
            request.description(),
            0,
            PipelineState.DRAFT.asDatabaseValue(),
            now,
            now,
            userId);

    PipelineEventEntity event =
        new PipelineEventEntity(
            eventId,
            pipelineId,
            tenantId,
            PipelineEventTypes.PIPELINE_CREATED,
            1,
            payload,
            Map.of(),
            now);

    OutboxEntity outbox =
        new OutboxEntity(
            AGGREGATE_TYPE,
            pipelineId,
            PipelineEventTypes.PIPELINE_CREATED,
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
            now);

    try {
      pipelineRepository.save(pipeline);
      pipelineEventRepository.save(event);
      outboxRepository.save(outbox);
    } catch (DataIntegrityViolationException e) {
      log.warn(
          "Duplicate pipeline name",
          kv("tenant_id", tenantId),
          kv("project_id", request.projectId()),
          kv("pipeline_name", request.name()));
      throw new DuplicatePipelineNameException(
          "A pipeline with this name already exists in the project", e);
    }

    log.info(
        "Pipeline created",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("event_id", eventId));

    return new PipelineResponse(
        pipelineId,
        request.projectId(),
        request.name(),
        request.description(),
        pipeline.getCurrentVersion(),
        pipeline.getStatus(),
        pipeline.getCreatedAt(),
        pipeline.getUpdatedAt());
  }

  @Transactional(readOnly = true)
  public PipelineDetailResponse getPipeline(UUID pipelineId) {
    UUID tenantId = requireTenantContext();

    PipelineEntity pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId));

    List<PipelineVersionEntity> versions =
        pipelineVersionRepository.findByPipelineIdOrderByVersionDesc(pipelineId);

    List<PipelineDetailResponse.VersionSummary> versionSummaries =
        versions.stream()
            .map(
                v ->
                    new PipelineDetailResponse.VersionSummary(
                        v.getVersion(), v.getPublishedAt(), v.getPublishedBy()))
            .toList();

    log.debug("Retrieved pipeline", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return new PipelineDetailResponse(
        pipeline.getId(),
        pipeline.getProjectId(),
        pipeline.getName(),
        pipeline.getDescription(),
        pipeline.getCurrentVersion(),
        pipeline.getStatus(),
        pipeline.getCreatedAt(),
        pipeline.getUpdatedAt(),
        pipeline.getCreatedBy(),
        versionSummaries);
  }

  @Transactional(readOnly = true)
  public PipelineListResponse listPipelines(UUID projectId, String status, int page, int size) {
    requireTenantContext();

    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));

    Page<PipelineEntity> pipelinePage;
    if (status != null && !status.isBlank()) {
      pipelinePage = pipelineRepository.findByProjectIdAndStatus(projectId, status, pageRequest);
    } else {
      pipelinePage = pipelineRepository.findActiveByProjectId(projectId, pageRequest);
    }

    List<PipelineResponse> content =
        pipelinePage.getContent().stream().map(this::toResponse).toList();

    return new PipelineListResponse(
        content,
        pipelinePage.getNumber(),
        pipelinePage.getSize(),
        pipelinePage.getTotalElements(),
        pipelinePage.getTotalPages(),
        pipelinePage.isLast());
  }

  @Transactional
  public PipelineResponse updatePipeline(UUID pipelineId, UpdatePipelineRequest request) {
    UUID tenantId = requireTenantContext();
    UUID userId = TenantContext.getCurrentUserId();

    PipelineEntity pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId));

    Instant now = Instant.now();
    UUID eventId = UUID.randomUUID();

    boolean hasChanges = false;
    Map<String, Object> changes = new LinkedHashMap<>();

    if (request.name() != null && !request.name().equals(pipeline.getName())) {
      changes.put("oldName", pipeline.getName());
      changes.put("newName", request.name());
      pipeline.setName(request.name());
      hasChanges = true;
    }

    if (request.description() != null && !request.description().equals(pipeline.getDescription())) {
      changes.put("oldDescription", pipeline.getDescription());
      changes.put("newDescription", request.description());
      pipeline.setDescription(request.description());
      hasChanges = true;
    }

    if (request.definitionYaml() != null) {
      parseYamlDefinition(request.definitionYaml());
      changes.put("definitionUpdated", true);
      hasChanges = true;
    }

    if (!hasChanges) {
      return toResponse(pipeline);
    }

    pipeline.setUpdatedAt(now);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("eventType", PipelineEventTypes.PIPELINE_UPDATED);
    payload.put("occurredAt", now.toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", pipelineId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("updatedBy", userId.toString());
    payload.put("changes", changes);

    int nextEventVersion = pipelineEventRepository.countByPipelineId(pipelineId) + 1;

    PipelineEventEntity event =
        new PipelineEventEntity(
            eventId,
            pipelineId,
            tenantId,
            PipelineEventTypes.PIPELINE_UPDATED,
            nextEventVersion,
            payload,
            Map.of(),
            now);

    OutboxEntity outbox =
        new OutboxEntity(
            AGGREGATE_TYPE,
            pipelineId,
            PipelineEventTypes.PIPELINE_UPDATED,
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
            now);

    try {
      pipelineRepository.save(pipeline);
      pipelineEventRepository.save(event);
      outboxRepository.save(outbox);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicatePipelineNameException(
          "A pipeline with this name already exists in the project", e);
    }

    log.info(
        "Pipeline updated",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("event_id", eventId));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse publishPipeline(UUID pipelineId, String definitionYaml) {
    UUID tenantId = requireTenantContext();
    UUID userId = TenantContext.getCurrentUserId();

    PipelineEntity pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId));

    PipelineState currentState = PipelineState.fromDatabase(pipeline.getStatus());
    if (!currentState.canTransitionTo(PipelineState.ACTIVE)) {
      throw new InvalidStateTransitionException(
          "Pipeline", pipelineId.toString(), currentState.name(), "publish");
    }

    Map<String, Object> definition = parseYamlDefinition(definitionYaml);
    Instant now = Instant.now();
    UUID eventId = UUID.randomUUID();
    int newVersion = pipeline.getCurrentVersion() + 1;

    PipelineVersionEntity version =
        new PipelineVersionEntity(pipelineId, newVersion, definition, now, userId);

    pipeline.setCurrentVersion(newVersion);
    pipeline.setStatus(PipelineState.ACTIVE.asDatabaseValue());
    pipeline.setUpdatedAt(now);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("eventType", PipelineEventTypes.PIPELINE_PUBLISHED);
    payload.put("occurredAt", now.toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", pipelineId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("version", newVersion);
    payload.put("publishedBy", userId.toString());
    payload.put("definition", definition);

    int nextEventVersion = pipelineEventRepository.countByPipelineId(pipelineId) + 1;

    PipelineEventEntity event =
        new PipelineEventEntity(
            eventId,
            pipelineId,
            tenantId,
            PipelineEventTypes.PIPELINE_PUBLISHED,
            nextEventVersion,
            payload,
            Map.of(),
            now);

    OutboxEntity outbox =
        new OutboxEntity(
            AGGREGATE_TYPE,
            pipelineId,
            PipelineEventTypes.PIPELINE_PUBLISHED,
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
            now);

    pipelineRepository.save(pipeline);
    pipelineVersionRepository.save(version);
    pipelineEventRepository.save(event);
    outboxRepository.save(outbox);

    log.info(
        "Pipeline published",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("version", newVersion));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse archivePipeline(UUID pipelineId) {
    UUID tenantId = requireTenantContext();
    UUID userId = TenantContext.getCurrentUserId();

    PipelineEntity pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId));

    PipelineState currentState = PipelineState.fromDatabase(pipeline.getStatus());
    if (!currentState.canTransitionTo(PipelineState.ARCHIVED)) {
      throw new InvalidStateTransitionException(
          "Pipeline", pipelineId.toString(), currentState.name(), "archive");
    }

    Instant now = Instant.now();
    UUID eventId = UUID.randomUUID();

    pipeline.setStatus(PipelineState.ARCHIVED.asDatabaseValue());
    pipeline.setUpdatedAt(now);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("eventType", PipelineEventTypes.PIPELINE_ARCHIVED);
    payload.put("occurredAt", now.toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", pipelineId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("archivedBy", userId.toString());

    int nextEventVersion = pipelineEventRepository.countByPipelineId(pipelineId) + 1;

    PipelineEventEntity event =
        new PipelineEventEntity(
            eventId,
            pipelineId,
            tenantId,
            PipelineEventTypes.PIPELINE_ARCHIVED,
            nextEventVersion,
            payload,
            Map.of(),
            now);

    OutboxEntity outbox =
        new OutboxEntity(
            AGGREGATE_TYPE,
            pipelineId,
            PipelineEventTypes.PIPELINE_ARCHIVED,
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
            now);

    pipelineRepository.save(pipeline);
    pipelineEventRepository.save(event);
    outboxRepository.save(outbox);

    log.info("Pipeline archived", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse restorePipeline(UUID pipelineId) {
    UUID tenantId = requireTenantContext();
    UUID userId = TenantContext.getCurrentUserId();

    PipelineEntity pipeline =
        pipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId));

    PipelineState currentState = PipelineState.fromDatabase(pipeline.getStatus());
    if (!currentState.canTransitionTo(PipelineState.ACTIVE)) {
      throw new InvalidStateTransitionException(
          "Pipeline", pipelineId.toString(), currentState.name(), "restore");
    }

    Instant now = Instant.now();
    UUID eventId = UUID.randomUUID();

    pipeline.setStatus(PipelineState.ACTIVE.asDatabaseValue());
    pipeline.setUpdatedAt(now);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("eventType", PipelineEventTypes.PIPELINE_RESTORED);
    payload.put("occurredAt", now.toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", pipelineId.toString());
    payload.put("tenantId", tenantId.toString());
    payload.put("restoredBy", userId.toString());

    int nextEventVersion = pipelineEventRepository.countByPipelineId(pipelineId) + 1;

    PipelineEventEntity event =
        new PipelineEventEntity(
            eventId,
            pipelineId,
            tenantId,
            PipelineEventTypes.PIPELINE_RESTORED,
            nextEventVersion,
            payload,
            Map.of(),
            now);

    OutboxEntity outbox =
        new OutboxEntity(
            AGGREGATE_TYPE,
            pipelineId,
            PipelineEventTypes.PIPELINE_RESTORED,
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
            now);

    pipelineRepository.save(pipeline);
    pipelineEventRepository.save(event);
    outboxRepository.save(outbox);

    log.info("Pipeline restored", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return toResponse(pipeline);
  }

  private UUID requireTenantContext() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID userId = TenantContext.getCurrentUserId();
    if (tenantId == null || userId == null) {
      throw new IllegalStateException("Missing tenant or user context");
    }
    return tenantId;
  }

  private PipelineResponse toResponse(PipelineEntity entity) {
    return new PipelineResponse(
        entity.getId(),
        entity.getProjectId(),
        entity.getName(),
        entity.getDescription(),
        entity.getCurrentVersion(),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private Map<String, Object> parseYamlDefinition(String yamlText) {
    try {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
      Object root = yaml.load(yamlText);
      if (root == null || !(root instanceof Map)) {
        throw new IllegalArgumentException(
            "Pipeline definition must be a non-empty YAML mapping at the root");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) root;
      Map<String, Object> normalized =
          objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
      return normalized;
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
    }
  }
}
