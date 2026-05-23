package io.pravah.runnerservice.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.runnerservice.domain.Runner;
import io.pravah.runnerservice.domain.RunnerStatus;
import io.pravah.runnerservice.repository.RunnerRepository;
import io.pravah.spring.multitenancy.SystemMaintenanceRlsHelper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing runners. */
@Service
@Transactional
public class RunnerService {

  private static final Logger log = LoggerFactory.getLogger(RunnerService.class);
  private static final int HEARTBEAT_TIMEOUT_MULTIPLIER = 3;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RunnerRepository repository;
  private final RunnerConnectionManager connectionManager;
  private final SystemMaintenanceRlsHelper maintenanceRlsHelper;

  public RunnerService(
      RunnerRepository repository,
      RunnerConnectionManager connectionManager,
      SystemMaintenanceRlsHelper maintenanceRlsHelper) {
    this.repository = repository;
    this.connectionManager = connectionManager;
    this.maintenanceRlsHelper = maintenanceRlsHelper;
  }

  /**
   * Registers a new runner, or re-authenticates an existing runner when {@code registrationToken}
   * matches.
   */
  public RegisterResult registerRunner(UUID tenantId, RegisterRequest request) {
    Optional<Runner> existing = repository.findByTenantIdAndName(tenantId, request.name());
    if (existing.isPresent()) {
      if (request.registrationToken() == null || request.registrationToken().isBlank()) {
        throw new IllegalArgumentException(
            "Runner with name '" + request.name() + "' already exists");
      }
      Runner runner = existing.get();
      if (!runner.getTokenHash().equals(hashToken(request.registrationToken()))) {
        throw new IllegalArgumentException(
            "Invalid registration token for runner '" + request.name() + "'");
      }
      applyRegistrationFields(runner, request);
      runner = repository.save(runner);
      log.info("Re-authenticated runner: id={}, name={}", runner.getId(), runner.getName());
      return new RegisterResult(
          runner.getId(), request.registrationToken(), runner.getHeartbeatIntervalSeconds());
    }

    // Generate authentication token
    String token = generateToken();
    String tokenHash = hashToken(token);

    Runner runner = new Runner();
    runner.setTenantId(tenantId);
    applyRegistrationFields(runner, request);
    runner.setTokenHash(tokenHash);
    runner.setStatus(RunnerStatus.OFFLINE);
    runner.setHeartbeatIntervalSeconds(30);

    runner = repository.save(runner);
    log.info(
        "Registered runner: id={}, name={}, tenant={}", runner.getId(), runner.getName(), tenantId);

    return new RegisterResult(runner.getId(), token, runner.getHeartbeatIntervalSeconds());
  }

  /** Validates a runner token and returns the runner if valid. */
  public Optional<Runner> validateToken(UUID runnerId, String token) {
    return repository
        .findById(runnerId)
        .filter(runner -> runner.getTokenHash().equals(hashToken(token)));
  }

