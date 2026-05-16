package io.pravah.scheduler.infrastructure.leader;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LeaderElectionService {

  private final SchedulerLeaderRepository leaderRepository;
  private final String holderId;
  private final long lockTtlSeconds;

  public LeaderElectionService(
      SchedulerLeaderRepository leaderRepository,
      @Value("${pravah.scheduler.leader-lock-ttl-seconds:90}") long lockTtlSeconds) {
    this.leaderRepository = leaderRepository;
    this.holderId = UUID.randomUUID() + "@" + hostname();
    this.lockTtlSeconds = lockTtlSeconds;
  }

  public boolean isLeader() {
    Instant expiresAt = Instant.now().plusSeconds(lockTtlSeconds);
    return leaderRepository.tryAcquireOrRenew(holderId, expiresAt);
  }

  private static String hostname() {
    String host = System.getenv("HOSTNAME");
    return host != null ? host : "local";
  }
}
