# ADR-005: gRPC for Runner ↔ Cloud Communication

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah's runners execute in customer infrastructure — on-premise data centers, private clouds, air-gapped environments. The runner binary must establish a connection to the Pravah cloud control plane to:

1. **Register itself**: announce capacity, labels, and version to the control plane
2. **Receive job assignments**: the control plane pushes jobs to the runner as they become available
3. **Report progress**: the runner streams real-time log lines and metrics back to the control plane
4. **Send heartbeats**: the control plane must know the runner is alive to keep assigning work
5. **Receive control signals**: the control plane may cancel a job mid-execution

This communication channel has several hard requirements:

- **Runner-initiated connection**: the runner lives in customer infrastructure. Firewall rules in customer networks typically allow outbound connections but block inbound connections. The control plane cannot reach into the customer network to connect to the runner. The runner must initiate the connection to the cloud.
- **Long-lived connection**: a runner is not a request/response client. It maintains a persistent connection with the control plane for hours or days. The connection is the runner's identity channel.
- **Bidirectional streaming**: the control plane pushes job assignments to the runner; the runner simultaneously pushes log lines, metrics, and status updates back to the control plane. Both directions flow concurrently over the same connection.
- **Efficiency**: a large deployment might have thousands of runners, each streaming log output. The protocol must be efficient on the wire.
- **Versioning**: the runner binary is distributed to customers. Different customers will run different versions. The protocol must support backward compatibility as both the runner and the control plane evolve.

---

## Decision

gRPC with bidirectional streaming is the protocol for all runner ↔ cloud communication.

**Protocol definition** (`.proto`):

```protobuf
syntax = "proto3";
package pravah.runner.v1;

service RunnerService {
    // Runner calls this once on startup to register and establish the
    // persistent work channel. Server streams job assignments to the runner;
    // runner streams status updates, logs, and heartbeats back.
    rpc Connect(stream RunnerMessage) returns (stream ControlPlaneMessage);
}

message RunnerMessage {
    oneof payload {
        RunnerRegistration registration = 1;
        Heartbeat          heartbeat    = 2;
        JobStatusUpdate    job_status   = 3;
        LogLine            log_line     = 4;
        JobMetrics         job_metrics  = 5;
    }
}

message ControlPlaneMessage {
    oneof payload {
        JobAssignment     job_assignment   = 1;
        JobCancellation   job_cancellation = 2;
        ConfigUpdate      config_update    = 3;
    }
}

message RunnerRegistration {
    string runner_id    = 1;
    string tenant_id    = 2;
    int32  capacity     = 3;  // max concurrent jobs
    repeated string labels = 4; // e.g., ["gpu", "region:us-east-1"]
    string version      = 5;
}

message Heartbeat {
    string runner_id    = 1;
    int32  active_jobs  = 2;
    int64  timestamp_ms = 3;
}
```

**Connection lifecycle:**

```
Runner (Customer Infra)            Control Plane (Cloud)
        │                                    │
        │──── TCP + TLS handshake ──────────▶│
        │──── mTLS: present certificate ────▶│  Runner authenticated by cert
        │                                    │
        │──── Connect() RPC initiated ───────▶│
        │──── RunnerMessage{registration} ───▶│  Runner announces capacity
        │                                    │  Runner registered in runner_db
        │◀─── ControlPlaneMessage{assignment}─│  Job pushed to runner
        │──── RunnerMessage{job_status=START}▶│
        │──── RunnerMessage{log_line} ───────▶│  Log streaming (concurrent)
        │──── RunnerMessage{log_line} ───────▶│
        │──── RunnerMessage{heartbeat} ──────▶│  Every 15 seconds
        │──── RunnerMessage{job_status=DONE}─▶│  Job complete
        │◀─── ControlPlaneMessage{assignment}─│  Next job pushed
        │                 ...                │
        │──── RunnerMessage{heartbeat} ──────▶│  (missed 3 heartbeats)
        │                                    │  Runner marked DISCONNECTED
        │──── connection dropped             │
        │         (reconnect after backoff)  │
```

**mTLS for authentication**: the runner authenticates to the control plane using a client certificate (see ADR-008). The `RunnerIdentityInterceptor` extracts the runner's identity from the certificate rather than from the message payload, preventing a compromised runner from impersonating another runner by spoofing its `runner_id`.

**Reconnection**: the runner implements exponential backoff with jitter on reconnection (1s, 2s, 4s, ... up to 60s max). The control plane maintains runner state externally — when the runner reconnects with the same certificate, it is treated as the same runner resuming work, not a new runner.

---

## Consequences

### Positive

