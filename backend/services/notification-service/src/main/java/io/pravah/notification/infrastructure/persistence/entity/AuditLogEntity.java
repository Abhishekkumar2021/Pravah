package io.pravah.notification.infrastructure.persistence.entity;

import io.pravah.notification.domain.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Audit log entry - immutable record of system events. */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private ActorType actorType;

  @Column(name = "actor_name")
  private String actorName;

  @Column(nullable = false)
  private String action;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "resource_name")
  private String resourceName;

  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String details;

  @Column(name = "ip_address", columnDefinition = "inet")
  @JdbcTypeCode(SqlTypes.INET)
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditLogEntity() {}

  private AuditLogEntity(Builder builder) {
    this.id = UUID.randomUUID();
    this.tenantId = builder.tenantId;
    this.actorId = builder.actorId;
    this.actorType = builder.actorType != null ? builder.actorType : ActorType.SYSTEM;
    this.actorName = builder.actorName;
    this.action = builder.action;
    this.resourceType = builder.resourceType;
    this.resourceId = builder.resourceId;
    this.resourceName = builder.resourceName;
    this.details = builder.details;
    this.ipAddress = builder.ipAddress;
    this.userAgent = builder.userAgent;
    this.requestId = builder.requestId;
    this.createdAt = Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public ActorType getActorType() {
    return actorType;
  }

  public String getActorName() {
    return actorName;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getResourceName() {
    return resourceName;
  }

  public String getDetails() {
    return details;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getRequestId() {
    return requestId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Builder {
    private UUID tenantId;
    private UUID actorId;
    private ActorType actorType;
    private String actorName;
    private String action;
    private String resourceType;
    private UUID resourceId;
    private String resourceName;
    private String details;
    private String ipAddress;
    private String userAgent;
    private String requestId;

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder actorId(UUID actorId) {
      this.actorId = actorId;
      return this;
    }

    public Builder actorType(ActorType actorType) {
      this.actorType = actorType;
      return this;
    }

    public Builder actorName(String actorName) {
      this.actorName = actorName;
      return this;
    }

    public Builder action(String action) {
      this.action = action;
      return this;
    }

    public Builder resourceType(String resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder resourceId(UUID resourceId) {
      this.resourceId = resourceId;
      return this;
    }

    public Builder resourceName(String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public Builder details(String details) {
      this.details = details;
      return this;
    }

    public Builder ipAddress(String ipAddress) {
      this.ipAddress = ipAddress;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public Builder requestId(String requestId) {
      this.requestId = requestId;
      return this;
    }

    public AuditLogEntity build() {
      if (tenantId == null) throw new IllegalStateException("tenantId is required");
      if (action == null) throw new IllegalStateException("action is required");
      if (resourceType == null) throw new IllegalStateException("resourceType is required");
      return new AuditLogEntity(this);
    }
  }
}
