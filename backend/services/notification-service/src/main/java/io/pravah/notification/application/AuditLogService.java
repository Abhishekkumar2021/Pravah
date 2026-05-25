package io.pravah.notification.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.notification.domain.ActorType;
import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import io.pravah.notification.infrastructure.persistence.repository.AuditLogRepository;
import io.pravah.notification.infrastructure.persistence.repository.AuditLogSpecifications;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for recording and querying audit log entries. */
@Service
public class AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Log an audit event.
   *
   * @param tenantId Tenant ID
   * @param actorId User or service ID (null for system)
   * @param actorType Type of actor
   * @param actorName Display name
   * @param action Action performed
   * @param resourceType Type of resource affected
   * @param resourceId ID of resource
   * @param resourceName Name of resource
   * @param details Additional details (will be serialized to JSON)
   * @param ipAddress Client IP
   * @param userAgent Client user agent
   * @param requestId Request correlation ID
   */
  @Transactional
  public void logEvent(
      UUID tenantId,
      UUID actorId,
      ActorType actorType,
      String actorName,
      String action,
      String resourceType,
      UUID resourceId,
      String resourceName,
      Object details,
      String ipAddress,
      String userAgent,
      String requestId) {

    TenantContext.setCurrentTenantId(tenantId);
    try {
      String detailsJson = null;
      if (details != null) {
        try {
          detailsJson = objectMapper.writeValueAsString(details);
        } catch (Exception e) {
          log.warn("Failed to serialize audit details", e);
          detailsJson = details.toString();
        }
      }

      AuditLogEntity entry =
          AuditLogEntity.builder()
              .tenantId(tenantId)
              .actorId(actorId)
              .actorType(actorType)
              .actorName(actorName)
              .action(action)
              .resourceType(resourceType)
              .resourceId(resourceId)
              .resourceName(resourceName)
              .details(detailsJson)
              .ipAddress(ipAddress)
              .userAgent(userAgent)
              .requestId(requestId)
              .build();

      auditLogRepository.save(entry);

      log.debug(
          "Audit logged",
          kv("action", action),
          kv("resource_type", resourceType),
          kv("resource_id", resourceId),
          kv("actor_type", actorType));

    } finally {
      TenantContext.clear();
    }
  }

  /** Query audit log with filters. */
  @Transactional(readOnly = true)
  public Page<AuditLogEntity> queryLogs(
      UUID tenantId,
      String action,
      String resourceType,
      UUID resourceId,
      UUID actorId,
      Instant from,
      Instant to,
      Pageable pageable) {

    TenantContext.setCurrentTenantId(tenantId);
    try {
      Specification<AuditLogEntity> spec =
          AuditLogSpecifications.filtered(
              tenantId, action, resourceType, resourceId, actorId, from, to);
      return auditLogRepository.findAll(spec, pageable);
    } finally {
      TenantContext.clear();
    }
  }

  /** Get audit log for a specific resource. */
  @Transactional(readOnly = true)
  public Page<AuditLogEntity> getResourceHistory(
      UUID tenantId, String resourceType, UUID resourceId, Pageable pageable) {

    TenantContext.setCurrentTenantId(tenantId);
    try {
      return auditLogRepository.findByTenantIdAndResourceTypeAndResourceIdOrderByCreatedAtDesc(
          tenantId, resourceType, resourceId, pageable);
    } finally {
      TenantContext.clear();
    }
  }
}
