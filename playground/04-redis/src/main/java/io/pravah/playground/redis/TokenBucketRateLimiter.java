package io.pravah.playground.redis;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

/**
 * Per-key token bucket using the Lua script from ADR-012 — atomic refill + consume on the Redis server.
 */
@Service
public class TokenBucketRateLimiter {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public TokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/token_bucket.lua")));
        @SuppressWarnings("rawtypes")
        DefaultRedisScript<List> s = this.script;
        s.setResultType(List.class);
    }

    /**
     * @param redisKey   e.g. {@code ratelimit:tenant:acme:api}
     * @param capacity   burst size
     * @param refillPerSecond steady-state tokens per second
     * @param nowMillis   wall clock — pass same source in tests for determinism
     * @param requested   usually 1
     * @return {@code true} if allowed
     */
    @SuppressWarnings("unchecked")
    public boolean tryConsume(String redisKey, int capacity, double refillPerSecond, long nowMillis, int requested) {
        List<Long> raw = redis.execute(
                script,
                List.of(redisKey),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(nowMillis),
                String.valueOf(requested));
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        Number allowed = (Number) raw.get(0);
        return allowed.intValue() == 1;
    }
}
