# ADR-022: Patroni for PostgreSQL High Availability

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

ADR-003 established PostgreSQL as Pravah's primary database. PostgreSQL's built-in streaming replication provides read replicas but does not handle automatic failover. If the primary PostgreSQL node crashes:

- Replication stops — replicas are in read-only standby mode
- Writes to the primary fail immediately
- Someone must manually promote a replica to primary and update connection strings
- During the time to detect the failure and complete manual promotion (5–30 minutes), Pravah cannot write to the database — pipeline state transitions fail, outbox events cannot be written, job status updates are lost

This is not acceptable for a production platform. PostgreSQL HA requires automated failover: when the primary dies, a healthy replica is promoted within seconds, and the service's connection strings automatically redirect to the new primary.

**Requirements for the HA solution:**

1. **Automatic failover** — detect primary failure and promote a replica without human intervention
2. **Leader election consensus** — multiple replicas cannot simultaneously believe they are the primary (split-brain)
3. **Connection routing** — applications connect to an endpoint that always points to the current primary; they must not need to know which physical node is primary
4. **Fencing** — the old primary must be prevented from accepting writes after being demoted, even if it recovers from a crash and incorrectly believes it is still primary
5. **Zero data loss option** — for the most critical databases, synchronous replication before failover

---

## Decision

