# ADR-030: SSO / SAML 2.0 for Enterprise Authentication

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's default authentication (ADR-009) uses JWT RS256 with email/password login. This is appropriate for individual developers and small teams who self-manage their credentials.

Enterprise customers have a different requirement: they manage identity centrally through an Identity Provider (IdP) — Okta, Azure Active Directory, Google Workspace, or an on-premise SAML IdP. These customers need:

- **Single Sign-On**: users log in once to their corporate IdP and access Pravah without a separate password. No new credential to manage, rotate, or potentially leak.
- **Centralized deprovisioning**: when an employee leaves the company, their access to Pravah is revoked automatically by the IdP — not by a manual Pravah admin action. A departing employee with a valid Pravah password is a compliance risk.
- **Audit by the IdP**: the enterprise's security team logs authentication events in their own SIEM via the IdP, not by relying on Pravah's audit log.
- **Enforced MFA**: the IdP enforces multi-factor authentication at the corporate level. Pravah does not need to implement its own MFA.
- **Group-based provisioning (SCIM)**: user accounts and role assignments can be pushed to Pravah automatically when a new user is added to the "Pravah Users" group in the IdP directory.

Without SSO, enterprise procurement and security review teams will block Pravah adoption. It is a commercial prerequisite for enterprise sales.

---

## Decision

SAML 2.0 and OpenID Connect (OIDC) for enterprise SSO, with SCIM 2.0 for automated user provisioning.

**Protocol choice — SAML 2.0 vs OIDC:**

| Protocol | Common With | Flow |
|----------|-------------|------|
| SAML 2.0 | Legacy enterprise IdPs (Okta classic, Azure AD SAML, ADFS) | XML assertions, POST binding |
| OIDC | Modern IdPs (Okta OIDC, Google Workspace, Azure AD v2, Auth0) | JWT-based, OAuth 2.0 flows |

Pravah supports both. The Tenant Service detects which protocol the IdP uses based on the SSO configuration the tenant provides.

**Authentication flow (OIDC, the more common modern path):**

```
1. User visits app.pravah.io and clicks "Login with SSO"
2. Tenant Service identifies tenant from email domain (megacorp.com → Okta config)
3. Redirect to IdP authorization endpoint with:
   - client_id (Pravah's registered app in the IdP)
   - scope: openid profile email groups
   - state: random nonce (CSRF protection)
   - redirect_uri: https://app.pravah.io/auth/callback
4. User authenticates with IdP (password + MFA enforced by IdP)
5. IdP redirects back to Pravah callback with authorization code
6. Tenant Service exchanges code for ID token + access token (back-channel)
7. ID token validated: signature, issuer, audience, nonce, expiry
8. Claims extracted: sub (IdP user ID), email, groups
9. User record created/updated in Pravah (upsert by IdP subject + tenant)
10. Pravah issues its own JWT (ADR-009) — now the session is standard JWT
    All subsequent API calls use the Pravah JWT, not the IdP token
```

**Why Pravah issues its own JWT after SSO rather than using the IdP token directly:**

- IdP tokens have varying formats and validation requirements across providers
- Pravah's JWT carries `tenant_id` and `roles` as custom claims — the IdP token does not
- Short-lived Pravah tokens (15 min) decouple Pravah's session lifecycle from the IdP's session lifecycle
- All services validate Pravah JWTs using the standard JWKS flow (ADR-009) — no per-provider handling in each service

**SAML 2.0 flow (for legacy IdPs):**

```
1. User clicks "Login with SSO"
2. Tenant Service generates SAML AuthnRequest (XML, signed with Pravah's SP certificate)
3. HTTP POST to IdP SSO endpoint
4. IdP authenticates user, generates SAML Response (XML assertion, signed with IdP certificate)
5. IdP POSTs SAML Response to Pravah ACS (Assertion Consumer Service) endpoint
6. Pravah validates: XML signature against IdP certificate, NotBefore/NotOnOrAfter, audience
7. Extract NameID (user identifier) and attribute assertions (email, groups)
8. Upsert user, issue Pravah JWT → same from here as OIDC
```

**SCIM 2.0 for automated provisioning:**

When a user is added to or removed from the "Pravah Users" group in Okta/Azure AD, the IdP calls Pravah's SCIM endpoint to provision or deprovision the user:

```
POST /scim/v2/Users        → create user account
PUT  /scim/v2/Users/{id}   → update user attributes or group membership
DELETE /scim/v2/Users/{id} → deprovision user (revoke access, invalidate sessions)
```

SCIM provisioning uses a long-lived SCIM bearer token that the IdP administrator configures once. This token is stored in Vault (ADR-007).

