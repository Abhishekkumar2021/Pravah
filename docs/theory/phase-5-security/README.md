# Phase 5 — Security, Auth & Multi-Tenancy

> **Status:** ✅ Complete — 7 of 7 chapters

Security in a multi-tenant pipeline platform is not optional and not an afterthought. Pravah executes arbitrary user-defined code, stores customer secrets, and provides shared infrastructure to hundreds of enterprise tenants. A single authorization bug could expose all tenants' data. A single credential leak could compromise a customer's entire data infrastructure.

This phase builds a coherent security system from first principles: starting with a threat model that defines what we protect and from whom, then implementing the specific controls for identity, secrets, authorization, tenant isolation, and API defense.

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [5.1](5.1-threat-model-security-principles.md) | **Threat Model & Security Principles** | Asset classification; threat actor profiles; STRIDE analysis; seven security principles Pravah follows |
| [5.2](5.2-jwt-authentication-oauth2.md) | **JWT Authentication & OAuth 2.0** | JWT structure; RS256 vs HS256; refresh token rotation; API keys; Spring Security configuration |
| [5.3](5.3-mtls-service-to-service.md) | **mTLS for Service-to-Service Communication** | Zero-trust between services; certificate anatomy; cert-manager automation; runner identity via SPIFFE; Istio vs application-level mTLS |
| [5.4](5.4-secret-management-vault.md) | **Secret Management with HashiCorp Vault** | Why Kubernetes Secrets are insufficient; dynamic database credentials; Kubernetes auth method; Vault Agent sidecar; Transit encryption |
| [5.5](5.5-rbac-authorization.md) | **RBAC & Authorization** | Pravah's role hierarchy; `@PreAuthorize`; IDOR vulnerability pattern; tenant scoping; privilege escalation prevention |
| [5.6](5.6-multi-tenancy-data-isolation.md) | **Multi-Tenancy & Data Isolation** | Silo vs pool model; PostgreSQL Row-Level Security; Kafka message-level isolation; per-tenant encryption keys; silo upgrade path |
| [5.7](5.7-api-security-rate-limiting-owasp.md) | **API Security — Rate Limiting, Input Validation & OWASP** | Token bucket algorithm; hierarchical rate limits; SSRF defense; SQL injection; OWASP Top 10 mapped to Pravah |

---

## The Security Stack

```
External request:
  HTTPS (TLS 1.3)
    ↓
  API Gateway
    • JWT validation (RS256, expiry, tenant_id, revocation)
    • Rate limiting (token bucket, per-key + per-tenant, Redis)
    • Input validation (Bean Validation, SSRF check)
    ↓
  Service layer
    • @PreAuthorize (role check)
    • Tenant ID from JWT → TenantContext
    • findByIdAndTenantId (ownership check)
    ↓
  Database layer
    • SET LOCAL pravah.current_tenant_id = ?
    • PostgreSQL RLS (row-level filter)
    • Tenant-scoped encryption keys (Vault Transit)

Internal service calls:
  mTLS (cert-manager + SPIFFE identity)
  Istio AuthorizationPolicy (service-to-service allow rules)

Runner communication:
  gRPC over mTLS (provisioned certificate per runner)
  SPIFFE URI = identity (tenant + runner ID)

Secrets:
  Vault dynamic DB credentials (1-hour TTL, auto-revoked)
  Vault KV for static secrets (versioned, audit-logged)
  Vault Agent sidecar (no Vault SDK in application code)
```

---

## Key Decisions and Their Rationale

| Decision | Alternative Considered | Reason for Choice |
|---|---|---|
| RS256 JWT signing | HS256 | Asymmetric: only auth service can sign; all services verify. HS256 requires sharing the secret with all services. |
| Short JWT lifetime (15 min) | Long-lived tokens | Limits stolen token window. Refresh token handles continuity. |
| Vault dynamic credentials | Static DB passwords | 1-hour TTL limits exposure. Auto-revoked. No manual rotation. |
| Pool model + RLS | Silo per tenant | Cost efficiency at scale. RLS + encryption provide strong logical isolation. Silo available as enterprise upgrade. |
| `findByIdAndTenantId` everywhere | `findById` + application check | Defense in depth. DB query cannot accidentally return cross-tenant data even with application bug. |
| Token bucket rate limiting | Fixed window | No boundary burst attack. Consistent enforcement across API Gateway pods via Redis. |
| SSRF validation at config + connection time | Config time only | DNS rebinding: hostname can resolve to different IP after validation. Connection-time re-validation defeats this. |

---

## Navigation

← [Phase 4 — Observability & Reliability](../phase-4-observability/README.md)
→ [Phase 6 — Kubernetes, Helm & Production Infrastructure](../phase-6-kubernetes/README.md) *(next)*
