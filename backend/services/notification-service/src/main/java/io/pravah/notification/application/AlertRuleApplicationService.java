package io.pravah.notification.application;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.notification.api.dto.AlertRuleResponse;
import io.pravah.notification.api.dto.CreateAlertRuleRequest;
import io.pravah.notification.api.dto.UpdateAlertRuleRequest;
import io.pravah.notification.infrastructure.persistence.entity.AlertRuleEntity;
import io.pravah.notification.infrastructure.persistence.repository.AlertRuleRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for managing alert rules. */
@Service
public class AlertRuleApplicationService {

  private final AlertRuleRepository alertRuleRepository;

  public AlertRuleApplicationService(AlertRuleRepository alertRuleRepository) {
    this.alertRuleRepository = alertRuleRepository;
  }

  private UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context set");
    }
    return tenantId;
  }

  private UUID requireUserId() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No user context set");
    }
    return userId;
  }

  @Transactional
  public AlertRuleResponse createAlertRule(CreateAlertRuleRequest request) {
    UUID tenantId = requireTenantId();
    UUID userId = requireUserId();

    if (alertRuleRepository.existsByTenantIdAndName(tenantId, request.name())) {
      throw new IllegalArgumentException(
          "Alert rule with name '%s' already exists".formatted(request.name()));
    }

    AlertRuleEntity entity =
        new AlertRuleEntity(
            tenantId,
            request.pipelineId(),
            request.name(),
            request.description(),
            request.conditions(),
            request.channels(),
            request.dedupWindowSecondsOrDefault(),
            userId);

    alertRuleRepository.save(entity);
    return AlertRuleResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public List<AlertRuleResponse> listAlertRules(UUID pipelineId) {
    UUID tenantId = requireTenantId();

    List<AlertRuleEntity> rules;
    if (pipelineId != null) {
      rules =
          alertRuleRepository.findByTenantIdAndPipelineIdOrderByCreatedAtDesc(tenantId, pipelineId);
    } else {
      rules = alertRuleRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    return rules.stream().map(AlertRuleResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public AlertRuleResponse getAlertRule(UUID ruleId) {
    UUID tenantId = requireTenantId();
    AlertRuleEntity entity =
        alertRuleRepository
            .findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + ruleId));
    return AlertRuleResponse.from(entity);
  }

  @Transactional
  public AlertRuleResponse updateAlertRule(UUID ruleId, UpdateAlertRuleRequest request) {
    UUID tenantId = requireTenantId();

    AlertRuleEntity entity =
        alertRuleRepository
            .findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + ruleId));

    if (request.enabled() != null) {
      entity.setEnabled(request.enabled());
    }

    if (request.name() != null
        || request.description() != null
        || request.conditions() != null
        || request.channels() != null
        || request.dedupWindowSeconds() != null) {
      entity.update(
          request.name() != null ? request.name() : entity.getName(),
          request.description() != null ? request.description() : entity.getDescription(),
          request.conditions() != null ? request.conditions() : entity.getConditions(),
          request.channels() != null ? request.channels() : entity.getChannels(),
          request.dedupWindowSeconds() != null
              ? request.dedupWindowSeconds()
              : entity.getDedupWindowSeconds());
    }

    return AlertRuleResponse.from(entity);
  }

  @Transactional
  public void deleteAlertRule(UUID ruleId) {
    UUID tenantId = requireTenantId();

    AlertRuleEntity entity =
        alertRuleRepository
            .findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + ruleId));

    alertRuleRepository.delete(entity);
  }

  @Transactional
  public AlertRuleResponse toggleAlertRule(UUID ruleId, boolean enabled) {
    UUID tenantId = requireTenantId();

    AlertRuleEntity entity =
        alertRuleRepository
            .findById(ruleId)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new EntityNotFoundException("Alert rule not found: " + ruleId));

    entity.setEnabled(enabled);
    return AlertRuleResponse.from(entity);
  }
}
