package io.pravah.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.notification.infrastructure.persistence.entity.AuditLogEntity;
import io.pravah.notification.infrastructure.persistence.repository.AuditLogRepository;
import io.pravah.spring.multitenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  private AuditLogService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new AuditLogService(auditLogRepository, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void queryLogs_withNoFilters_usesSpecificationQuery() {
    Pageable pageable = PageRequest.of(0, 50);
    Page<AuditLogEntity> page = new PageImpl<>(List.of());
    when(auditLogRepository.findAll(
            ArgumentMatchers.<Specification<AuditLogEntity>>any(), eq(pageable)))
        .thenReturn(page);

    Page<AuditLogEntity> result =
        service.queryLogs(tenantId, null, null, null, null, null, null, pageable);

    assertThat(result).isSameAs(page);
    verify(auditLogRepository)
        .findAll(ArgumentMatchers.<Specification<AuditLogEntity>>any(), eq(pageable));
    assertThat(TenantContext.getCurrentTenantId()).isNull();
  }

  @Test
  void queryLogs_withActionFilter_usesSpecificationQuery() {
    Pageable pageable = PageRequest.of(0, 20);
    Page<AuditLogEntity> page = new PageImpl<>(List.of());
    when(auditLogRepository.findAll(
            ArgumentMatchers.<Specification<AuditLogEntity>>any(), eq(pageable)))
        .thenReturn(page);

    Page<AuditLogEntity> result =
        service.queryLogs(tenantId, "execution.failed", null, null, null, null, null, pageable);

    assertThat(result).isSameAs(page);
    verify(auditLogRepository)
        .findAll(ArgumentMatchers.<Specification<AuditLogEntity>>any(), eq(pageable));
  }
}
