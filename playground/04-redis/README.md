# Playground 04 — Redis (distributed lock + token bucket)

**Pravah ADR:** [ADR-012: Redis for Caching, Distributed Locking & Rate Limiting](../../docs/adr/ADR-012-redis-caching-locking.md)

This exercise wires **Spring Data Redis** (Lettuce) to two patterns Pravah relies on:

1. **Distributed lock** — `SET key token NX PX ttl` plus a **Lua** unlock that deletes only if the token matches (safe after TTL expiry when another process may have acquired the lock).
2. **Token bucket rate limiter** — the **Lua** script from ADR-012 runs atomically on the server so refill + consume cannot race.

Production Pravah also uses **Redisson** for richer lock APIs and **Redis Sentinel** for HA; here you stay close to the wire so the semantics are obvious.

---

## Prerequisites

- Java 21, Docker, Docker Compose
- Same Gradle wrapper discipline as other playgrounds: always `./gradlew` from this directory

---

## Quick start

```bash
docker compose up -d
./gradlew bootRun   # optional — app starts with no HTTP surface by default
./gradlew test      # Testcontainers Redis (skips if Docker unavailable)
```

Redis is exposed on **localhost:6379**.

---

## What to read first

| Order | File | Why |
|------:|------|-----|
| 1 | `src/main/resources/redis/token_bucket.lua` | Matches ADR-012 — see how refill uses elapsed ms |
| 2 | `src/main/resources/redis/unlock.lua` | Why compare token before `DEL` |
| 3 | `DistributedLockService.java` | `setIfAbsent` + TTL + polling wait |
| 4 | `TokenBucketRateLimiter.java` | How Spring loads and executes a classpath Lua script |

---

## Tasks (prove you understand it)

1. **Lock TTL** — In `DistributedLockService`, trace what happens if the holder crashes before `unlock`. How long until another instance can take the lock? Change `leaseMs` in a test and observe.
2. **Wrong-token unlock** — Read `RedisPlaygroundIT.distributedLock_unlockWithWrongTokenDoesNotDelete`. Explain why deleting without a token check would be unsafe.
3. **Rate limit key shape** — Pravah uses keys like `ratelimit:tenant:{id}:api`. Add a small helper that builds keys from `tenantId` and run two tenants in parallel in a test — they must not share bucket state.
4. **Cache-aside (optional)** — ADR-012 shows `tenant:config:{id}` cache-aside. Implement a `TenantConfigCache` that `GET`s JSON, on miss loads from a fake repository, `SETEX`s with a TTL, and invalidates on update. No starter code required — this is free-form practice.
5. **Fail-open vs fail-closed** — ADR-012 says rate limiting may **fail open** when Redis is down, but JWT blocklist checks **fail closed**. Write two bullet points in your notes explaining the difference.

---

## Verification

- `./gradlew test` passes (Docker required for Testcontainers).
- With `docker compose up`, you can run `redis-cli MONITOR` in another terminal and watch Lua + `SET` traffic while tests run.

---

## Troubleshooting

| Symptom | Likely cause |
|--------|----------------|
| `Connection refused` on `bootRun` | Redis not running — `docker compose up -d` |
| Tests skipped | Docker not running — `@Testcontainers(disabledWithoutDocker = true)` |
| Stale rate-limit state in manual experiments | Keys live up to 1h TTL — `redis-cli FLUSHDB` in dev only |

---

## Next

After this playground, **05-vault** covers dynamic credentials and PKI (ADR-007 / ADR-008).