- **Bidirectional streaming is native**: gRPC's `bidirectional streaming` RPC type is exactly the model needed. A single HTTP/2 connection carries job assignments from cloud to runner and log/status messages from runner to cloud simultaneously.
- **Protobuf efficiency**: Protobuf is a compact binary format — significantly smaller on the wire than JSON for high-volume log streaming. For a runner streaming 1000 log lines per minute across thousands of runners, wire efficiency matters.
- **Schema evolution with backward compatibility**: Protobuf's field numbering scheme ensures that adding new optional fields is backward compatible. An older runner (field number 1,2,3) can connect to a newer control plane (field numbers 1,2,3,4,5) — the runner simply ignores unknown fields.
- **Strong typing**: the `.proto` file is the contract between the runner and the control plane. Any breaking change is caught at compile time by the generated code, not at runtime.
- **Language agnostic**: the runner binary may be implemented in Go or Rust for performance. The control plane is Java (Spring Boot). Protobuf generates client and server stubs in both languages from the same `.proto` file, ensuring the contract is identical.
- **HTTP/2 multiplexing**: multiple concurrent RPC streams (log streaming, heartbeats, job status) share a single TCP connection via HTTP/2 multiplexing, without head-of-line blocking.

### Negative

- **Debugging complexity**: binary Protobuf payloads are not human-readable. Debugging requires either a Protobuf-aware tool (e.g., `grpcurl`, Postman's gRPC mode) or explicitly logging decoded messages.
- **Not browser-native**: gRPC over HTTP/2 is not directly usable from a browser. The runner is a binary, not a browser client, so this does not affect Pravah's use case. If a browser-based runner is ever needed, gRPC-Web or a WebSocket bridge would be required.
- **Load balancing complexity**: standard HTTP/1.1 load balancers (e.g., nginx in round-robin mode) do not understand gRPC. gRPC connections are long-lived HTTP/2 connections; a simple round-robin by connection would send all traffic to one backend. Kubernetes-level load balancing (L7 via Istio, or client-side load balancing) is required for the control plane.
- **Connection state management**: the control plane must track which runner is connected to which pod. If the control plane pod handling a runner's connection restarts, the runner reconnects — possibly to a different pod. The new pod must reconstruct the runner's state from the database, not from in-memory state.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Runner behind NAT drops idle connections | Heartbeat every 15 seconds keeps the connection alive through NAT timeout |
| Control plane pod restart orphans runner assignment | Runner reconnects; assignment is re-read from `runner_db`; in-flight job state is preserved in `execution_db` |
| gRPC connection terminates mid-job-assignment | Runner acknowledges each assignment; unacknowledged assignments are re-queued after timeout |
| Control plane overwhelmed by log volume | Log lines are buffered in-runner and sent in batches; control plane applies backpressure via gRPC flow control |

---

## Alternatives Considered

### Long-polling over HTTPS

The runner periodically calls a REST endpoint to check for new job assignments (e.g., `GET /api/v1/runners/{id}/pending-jobs`). Job results are pushed via separate REST calls.

Rejected because:
- Bidirectional streaming cannot be simulated cleanly with polling. Each poll is a new connection with TCP handshake and TLS overhead.
- Poll interval creates a latency floor for job dispatch. A 5-second poll means up to 5 seconds of idle time before a job starts.
- Streaming log output from runner to cloud via polling would require the runner to buffer log lines and batch them in POST requests — adding buffering complexity and data loss risk if the runner crashes with a full buffer.
- Long-polling keeps connections open waiting for data, but does not naturally handle the bidirectional case.

### WebSocket

WebSocket provides bidirectional streaming over HTTP/1.1 and is browser-compatible.

Rejected because:
- WebSocket has no built-in schema definition, versioning, or type safety. The message format would be ad-hoc JSON, with no compile-time validation.
- WebSocket does not provide HTTP/2 multiplexing. Each WebSocket connection is a single stream; multiple concurrent flows (heartbeats, logs, assignments) would require multiplexing implemented in application code.
- Protobuf's efficiency advantage over JSON is lost.

### MQTT

A lightweight pub/sub protocol designed for IoT devices with unreliable connections.

Rejected because:
- MQTT is a broker-based pub/sub model. The runner would subscribe to a topic rather than maintaining a direct connection to the control plane. This complicates runner authentication (the broker must enforce which runners can subscribe to which topics) and makes job assignment routing (which runner gets this specific job) more complex.
- MQTT brokers are another operational dependency. Kafka already serves as the async message backbone for internal services; adding an MQTT broker adds operational complexity without clear benefit.
- gRPC's connection model maps more naturally to the "runner establishes identity channel with control plane" concept.
