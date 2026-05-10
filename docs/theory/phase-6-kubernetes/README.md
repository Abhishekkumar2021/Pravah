# Phase 6 — Kubernetes, Helm & Production Infrastructure

> **Status:** ✅ Complete — 6 of 6 chapters

Kubernetes is the operating environment for Pravah. Understanding it deeply — not just "how to write YAML" but why each decision exists and what breaks when you get it wrong — is what separates a system that works in development from one that survives production.

This phase covers the complete operational stack: choosing the right workload type for each component, packaging deployments with Helm, scaling dynamically with HPA/KEDA, enforcing network isolation with NetworkPolicy, right-sizing resources, and deploying safely with canary releases and disruption budgets.

---

## Chapters

| # | Chapter | What You'll Learn |
|---|---------|-------------------|
| [6.1](6.1-kubernetes-workload-design.md) | **Kubernetes Workload Design** | Deployment vs StatefulSet vs DaemonSet; topologySpreadConstraints; init containers; headless Services for StatefulSets |
| [6.2](6.2-helm-charts-release-management.md) | **Helm Charts & Release Management** | Chart structure; values layering; rolling updates via helm upgrade; rollback; ConfigMap checksum annotation; GitOps with Argo CD |
| [6.3](6.3-autoscaling-hpa-vpa-keda.md) | **Autoscaling — HPA, VPA & KEDA** | CPU-based and custom metric HPA; VPA for right-sizing; KEDA for event-driven scaling on Kafka lag; scale to zero for cloud runners |
| [6.4](6.4-networking-network-policies.md) | **Kubernetes Networking & Network Policies** | Service types; Ingress for L7 routing; default-deny NetworkPolicy; pod-to-pod allow rules; management port isolation; Istio at L7 |
| [6.5](6.5-resource-management-requests-limits-qos.md) | **Resource Management — Requests, Limits & QoS** | Requests vs limits; CPU throttling; OOM kills; QoS classes; JVM heap sizing; LimitRange; ResourceQuota; PriorityClass |
| [6.6](6.6-production-operations-deployments-canary-pdbs.md) | **Production Operations — Deployments, Canary & PDBs** | Rolling update mechanics; canary via Argo Rollouts; automated analysis templates; PodDisruptionBudget; blue-green; deployment runbook |

---

## Pravah's Kubernetes Topology

```
Kubernetes Cluster
│
├── Namespace: pravah (production workloads)
│   ├── Deployments (Stateless Services)
│   │   ├── api-gateway           (3–10 pods, HPA CPU)
│   │   ├── execution-service     (3–30 pods, KEDA Kafka lag)
│   │   ├── pipeline-service      (3–10 pods, HPA CPU)
│   │   ├── scheduler-service     (3–5 pods, HPA CPU)
│   │   ├── runner-service        (3–8 pods, HPA CPU)
│   │   └── notification-service  (2–5 pods, HPA CPU)
│   │
│   ├── StatefulSets (Stateful Infrastructure)
│   │   ├── postgres (3 pods: primary + 2 standbys, Patroni)
│   │   ├── kafka    (3 pods: brokers + KRaft controller)
│   │   └── redis    (3 pods: primary + 2 Sentinel)
│   │
│   └── NetworkPolicies
│       ├── default-deny-all
│       ├── allow-dns-egress
│       ├── execution-service-policy
│       ├── api-gateway-policy
│       └── (one policy per service)
│
├── Namespace: vault (secret management)
│   └── StatefulSet: vault (3 pods, Raft HA)
│
├── Namespace: monitoring (observability)
│   ├── Deployment: prometheus
│   ├── Deployment: grafana
│   ├── Deployment: jaeger
│   ├── Deployment: alertmanager
│   └── DaemonSet: otel-collector (one per node)
│
├── Namespace: logging (log aggregation)
│   ├── StatefulSet: elasticsearch (3 pods)
│   ├── Deployment: logstash
│   ├── Deployment: kibana
│   └── DaemonSet: filebeat (one per node)
│
└── Namespace: ingress-nginx
    └── Deployment: ingress-controller (cloud LB → cluster entry)
```

---

## Key Numbers

```
Metric                                  Value          Notes
─────────────────────────────────────────────────────────────────────────
Rolling update maxUnavailable           0              Never offline old pods before new ready
Rolling update maxSurge                 1              One extra pod during rollout
Startup probe failureThreshold          36 × 5s = 3m   Time for migrations + JVM startup
Deployment progress deadline            600s           Fail if rollout stuck > 10 minutes
PDB minAvailable (services)             2              Never fewer than 2 pods during drain
PDB maxUnavailable (Kafka/Postgres)     1              One node at a time
KEDA polling interval                   15s            Check Kafka lag every 15 seconds
KEDA cooldown (Execution Service)       300s           Wait 5 min before scaling down
KEDA cooldown (Cloud Runners)           120s           Scale runners down faster
HPA scale-down stabilization            300s           5-minute window before scale-down
CPU limit                               4× request     Headroom for burst without starvation
Memory limit                            2× request     OOM headroom
JVM heap                                75% of limit   -XX:MaxRAMPercentage=75.0
Canary initial traffic weight           5%             Start small
Canary promotion cadence                5/25/50/75/100% With 5–10 min pauses + analysis
```

---

## Navigation

← [Phase 5 — Security, Auth & Multi-Tenancy](../phase-5-security/README.md)
→ [Phase 7 — Architecture Decision Records](../phase-7-adrs/README.md) *(next)*
