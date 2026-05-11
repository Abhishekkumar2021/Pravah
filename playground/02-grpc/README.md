# Playground 02 — gRPC (bidirectional streaming + mTLS path)

Hands-on exercise aligned with **[ADR-005: gRPC runner communication](../../docs/adr/ADR-005-grpc-runner-communication.md)** and **[ADR-008: mTLS service-to-service](../../docs/adr/ADR-008-mtls-service-to-service.md)**.

You prove that:

1. A **single long-lived RPC** can carry traffic **both ways at once** (runner-initiated connection, control plane pushes assignments while runner pushes logs/heartbeats — same *idea* as production).
2. You can **inspect** services with **reflection + grpcurl** (like debugging internal APIs).
3. You understand where **mTLS** plugs in (TLS handshake + client cert authentication — staged as an advanced task below).

There is **no extra Docker Compose** here: gRPC is process-to-process. Production Pravah adds Kafka, Vault PKI, etc.; this playground stays minimal.

---

## Prerequisites

- **Java 21**
- **Gradle wrapper** in this directory (`./gradlew`)
- Optional: **[grpcurl](https://github.com/fullstorydev/grpcurl)** for manual calls against a running server

---

## Project layout

| Path | Purpose |
|------|---------|
| `src/main/proto/runner_playground.proto` | Service + messages (subset of the ADR-005 shape) |
| `RunnerPlaygroundEndpoint.java` | `@GrpcService` — server-side `Connect` bidi stream |
| `DemoRunnerClient.java` | Optional runner simulator (off by default) |
| `RunnerPlaygroundInProcessTest.java` | Fast **in-process** test (no TCP) |

---

## Quick start

```bash
cd playground/02-grpc
./gradlew test          # should pass — in-process + Spring context smoke test
./gradlew bootRun       # gRPC listens on port 9090 (plaintext)
```

With the server running:

```bash
# Requires grpcurl; reflection is enabled in application.yml
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9090 describe pravah.playground.grpc.v1.RunnerPlayground
```

`grpcurl` is weak for **interactive bidi** streaming; use a small Java/Kotlin client or enable **`playground.runner.demo-on-startup`** (see below) to see traffic in logs.

---

## Tasks (recommended order)

### Task 1 — Read the contract

1. Open `runner_playground.proto`. Identify:
   - The **service** and the single **`Connect`** RPC.
   - **Who sends first** in Pravah’s model (runner initiates outbound connection — see ADR-005).
2. Compare mentally with the fuller message tree in ADR-005 (`RunnerMessage` / `ControlPlaneMessage`). This playground uses **simplified** enums to reduce codegen noise while keeping the same **bidi** shape.

### Task 2 — Trace server behaviour

1. Open `RunnerPlaygroundEndpoint.java`.
2. For each incoming `RunnerMessageKind`, list what the server writes back on the **response** stream.
3. Ask yourself: *Why is this a single RPC instead of separate unary calls for “register”, “heartbeat”, and “logs”?* (Hint: one TCP session, firewall-friendly outbound-only runner.)

### Task 3 — Prove it without Spring (in-process test)

1. Run `./gradlew test --tests RunnerPlaygroundInProcessTest`.
2. Read `RunnerPlaygroundInProcessTest`: it builds an **in-process** server and channel — no network sockets. This is how teams keep gRPC tests fast and deterministic.

### Task 4 — Optional demo runner (same JVM)

Set in `application.yml` or on the command line:

```yaml
playground:
  runner:
    demo-on-startup: true
```

Or:

```bash
./gradlew bootRun --args='--playground.runner.demo-on-startup=true'
```

Watch logs: `[demo-runner]` vs `[control-plane]`. Then turn the flag off again so tests stay quiet.

### Task 5 — mTLS (advanced, matches ADR-008)

Production Pravah validates **both** peers with certificates (runner presents cert issued by Vault PKI; server trusts org CA). This playground uses **plaintext** so you focus on streaming first.

To go deeper:

1. Generate a small CA + server cert + client cert (OpenSSL or **mkcert** for local dev only).
2. Replace **plaintext** Netty settings with **server credentials** that `require()` client authentication, and configure the gRPC client channel with **trust store + client key**.
3. Cross-read ADR-008 sections on cert-manager / SPIFFE — map concepts to **what you would configure** in Kubernetes vs **what you did locally**.

*(No scripted mTLS wiring is enforced in CI for this repo — treat this task as self-directed.)*

---

## Verification checklist

| Check | How |
|-------|-----|
| Proto compiles | `./gradlew generateProto` / `./gradlew compileJava` |
| Tests pass | `./gradlew test` |
| Reflection works | `grpcurl -plaintext localhost:9090 list` while `bootRun` |
| You can explain bidi vs unary | Teach someone else in one minute |

---

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| Port in use | Change `grpc.server.port` in `application.yml` |
| `grpcurl` empty list | Server not running, wrong port, or reflection disabled |
| Demo runner connection refused | Server not ready yet; start server before client or increase delay |

---

## What’s next

After this playground, continue to **[03-postgres-advanced](../03-postgres-advanced/)** (partitioning, RLS, PgBouncer) — or deepen **06-outbox** once Kafka (01) and gRPC (02) feel familiar.
