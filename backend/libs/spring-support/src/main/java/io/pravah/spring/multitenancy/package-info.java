/**
 * Multi-tenancy infrastructure for Spring-based services.
 *
 * <p>This package provides:
 *
 * <ul>
 *   <li>{@link io.pravah.spring.multitenancy.TenantContext} - Thread-local tenant/user context
 *   <li>{@link io.pravah.spring.multitenancy.RlsAspect} - Sets PostgreSQL RLS context per
 *       transaction
 *   <li>{@link io.pravah.spring.multitenancy.RlsTransactionConfig} - Ensures correct aspect
 *       ordering
 * </ul>
 *
 * <p>Services using JPA with multi-tenancy should:
 *
 * <ol>
 *   <li>Depend on {@code :libs:spring-support}
 *   <li>Component-scan {@code io.pravah.spring.multitenancy} or import {@link
 *       io.pravah.spring.multitenancy.RlsTransactionConfig}
 *   <li>Set {@link io.pravah.spring.multitenancy.TenantContext} via {@link
 *       io.pravah.spring.security.ApiTenantJwtFilter} and {@link
 *       io.pravah.spring.security.JwtTokenProvider} (ADR-009) or a service-specific filter
 * </ol>
 *
 * @see <a href="docs/adr/ADR-013-postgresql-rls-tenant-isolation.md">ADR-013: PostgreSQL RLS</a>
 */
package io.pravah.spring.multitenancy;
