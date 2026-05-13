# Playground 11: OpenTelemetry Distributed Tracing

**ADR**: [ADR-014 Tail-Based Sampling](../../docs/adr/ADR-014-tail-based-sampling.md)

## What You'll Learn

1. **Distributed Tracing Fundamentals**: How traces connect spans across services
2. **W3C Trace Context**: The `traceparent` header that propagates trace IDs
3. **Auto-Instrumentation**: Zero-code tracing for HTTP clients/servers
4. **Manual Spans**: Creating custom spans for business logic
5. **Span Attributes**: Adding context (user_id, tenant_id, operation details)
6. **Jaeger UI**: Visualizing traces and understanding service dependencies

---

## Architecture

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Client    │ ──────► │  Service A  │ ──────► │  Service B  │
│ (curl/http) │         │  (port 8080)│         │  (port 8081)│
└─────────────┘         └─────────────┘         └─────────────┘
                              │                       │
                              │   OTLP/gRPC           │
                              ▼                       ▼
                        ┌─────────────────────────────────────┐
                        │            Jaeger                    │
                        │   http://localhost:16686 (UI)       │
                        │   Receives spans from both services │
                        └─────────────────────────────────────┘
```

---

## Quick Start

### 1. Build Both Services

```bash
# From playground/11-opentelemetry
cd service-a && ./gradlew bootJar && cd ..
cd service-b && ./gradlew bootJar && cd ..
```

### 2. Start Everything

```bash
docker compose up --build
```

### 3. Generate Traces

```bash
# Simple call (1 service, 1 span)
curl http://localhost:8080/hello

# Cross-service call (2 services, trace propagates)
curl http://localhost:8080/call-b

# Manual span + cross-service (multiple spans)
curl http://localhost:8080/process/order-123

# Slow operation (for sampling demo)
curl http://localhost:8080/call-b & curl http://localhost:8081/slow

# Error trace
curl http://localhost:8080/error
```

### 4. View in Jaeger

Open http://localhost:16686

1. Select **service-a** from the Service dropdown
2. Click **Find Traces**
3. Click any trace to see the span waterfall

---

## How Distributed Tracing Works

### The Problem

In a microservices architecture, a single user request touches multiple services:

```
User → API Gateway → Order Service → Inventory Service → Payment Service
```

Without tracing, if something fails or is slow, you have no way to:
- Know which service caused the delay
- Correlate logs across services
- Understand the request flow

### The Solution: Trace Context Propagation

Each request gets a unique **trace_id**. Every service:
1. Extracts the trace_id from incoming headers
2. Creates spans linked to that trace_id
3. Propagates the trace_id in outgoing requests

```
W3C Trace Context Header:
traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
             │  │                                │                  │
             │  │                                │                  └── flags (sampled)
             │  │                                └── parent_span_id
             │  └── trace_id (128-bit)
             └── version
```

### OpenTelemetry Auto-Instrumentation

The `opentelemetry-spring-boot-starter` automatically:
- Creates server spans for incoming HTTP requests
- Creates client spans for outgoing HTTP calls (RestTemplate, WebClient)
- Injects `traceparent` header into outgoing requests
- Extracts `traceparent` from incoming requests

You write **zero tracing code** for basic HTTP tracing.

---

## Code Walkthrough

### Auto-Instrumented Span (No Code Required)

```java
@GetMapping("/hello")
public String hello() {
    return "Hello!";
}
```

The starter automatically creates a span like:
```
Span Name: GET /hello
Attributes:
  http.method: GET
  http.url: /hello
  http.status_code: 200
```

### Manual Span Creation

```java
@GetMapping("/process/{id}")
public String process(@PathVariable String id) {
    // Create a custom span for business logic
    Span span = tracer.spanBuilder("process-business-logic")
            .setAttribute("request.id", id)
            .startSpan();

    try (Scope scope = span.makeCurrent()) {
        // Your code here - any outgoing calls will be children of this span
        String result = doWork();
        span.setAttribute("result.status", "success");
        return result;
    } catch (Exception e) {
        span.recordException(e);  // Records the exception in the span
        throw e;
    } finally {
        span.end();  // Always end the span
    }
}
```

### Span Attributes for Pravah

In production Pravah, you'd add attributes like:

```java
span.setAttribute("pravah.tenant_id", tenantId);
span.setAttribute("pravah.pipeline_id", pipelineId);
span.setAttribute("pravah.job_id", jobId);
span.setAttribute("pravah.trace.type", "job_execution");  // For sampling rules
```

---

## Tasks

### Task 1: Trace a Multi-Service Request

1. Run `curl http://localhost:8080/call-b`
2. Open Jaeger UI and find the trace
3. Verify you see spans from **both** service-a and service-b
4. Note the `traceparent` header in service-b's span

### Task 2: Add a Custom Span

Add a span around the `simulateWork` call in `ServiceBController`:

```java
private void simulateWork(int millis) {
    Span span = tracer.spanBuilder("simulate-work")
            .setAttribute("duration_ms", millis)
            .startSpan();
    try (Scope scope = span.makeCurrent()) {
        Thread.sleep(millis);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        span.end();
    }
}
```

Rebuild and verify the span appears in Jaeger.

### Task 3: Add Tenant Context

Modify `ServiceAController` to extract a `X-Tenant-ID` header and add it as a span attribute:

```java
@GetMapping("/process/{id}")
public String processWithManualSpan(
        @PathVariable String id,
        @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId) {
    
    Span span = tracer.spanBuilder("process-business-logic")
            .setAttribute("request.id", id)
            .setAttribute("pravah.tenant_id", tenantId != null ? tenantId : "unknown")
            .startSpan();
    // ...
}
```

Test: `curl -H "X-Tenant-ID: acme-corp" http://localhost:8080/process/123`

### Task 4: Understand Error Traces

1. Run `curl http://localhost:8080/error`
2. Find the trace in Jaeger
3. Notice the span has `status = ERROR` and the exception is recorded
4. Per ADR-014, these traces are **always** sampled (100%)

### Task 5: Understand Slow Trace Sampling

1. Run `curl http://localhost:8081/slow`
2. This creates a 3-second trace
3. Per ADR-014, traces > 2 seconds are **always** sampled (100%)

---

## Pravah Usage

In Pravah, OpenTelemetry traces every:

| Operation | Trace Type | Sampling (ADR-014) |
|-----------|------------|-------------------|
| API request (fast, success) | `api_request` | 1% |
| API request (slow > 500ms) | `api_request` | 50% |
| API request (error) | `api_request` | 100% |
| Job execution | `job_execution` | 10% |
| Runner heartbeat | `heartbeat` | 0.1% |

Key span attributes in Pravah:

```yaml
pravah.tenant_id: "acme-corp"
pravah.pipeline_id: "p-1234"
pravah.job_id: "j-5678"
pravah.step_id: "s-9012"
pravah.trace.type: "job_execution"
```

---

## Troubleshooting

### No Traces in Jaeger

1. Check services are running: `docker compose ps`
2. Check Jaeger is receiving data: http://localhost:16686
3. Check service logs for OTLP export errors: `docker compose logs service-a`

### Traces Not Connected

If Service A and Service B traces appear separately:
1. Verify `RestTemplate` is being used (not a manually created HttpClient)
2. Check the `RestTemplate` is a Spring bean (auto-instrumented)
3. Look for `traceparent` in the request headers

### Port Conflicts

Change ports in `docker-compose.yml` if 8080, 8081, or 16686 are in use.

---

## Cleanup

```bash
docker compose down
```
