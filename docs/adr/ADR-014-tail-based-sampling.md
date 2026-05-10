# ADR-014: Tail-Based Sampling for Distributed Tracing

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah uses OpenTelemetry for distributed tracing. Every request produces a trace composed of spans from each service it touches. A complete trace through the critical path (API Gateway → Execution Service → Runner Service) produces approximately 15-30 spans.

At production scale, Pravah processes millions of API requests and hundreds of thousands of job events per day. If every trace is stored, the volume of trace data becomes:

```
1,000,000 requests/day
× 20 spans/trace average
× 2KB/span
= 40 GB/day of raw trace data
```

At this volume, trace storage cost is significant and Jaeger's write throughput becomes a bottleneck. Sampling is necessary. The question is: how do we decide which traces to keep?

**Head-based sampling** makes the sampling decision at the start of a trace (at the entry point) before any spans are collected. The decision is propagated to all downstream services. This is simple to implement but has a critical flaw: the decision is made before anything is known about the trace outcome. A 1% head-based sampler will drop 99% of error traces just as it drops 99% of healthy traces. Error traces are exactly the traces that must be kept.

**Tail-based sampling** makes the sampling decision after the trace is complete — after all spans have been collected and the outcome is known. This allows intelligent decisions: keep all error traces, keep all slow traces, keep a small percentage of healthy fast traces.

---

## Decision

Tail-based sampling via the OpenTelemetry Collector with the `tailsampling` processor.

**Sampling rules:**

| Condition | Sample Rate | Reason |
|-----------|-------------|--------|
| Any span has `status = ERROR` | 100% | Every error must be preserved for debugging |
| Trace duration > 2 seconds | 100% | Slow traces reveal latency regressions |
| Trace duration > 500ms | 50% | Moderately slow traces — high value, acceptable partial sample |
| Job execution traces | 10% | High volume, operational value in representative sample |
| Healthy API traces (< 200ms) | 1% | Routine traffic; low value to store at high rate |
| Runner heartbeat traces | 0.1% | Very high volume, very low diagnostic value |

**Architecture:**

```
Services (all send 100% of spans)
    │
    ▼
OpenTelemetry Collector (edge — receives all spans)
    │
    │  Buffers spans per trace_id
    │  Waits up to 30 seconds for trace to complete
    │  Evaluates sampling rules against complete trace
    │
    ▼
OpenTelemetry Collector (gateway — if scale requires sharding)
    │
    │  Routes spans by trace_id to the same collector instance
    │  (All spans for a trace must reach the same instance)
    │
    ▼
Jaeger (only receives sampled traces)
    │
    ▼
Elasticsearch (Jaeger backend storage)
```

**Critical design constraint — consistent trace routing:**

Tail-based sampling requires that all spans for a given trace arrive at the same Collector instance. If Span A (from the API Gateway) arrives at Collector-1 and Span B (from the Execution Service) arrives at Collector-2, neither collector can make a sampling decision for the complete trace.

The solution: the Collector gateway routes spans by `trace_id` hash to a specific Collector instance. All spans with the same `trace_id` always reach the same instance. This requires the gateway layer if the Collector is horizontally scaled.

**OpenTelemetry Collector configuration:**

```yaml
processors:
  tail_sampling:
    decision_wait: 30s          # Wait up to 30s for all spans
    num_traces: 50000           # Max traces held in memory
    expected_new_traces_per_sec: 1000
    policies:
      - name: errors-policy
        type: status_code
        status_code: {status_codes: [ERROR]}

      - name: slow-traces-policy
        type: latency
        latency: {threshold_ms: 2000}

      - name: moderate-latency-policy
        type: and
        and:
          and_sub_policy:
            - name: latency-check
              type: latency
              latency: {threshold_ms: 500}
            - name: probabilistic-50
              type: probabilistic
              probabilistic: {sampling_percentage: 50}

      - name: job-traces-policy
        type: and
        and:
          and_sub_policy:
            - name: job-attribute
              type: string_attribute
              string_attribute: {key: "pravah.trace.type", values: ["job_execution"]}
            - name: probabilistic-10
              type: probabilistic
              probabilistic: {sampling_percentage: 10}

      - name: baseline-policy
        type: probabilistic
        probabilistic: {sampling_percentage: 1}
```

**Propagation through Kafka:**

