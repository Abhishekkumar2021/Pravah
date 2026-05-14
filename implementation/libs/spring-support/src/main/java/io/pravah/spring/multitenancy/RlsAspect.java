package io.pravah.spring.multitenancy;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that sets the PostgreSQL RLS context at the start of each transaction.
 *
 * <p>This ensures Row-Level Security policies are enforced for all database operations within the
 * transaction. The tenant ID is read from {@link TenantContext}, which must be set by an upstream
 * filter/interceptor from the authenticated JWT token.
 *
 * <p><strong>Per ADR-013:</strong>
 *
 * <ul>
 *   <li>Uses {@code SET LOCAL pravah.current_tenant_id = :tenantId} which is transaction-scoped
 *   <li>Works correctly with PgBouncer in transaction mode
 *   <li>If tenant context is not set, queries return zero rows (safe failure mode)
 * </ul>
 *
 * <p><strong>Ordering:</strong> This aspect runs at {@link Ordered#LOWEST_PRECEDENCE} so that the
 * transaction (started by {@link RlsTransactionConfig}) is already active when {@code SET LOCAL}
 * executes.
 *
 * @see <a href="docs/adr/ADR-013-postgresql-rls-tenant-isolation.md">ADR-013: PostgreSQL RLS</a>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RlsAspect {

  private static final Logger log = LoggerFactory.getLogger(RlsAspect.class);

  @PersistenceContext private EntityManager entityManager;

  /**
   * Sets the RLS tenant context before any {@code @Transactional} method executes.
   *
   * <p>Per ADR-013, we use {@code SET LOCAL} which:
   *
   * <ul>
   *   <li>Is scoped to the current transaction only
   *   <li>Automatically cleared on commit/rollback
   *   <li>Safe with PgBouncer transaction pooling
   * </ul>
   *
   * <p>If no tenant context is set, the RLS policy {@code
   * current_setting('pravah.current_tenant_id', TRUE)} returns NULL, causing the policy predicate
   * {@code tenant_id = NULL} to evaluate to FALSE, returning zero rows. This is the safe failure
   * mode per ADR-013.
   */
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
