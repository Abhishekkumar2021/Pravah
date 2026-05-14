/**
 * Shared HTTP security helpers for Pravah services.
 *
 * <p>{@link io.pravah.spring.security.JwtTokenVerifier} verifies RS256 access tokens using JWKS
 * (ADR-009). {@link io.pravah.spring.security.ApiTenantJwtFilter} enforces JWT authentication on
 * {@code /api/**} paths and sets {@link io.pravah.spring.multitenancy.TenantContext} for RLS
 * (ADR-013).
 *
 * <p>Token issuance is handled by tenant-service's JwtTokenIssuer, which holds the private signing
 * key. This separation ensures that application services cannot forge tokens—they can only verify.
 *
 * @see <a href="../../../../../../docs/adr/ADR-009-jwt-oauth2-authentication.md">ADR-009</a>
 * @see <a href="../../../../../../docs/adr/ADR-013-postgresql-rls-tenant-isolation.md">ADR-013</a>
 */
package io.pravah.spring.security;
