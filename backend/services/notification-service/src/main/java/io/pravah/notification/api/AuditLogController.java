package io.pravah.notification.api;

import io.pravah.notification.api.dto.AuditLogResponse;
import io.pravah.notification.application.AuditLogService;
import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import io.pravah.spring.multitenancy.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @PreAuthorize("@permissionChecker.hasAny('settings:read', 'users:*')")
  public Page<AuditLogResponse> list(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) UUID resourceId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context");
    }

    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    Page<AuditLogEntity> results =
        auditLogService.queryLogs(
            tenantId, action, resourceType, resourceId, actorId, from, to, pageable);

    return results.map(AuditLogResponse::from);
  }

  @GetMapping("/resource")
  public Page<AuditLogResponse> getResourceHistory(
      @RequestParam String resourceType,
      @RequestParam UUID resourceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context");
    }

    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    Page<AuditLogEntity> results =
        auditLogService.getResourceHistory(tenantId, resourceType, resourceId, pageable);

    return results.map(AuditLogResponse::from);
  }
}
