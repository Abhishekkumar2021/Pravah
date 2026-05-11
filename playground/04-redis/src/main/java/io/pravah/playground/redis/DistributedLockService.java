package io.pravah.playground.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

/**
 * Minimal distributed lock without Redisson — same primitives ADR-012 builds on.
 *
 * <p>Acquire: {@code SET key token NX PX leaseMs} — only one holder. Token prevents unlocking a lock
 * someone else took after TTL expiry.
 *
 * <p>Release: Lua script compares token before {@code DEL}.
 */
@Service
public class DistributedLockService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> unlockScript;

    public DistributedLockService(StringRedisTemplate redis) {
        this.redis = redis;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/unlock.lua")));
        this.unlockScript.setResultType(Long.class);
    }

    /**
     * Try to acquire {@code lockKey} within {@code waitMs}, holding it for at most {@code leaseMs}.
     *
     * @return non-null handle if acquired; empty if not acquired within the wait window
     */
    public LockHandle tryLock(String lockKey, long waitMs, long leaseMs) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            Boolean ok = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofMillis(leaseMs));
            if (Boolean.TRUE.equals(ok)) {
                return new LockHandle(lockKey, token);
            }
            sleepJitter();
        }
        return null;
    }

    /** Release a lock previously acquired via {@link #tryLock}; safe if key expired or stolen. */
    public void unlock(LockHandle handle) {
        if (handle == null) {
            return;
        }
        redis.execute(unlockScript, Collections.singletonList(handle.key()), handle.token());
    }

    private static void sleepJitter() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 25));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record LockHandle(String key, String token) {}
}
