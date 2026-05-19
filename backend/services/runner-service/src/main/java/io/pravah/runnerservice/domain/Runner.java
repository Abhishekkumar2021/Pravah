package io.pravah.runnerservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a registered runner in the system. Runners connect via gRPC to receive job
 * assignments.
 */
@Entity
@Table(name = "runners")
public class Runner {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RunnerStatus status = RunnerStatus.OFFLINE;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "max_concurrent_jobs")
  private int maxConcurrentJobs = 4;

  @Column(name = "active_jobs")
  private int activeJobs = 0;

  @ElementCollection
  @CollectionTable(name = "runner_labels", joinColumns = @JoinColumn(name = "runner_id"))
  @MapKeyColumn(name = "label_key")
  @Column(name = "label_value")
  private Map<String, String> labels = new HashMap<>();

  @Column(name = "supported_executors")
  private String supportedExecutors;

  @Column(name = "available_memory_bytes")
  private long availableMemoryBytes;

  @Column(name = "available_cpus")
  private int availableCpus;

  @Column(name = "heartbeat_interval_seconds")
  private int heartbeatIntervalSeconds = 30;

  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  @Column(name = "registered_at", nullable = false, updatable = false)
  private Instant registeredAt;

  @Column(name = "last_metrics_cpu_percent")
  private double lastMetricsCpuPercent;

  @Column(name = "last_metrics_memory_used_bytes")
  private long lastMetricsMemoryUsedBytes;

  @Column(name = "last_metrics_disk_available_bytes")
  private long lastMetricsDiskAvailableBytes;

  @Version private Long versionNum;

  @PrePersist
  protected void onCreate() {
    registeredAt = Instant.now();
  }

  // Getters and setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public RunnerStatus getStatus() {
    return status;
  }

  public void setStatus(RunnerStatus status) {
    this.status = status;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public int getMaxConcurrentJobs() {
    return maxConcurrentJobs;
  }

  public void setMaxConcurrentJobs(int maxConcurrentJobs) {
    this.maxConcurrentJobs = maxConcurrentJobs;
  }

  public int getActiveJobs() {
    return activeJobs;
  }

  public void setActiveJobs(int activeJobs) {
    this.activeJobs = activeJobs;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public String getSupportedExecutors() {
    return supportedExecutors;
  }

  public void setSupportedExecutors(String supportedExecutors) {
    this.supportedExecutors = supportedExecutors;
  }

  public long getAvailableMemoryBytes() {
    return availableMemoryBytes;
  }

  public void setAvailableMemoryBytes(long availableMemoryBytes) {
    this.availableMemoryBytes = availableMemoryBytes;
  }

  public int getAvailableCpus() {
    return availableCpus;
  }

  public void setAvailableCpus(int availableCpus) {
    this.availableCpus = availableCpus;
  }

  public int getHeartbeatIntervalSeconds() {
    return heartbeatIntervalSeconds;
  }

  public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
    this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
    this.lastHeartbeatAt = lastHeartbeatAt;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public double getLastMetricsCpuPercent() {
    return lastMetricsCpuPercent;
  }

  public void setLastMetricsCpuPercent(double lastMetricsCpuPercent) {
    this.lastMetricsCpuPercent = lastMetricsCpuPercent;
  }

  public long getLastMetricsMemoryUsedBytes() {
    return lastMetricsMemoryUsedBytes;
  }

  public void setLastMetricsMemoryUsedBytes(long lastMetricsMemoryUsedBytes) {
    this.lastMetricsMemoryUsedBytes = lastMetricsMemoryUsedBytes;
  }

  public long getLastMetricsDiskAvailableBytes() {
    return lastMetricsDiskAvailableBytes;
  }

  public void setLastMetricsDiskAvailableBytes(long lastMetricsDiskAvailableBytes) {
    this.lastMetricsDiskAvailableBytes = lastMetricsDiskAvailableBytes;
  }

  public boolean hasCapacityForJob() {
    return status == RunnerStatus.ONLINE && activeJobs < maxConcurrentJobs;
  }

  public void incrementActiveJobs() {
    this.activeJobs++;
    if (activeJobs >= maxConcurrentJobs) {
      this.status = RunnerStatus.BUSY;
    }
  }

  public void decrementActiveJobs() {
    this.activeJobs = Math.max(0, this.activeJobs - 1);
    if (activeJobs < maxConcurrentJobs && status == RunnerStatus.BUSY) {
      this.status = RunnerStatus.ONLINE;
    }
  }
}
