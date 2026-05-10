# ADR-009: JWT RS256 + OAuth 2.0 for User Authentication

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah's API serves two classes of callers:

1. **Human users** — logging in via the dashboard to manage pipelines, view execution history, and configure runners
2. **Programmatic clients** — CI/CD systems, scripts, and automated tools calling the API to trigger pipelines, fetch status, or manage configuration

Both need authentication (who are you?) and the result of authentication must carry enough information for authorization decisions (what are you allowed to do?).

**Requirements:**

- The authentication mechanism must be **stateless** at the service level. Pravah's API services scale horizontally; every instance must be able to validate a token without calling a central session store.
- Tokens must carry the caller's **tenant context** (`tenant_id`). This is the most critical piece of information for authorization — it determines which data the caller can access.
- The token must be **short-lived** to limit the blast radius of a stolen token.
- The mechanism must support **token revocation** for emergency use (compromised account, suspicious activity).
- **API keys** must be supported for programmatic access — long-lived, revocable, and auditable.
- The signing infrastructure must support **key rotation** without service restarts or downtime.

---

## Decision

**JSON Web Tokens (JWT) with RS256 asymmetric signing** for user-facing authentication. **OAuth 2.0 authorization code flow** for user login with short-lived access tokens and rotating refresh tokens. **Hashed API keys** for programmatic access.

**Why RS256 over HS256:**

HS256 uses a single shared secret for both signing and verification. Every service that needs to verify a token must have a copy of the signing secret. This means:
- The signing secret must be distributed to all services (a secret management problem)
- Any service that can verify a token can also forge a token

RS256 uses a private key (held only by the Auth Service) for signing, and a public key (distributed freely) for verification. Any service can verify tokens without being able to forge them. Key rotation means publishing a new public key via JWKS endpoint — services pick it up automatically.

**Token structure:**

```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "pravah-2024-01-key-1"
  },
  "payload": {
    "sub": "user-uuid-abc123",
    "tenant_id": "megacorp",
    "roles": ["PIPELINE_EDITOR"],
    "iat": 1704067200,
    "exp": 1704068100,
    "jti": "token-uuid-xyz789"
  }
}
```

**Lifetime and refresh strategy:**

| Token | Lifetime | Storage | Rotation |
|-------|----------|---------|----------|
| Access token | 15 minutes | Memory (not localStorage) | N/A — short enough that expiry is acceptable |
| Refresh token | 7 days | HttpOnly, Secure, SameSite=Strict cookie | Rotated on every use |

Refresh token rotation: when a client uses a refresh token, a new refresh token is issued and the old one is immediately invalidated. If a stolen refresh token is used, the legitimate user's next refresh call will fail (the old token is already invalidated), alerting them that their session is compromised.

**Emergency token revocation:**

For immediate revocation (compromised account), Pravah maintains a Redis blocklist keyed by `jti` (JWT ID). On every token validation:

```java
if (redisBlocklist.exists("blocklist:" + claims.getId())) {
    throw new InvalidTokenException("Token has been revoked");
}
```

This adds ~1ms to every request (Redis round-trip). Entries expire from the blocklist after the token's natural `exp` time — no manual cleanup required.

**API Keys for programmatic access:**

```
Format:    prv_1_<random-32-bytes-hex>
Example:   prv_1_a3b4c5d6e7f8901234567890abcdef12

Storage:   SHA-256 hash stored in the database.
           Raw key returned exactly once on creation.
           Pravah cannot recover the raw key.

Prefix:    prv_1_ enables GitHub secret scanning to
           automatically detect and alert on leaked keys.

Validation: hash the incoming key → compare to stored hash
```

**Key rotation (JWKS):**

The Auth Service exposes a JWKS endpoint (`/.well-known/jwks.json`). When a new signing key is generated, the new key is added to JWKS _before_ the old key is retired. During the overlap period, both keys are valid. Services fetch JWKS periodically (every 5 minutes) and cache it. Old tokens signed with the previous key remain valid until they naturally expire (15 minutes max).

