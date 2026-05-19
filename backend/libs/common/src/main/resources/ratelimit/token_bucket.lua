-- Atomic token bucket (ADR-012). Executes as a single server-side unit — no check-then-act races.
-- KEYS[1]   = rate limit key, e.g. ratelimit:tenant:{tenantId}:api
-- ARGV[1]   = capacity (burst)
-- ARGV[2]   = refill rate (tokens per second)
-- ARGV[3]   = now (unix epoch milliseconds)
-- ARGV[4]   = requested tokens (usually 1)
-- Returns: { allowed (1|0), remaining_tokens_floor, third }
--   third = burst capacity when allowed, retry_after_ms when denied

local key       = KEYS[1]
local capacity  = tonumber(ARGV[1])
local refill    = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data      = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens    = tonumber(data[1]) or capacity
local last      = tonumber(data[2]) or now

local elapsed = math.max(0, now - last)
tokens = math.min(capacity, tokens + elapsed * refill / 1000)

if tokens >= requested then
  tokens = tokens - requested
  redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('EXPIRE', key, 3600)
  return {1, math.floor(tokens), math.floor(capacity)}
else
  redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('EXPIRE', key, 3600)
  local wait_ms = math.ceil((requested - tokens) * 1000 / refill)
  return {0, math.floor(tokens), wait_ms}
end