**Per-tenant SSO configuration (stored in `tenant_db`):**

```sql
CREATE TABLE sso_configurations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    protocol        VARCHAR(10) NOT NULL,      -- 'OIDC' or 'SAML'
    provider_name   VARCHAR(100) NOT NULL,     -- 'Okta', 'Azure AD', etc.
    -- OIDC fields
    issuer_url      TEXT,
    client_id       TEXT,
    client_secret_vault_path TEXT,             -- path in Vault KV
    -- SAML fields
    idp_entity_id   TEXT,
    idp_sso_url     TEXT,
    idp_certificate TEXT,
    -- Common
    domain_hints    TEXT[],                    -- ['megacorp.com', 'megacorp.co.uk']
    attribute_mappings JSONB,                  -- {'email': 'http://schemas...', 'groups': '...'}
    default_role    VARCHAR(50) DEFAULT 'VIEWER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Domain-based IdP routing:**

When a user enters their email address on the login page, Pravah extracts the domain (`megacorp.com`) and looks up the SSO configuration for that domain. If found, redirect to SSO. If not found, fall back to email/password login.

---

## Consequences

### Positive

- **Enterprise sales unblocked**: SSO is a checkbox item for enterprise procurement. Without it, security reviews fail.
- **Automatic deprovisioning via SCIM**: access is revoked immediately when the IdP deactivates the user — no manual Pravah admin action required.
- **MFA enforcement by the IdP**: Pravah inherits the enterprise's MFA policy without implementing its own MFA flow.
- **Reduced credential sprawl**: no new Pravah password for enterprise users to manage, share, or forget.
- **Standard protocols**: SAML 2.0 and OIDC are well-understood standards with mature libraries (Spring Security SAML, Spring Security OAuth 2.0). No proprietary implementation.
- **Pravah JWT isolation**: downstream services continue validating standard Pravah JWTs — zero changes required to any service other than the Tenant Service.

### Negative

- **Operational complexity of SSO onboarding**: each enterprise customer requires a configuration step (registering Pravah as a Service Provider in their IdP, configuring attribute mappings). This is a one-time setup but requires clear documentation and support.
- **IdP availability dependency**: if the customer's IdP is down, users cannot log in via SSO. Pravah can offer an emergency bypass (admin console with local credentials) for break-glass scenarios, but this must be carefully controlled.
- **SAML XML complexity**: SAML assertions are verbose XML with cryptographic signatures. Debugging SAML failures requires specialized tools (`saml-tracer` browser extension, careful XML parsing). OIDC is significantly simpler.
- **Client secret management**: OIDC requires storing a `client_secret` issued by the IdP. This is stored in Vault (ADR-007) but adds a credential that must be rotated when it expires or is compromised.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| SAML assertion replay attack | OneTimeUse assertion condition enforced; assertion ID stored in Redis with TTL = NotOnOrAfter - now |
| OIDC state parameter CSRF | State nonce validated on callback; mismatch = reject and re-initiate |
| IdP certificate rotation breaks SAML validation | Certificate stored in DB with multiple active certs supported; rotation window where both old and new certs are valid |
| SCIM token compromised | SCIM token stored in Vault; rotation invalidates all existing provisioned sessions |
| User changes email at IdP | SCIM PATCH updates email in Pravah; login continues via subject ID (not email) |

---

## Alternatives Considered

### Custom LDAP/AD Integration

Connect Pravah directly to each enterprise customer's LDAP or Active Directory server.

Rejected because:
- Requires network connectivity from Pravah cloud to the customer's on-premise directory — a firewall rule that most enterprises are unwilling to create.
- Per-customer LDAP schemas vary widely; maintaining compatibility is ongoing maintenance burden.
- SAML/OIDC is the standardized abstraction layer that enterprise IdPs provide precisely to avoid this problem.

### Requiring All Enterprise Users to Use Pravah-Native Auth

Force enterprises to manage a separate set of credentials within Pravah.

Rejected because:
- Fails the enterprise security review. "Users have another password to manage" is a compliance concern.
- No automatic deprovisioning when employees leave. Manual offboarding is an audit risk.
- Blocks enterprise sales.

### Implementing MFA Within Pravah

Build TOTP (Google Authenticator, Authy) MFA natively into Pravah's auth flow.

Rejected in favor of delegating MFA to the IdP. For enterprise customers, the IdP already enforces MFA — duplicating it in Pravah adds friction without security benefit. For non-enterprise customers, Pravah plans to add TOTP as a future feature, but it is not the priority compared to SSO.
