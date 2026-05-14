package io.pravah.pipeline.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pipeline_versions")
public class PipelineVersionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false)
  private UUID id;

  @Column(name = "pipeline_id", nullable = false)
  private UUID pipelineId;

  @Column(nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode definition;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "published_by", nullable = false)
  private UUID publishedBy;

  protected PipelineVersionEntity() {}

  public PipelineVersionEntity(
      UUID pipelineId, int version, JsonNode definition, Instant publishedAt, UUID publishedBy) {
    this.pipelineId = pipelineId;
    this.version = version;
    this.definition = definition;
    this.publishedAt = publishedAt;
    this.publishedBy = publishedBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPipelineId() {
    return pipelineId;
  }

  public int getVersion() {
    return version;
  }

  public JsonNode getDefinition() {
    return definition;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public UUID getPublishedBy() {
    return publishedBy;
  }
}