```json
{
  "keys": [
    { "kid": "pravah-2024-01-key-1", "kty": "RSA", "n": "...", "e": "AQAB" },
    { "kid": "pravah-2024-02-key-1", "kty": "RSA", "n": "...", "e": "AQAB" }
  ]
}
```

**Spring Security configuration:**

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtAuthenticationConverter(pravahJwtConverter())
                .decoder(jwtDecoder())
            )
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**").permitAll()
            .anyRequest().authenticated()
        )
        .build();
}
```

---

## Consequences

### Positive

- **Stateless verification**: every service instance validates tokens independently using the public key. No session store lookup on the hot path. Horizontal scaling adds no authentication overhead.
- **Tenant context embedded**: `tenant_id` in the JWT payload means the service has tenant context from the first line of request handling — no additional database lookup required.
- **Separation of signing and verification**: RS256 ensures only the Auth Service can issue tokens. A compromised application service cannot forge tokens.
- **Key rotation without downtime**: JWKS overlap window means rotating signing keys is a zero-downtime operation.
- **API key security**: SHA-256 hashing means a database breach does not expose usable API keys. The `prv_1_` prefix enables automated secret scanning in git repositories.
- **Audit trail**: every token has a `jti` (JWT ID). Every API key has a UUID. API access logs correlate requests to specific tokens/keys, enabling per-request audit.

### Negative

- **15-minute token lifetime means frequent refresh**: clients must implement token refresh logic. Mobile and SPA clients must handle refresh transparently to the user.
- **Blocklist adds a hot-path Redis dependency**: the Redis blocklist check adds ~1ms to every authenticated request. If Redis is unavailable, the options are: fail open (skip the check, security risk) or fail closed (reject all requests, availability risk). Pravah chooses fail closed with a circuit breaker that alerts immediately.
- **JWT payload is readable (not encrypted)**: the JWT payload is base64-encoded, not encrypted. It must not contain sensitive information (secrets, PII). `tenant_id` and `roles` are safe to include; user email or internal identifiers should be avoided.
- **Refresh token management complexity**: rotating refresh tokens must handle concurrent requests (two requests using the same refresh token simultaneously). Race conditions require database-level locks or atomic compare-and-swap.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| JWT signing private key leaked | Key is stored in Vault (ADR-007); rotation is immediate if compromise is detected; old tokens expire within 15 minutes |
| Refresh token stolen from cookie | HttpOnly + Secure + SameSite=Strict cookie mitigates XSS and CSRF; token rotation detects theft when the legitimate user's next refresh fails |
| JWKS endpoint unavailable during key rotation | Services cache JWKS for 5 minutes; Auth Service JWKS endpoint is highly available (StatefulSet, multiple replicas); old key remains valid during overlap |
| API key prefix changes break secret scanning | API key format versioned (the `1` in `prv_1_`); new format versions are added to secret scanning rules before old format is retired |

---

## Alternatives Considered

### Session-Based Authentication (Server-Side Sessions)

Store session state in a central Redis store. Every request looks up the session ID from a cookie.

Rejected because:
- Every request requires a Redis round-trip to fetch session state. This adds latency and creates a hard dependency on Redis availability on the request hot path.
- Horizontal scaling of API services is simple; horizontal scaling of a centralized session store is not trivial.
- Redis is still used for the JWT blocklist (emergency revocation), but this is an infrequent operation rather than every-request overhead.

### Opaque Tokens

Issue random opaque tokens that are looked up in a database on every request.

Rejected for the same reason as session-based authentication — database round-trip on every request. JWTs allow stateless validation using only the public key.

### HS256 (Symmetric Signing)

Simpler to implement — a single shared secret for signing and verification.

Rejected because:
- Every service that verifies tokens must hold the signing secret. Distributing secrets to every service is exactly what Vault is designed to avoid.
- A compromised service can forge tokens.
- Key rotation requires updating the secret in every service simultaneously, introducing a coordination problem.
