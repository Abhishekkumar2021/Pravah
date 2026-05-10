# ADR-008: mTLS for Service-to-Service Communication

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah runs multiple services in Kubernetes. By default, all pods in a Kubernetes cluster can communicate with all other pods across any namespace. There is no network-level authentication between services. If an attacker compromises one pod (e.g., via a supply chain vulnerability in a dependency), they can make arbitrary requests to any other service in the cluster — impersonating the legitimate caller, bypassing authorization checks, or reading internal APIs that are not exposed externally.

Traditional perimeter-based security ("trust everything inside the cluster, distrust everything outside") is insufficient for a multi-tenant platform. The risk model for Pravah includes:

- A compromised runner binary connecting to the Runner Service
- A compromised dependency in one service making internal API calls to another
- A misconfigured pod reaching services it should not have access to

The principle of Zero Trust demands: **never trust, always verify** — regardless of whether the caller is internal or external to the network. Every service-to-service call must authenticate the caller's identity.

---

## Decision

Mutual TLS (mTLS) is the authentication mechanism for all service-to-service communication in Pravah, including runner ↔ cloud communication.

**What mTLS provides:**

In standard TLS, only the server presents a certificate. The client verifies the server's identity but the server does not verify the client's identity.

In mTLS, both sides present certificates:
- The client verifies the server's certificate (standard TLS server authentication)
- The server verifies the client's certificate (mutual authentication)

A request is only processed if both certificates are valid and signed by a trusted Certificate Authority.

**Certificate infrastructure:**

`cert-manager` runs in the Kubernetes cluster and manages the full certificate lifecycle:

```
Root CA (offline, stored in Vault)
    │
    └── Intermediate CA (cert-manager, stored in Kubernetes Secret)
             │
             ├── execution-service.pravah.svc.cluster.local  (90-day cert)
             ├── runner-service.pravah.svc.cluster.local      (90-day cert)
             ├── pipeline-service.pravah.svc.cluster.local    (90-day cert)
             └── scheduler-service.pravah.svc.cluster.local   (90-day cert)
```

Each service gets a certificate with:
- **Subject**: `CN=execution-service, O=pravah`
- **SAN (Subject Alternative Names)**: `DNS:execution-service.pravah.svc.cluster.local`
- **SPIFFE URI**: `spiffe://pravah.cluster.local/ns/pravah/sa/execution-service`
- **Validity**: 90 days (auto-renewed by `cert-manager` before expiry)

The SPIFFE URI encodes the service's identity in a standard, verifiable format. This is the identity used for authorization decisions.

**Runner certificates:**

