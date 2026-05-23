package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.scheduler.api.dto.CreatePipelineTriggerRequest;
import io.pravah.scheduler.api.dto.PipelineTriggerResponse;
import io.pravah.scheduler.api.dto.TriggerDispatchHistoryResponse;
import io.pravah.scheduler.api.dto.UpdatePipelineTriggerRequest;
import io.pravah.scheduler.domain.model.PipelineTrigger;
import io.pravah.scheduler.domain.model.TriggerType;
import io.pravah.scheduler.domain.repository.PipelineTriggerRepository;
import io.pravah.scheduler.infrastructure.persistence.repository.TriggerDispatchHistoryRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PipelineTriggerService {

  private static final Logger log = LoggerFactory.getLogger(PipelineTriggerService.class);

  private final PipelineTriggerRepository triggerRepository;
  private final PasswordEncoder passwordEncoder;
  private final WebhookSecretGenerator secretGenerator;
  private final String hooksBaseUrl;
  private final ApplicationEventPublisher eventPublisher;
  private final PipelineTriggerDispatchService dispatchService;
  private final TriggerDispatchHistoryRepository dispatchHistoryRepository;

  public PipelineTriggerService(
      PipelineTriggerRepository triggerRepository,
      PasswordEncoder passwordEncoder,
      WebhookSecretGenerator secretGenerator,
      @Value("${pravah.hooks.base-url:http://localhost:8080/api/v1/hooks}") String hooksBaseUrl,
      ApplicationEventPublisher eventPublisher,
      PipelineTriggerDispatchService dispatchService,
      TriggerDispatchHistoryRepository dispatchHistoryRepository) {
    this.triggerRepository = triggerRepository;
    this.passwordEncoder = passwordEncoder;
    this.secretGenerator = secretGenerator;
    this.hooksBaseUrl =
        hooksBaseUrl.endsWith("/")
            ? hooksBaseUrl.substring(0, hooksBaseUrl.length() - 1)
            : hooksBaseUrl;
    this.eventPublisher = eventPublisher;
    this.dispatchService = dispatchService;
    this.dispatchHistoryRepository = dispatchHistoryRepository;
  }

  public PipelineTriggerResponse createTrigger(CreatePipelineTriggerRequest request) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();
    TriggerType type = TriggerType.fromValue(request.triggerType());

    if (triggerRepository.existsByTenantIdAndPipelineIdAndName(
        tenantId, request.pipelineId(), request.name().trim())) {
      throw ValidationException.of("name", "Trigger name already exists for this workflow");
    }

    Map<String, Object> config = request.config() != null ? request.config() : Map.of();
    if (type == TriggerType.KAFKA) {
      TriggerConfigSupport.requireKafkaTopic(config);
    }

    String plainSecret = null;
    String secretHash = null;
    if (type == TriggerType.WEBHOOK) {
      plainSecret = secretGenerator.generate();
      secretHash = passwordEncoder.encode(plainSecret);
    }

    PipelineTrigger trigger =
        PipelineTrigger.builder()
            .tenantId(tenantId)
            .pipelineId(request.pipelineId())
            .name(request.name().trim())
            .triggerType(type)
            .config(TriggerConfigSupport.toJson(config))
            .secretHash(secretHash)
            .createdBy(userId)
            .build();

    trigger = triggerRepository.save(trigger);
    publishTriggersChanged();

    String webhookUrl = type == TriggerType.WEBHOOK ? webhookUrl(trigger.getId()) : null;
    log.info(
        "Created pipeline trigger",
        kv("trigger_id", trigger.getId()),
        kv("pipeline_id", trigger.getPipelineId()),
        kv("trigger_type", type.value()));

    return PipelineTriggerResponse.from(trigger, config, webhookUrl, plainSecret);
  }

  @Transactional(readOnly = true)
  public List<PipelineTriggerResponse> listTriggers(UUID pipelineId) {
    UUID tenantId = requireTenantId();
    return triggerRepository
        .findByTenantIdAndPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId)
        .stream()
        .map(
            trigger ->
                PipelineTriggerResponse.from(
                    trigger, TriggerConfigSupport.parseConfig(trigger.getConfig())))
        .toList();
  }

  @Transactional(readOnly = true)
  public PipelineTriggerResponse getTrigger(UUID triggerId) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    return PipelineTriggerResponse.from(
        trigger, TriggerConfigSupport.parseConfig(trigger.getConfig()));
  }

  public void deleteTrigger(UUID triggerId) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    triggerRepository.delete(trigger);
    publishTriggersChanged();
    log.info("Deleted pipeline trigger", kv("trigger_id", triggerId));
  }

  public PipelineTriggerResponse disableTrigger(UUID triggerId) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    trigger.disable();
    trigger = triggerRepository.save(trigger);
    publishTriggersChanged();
    log.info("Disabled pipeline trigger", kv("trigger_id", triggerId));
    return PipelineTriggerResponse.from(
        trigger, TriggerConfigSupport.parseConfig(trigger.getConfig()));
  }

  public PipelineTriggerResponse enableTrigger(UUID triggerId) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    trigger.enable();
    trigger = triggerRepository.save(trigger);
    publishTriggersChanged();
    log.info("Enabled pipeline trigger", kv("trigger_id", triggerId));
    return PipelineTriggerResponse.from(
        trigger, TriggerConfigSupport.parseConfig(trigger.getConfig()));
  }

  public PipelineTriggerResponse updateTrigger(
      UUID triggerId, UpdatePipelineTriggerRequest request) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    UUID tenantId = trigger.getTenantId();
    boolean changed = false;

    if (request.name() != null && !request.name().isBlank()) {
      String newName = request.name().trim();
      if (!newName.equals(trigger.getName())) {
        if (triggerRepository.existsByTenantIdAndPipelineIdAndName(
            tenantId, trigger.getPipelineId(), newName)) {
          throw ValidationException.of("name", "Trigger name already exists for this workflow");
        }
        trigger.updateName(newName);
        changed = true;
      }
    }

    if (request.config() != null) {
      if (trigger.getTriggerType() == TriggerType.KAFKA) {
        TriggerConfigSupport.requireKafkaTopic(request.config());
      }
      trigger.updateConfig(TriggerConfigSupport.toJson(request.config()));
      changed = true;
    }

    if (request.enabled() != null) {
      if (request.enabled() && !trigger.isEnabled()) {
        trigger.enable();
        changed = true;
      } else if (!request.enabled() && trigger.isEnabled()) {
        trigger.disable();
        changed = true;
      }
    }

    if (changed) {
      trigger = triggerRepository.save(trigger);
      publishTriggersChanged();
      log.info("Updated pipeline trigger", kv("trigger_id", triggerId));
    }

    return PipelineTriggerResponse.from(
        trigger, TriggerConfigSupport.parseConfig(trigger.getConfig()));
  }

  PipelineTrigger findTriggerOrThrow(UUID triggerId) {
    UUID tenantId = requireTenantId();
    return triggerRepository
        .findByIdAndTenantId(triggerId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("PipelineTrigger", triggerId));
  }

  @Transactional(readOnly = true)
  public List<TriggerDispatchHistoryResponse> listDispatchHistory(UUID triggerId) {
    findTriggerOrThrow(triggerId);
    return dispatchHistoryRepository.findByTriggerIdOrderByCreatedAtDesc(triggerId).stream()
        .map(TriggerDispatchHistoryResponse::from)
        .toList();
  }

  public UUID testTrigger(UUID triggerId, Map<String, Object> payload) {
    PipelineTrigger trigger = findTriggerOrThrow(triggerId);
    Map<String, Object> body = payload != null ? payload : Map.of("test", true);
    return dispatchService.dispatch(trigger, body);
  }

  String webhookUrl(UUID triggerId) {
    return hooksBaseUrl + "/" + triggerId;
  }

  private void publishTriggersChanged() {
    eventPublisher.publishEvent(
        new io.pravah.scheduler.infrastructure.kafka.KafkaTriggerListenerManager
            .TriggersChangedEvent());
  }

  private static UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }

  private static UUID requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No user context set");
    }
    return userId;
  }
}
