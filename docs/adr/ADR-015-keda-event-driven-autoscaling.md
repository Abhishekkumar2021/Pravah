# ADR-015: KEDA for Event-Driven Autoscaling

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's job processing load is highly variable. Pipeline execution is driven by schedules (cron-based spikes at 9am, end-of-day batch jobs) and event triggers (a code push triggers a CI pipeline; a data import triggers a transformation pipeline). The load profile is:

- **Not uniform**: quiet periods at 2am, traffic spikes at 9am and 6pm
- **Not gradual**: a large batch of pipelines triggered simultaneously creates an immediate spike in job queue depth
- **Not CPU-driven**: the Execution Service might be idle (low CPU) but have 10,000 pending jobs in the Kafka queue

**The limitation of CPU-based autoscaling (standard HPA):**

Kubernetes' Horizontal Pod Autoscaler scales based on CPU and memory by default. For the Execution Service, CPU is a consequence of processing jobs — not the signal that jobs need processing.

```
Timeline:
t=0:00  Kafka queue depth: 10,000 jobs (batch trigger fires)
t=0:00  CPU: 20% (no consumers yet processing)
t=0:15  CPU: 85% (some pods started consuming, queue partially drained)
t=2:00  HPA finally triggers, new pods start
t=3:30  New pods ready, start consuming
t=7:00  Queue drained

vs.

t=0:00  Kafka queue depth: 10,000 jobs
t=0:00  KEDA triggers: lag = 10,000 > threshold of 100 per pod
t=0:00  KEDA creates 100 new pods immediately
t=1:30  New pods ready, start consuming
t=3:00  Queue drained
```

CPU-based autoscaling responds to a lagging indicator (CPU goes up only after pods are already processing). KEDA scales on the leading indicator (queue depth — the actual cause of the load).

---

## Decision

**KEDA (Kubernetes Event Driven Autoscaler)** for scaling the Execution Service and Scheduler Service based on Kafka consumer lag.

KEDA extends Kubernetes with a CRD (`ScaledObject`) that defines how a workload scales based on external event sources. KEDA creates and manages an underlying HPA resource — so it integrates natively with Kubernetes' scheduling and pod lifecycle.

**Execution Service ScaledObject:**

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: execution-service-scaler
  namespace: pravah
spec:
  scaleTargetRef:
    name: execution-service
  pollingInterval: 15          # Check every 15 seconds
  cooldownPeriod: 60           # Wait 60s before scaling down
  minReplicaCount: 3           # Never below 3 (for HA)
  maxReplicaCount: 50          # Hard upper bound
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka.pravah.svc.cluster.local:9092
        consumerGroup: execution-service
        topic: pravah.job.assigned
        lagThreshold: "100"    # target: max 100 messages lag per pod
        offsetResetPolicy: earliest
      authenticationRef:
        name: kafka-trigger-auth
```

**Target replicas calculation:**

```
current_lag = 8,500 messages
lag_threshold_per_pod = 100
target_replicas = ceil(8,500 / 100) = 85 → capped at maxReplicaCount = 50
```

**Multi-trigger scaling (scale on the worst case):**

```yaml
triggers:
  - type: kafka
    metadata:
      topic: pravah.job.assigned
      lagThreshold: "100"

  - type: prometheus
    metadata:
      serverAddress: http://prometheus.monitoring.svc:9090
      metricName: pravah_job_dispatch_p99_latency_seconds
      threshold: "2"          # Scale up if p99 > 2 seconds
      query: |
        histogram_quantile(0.99,
          sum(rate(pravah_job_dispatch_duration_seconds_bucket[5m])) by (le)
        )
```

KEDA scales to the maximum of what any individual trigger recommends. If Kafka lag says 10 pods and Prometheus latency says 15 pods, the Execution Service scales to 15.

**Scale-to-zero for non-critical workloads:**

For non-critical background services (e.g., an audit report generator, a data export service):

```yaml
spec:
  minReplicaCount: 0          # Scale to zero when no work
  maxReplicaCount: 10
  triggers:
    - type: kafka
      metadata:
        lagThreshold: "1"    # Wake up on first message