Runners in customer infrastructure receive certificates from the Pravah PKI (via Vault's PKI engine) during registration:

- **Subject**: `CN=runner-{uuid}, O=tenant-megacorp`
- **SAN**: `spiffe://pravah.cluster.local/runner/{runner-id}`
- **Validity**: 7 days (runner must re-register to renew)

The `RunnerIdentityInterceptor` in the Runner Service extracts `runner_id` and `tenant_id` from the client certificate's Subject and SAN — not from the message payload. A compromised runner binary cannot impersonate a different runner by putting a different `runner_id` in its gRPC message.

**Internal service mTLS configuration (Spring Boot):**

```java
@Bean
public NettyReactiveWebServerFactory webServerFactory() {
    SslContextBuilder ssl = SslContextBuilder
        .forServer(new File("/vault/secrets/tls.crt"),
                   new File("/vault/secrets/tls.key"))
        .clientAuth(ClientAuth.REQUIRE)
        .trustManager(new File("/vault/secrets/ca.crt"));

    // ... configure Netty with ssl context
}
```

**NetworkPolicy for defense in depth:**

mTLS provides authentication; NetworkPolicy provides network-level access control. Both are applied:

```yaml
# Only the API Gateway can reach the Execution Service on port 8080
# mTLS further verifies that the caller IS the API Gateway, not just from the right IP
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: execution-service-ingress
spec:
  podSelector:
    matchLabels:
      app: execution-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - port: 8080
```

---

## Consequences

### Positive

- **Cryptographically verified service identity**: each service's identity is bound to a certificate signed by Pravah's CA. There is no way to forge this identity without access to the CA's private key (which lives offline and in Vault).
- **Encrypted in transit within the cluster**: all inter-service traffic is encrypted end-to-end, even within the Kubernetes cluster. An attacker with network access (e.g., via a compromised node) cannot read inter-service traffic.
- **Runner authentication without shared secrets**: the runner authenticates using its certificate, not a shared API key or token. Certificates can be individually revoked via CRL or OCSP without affecting other runners.
- **Automatic certificate rotation**: `cert-manager` renews certificates before expiry. 90-day certificates mean the exposure window for a compromised certificate is bounded.
- **SPIFFE standard**: using SPIFFE URIs in certificates provides a standard, workload-identity-compatible identity format that integrates with future service mesh adoption.

### Negative

- **TLS handshake overhead**: every new connection requires a TLS handshake (~1ms for ECDHE). For high-frequency, short-lived connections, this adds latency. Mitigated by HTTP/2 connection reuse (multiple requests over a single TLS connection) and connection pooling.
- **Certificate management complexity**: `cert-manager`, the intermediate CA in Kubernetes, and the root CA in Vault must all be operated correctly. A misconfigured certificate or an expired CA causes service authentication failures — which are hard to debug under pressure.
- **Development environment friction**: local development requires either mTLS disabled (by profile) or a local `cert-manager` equivalent. Spring Boot profiles (`dev` profile disables mTLS; `prod` profile requires it) manage this.
- **Certificate revocation is not instant**: revocation via CRL requires that services periodically check the CRL. Between CRL update intervals, a revoked certificate may still be accepted. OCSP stapling provides faster revocation but adds complexity.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| cert-manager fails to renew before expiry | Alert when cert is within 14 days of expiry; cert-manager has a 30-day renewal buffer by default for 90-day certs |
| Root CA private key compromise | Root CA is offline (HSM or Vault with strict access); intermediate CA is used for all issuance; compromising only the intermediate CA limits blast radius |
| mTLS misconfiguration allows unverified callers | Integration tests in staging enforce mTLS by attempting calls from pods without valid certs and asserting rejection |
| Service mesh adoption requires migration | SPIFFE URI format is compatible with Istio and Linkerd; migrating to a service mesh for mTLS does not require changing certificate contents |

---

## Alternatives Considered

### Network Policies Only

Rely on Kubernetes NetworkPolicy to control which pods can communicate, without mTLS.

Rejected because:
- NetworkPolicy is an IP-level control. It controls which source IPs can reach which destination IPs. If a pod is compromised, any process within that pod can make requests from that IP.
- NetworkPolicy does not provide cryptographic identity verification. A compromised pod in the "allowed" IP range can impersonate any service.
- NetworkPolicy provides no encryption. Traffic within the cluster is plaintext.

### Service Mesh (Istio / Linkerd)

Install a service mesh that transparently handles mTLS between all pods via sidecar proxies.

Considered seriously. Deferred rather than rejected:
- A service mesh would handle mTLS transparently without changes to application code. This is the long-term direction.
- However, service mesh installation at early scale (before traffic justifies it) adds significant operational overhead: sidecar injection on every pod, the control plane (Istio's `istiod` or Linkerd's control plane), and additional latency from the sidecar proxy hop.
- The `cert-manager` + application-level mTLS approach provides the same security guarantee with less infrastructure complexity. When Pravah reaches the scale where a service mesh's observability and traffic management features (canary routing, retries, circuit breaking at the mesh level) become valuable, migration is straightforward because SPIFFE certificates are compatible.

### JWT Tokens for Service Identity

Each service gets a long-lived JWT that it presents in an `Authorization: Bearer` header. Other services validate the JWT.

Rejected because:
- A JWT is a credential that can be stolen from environment variables, log output, or memory dumps. A certificate's private key is stored as a file and is significantly harder to exfiltrate.
- JWT validation requires the verifying service to trust the JWT issuer. This creates a dependency on the JWT signing service being available.
- Certificate-based identity (especially via SPIFFE) is the industry standard for workload identity in Kubernetes environments.
