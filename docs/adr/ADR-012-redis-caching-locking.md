# ADR-012: Redis for Caching, Distributed Locking & Rate Limiting

**Status**: Accepted  
**Date**: 2024-01-01

---

## Context

Pravah's services need three distinct capabilities that share a common requirement: **fast, shared, ephemeral state** that spans multiple service instances.

**1. Caching**

Several hot read paths in Pravah query data that is expensive to fetch and changes rarely:
- Tenant configuration (SLOs, rate limits, runner labels, feature flags) — read on every API request, changes at most a few times per day
- Pipeline definitions — read by the Execution Service to construct jobs, updated infrequently relative to execution frequency
- Public key sets (JWKS) for JWT validation — fetched per request if not cached, but changes only during key rotation

PostgreSQL can handle these reads, but adding a caching layer removes unnecessary load from the primary database and reduces p99 latency on hot paths from ~5ms (PostgreSQL round-trip) to ~0.3ms (Redis round-trip).

**2. Distributed Locking**

Multiple instances of the Scheduler service run concurrently for high availability. A cron-based pipeline scheduled for `0 9 * * *` must trigger exactly once — not once per Scheduler instance. Without a distributed lock, every Scheduler instance would independently trigger the pipeline.

The Outbox publisher (ADR-004) also requires a distributed lock to prevent multiple instances from picking up the same outbox events (though `SELECT FOR UPDATE SKIP LOCKED` in PostgreSQL handles this at the database level, Redis locks are used for coarser-grained leadership scenarios).

**3. Rate Limiting**

Pravah's API must enforce per-tenant rate limits to prevent noisy neighbors from exhausting shared infrastructure. Rate limit state must be shared across all API Gateway instances — if a tenant hits their limit on instance A, subsequent requests to instance B must also be blocked.

A distributed rate limiter requires shared atomic counters that can be incremented and checked with sub-millisecond latency.

---

## Decision

Redis is the shared ephemeral state store for caching, distributed locking, and rate limiting.

**Redis deployment:**

Redis Sentinel (3 nodes: 1 primary, 2 replicas + 3 sentinel processes) for high availability. Sentinel handles automatic failover if the primary node fails. Pravah does not use Redis Cluster because the data size does not require horizontal partitioning.

**1. Caching strategy:**

Cache-aside pattern (application fetches from cache; on miss, fetches from DB and populates cache):

```java
public TenantConfig getTenantConfig(String tenantId) {
    String key = "tenant:config:" + tenantId;
    TenantConfig cached = redis.get(key, TenantConfig.class);
    if (cached != null) {
        return cached;
    }
    TenantConfig config = tenantRepository.findById(tenantId).orElseThrow();
    redis.set(key, config, Duration.ofMinutes(10));
    return config;
}
```

Cache invalidation: when tenant configuration is updated, the service that owns the update calls `redis.delete("tenant:config:" + tenantId)`. The TTL (10 minutes) serves as a backstop — even without explicit invalidation, stale configuration expires within 10 minutes.

**2. Distributed locking (Redisson RedLock):**

The scheduler uses a distributed lock to ensure only one instance runs the trigger evaluation loop at a time:

```java
RLock lock = redissonClient.getLock("scheduler:trigger-lock:" + pipelineId);
boolean acquired = lock.tryLock(
    100,             // wait up to 100ms to acquire
    30_000,          // lock expires after 30 seconds (safety net)
    TimeUnit.MILLISECONDS
);
if (acquired) {
    try {
        evaluateAndTrigger(pipelineId);
    } finally {
        lock.unlock();
    }
} else {
    // Another instance is processing this pipeline — skip
    log.debug("Lock not acquired for pipeline {}, skipping", pipelineId);
}
```

The lock TTL (30 seconds) is a safety net against crashes. If the lock holder crashes without releasing, the lock expires and another instance can proceed after 30 seconds — preventing permanent deadlock.

**3. Rate limiting (token bucket via Lua script):**

```lua
-- Atomic token bucket in Redis
local key       = KEYS[1]           -- e.g., "ratelimit:tenant:megacorp:api"
local capacity  = tonumber(ARGV[1]) -- max tokens (burst capacity)
local refill    = tonumber(ARGV[2]) -- tokens per second
local now       = tonumber(ARGV[3]) -- current unix timestamp (ms)
local requested = tonumber(ARGV[4]) -- tokens needed (usually 1)

local data      = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens    = tonumber(data[1]) or capacity
local last      = tonumber(data[2]) or now

-- Refill tokens based on elapsed time
local elapsed = math.max(0, now - last)
tokens = math.min(capacity, tokens + elapsed * refill / 1000)

if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return {1, math.floor(tokens)}  -- allowed
else
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return {0, math.floor(tokens)}  -- denied
end
```

