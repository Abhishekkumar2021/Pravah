package io.pravah.execution.infrastructure.persistence.entity;

import io.pravah.execution.domain.JobLogLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_logs")
public class JobLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "log_time", nullable = false)
  private Instant logTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private JobLogLevel level;

  @Column(nullable = false, columnDefinition = "text")
  private String message;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> attributes;

  protected JobLogEntity() {}

  public JobLogEntity(
      UUID jobId,
      Instant logTime,
      JobLogLevel level,
      String message,
      Map<String, Object> attributes) {
    this.jobId = jobId;
    this.logTime = logTime;
    this.level = level;
    this.message = message;
    this.attributes = attributes;
  }

  public UUID getId() {
    return id;
  }

  public UUID getJobId() {
    return jobId;
  }

  public Instant getLogTime() {
    return logTime;
  }

  public JobLogLevel getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }
}
