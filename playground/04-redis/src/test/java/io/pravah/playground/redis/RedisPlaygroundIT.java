package io.pravah.playground.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RedisPlaygroundIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private DistributedLockService locks;

    @Autowired
    private TokenBucketRateLimiter limiter;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @Test
    void distributedLock_onlyOneHolderUntilReleased() {
        String key = "playground:lock:" + UUID.randomUUID();

        DistributedLockService.LockHandle a = locks.tryLock(key, 200, 5_000);
        assertThat(a).isNotNull();

        DistributedLockService.LockHandle b = locks.tryLock(key, 150, 5_000);
        assertThat(b).isNull();

        locks.unlock(a);

        DistributedLockService.LockHandle c = locks.tryLock(key, 200, 5_000);
        assertThat(c).isNotNull();
        locks.unlock(c);
    }

    @Test
    void distributedLock_unlockWithWrongTokenDoesNotDelete() {
        String key = "playground:lock:" + UUID.randomUUID();
        DistributedLockService.LockHandle real = locks.tryLock(key, 100, 5_000);
        assertThat(real).isNotNull();

        // Simulate a stale client trying to unlock with a fake token — must not remove the real lock.
        locks.unlock(new DistributedLockService.LockHandle(key, "wrong-token"));

        Boolean stillHeld = redis.hasKey(key);
        assertThat(Boolean.TRUE.equals(stillHeld)).isTrue();

        locks.unlock(real);
    }

    @Test
    void tokenBucket_exhaustsBurstThenDenies() {
        String key = "playground:rl:" + UUID.randomUUID();
        int capacity = 3;
        double refillPerSec = 0.0;
        long now = 1_700_000_000_000L;

        assertThat(limiter.tryConsume(key, capacity, refillPerSec, now, 1)).isTrue();
        assertThat(limiter.tryConsume(key, capacity, refillPerSec, now, 1)).isTrue();
        assertThat(limiter.tryConsume(key, capacity, refillPerSec, now, 1)).isTrue();
        assertThat(limiter.tryConsume(key, capacity, refillPerSec, now, 1)).isFalse();
    }

    @Test
    void tokenBucket_refillAllowsAgainAfterTimePasses() {
        String key = "playground:rl:" + UUID.randomUUID();
        int capacity = 1;
        double refillPerSec = 1000.0;
        long t0 = 1_700_000_000_000L;

        assertThat(limiter.tryConsume(key, capacity, refillPerSec, t0, 1)).isTrue();
        assertThat(limiter.tryConsume(key, capacity, refillPerSec, t0, 1)).isFalse();

        long t1 = t0 + 2;
        assertThat(limiter.tryConsume(key, capacity, refillPerSec, t1, 1)).isTrue();
    }

    @Test
    void distributedLock_secondThreadBlocksUntilUnlock() throws Exception {
        String key = "playground:lock:" + UUID.randomUUID();
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        DistributedLockService.LockHandle first = locks.tryLock(key, 200, 10_000);
        assertThat(first).isNotNull();

        Thread other = new Thread(() -> {
            DistributedLockService.LockHandle h = locks.tryLock(key, 3_000, 5_000);
            if (h != null) {
                secondAcquired.set(true);
                locks.unlock(h);
            }
        });
        other.start();
        Thread.sleep(80);
        assertThat(secondAcquired.get()).isFalse();

        locks.unlock(first);
        other.join(5_000);
        assertThat(secondAcquired.get()).isTrue();
    }
}