**Patroni** manages PostgreSQL high availability for all Pravah service databases. Patroni uses etcd (already present in the Kubernetes cluster as the Kubernetes control plane's backing store) as the distributed consensus store for leader election.

**Architecture per service database:**

```
┌────────────────────────────────────────────────────────┐
│  PostgreSQL Cluster for execution_db                   │
│                                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  Node 1      │  │  Node 2      │  │  Node 3      │ │
│  │  Patroni     │  │  Patroni     │  │  Patroni     │ │
│  │  PostgreSQL  │  │  PostgreSQL  │  │  PostgreSQL  │ │
│  │  [PRIMARY]   │  │  [REPLICA]   │  │  [REPLICA]   │ │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘ │
│         │                 │                  │         │
│         └─────────────────┴──────────────────┘         │
│              Streaming replication + Patroni            │
└──────────────────────────┬─────────────────────────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
    ┌─────────▼─────────┐  ┌────────────▼──────────┐
    │   Primary Service  │  │  Read Replica Service  │
    │   (VIP / HAProxy)  │  │  (HAProxy round-robin) │
    │   Always → primary │  │  Always → replicas     │
    └────────────────────┘  └───────────────────────┘
              │                         │
    ┌─────────▼──────────────────────────▼─────────┐
    │                  PgBouncer                     │
    │         (connection pooling, transaction mode) │
    └────────────────────────────────────────────────┘
              │
    Application (Execution Service, etc.)
```

**Patroni configuration:**

```yaml
# patroni.yml — per node
scope: execution-db-cluster
namespace: /pravah/db/
name: execution-db-node-1

etcd3:
  hosts: etcd:2379

bootstrap:
  dcs:
    ttl: 30                  # lease TTL — primary must renew every 30s
    loop_wait: 10            # Patroni loop interval
    retry_timeout: 10
    maximum_lag_on_failover: 1048576   # 1MB — don't promote if too far behind
    synchronous_mode: false  # async replication (use true for audit_db)
    postgresql:
      use_pg_rewind: true
      parameters:
        max_connections: 200
        wal_level: replica
        hot_standby: "on"
        wal_log_hints: "on"    # required for pg_rewind

postgresql:
  listen: 0.0.0.0:5432
  connect_address: node1:5432
  data_dir: /var/lib/postgresql/data
  pg_hba:
    - host replication replicator 0.0.0.0/0 md5
    - host all all 0.0.0.0/0 md5
```

**Failover sequence:**

```
t=0:00  Primary node crashes or becomes unresponsive
t=0:10  Patroni on replicas detects primary hasn't renewed lease (TTL=30s, loop=10s)
t=0:30  TTL expires — primary's etcd key is gone; election begins
t=0:31  Replica with lowest replication lag wins election; Patroni promotes it
t=0:31  HAProxy health check detects new primary (endpoint changes)
t=0:35  Application connections via HAProxy/PgBouncer automatically route to new primary
t=0:35  Old primary restarts → Patroni detects it is no longer leader → pg_rewind catches up → rejoins as replica
```

Total failover time: **30–40 seconds** (dominated by TTL expiry).

For synchronous replication clusters (`synchronous_mode: true`, used for `audit_db`):
- Replica must acknowledge WAL before primary considers write committed
- Failover is zero-data-loss but failover time is the same
- Write throughput is ~20% lower due to synchronous confirmation

**Connection routing via HAProxy:**

```
frontend primary
  bind *:5000
  default_backend primary-backend

backend primary-backend
  option httpchk GET /primary   ← Patroni REST API on port 8008
  server node1 node1:5432 check port 8008
  server node2 node2:5432 check port 8008
  server node3 node3:5432 check port 8008
  # Patroni returns HTTP 200 on /primary for the current primary only
  # HAProxy routes all traffic to the single responding server

frontend replica
  bind *:5001
  default_backend replica-backend

backend replica-backend
  option httpchk GET /replica
  balance roundrobin
  server node1 node1:5432 check port 8008
  server node2 node2:5432 check port 8008
  server node3 node3:5432 check port 8008
```

Applications connect to `haproxy:5000` (primary) or `haproxy:5001` (read replica). When failover occurs, HAProxy's health checks detect the new primary within one check interval (5 seconds) and reroutes automatically. Applications using PgBouncer do not see connection interruptions longer than the health check interval.

---

## Consequences

### Positive

- **Automatic failover in 30–40 seconds** — far better than the 5–30 minutes of manual failover. Pipeline execution pauses briefly (PgBouncer queues connections) and resumes automatically.
- **Split-brain prevention via etcd consensus** — etcd is a Raft-based consensus store. Only one Patroni node can hold the leader lease at a time. There is no scenario where two nodes simultaneously believe they are primary.
- **Transparent to applications** — applications connect to HAProxy, not to individual PostgreSQL nodes. When failover occurs, applications retry the failed connection and succeed on the new primary via the same HAProxy address.
- **pg_rewind for rapid old-primary reintegration** — when the old primary recovers, `pg_rewind` fast-forwards it to the new primary's state (rewinds diverging WAL) so it can rejoin as a replica quickly rather than performing a full `pg_basebackup`.
- **Per-cluster configuration** — `audit_db` uses synchronous replication (zero data loss for compliance). Other databases use asynchronous replication (faster writes, tolerate up to the maximum lag of the promote candidate on failover).

### Negative

- **etcd dependency** — Patroni requires etcd for leader election. Kubernetes already runs etcd for its own control plane; Pravah uses the same etcd cluster (dedicated namespace) to avoid running a separate etcd cluster.
- **30–40 second failover** — pipeline steps that are in the middle of a transaction when the primary fails will see a connection error. They must retry. All database operations must be designed with retry logic.
- **HAProxy adds a network hop** — every database connection goes through HAProxy. This adds ~0.3ms of latency per connection (acceptable; mitigated by PgBouncer connection pooling).
- **Replication lag on replicas** — async replication means replicas may lag behind the primary by milliseconds to seconds under heavy write load. Read queries routed to replicas may see stale data. Application code must be explicit about which reads can tolerate stale data.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| etcd unavailable — Patroni cannot elect leader | etcd has 3 replicas (Kubernetes control plane HA); Patroni pauses writes but existing connections continue; etcd recovery restores Patroni |
| Both remaining replicas have high replication lag at failover time | `maximum_lag_on_failover` prevents promoting a replica that is too far behind; alert at 10MB lag |
| pg_rewind fails — old primary must do full basebackup | Full basebackup from new primary; takes 10–30 minutes depending on data size; automated via Patroni |
| HAProxy becomes a SPOF | HAProxy runs 2 replicas with a VIP (keepalived); VIP moves to the other HAProxy if one fails |

---

## Alternatives Considered

### Manual Failover (No HA solution)

Rely on monitoring and on-call engineers to manually promote replicas when the primary fails.

Rejected because:
- MTTR of 5–30 minutes is unacceptable for a production platform
- 3am failures require waking someone up for a task that can be fully automated
- Human error during manual failover (e.g., promoting the wrong replica) risks data loss

### Citus (Distributed PostgreSQL)

Citus extends PostgreSQL with distributed query execution and horizontal sharding. It includes HA features.

Rejected because:
- Pravah does not need horizontal write sharding at current scale (ADR-003)
- Citus's HA approach is based on coordinator/worker architecture, which is different from Patroni's primary/replica model
- Patroni works with standard PostgreSQL; no changes to application queries are needed

### Cloud-Managed PostgreSQL (RDS, Cloud SQL)

Use a managed PostgreSQL service that handles HA automatically.

Rejected for the same reason as other cloud-specific services: Pravah must be deployable on-premise. Cloud-managed PostgreSQL is not available on-premise.