Traces that cross Kafka topic boundaries must propagate the trace context. Producers inject the trace context into Kafka message headers using the W3C `traceparent` format. Consumers extract it and create child spans:

```java
// Producer side
TextMapSetter<ProducerRecord<?, ?>> setter =
    (record, key, value) -> record.headers().add(key, value.getBytes());
propagator.inject(Context.current(), record, setter);

// Consumer side
TextMapGetter<ConsumerRecord<?, ?>> getter =
    (record, key) -> {
        Header header = record.headers().lastHeader(key);
        return header != null ? new String(header.value()) : null;
    };
Context context = propagator.extract(Context.current(), record, getter);
Span span = tracer.spanBuilder("consume." + record.topic())
    .setParent(context)
    .startSpan();
```

---

## Consequences

### Positive

- **100% coverage of errors and slow traces**: every error is captured. Every slow trace is captured. These are the traces with the highest diagnostic value, and no sampling compromise is made for them.
- **Dramatic storage reduction**: a 1% baseline sampling rate for healthy fast traces reduces storage by 99% for the high-volume routine traffic. The overall storage reduction depends on the error rate, but typically tail-based sampling reduces Jaeger storage by 90-95% versus storing everything.
- **Cost proportional to incidents**: storage cost is naturally higher during incidents (more errors, more slow traces) and lower during normal operation. The sampling strategy aligns cost with diagnostic value.
- **No code changes when sampling rules change**: sampling rules live in the Collector configuration. Adjusting rates, adding new policies, or exempting specific operations requires only a Collector configuration update — not a service deployment.

### Negative

- **30-second memory buffer**: the Collector must hold all spans in memory for up to 30 seconds before making a sampling decision. For 1,000 new traces per second with 20 spans per trace at 2KB per span, this is approximately 1.2GB of in-memory span data. The Collector must be sized appropriately.
- **Trace routing complexity**: consistent routing by `trace_id` requires the gateway layer (or a single-instance Collector, which limits scalability). If routing breaks down and spans for the same trace split across instances, the tail sampler cannot make a complete decision.
- **Late-arriving spans may miss the window**: if a span arrives more than 30 seconds after the trace started (e.g., a very slow asynchronous operation), the Collector may have already made a sampling decision for that trace. Late spans are either buffered (with increased memory pressure) or dropped.
- **Decision_wait adds latency to trace export**: traces are not exported to Jaeger until the sampling decision is made (up to 30 seconds after the trace completes). For debugging purposes, there is a 30-second lag between when an error occurs and when it appears in Jaeger.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Collector OOM from span buffer | Memory limits and `num_traces` cap in Collector config; Kubernetes memory limit on Collector pod; alert on Collector memory > 80% |
| `trace_id` routing hash collision causing split traces | Consistent hashing by `trace_id` on the gateway; monitored by incomplete trace rate metric |
| Kafka trace propagation missing on some paths | OpenTelemetry instrumentation test suite verifies `traceparent` headers are present on all Kafka produce/consume paths |
| 30s window misses fast errors | Errors with `status = ERROR` trigger immediate export policy (policy evaluated on first span with error status, not waiting for full window) |

---

## Alternatives Considered

### Head-Based Sampling at 1%

Apply a 1% random sample at the first service (API Gateway). Propagate the decision. Store only sampled traces.

Rejected because:
- 99% of error traces are discarded. When debugging a production incident, the traces that would help are gone.
- The sampling rate must be high to ensure error coverage, which defeats the cost-reduction purpose.
- Head-based sampling is appropriate when all traces have similar diagnostic value (e.g., performance benchmarking). It is not appropriate when error traces are categorically more valuable than healthy traces.

### Store All Traces

Accept the storage cost and store 100% of traces.

Considered for early-stage Pravah (low traffic). Rejected as a long-term strategy because:
- At production scale, the storage cost is significant (40+ GB/day).
- Jaeger query performance degrades with very large trace volumes. Finding a specific trace in a sea of traces is slower.
- Tail-based sampling does not reduce observability — it increases it by ensuring the most valuable traces (errors, slow requests) are always kept.

### Probabilistic Sampling Everywhere

Apply a consistent 5% probabilistic sample. Easy to reason about.

Rejected for the same reason as head-based sampling: probabilistic sampling treats all traces equally. A 5% sample on error traces during a high-error-rate incident means 95% of error traces are lost during the most critical debugging window.
