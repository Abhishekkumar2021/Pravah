package io.pravah.pipeline.infrastructure.persistence;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.pipeline.infrastructure.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Sets {@code SET LOCAL pravah.current_tenant_id} before transactional work (ADR-013). */
@Aspect
@Component
@Order(1)
public class RlsAspect {

  private static final Logger log = LoggerFactory.getLogger(RlsAspect.class);

  @PersistenceContext private EntityManager entityManager;

  @Before(
      "@within(org.springframework.transaction.annotation.Transactional) || "
          + "@annotation(org.springframework.transaction.annotation.Transactional)")
  public void setTenantContextBeforeTransaction() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      log.warn("No tenant context for @Transactional method - RLS will return zero rows");
      return;
    }
    entityManager
        .createNativeQuery("SET LOCAL pravah.current_tenant_id = :tenantId")
        .setParameter("tenantId", tenantId.toString())
        .executeUpdate();
    log.debug("Set RLS context", kv("tenant_id", tenantId));
  }
}