```

Scale-to-zero is not applied to the Execution Service (minimum 3 replicas for HA) or the Scheduler (leader election requires at least 2). It is used for batch processing services where cold start latency is acceptable.

**Gradual scale-down with cooldownPeriod:**

Scale-up is aggressive (immediate response to lag spike). Scale-down is gradual (60-second cooldown). This prevents thrashing: if a queue drains and refills quickly (bursty traffic), the service does not scale down only to immediately scale back up.

---

## Consequences

### Positive

- **Scales on cause, not effect**: KEDA scales when jobs are waiting (the cause), not when CPU is high (an effect that lags behind by minutes). This dramatically reduces the time from "jobs arrive" to "enough consumers processing them."
- **Native Kubernetes integration**: KEDA manages an HPA internally. Pod scheduling, resource limits, readiness probes, and all standard Kubernetes lifecycle management apply normally. KEDA is an extension, not a replacement.
- **Pluggable triggers**: KEDA supports 60+ scalers out of the box (Kafka, AWS SQS, Azure Service Bus, RabbitMQ, Prometheus, MySQL, Redis, Cron, etc.). Future scaling dimensions (e.g., scale based on Redis queue depth or a custom metric) require only a new trigger block, not new infrastructure.
- **Scale-to-zero capability**: background services that run only when triggered can scale to zero, eliminating idle compute cost. This is not possible with standard HPA (minimum is 1 replica).
- **Visible and auditable**: the `ScaledObject` CR is a Kubernetes resource — version-controlled in Git, visible in Argo CD, auditable in the cluster.

### Negative

- **KEDA is an additional operational dependency**: KEDA requires its own deployment (the `keda-operator` and `keda-metrics-apiserver`). A KEDA failure means custom scaling stops working; the underlying HPAs continue to exist but are no longer updated based on Kafka lag.
- **Cold start latency for scale-to-zero workloads**: scaling from zero to one pod takes ~30-60 seconds (pod scheduling, image pull, JVM startup, readiness probe passing). For latency-sensitive services this is unacceptable. Scale-to-zero is appropriate only for batch-style background services.
- **Kafka scaler requires broker access from the KEDA operator**: KEDA polls Kafka to read consumer group offsets. This means the KEDA operator pod needs network access to the Kafka brokers. The NetworkPolicy must allow this explicitly.
- **lagThreshold tuning is empirical**: the right `lagThreshold` depends on per-pod throughput (how many jobs can one Execution Service instance process per second). This must be measured in load testing and re-tuned as the application changes.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| KEDA operator crash — scaling stops | KEDA operator runs with 2 replicas (HA mode); underlying HPA continues to scale on CPU as fallback |
| Consumer group offset not committed — artificial lag | KEDA measures committed offset lag; if consumers are processing but not committing, the lag stays high. Ensure `commitInterval` is short (1 second) |
| Scale-up to maxReplicaCount insufficient | Alert when replicas reach maxReplicaCount AND lag is still growing; this indicates either maxReplicaCount is too low or per-pod throughput has degraded |
| Thrashing (rapid scale-up then scale-down) | `cooldownPeriod: 60` prevents rapid scale-down; `stabilizationWindowSeconds` in the HPA spec smooths scale-up decisions |
| Node resource exhaustion during scale-up | Kubernetes Cluster Autoscaler provisions new nodes when pod scheduling fails due to insufficient capacity; KEDA and Cluster Autoscaler compose naturally |

---

## Alternatives Considered

### Standard HPA with CPU Metrics

Scale the Execution Service based on CPU utilization.

Rejected as the primary scaling trigger because:
- CPU is a lagging indicator for queue-driven workloads. By the time CPU goes high, jobs have already been waiting in the queue.
- The Execution Service's CPU profile is irregular: fetching from PostgreSQL is I/O-bound (low CPU), while deserializing large payloads and calling Vault are CPU-bound. CPU is not a reliable proxy for the number of jobs being processed.
- CPU-based HPA is kept as a secondary trigger (to catch runaway CPU situations) but is not the primary scaling signal.

### VPA (Vertical Pod Autoscaler) for Right-Sizing

Scale up individual pod resources (CPU cores, memory) rather than the number of pods.

VPA and KEDA serve different purposes. VPA optimizes the size of individual pods (used in Pravah in recommendation mode to right-size resource requests). KEDA optimizes the number of pods. For queue-driven workloads, horizontal scaling (more pods) is more effective than vertical scaling (larger pods) because each pod is an independent consumer — doubling pods doubles throughput, while doubling pod CPU may not.

### Custom Prometheus Adapter (metrics-server extension)

Use Prometheus Adapter to expose custom metrics (Kafka consumer lag) to the Kubernetes HPA API. HPA scales on those custom metrics.

This approach works and is the predecessor to KEDA. Rejected in favor of KEDA because:
- Prometheus Adapter requires a `HorizontalPodAutoscaler` resource with custom metric definitions, plus Prometheus Adapter deployment and configuration. KEDA consolidates this into the `ScaledObject` CRD.
- Prometheus Adapter only supports Prometheus as a metric source. KEDA supports 60+ scalers including Kafka directly (reading consumer group offsets from Kafka's own coordinator, not requiring a Prometheus metric to be defined).
- Scale-to-zero is not possible with standard HPA (minimum 1 replica). KEDA enables scale-to-zero natively.
