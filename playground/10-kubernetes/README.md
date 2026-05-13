# Playground 10 — Kubernetes + KEDA Autoscaling

**ADRs**: [ADR-010 Kubernetes + Helm + Argo CD](../../docs/adr/ADR-010-kubernetes-helm-argocd.md), [ADR-015 KEDA Event-Driven Autoscaling](../../docs/adr/ADR-015-keda-event-driven-autoscaling.md)  
**Concepts**: Kubernetes deployments, KEDA ScaledObject, Kafka consumer lag scaling, HPA

---

## Why KEDA?

Standard Kubernetes HPA scales based on CPU/memory — **lagging indicators**. When jobs pile up in Kafka, CPU is still low (nothing is processing yet). By the time CPU goes high, you've already lost time.

**KEDA scales on the leading indicator**: Kafka consumer lag.

```
Without KEDA (CPU-based HPA):
t=0:00  Queue: 10,000 jobs | CPU: 20% | Pods: 3
t=2:00  Queue: 8,000 jobs  | CPU: 85% | HPA triggers
t=3:30  Queue: 6,000 jobs  | CPU: 80% | New pods ready
t=7:00  Queue: 0 jobs      | Drained

With KEDA (lag-based scaling):
t=0:00  Queue: 10,000 jobs | Lag: 10,000 | KEDA triggers immediately
t=0:00  KEDA calculates: 10,000 / 100 per pod = 100 pods (capped at max)
t=1:30  New pods ready, consuming
t=3:00  Queue: 0 jobs      | Drained
```

---

## Prerequisites

- Docker Desktop running
- Kind cluster created: `kind create cluster --name pravah-playground`
- KEDA installed in cluster

If you followed the setup earlier, this is already done.

---

## Quick Start

```bash
cd /Users/abhishek/Dev/Pravah/playground/10-kubernetes

# Make scripts executable
chmod +x scripts/*.sh

# Run full setup (build app, deploy Kafka, deploy consumer, configure KEDA)
./scripts/setup.sh

# Watch pods
kubectl get pods -n pravah-demo -w
```

---

## Trigger Scaling

```bash
# Produce 100 messages to Kafka (triggers scale-up)
./scripts/trigger-scale.sh

# In another terminal, watch the pods scale:
kubectl get pods -n pravah-demo -w

# Check the HPA that KEDA created:
kubectl get hpa -n pravah-demo
```

---

## What's in Here

```
10-kubernetes/
├── manifests/
│   ├── namespace.yaml           # pravah-demo namespace
│   ├── kafka.yaml               # Single-broker Kafka (KRaft mode)
│   ├── consumer-deployment.yaml # Job consumer deployment
│   ├── keda-scaledobject.yaml   # KEDA scaling config ← THE KEY FILE
│   └── producer-job.yaml        # One-shot job to produce test messages
├── app/
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/main/java/.../JobConsumer.java
└── scripts/
    ├── setup.sh                 # Full setup script
    ├── trigger-scale.sh         # Trigger scaling by producing messages
    └── cleanup.sh               # Delete resources
```

---

## Understanding the ScaledObject

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: job-consumer-scaler
spec:
  scaleTargetRef:
    name: job-consumer           # Which Deployment to scale
  pollingInterval: 15            # Check lag every 15 seconds
  cooldownPeriod: 30             # Wait 30s before scaling down
  minReplicaCount: 1             # Never go below 1 pod
  maxReplicaCount: 10            # Never exceed 10 pods
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka.pravah-demo.svc.cluster.local:9092
        consumerGroup: job-consumer-group
        topic: pravah.jobs
        lagThreshold: "10"       # Target: max 10 messages lag per pod
```

**How KEDA calculates replicas:**

```
current_lag = 85 messages
lag_threshold_per_pod = 10
target_replicas = ceil(85 / 10) = 9 pods
```

---

## Tasks

### 1. Watch the scaling in action

```bash
# Terminal 1: Watch pods
kubectl get pods -n pravah-demo -w

# Terminal 2: Produce messages
./scripts/trigger-scale.sh
```

Observe:
- Pods scale up as messages arrive
- After cooldownPeriod, pods scale back down

### 2. Modify lagThreshold

Edit `manifests/keda-scaledobject.yaml`:
```yaml
lagThreshold: "5"   # More aggressive scaling
```

Apply and re-trigger:
```bash
kubectl apply -f manifests/keda-scaledobject.yaml
./scripts/trigger-scale.sh
```

### 3. Check the HPA KEDA created

```bash
kubectl get hpa -n pravah-demo
kubectl describe hpa keda-hpa-job-consumer-scaler -n pravah-demo
```

KEDA creates a standard Kubernetes HPA under the hood — it's just powered by custom metrics from Kafka.

### 4. Explore scale-to-zero (advanced)

Change `minReplicaCount: 0` in the ScaledObject. When there are no messages, the deployment scales to zero. When a message arrives, KEDA wakes it up.

Note: Scale-to-zero has cold start latency (~30-60s). Not suitable for latency-sensitive services.

---

## How Pravah Uses This

| Service | Scaling Trigger | Min | Max |
|---------|----------------|-----|-----|
| Execution Service | Kafka lag on `pravah.job.assigned` | 3 | 50 |
| Scheduler Service | CPU + memory (HPA) | 2 | 10 |
| Runner (edge) | Not auto-scaled (external) | - | - |
| Audit Export Service | Kafka lag (scale-to-zero OK) | 0 | 5 |

---

## Cleanup

```bash
# Delete namespace and resources
./scripts/cleanup.sh

# Delete the Kind cluster entirely
kind delete cluster --name pravah-playground
```

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Pods not scaling | Check `kubectl get scaledobject -n pravah-demo` for errors |
| KEDA not detecting lag | Verify Kafka is running: `kubectl logs deployment/kafka -n pravah-demo` |
| Image pull error | Run `kind load docker-image pravah-job-consumer:latest --name pravah-playground` |
| Consumer group not found | Ensure consumer has started at least once to register the group |

---

## Further Reading

- [KEDA Documentation](https://keda.sh/docs/)
- [KEDA Kafka Scaler](https://keda.sh/docs/2.16/scalers/apache-kafka/)
- [Kubernetes HPA](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)
- ADR-010 and ADR-015 in this repo