The Lua script executes atomically on the Redis server — no race conditions between the check and the decrement. The API Gateway calls this script before forwarding any request, returning HTTP 429 if the script returns `{0, ...}`.

**4. JWT revocation blocklist:**

```java
// On token revocation (logout, suspicious activity)
long ttlSeconds = claims.getExpiresAt().toEpochSecond() - Instant.now().getEpochSecond();
redis.setex("jwt:blocklist:" + claims.getId(), ttlSeconds, "1");

// On token validation (every authenticated request)
if (redis.exists("jwt:blocklist:" + claims.getId())) {
    throw new TokenRevokedException();
}
```

---

## Consequences

### Positive

- **Sub-millisecond hot-path performance**: cached tenant configs and rate limit checks add ~0.3ms to request processing, versus ~5ms for a PostgreSQL round-trip. At high QPS, this difference accumulates into meaningful throughput improvement.
- **Shared state without database contention**: rate limiting state and distributed locks are stored in Redis, not PostgreSQL. Locking operations that would create PostgreSQL advisory lock contention are instead handled by Redis with minimal impact on database connections.
- **Atomic operations via Lua**: Redis Lua scripts execute atomically on the server, eliminating race conditions in the rate limiter and lock acquisition without requiring client-side retry logic.
- **TTL-based expiry is natural**: ephemeral data (locks, cached configs, JWT blocklist entries) has natural expiry. TTLs ensure stale data is automatically cleaned up without explicit background jobs.

### Negative

- **Redis is another operational dependency**: Redis failure impacts caching, locking, and rate limiting simultaneously. Services must handle Redis unavailability gracefully (see risks).
- **Cache invalidation bugs**: if the service that updates tenant configuration fails to invalidate the cache, service instances see stale config for up to the TTL duration. In most cases this is acceptable; in security-sensitive cases (e.g., a tenant is disabled), the TTL must be short or invalidation must be guaranteed.
- **Redis is not a durable store**: Redis data is in-memory. With persistence (AOF + RDB), Redis can survive restarts, but data may be slightly stale after a crash recovery. Pravah's Redis data is all derived (cached from PostgreSQL) or ephemeral (locks with short TTLs) — data loss on Redis restart is acceptable and recovered automatically.
- **Redlock controversy**: the RedLock algorithm (multi-node distributed lock) has known theoretical issues under network partitions and clock skew. Pravah uses single-node Sentinel-based locking, not multi-node RedLock. The tradeoff: simpler and more predictable, but the distributed lock is unavailable during Redis primary failover (typically < 30 seconds for Sentinel failover).

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Redis unavailable — rate limiter down | Fail open with heavy logging; rate limiting drops gracefully while Redis recovers; alert pages on-call immediately |
| Redis unavailable — distributed lock cannot be acquired | Scheduler falls back to coarser-grained lock using PostgreSQL advisory locks during Redis outage |
| Redis unavailable — JWT blocklist not checked | Fail closed: authentication fails safe; emergency revocation requires a service restart (forcing token re-issuance) if Redis is down |
| Redis primary failover during lock acquisition | Lock TTL provides safety net; worst case is a 30-second window where two instances might both acquire the "lock"; Scheduler operations are idempotent and tolerate brief double-execution |
| Redis memory exhausted | `maxmemory-policy allkeys-lru` evicts least-recently-used keys first; cache keys are acceptable to evict (they are re-populated on next miss); lock keys have short TTLs and are tiny |

---

## Alternatives Considered

### In-Process Cache (Caffeine / Guava Cache)

Each service instance maintains its own in-process cache. No network round-trip for cache hits.

Rejected for rate limiting and locking because they require cross-instance coordination. In-process caches cannot share state between API Gateway instances.

Accepted for JWKS caching (public keys for JWT validation): each service instance caches the JWKS locally for 5 minutes. This is appropriate because JWKS is public information, changes are infrequent, and the 5-minute stale window is acceptable for key rotation.

### PostgreSQL Advisory Locks

PostgreSQL provides advisory locks that can coordinate distributed processes via the database.

Used as a fallback for distributed locking when Redis is unavailable. Not the primary mechanism because:
- Advisory locks consume database connections — a resource shared with all application queries
- Lock acquisition requires a database round-trip (~5ms) versus Redis (~0.3ms)
- Redis is the appropriate tool for high-frequency, short-lived coordination

### Memcached

A simpler caching system with no persistence, pub/sub, scripting, or sorted sets.

Rejected because Pravah needs Redis-specific features: Lua scripting for atomic rate limiting, TTL-based expiry for JWT blocklist entries, and potentially sorted sets for leaderboard-style runner capacity ranking. Memcached does not support these.
