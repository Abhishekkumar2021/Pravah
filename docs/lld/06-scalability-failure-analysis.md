# Scalability & Failure Analysis

This document analyzes how Pravah scales and handles failures. Essential for system design interviews and operational planning.

---

## Table of Contents

1. [Scalability Analysis](#scalability-analysis)
2. [Bottleneck Analysis](#bottleneck-analysis)
3. [Failure Modes](#failure-modes)
4. [Disaster Recovery](#disaster-recovery)
5. [Capacity Planning](#capacity-planning)
6. [Interview Questions](#interview-questions)

---

## Scalability Analysis

### Scale Dimensions

| Dimension | Current Target | Scale Factor | Limiting Factor |
|-----------|----------------|--------------|-----------------|
| Concurrent executions | 10,000 | Kafka partitions, Runner fleet | Runner capacity |
| Pipelines per tenant | 10,000 | PostgreSQL | Query performance |
| Tenants | 1,000 | Database connections | Connection pooling |
| Events/second | 100,000 | Kafka throughput | Broker capacity |
| Log lines/second | 1,000,000 | Log aggregation | Elasticsearch |

### Horizontal Scaling Model

```mermaid
graph TB
    subgraph "STATELESS TIER"
        direction TB
        subgraph "Gateway Layer"
            G1[Gateway 1]
            G2[Gateway 2]
            G3[Gateway N]
        end
        subgraph "Pipeline Service"
            PS1[Pipeline 1]
            PS2[Pipeline 2]
            PS3[Pipeline 3]
        end
        subgraph "Execution Service"
            ES1[Execution 1]
            ES2[Execution 2]
            ES3[Execution 3]
        end
    end
    
    subgraph "SINGLETON TIER"
        direction LR
        SA[Scheduler ★ Active]
        SS[Scheduler Standby]
        SA -.->|failover| SS
    end
    
    subgraph "STATEFUL TIER"
        direction LR
        PG[(PostgreSQL)]
        PGB[PgBouncer]
        KFK[(Kafka)]
        RDS[(Redis)]
        ES[(Elasticsearch)]
    end
    
    subgraph "RUNNER TIER - KEDA Auto-scaled"
        R1[Runner 1]
        R2[Runner 2]
        R3[Runner 3]
        RN[Runner N]
    end
    
    G1 --> PS1
    G2 --> PS2
    G3 --> PS3
    
    PS1 --> PGB
    PS2 --> PGB
    PS3 --> PGB
    PGB --> PG
    
    ES1 --> KFK
    ES2 --> KFK
    ES3 --> KFK
    
    KFK --> R1
    KFK --> R2
    KFK --> R3
    KFK --> RN
```

### Service-Specific Scaling

#### Pipeline Service

```mermaid
flowchart LR
    subgraph "Scaling Pipeline Service"
        RPS[Request Rate > 100/s] --> ADD[Add Replica]
        ADD --> LIM[Limit: DB Pool 100 conn/replica]
    end
    
    subgraph "Read Optimization"
        DL[GraphQL DataLoader]
        RC[Redis Cache TTL:5m]
        RR[Read Replica]
    end
```

**Scaling trigger:** API request rate  
**Metric:** requests/second > 100 per replica  
**Action:** Add replica  
**Limit:** Database connection pool (100 connections per replica)

#### Execution Service

```mermaid
flowchart LR
    subgraph "Scaling Execution Service"
        LAG[Kafka Lag > 1000] --> ADD[Add Consumer]
        ADD --> LIM[Limit: 32 Partitions]
    end
    
    subgraph "Optimization"
        BATCH[Batch Status Updates]
        ASYNC[Async Outbox]
        CKPT[Checkpoint Aggregation]
    end
```

**Scaling trigger:** Kafka consumer lag  
**Metric:** lag > 1000 messages  
**Action:** Add consumer replica  
**Limit:** Kafka partitions (32 default)

#### Runner Service (KEDA)

```mermaid
flowchart TB
    subgraph "KEDA Auto-scaling"
        LAG[Kafka Lag > 10/partition] --> UP[Scale Up]
        ZERO[Lag = 0 for 5min] --> DOWN[Scale Down]
    end
    
    subgraph "Constraints"
        MIN[Min: 2]
        MAX[Max: 100]
        COOL[Cooldown: 30s]
    end
```

---

## Bottleneck Analysis

### Identified Bottlenecks

#### Bottleneck 1: Database Connections

```mermaid
flowchart LR
    subgraph "Problem"
        S1[Service 1<br/>10 conn]
        S2[Service 2<br/>10 conn]
        S3[Service 3<br/>10 conn]
        SN[10 services × 3 replicas]
    end
    
    subgraph "Solution"
        PGB[PgBouncer<br/>Transaction Pooling]
        PG[(PostgreSQL<br/>100 connections)]
    end
    
    S1 --> PGB
    S2 --> PGB
    S3 --> PGB
    SN --> PGB
    PGB --> PG
```

**Problem:** Each service replica needs database connections.  
PostgreSQL default: 100 connections.  
10 services × 3 replicas × 10 connections = 300 connections

**Solution:** PgBouncer transaction pooling
- 100 PgBouncer connections to PostgreSQL
- 1000+ application connections to PgBouncer
- Transaction pooling mode (connection reused after TX)

#### Bottleneck 2: Kafka Partitions

```mermaid
flowchart TB
    subgraph "Consumer Group"
        C1[Consumer 1]
        C2[Consumer 2]
        C3[Consumer 3]
        C4[Consumer 4 IDLE]
    end
    
    subgraph "Topic: job.created"
        P1[Partition 1]
        P2[Partition 2]
        P3[Partition 3]
    end
    
    P1 --> C1
    P2 --> C2
    P3 --> C3
    C4 -.->|No partition| X[Idle]
```

**Problem:** Consumer parallelism limited by partition count.  
More consumers than partitions = idle consumers.

**Solution:** Sufficient partitions upfront
- job.created: 32 partitions
- execution.events: 16 partitions
- Partition key: tenant_id (ensures ordering per tenant)

#### Bottleneck 3: Scheduler Leader

**Problem:** Only one scheduler instance is active (leader election).  
All schedule evaluation happens on one instance.

**Solution:** Efficient evaluation + sharding (future)
- Current: Single leader, evaluates all schedules
- Optimization: Batch schedule evaluation
- Future: Shard schedules by tenant hash

#### Bottleneck 4: Log Ingestion

```mermaid
flowchart LR
    R[Runner<br/>logs] --> G[gRPC Stream<br/>batched] --> B[Buffer<br/>async] --> E[(Elasticsearch<br/>indexed)]
```

**Problem:** High-volume log streams from runners.  
1000 runners × 100 log lines/second = 100K lines/second

**Solution:** Buffering + async ingestion
- Runner buffers logs, sends in batches
- gRPC streaming with flow control
- Async write to log store (Elasticsearch/Loki)
- Sampling for very verbose jobs (configurable)

### Performance Targets

| Operation | Target Latency | P99 Latency | Throughput |
|-----------|----------------|-------------|------------|
| Create pipeline | < 100ms | < 500ms | 100/sec |
| Trigger execution | < 200ms | < 1s | 500/sec |
| Job assignment | < 1s | < 5s | 1000/sec |
| Log retrieval | < 500ms | < 2s | 100/sec |
| GraphQL query | < 200ms | < 1s | 1000/sec |
| Webhook trigger | < 100ms | < 500ms | 500/sec |

---

## Failure Modes

### Failure Taxonomy

```mermaid
mindmap
  root((Failure Modes))
    Infrastructure
      F1: Database Unavailable
      F2: Kafka Unavailable
      F3: Redis Unavailable
    Service
      F4: Service Crash
      F5: Scheduler Leader Failure
      F6: Runner Goes Dead
    Data
      F7: Poison Message
      F8: Data Corruption
    Network
      F9: Network Partition
      F10: Runner Disconnect
```

### Infrastructure Failures

#### F1: Database Unavailable

```mermaid
sequenceDiagram
    participant S as Service
    participant CB as Circuit Breaker
    participant PG as PostgreSQL (Primary)
    participant R as Read Replica
    
    S->>PG: Query
    PG--xS: Connection Timeout
    S->>CB: Mark Failure
    CB->>CB: Open Circuit
    S->>R: Failover (< 30s)
    R-->>S: Success
```

**Impact:** All writes fail, reads from cache may continue  
**Detection:** Connection timeout, health check failure  
**Mitigation:**
- Automatic failover to read replica (< 30s)
- Circuit breaker prevents cascade
- Retry with exponential backoff  
**Recovery:** Automatic (failover) or manual (restore from backup)

#### F2: Kafka Unavailable

**Impact:** Events not published, consumers stall  
**Detection:** Producer timeout, consumer lag spike  
**Mitigation:**
- Outbox pattern: events buffered in database
- Retry publishing when Kafka recovers
- Consumers resume from last committed offset  
**Recovery:** Automatic (Kafka replication) or manual (restore)

#### F3: Redis Unavailable

**Impact:** Cache miss, runner heartbeats not tracked  
**Detection:** Connection timeout  
**Mitigation:**
- Fallback to database for cache
- Runner heartbeats: temporary blind spot (< 60s)
- No job assignments during outage (safe)  
**Recovery:** Automatic (Redis Sentinel failover)

### Service Failures

#### F4: Service Crash

**Impact:** Requests to that replica fail  
**Detection:** Kubernetes liveness probe failure  
**Mitigation:**
- Multiple replicas (N+1 redundancy)
- Load balancer routes to healthy replicas
- Kubernetes restarts failed pod  
**Recovery:** Automatic (< 60s for pod restart)

#### F5: Scheduler Leader Failure

```mermaid
sequenceDiagram
    participant L as Leader
    participant S as Standby
    participant DB as Database Lock
    
    L->>DB: Hold Lock
    L--xL: Crash
    DB->>DB: Lock Expires (30s)
    S->>DB: Acquire Lock
    DB-->>S: Lock Acquired
    S->>S: Become Leader
    Note over S: Catchup missed schedules
```

**Impact:** Schedules not evaluated during failover  
**Detection:** Leader lock expires  
**Mitigation:**
- Standby instance acquires leadership
- Catchup policy handles missed schedules  
**Recovery:** Automatic (< 30s for leader election)

#### F6: Runner Goes Dead

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE : registered
    AVAILABLE --> SUSPECT : heartbeat timeout (30s)
    SUSPECT --> AVAILABLE : heartbeat received
    SUSPECT --> DEAD : heartbeat timeout (60s)
    DEAD --> AVAILABLE : reconnect
    
    note right of DEAD : Orphaned jobs marked FAILED\nRetryable jobs re-queued
```

**Impact:** Jobs on that runner are orphaned  
**Detection:** Heartbeat timeout (60s)  
**Mitigation:**
- Runner marked DEAD after 60s silence
- Orphaned jobs marked FAILED
- Retryable jobs re-queued  
**Recovery:** Automatic (jobs reassigned to other runners)

### Data Failures

#### F7: Poison Message in Kafka

```mermaid
flowchart LR
    subgraph "Poison Message Handling"
        MSG[Bad Message] --> C[Consumer]
        C --> R1[Retry 1]
        R1 --> R2[Retry 2]
        R2 --> R3[Retry 3]
        R3 --> DLT[Dead Letter Topic]
        DLT --> ALERT[Alert Team]
    end
```

**Impact:** Consumer crashes repeatedly on same message  
**Detection:** Repeated consumer restart, same offset  
**Mitigation:**
- Retry limit (3 attempts)
- Dead Letter Topic (DLT) for failed messages
- Alert on DLT ingestion  
**Recovery:** Manual investigation of DLT messages

#### F8: Data Corruption

**Impact:** Incorrect state, potential cascading errors  
**Detection:** Constraint violations, application errors  
**Mitigation:**
- Database constraints
- Event sourcing enables replay from events
- Point-in-time recovery from backups  
**Recovery:** Restore from backup, replay events

### Network Failures

#### F9: Network Partition

**Impact:** Services can't communicate  
**Detection:** Connection timeouts, health check failures  
**Mitigation:**
- Circuit breaker pattern
- Async communication via Kafka (eventually consistent)
- Local caching for read operations  
**Recovery:** Automatic when network heals

#### F10: Runner Network Disconnect

**Impact:** Runner can't receive jobs or report status  
**Detection:** Heartbeat timeout  
**Mitigation:**
- Runner continues executing current job
- Buffers logs locally
- Reconnects with exponential backoff
- On reconnect: flushes buffered logs, reports status  
**Recovery:** Automatic reconnection

### Failure Response Matrix

| Failure | Detection Time | Auto-Recovery | RTO | RPO |
|---------|----------------|---------------|-----|-----|
| Database down | < 10s | Yes (failover) | < 30s | 0 |
| Kafka down | < 30s | Yes (replication) | < 60s | 0 |
| Service crash | < 15s | Yes (K8s restart) | < 60s | 0 |
| Scheduler leader | < 30s | Yes (election) | < 30s | Catchup |
| Runner dead | 60s | Yes (reassign) | < 120s | Checkpoint |
| Network partition | Variable | Yes | Variable | 0 |
| Data corruption | Manual | No | Hours | Backup age |

---

## Disaster Recovery

### Recovery Objectives

| Tier | RTO | RPO | Scenario |
|------|-----|-----|----------|
| **Tier 1** (Critical) | < 1 hour | < 1 minute | Complete region failure |
| **Tier 2** (Important) | < 4 hours | < 15 minutes | Database corruption |
| **Tier 3** (Standard) | < 24 hours | < 1 hour | Non-critical data loss |

### Backup Strategy

```mermaid
flowchart TB
    subgraph "PostgreSQL Backup"
        WAL[WAL Archiving<br/>RPO < 1 min] --> S3A[(S3)]
        DAILY[Daily pg_dump<br/>30 days] --> S3A
        WEEKLY[Weekly Full<br/>1 year] --> COLD[(Cold Storage)]
        REPLICA[Cross-region<br/>Async Replica]
    end
    
    subgraph "Kafka Backup"
        REP[Replication Factor: 3]
        MM[MirrorMaker 2<br/>Cross-region]
        RET[Retention: 7 days]
    end
    
    subgraph "Redis Backup"
        RDB[RDB Snapshots<br/>Every 15 min]
        AOF[AOF for durability]
        NOTE[Note: Ephemeral/Rebuildable]
    end
    
    subgraph "Elasticsearch"
        SNAP[Daily Snapshot] --> S3B[(S3)]
        HOT[30 days hot]
        WARM[90 days warm]
    end
```

### DR Runbook

```mermaid
flowchart TD
    A[1. ASSESS<br/>5 minutes] --> B[2. ACTIVATE DR<br/>15 minutes]
    B --> C[3. VERIFY<br/>10 minutes]
    C --> D[4. COMMUNICATE<br/>ongoing]
    D --> E[5. MONITOR<br/>ongoing]
    E --> F[6. FAILBACK<br/>when recovered]
    
    A --> A1[Confirm region down]
    A --> A2[Check dashboards]
    A --> A3[Notify stakeholders]
    
    B --> B1[Update DNS to DR region]
    B --> B2[Promote replica to primary]
    B --> B3[Start DR services]
    B --> B4[Verify MirrorMaker sync]
    
    C --> C1[Run smoke tests]
    C --> C2[Check pipeline creation]
    C --> C3[Verify runner connectivity]
```

**RTO Target:** 1 hour  
**RPO Target:** 1 minute (WAL archiving)

---

## Capacity Planning

### Resource Sizing Guidelines

```mermaid
graph TB
    subgraph "SMALL < 100 concurrent"
        S_SVC[Services: 2 replicas each]
        S_PG[PostgreSQL: 2 vCPU, 8GB RAM]
        S_KFK[Kafka: 3 brokers, 2 vCPU each]
        S_RDS[Redis: 2 vCPU, 4GB RAM]
        S_RUN[Runners: 5-10]
        S_COST[~$2,000-3,000/month]
    end
    
    subgraph "MEDIUM 100-1,000 concurrent"
        M_SVC[Services: 3-5 replicas each]
        M_PG[PostgreSQL: 4 vCPU, 16GB + replica]
        M_KFK[Kafka: 5 brokers, 4 vCPU each]
        M_RDS[Redis: 4 vCPU, 8GB cluster]
        M_RUN[Runners: 20-50]
        M_ES[Elasticsearch: 3 nodes]
        M_COST[~$8,000-15,000/month]
    end
    
    subgraph "LARGE 1,000-10,000 concurrent"
        L_SVC[Services: 5-10 replicas, dedicated pools]
        L_PG[PostgreSQL: 8 vCPU, 32GB HA cluster]
        L_KFK[Kafka: 7+ brokers, 8 vCPU each]
        L_RDS[Redis: 8 vCPU, 6 shards]
        L_RUN[Runners: 50-200, multiple pools]
        L_ES[Elasticsearch: 6+ nodes]
        L_COST[~$30,000-60,000/month]
    end
```

### Scaling Triggers

| Component | Metric | Scale Up | Scale Down |
|-----------|--------|----------|------------|
| Gateway | CPU > 70% | Add replica | CPU < 30% for 10m |
| Services | CPU > 70% | Add replica | CPU < 30% for 10m |
| Runners | Kafka lag > 10 | Add runner | Lag = 0 for 5m |
| PostgreSQL | Connections > 80% | Increase pool | N/A |
| Kafka | Disk > 70% | Add broker | N/A |

---

## Interview Questions

**Q: "How does Pravah scale to handle 10,000 concurrent executions?"**
> Multiple dimensions:
> 1. **Stateless services** scale horizontally (add replicas)
> 2. **Kafka partitions** enable parallel event processing
> 3. **KEDA auto-scales runners** based on queue depth
> 4. **PgBouncer** multiplexes database connections
> 5. **Read replicas** handle read-heavy workloads

**Q: "What happens if the database goes down?"**
> 1. Circuit breaker activates, preventing cascade
> 2. Automatic failover to read replica (< 30s)
> 3. Writes are buffered (outbox survives in replica's WAL)
> 4. Services degrade gracefully (reads from cache)
> 5. After recovery, outbox catches up Kafka

**Q: "How do you handle a runner that crashes mid-job?"**
> 1. Runner heartbeat times out after 60 seconds
> 2. Runner Service marks runner DEAD
> 3. Orphaned jobs are marked FAILED
> 4. Jobs with retries remaining are re-queued
> 5. Checkpoints allow resumption from last successful stage

**Q: "What's your disaster recovery strategy?"**
> 1. **RPO < 1 minute**: WAL archiving to S3
> 2. **RTO < 1 hour**: Warm standby in DR region
> 3. Cross-region PostgreSQL replica (async)
> 4. Kafka MirrorMaker 2 for topic replication
> 5. Documented runbook with regular testing

**Q: "How do you prevent cascading failures?"**
> Multiple layers:
> 1. **Circuit breakers** (Resilience4j) stop calls to failing services
> 2. **Bulkheads** isolate thread pools per downstream
> 3. **Timeouts** on all external calls
> 4. **Async communication** via Kafka (decouples services)
> 5. **Graceful degradation** (serve from cache when DB slow)

**Q: "What are the main bottlenecks and how do you address them?"**
> 1. **Database connections**: PgBouncer transaction pooling
> 2. **Kafka partitions**: Pre-provision sufficient partitions (32+)
> 3. **Scheduler leader**: Efficient batch evaluation, future sharding
> 4. **Log ingestion**: Buffering, batching, sampling for verbose jobs

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-13 | Engineering | Initial analysis |
| 1.1 | 2026-05-13 | Engineering | Updated to Mermaid diagrams |
