package io.pravah.pipeline.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.common.domain.UserId;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.pipeline.api.dto.CreatePipelineRequest;
import io.pravah.pipeline.api.dto.PipelineDetailResponse;
import io.pravah.pipeline.api.dto.PipelineListResponse;
import io.pravah.pipeline.api.dto.PipelineResponse;
import io.pravah.pipeline.api.dto.UpdatePipelineRequest;
import io.pravah.pipeline.domain.Pipeline;
import io.pravah.pipeline.domain.PipelineEventTypes;
import io.pravah.pipeline.domain.repository.PipelineRepository;
import io.pravah.pipeline.infrastructure.persistence.entity.OutboxEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineEventEntity;
import io.pravah.pipeline.infrastructure.persistence.entity.PipelineVersionEntity;
import io.pravah.pipeline.infrastructure.persistence.repository.OutboxRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineEventRepository;
import io.pravah.pipeline.infrastructure.persistence.repository.PipelineVersionRepository;
import io.pravah.spring.multitenancy.TenantContext;
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

/**
 * Application service for pipeline management.
 *
 * <p>This service orchestrates domain operations and infrastructure concerns (event storage,
 * outbox). It delegates business logic to the Pipeline aggregate.
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns</a>
 */
@Service
public class PipelineApplicationService {

  private static final Logger log = LoggerFactory.getLogger(PipelineApplicationService.class);
  private static final String AGGREGATE_TYPE = "Pipeline";

  private final PipelineRepository pipelineRepository;
  private final PipelineEventRepository pipelineEventRepository;
  private final PipelineVersionRepository pipelineVersionRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public PipelineApplicationService(
      PipelineRepository pipelineRepository,
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
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();

    log.info(
        "Creating pipeline",
        kv("tenant_id", tenantId),
        kv("project_id", request.projectId()),
        kv("pipeline_name", request.name()));

    parseYamlDefinition(request.definitionYaml());

    Pipeline pipeline =
        Pipeline.create(
            tenantId,
            ProjectId.of(request.projectId()),
            request.name(),
            request.description(),
            userId);

    try {
      pipeline = pipelineRepository.save(pipeline);
      persistDomainEvents(pipeline);
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
        "Pipeline created", kv("tenant_id", tenantId), kv("pipeline_id", pipeline.getId().value()));

    return toResponse(pipeline);
  }