  /** Updates runner status to ONLINE when it connects. */
  public void markOnline(UUID runnerId) {
    repository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setStatus(RunnerStatus.ONLINE);
              runner.setLastHeartbeatAt(Instant.now());
              repository.save(runner);
              log.info("Runner {} is now ONLINE", runnerId);
            });
  }

  /** Updates runner status to OFFLINE when it disconnects. */
  public void markOffline(UUID runnerId) {
    repository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setStatus(RunnerStatus.OFFLINE);
              repository.save(runner);
              log.info("Runner {} is now OFFLINE", runnerId);
            });
  }

  /** Processes a heartbeat from a runner. */
  public void processHeartbeat(UUID runnerId, HeartbeatData data) {
    repository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setLastHeartbeatAt(Instant.now());
              runner.setLastMetricsCpuPercent(data.cpuUsagePercent());
              runner.setLastMetricsMemoryUsedBytes(data.memoryUsedBytes());
              runner.setLastMetricsDiskAvailableBytes(data.diskAvailableBytes());
              // Server-authoritative job count; heartbeat metrics are informational only.
              int reported = Math.max(0, data.activeJobs());
              if (reported > runner.getActiveJobs()) {
                log.debug(
                    "Runner reported higher active_jobs than server count; keeping server value",
                    kv("runnerId", runnerId),
                    kv("serverActiveJobs", runner.getActiveJobs()),
                    kv("reportedActiveJobs", reported));
              }

              // Update status based on capacity (server activeJobs)
              if (runner.getActiveJobs() >= runner.getMaxConcurrentJobs()) {
                runner.setStatus(RunnerStatus.BUSY);
              } else if (runner.getStatus() == RunnerStatus.BUSY) {
                runner.setStatus(RunnerStatus.ONLINE);
              }

              repository.save(runner);
            });
  }

  /** Gets a runner by ID. */
  @Transactional(readOnly = true)
  public Optional<Runner> getRunner(UUID tenantId, UUID runnerId) {
    return repository.findByIdAndTenantId(runnerId, tenantId);
  }

  /** Lists all runners for a tenant. */
  @Transactional(readOnly = true)
  public List<Runner> listRunners(UUID tenantId) {
    return repository.findByTenantId(tenantId);
  }

  /** Lists runners by status. */
  @Transactional(readOnly = true)
  public List<Runner> listRunnersByStatus(UUID tenantId, RunnerStatus status) {
    return repository.findByTenantIdAndStatus(tenantId, status);
  }

  /** Finds available runners that can accept new jobs. */
  @Transactional(readOnly = true)
  public List<Runner> findAvailableRunners(UUID tenantId) {
    return repository.findAvailableRunners(tenantId);
  }

  /** Assigns a job to a runner. */
  public boolean assignJob(UUID runnerId) {
    return repository
        .findById(runnerId)
        .filter(Runner::hasCapacityForJob)
        .map(
            runner -> {
              runner.incrementActiveJobs();
              repository.save(runner);
              return true;
            })
        .orElse(false);
  }

  /** Marks a job as completed on a runner. */
  public void completeJob(UUID runnerId) {
    repository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.decrementActiveJobs();
              repository.save(runner);
            });
  }

  /** Deletes a runner. */
  public void deleteRunner(UUID tenantId, UUID runnerId) {
    Runner runner =
        repository
            .findByIdAndTenantId(runnerId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Runner not found"));

    if (connectionManager.isConnected(runnerId)) {
      connectionManager.getConnection(runnerId).ifPresent(conn -> conn.close());
    }

    repository.delete(runner);
    log.info("Deleted runner: id={}", runnerId);
  }

  /** Scheduled task to mark stale runners as offline. */
  @Scheduled(fixedDelay = 60000)
  public void checkStaleRunners() {
    Instant cutoff = Instant.now().minusSeconds(30 * HEARTBEAT_TIMEOUT_MULTIPLIER);
    List<Runner> staleRunners =
        maintenanceRlsHelper.runWithMaintenance(() -> repository.findStaleRunners(cutoff));

    for (Runner runner : staleRunners) {
      log.warn("Runner {} missed heartbeats, marking as OFFLINE", runner.getId());
      runner.setStatus(RunnerStatus.OFFLINE);
      repository.save(runner);

      connectionManager.unregister(runner.getId());
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static void applyRegistrationFields(Runner runner, RegisterRequest request) {
    runner.setName(request.name());
    runner.setVersion(request.version());
    runner.setMaxConcurrentJobs(request.maxConcurrentJobs());
    runner.setSupportedExecutors(String.join(",", request.supportedExecutors()));
    runner.setAvailableMemoryBytes(request.availableMemoryBytes());
    runner.setAvailableCpus(request.availableCpus());
    runner.setLabels(request.labels());
  }

  private String hashToken(String token) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  // Request/Response records

  public record RegisterRequest(
      String name,
      String version,
      java.util.Map<String, String> labels,
      int maxConcurrentJobs,
      List<String> supportedExecutors,
      long availableMemoryBytes,
      int availableCpus,
      String registrationToken) {}

  public record RegisterResult(UUID runnerId, String token, int heartbeatIntervalSeconds) {}

  public record HeartbeatData(
      double cpuUsagePercent,
      long memoryUsedBytes,
      long memoryTotalBytes,
      int activeJobs,
      long diskAvailableBytes) {}
}
