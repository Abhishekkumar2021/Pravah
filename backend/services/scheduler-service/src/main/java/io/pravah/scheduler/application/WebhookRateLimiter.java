package io.pravah.scheduler.application;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory per-trigger rate limiter (requests per minute).
 *
 * <p>Production notes:
 *
 * <ul>
 *   <li>Memory is bounded by periodic cleanup of stale entries
 *   <li>For distributed setups, replace with Redis-based limiter
 * </ul>
 */
@Component
public class WebhookRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(WebhookRateLimiter.class);
  private static final int MAX_TRACKED_TRIGGERS = 10_000;
  private static final long WINDOW_SECONDS = 60;

  private final Map<UUID, Deque<Instant>> windows = new ConcurrentHashMap<>();

  public boolean tryAcquire(UUID triggerId, int limitPerMinute) {
    if (windows.size() >= MAX_TRACKED_TRIGGERS && !windows.containsKey(triggerId)) {
      log.warn(
          "Rate limiter capacity exceeded, rejecting new trigger",
          kv("trigger_id", triggerId),
          kv("max_triggers", MAX_TRACKED_TRIGGERS));
      return false;
    }

    Deque<Instant> window = windows.computeIfAbsent(triggerId, id -> new ArrayDeque<>());
    Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
    synchronized (window) {
      while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
        window.removeFirst();
      }
      if (window.size() >= limitPerMinute) {
        log.debug(
            "Rate limit exceeded",
            kv("trigger_id", triggerId),
            kv("limit", limitPerMinute),
            kv("current", window.size()));
        return false;
      }
      window.addLast(Instant.now());
      return true;
    }
  }

  @Scheduled(fixedRate = 300_000)
  void cleanup() {
    Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS * 2);
    int removed = 0;
    Iterator<Map.Entry<UUID, Deque<Instant>>> iter = windows.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<UUID, Deque<Instant>> entry = iter.next();
      Deque<Instant> window = entry.getValue();
      synchronized (window) {
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
          window.removeFirst();
        }
        if (window.isEmpty()) {
          iter.remove();
          removed++;
        }
      }
    }
    if (removed > 0) {
      log.debug("Rate limiter cleanup removed stale entries", kv("removed", removed));
    }
  }

  int trackedTriggerCount() {
    return windows.size();
  }
}