  @Transactional(readOnly = true)
  public PipelineDetailResponse getPipeline(UUID pipelineId) {
    UUID tenantId = requireTenantId();

    Pipeline pipeline = findPipelineOrThrow(PipelineId.of(pipelineId));

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
        pipeline.getId().value(),
        pipeline.getProjectId().value(),
        pipeline.getName(),
        pipeline.getDescription(),
        pipeline.getCurrentVersion(),
        pipeline.getState().asDatabaseValue(),
        pipeline.getCreatedAt(),
        pipeline.getUpdatedAt(),
        pipeline.getCreatedBy().value(),
        versionSummaries);
  }

  @Transactional(readOnly = true)
  public PipelineListResponse listPipelines(UUID projectId, String status, int page, int size) {
    requireTenantId();

    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
    ProjectId projId = ProjectId.of(projectId);

    Page<Pipeline> pipelinePage;
    if (status != null && !status.isBlank()) {
      pipelinePage = pipelineRepository.findByProjectIdAndStatus(projId, status, pageRequest);
    } else {
      pipelinePage = pipelineRepository.findActiveByProjectId(projId, pageRequest);
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
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();

    Pipeline pipeline = findPipelineOrThrow(PipelineId.of(pipelineId));

    boolean hasChanges = false;

    if (request.name() != null) {
      hasChanges |= pipeline.updateName(request.name(), userId);
    }

    if (request.description() != null) {
      hasChanges |= pipeline.updateDescription(request.description(), userId);
    }

    if (request.definitionYaml() != null) {
      parseYamlDefinition(request.definitionYaml());
      hasChanges = true;
    }

    if (!hasChanges) {
      return toResponse(pipeline);
    }

    try {
      pipeline = pipelineRepository.save(pipeline);
      persistDomainEvents(pipeline);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicatePipelineNameException(
          "A pipeline with this name already exists in the project", e);
    }

    log.info("Pipeline updated", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse publishPipeline(UUID pipelineId, String definitionYaml) {
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();

    Pipeline pipeline = findPipelineOrThrow(PipelineId.of(pipelineId));

    Map<String, Object> definition = parseYamlDefinition(definitionYaml);

    int newVersion = pipeline.publish(userId);

    PipelineVersionEntity version =
        new PipelineVersionEntity(
            pipelineId, newVersion, definition, pipeline.getUpdatedAt(), userId.value());

    pipelineRepository.save(pipeline);
    pipelineVersionRepository.save(version);
    persistDomainEvents(pipeline);

    log.info(
        "Pipeline published",
        kv("tenant_id", tenantId),
        kv("pipeline_id", pipelineId),
        kv("version", newVersion));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse archivePipeline(UUID pipelineId) {
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();

    Pipeline pipeline = findPipelineOrThrow(PipelineId.of(pipelineId));

    pipeline.archive(userId);

    pipelineRepository.save(pipeline);
    persistDomainEvents(pipeline);

    log.info("Pipeline archived", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return toResponse(pipeline);
  }

  @Transactional
  public PipelineResponse restorePipeline(UUID pipelineId) {
    UUID tenantId = requireTenantId();
    UserId userId = requireUserId();

    Pipeline pipeline = findPipelineOrThrow(PipelineId.of(pipelineId));

    pipeline.restore(userId);

    pipelineRepository.save(pipeline);
    persistDomainEvents(pipeline);

    log.info("Pipeline restored", kv("tenant_id", tenantId), kv("pipeline_id", pipelineId));

    return toResponse(pipeline);
  }

  private Pipeline findPipelineOrThrow(PipelineId pipelineId) {
    return pipelineRepository
        .findById(pipelineId)
        .orElseThrow(() -> new EntityNotFoundException("Pipeline", pipelineId.value()));
  }

  private void persistDomainEvents(Pipeline pipeline) {
    List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();

    for (Pipeline.DomainEventData eventData : events) {
      int nextEventVersion = pipelineEventRepository.countByPipelineId(eventData.pipelineId()) + 1;

      Map<String, Object> payload = buildEventPayload(eventData, pipeline);

      PipelineEventEntity event =
          new PipelineEventEntity(
              UUID.randomUUID(),
              eventData.pipelineId(),
              eventData.tenantId(),
              eventData.eventType(),
              nextEventVersion,
              payload,
              Map.of(),
              eventData.occurredAt());

      OutboxEntity outbox =
          new OutboxEntity(
              AGGREGATE_TYPE,
              eventData.pipelineId(),
              eventData.eventType(),
              objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {}),
              eventData.occurredAt());

      pipelineEventRepository.save(event);
      outboxRepository.save(outbox);
    }
  }

  private Map<String, Object> buildEventPayload(
      Pipeline.DomainEventData eventData, Pipeline pipeline) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("eventType", eventData.eventType());
    payload.put("occurredAt", eventData.occurredAt().toString());
    payload.put("aggregateType", AGGREGATE_TYPE);
    payload.put("aggregateId", eventData.pipelineId().toString());
    payload.put("tenantId", eventData.tenantId().toString());
    payload.put("pipelineId", eventData.pipelineId().toString());
    payload.put("actorId", eventData.actorId().toString());

    switch (eventData.eventType()) {
      case PipelineEventTypes.PIPELINE_CREATED -> {
        payload.put("name", pipeline.getName());
        payload.put("description", pipeline.getDescription());
        payload.put("projectId", pipeline.getProjectId().value().toString());
        payload.put("status", pipeline.getState().asDatabaseValue());
      }
      case PipelineEventTypes.PIPELINE_PUBLISHED -> {
        payload.put("version", pipeline.getCurrentVersion());
      }
      default -> {}
    }

    return payload;
  }

  private UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Missing tenant context");
    }
    return tenantId;
  }

  private UserId requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("Missing user context");
    }
    return UserId.of(userId);
  }

  private PipelineResponse toResponse(Pipeline pipeline) {
    return new PipelineResponse(
        pipeline.getId().value(),
        pipeline.getProjectId().value(),
        pipeline.getName(),
        pipeline.getDescription(),
        pipeline.getCurrentVersion(),
        pipeline.getState().asDatabaseValue(),
        pipeline.getCreatedAt(),
        pipeline.getUpdatedAt());
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
      return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
    }
  }
}
