package io.pravah.tenant.domain.model;

import io.pravah.common.domain.UserId;
import io.pravah.tenant.infrastructure.persistence.UserStatusType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Type;

/**
 * User entity within a tenant.
 *
 * <p>Users belong to exactly one tenant and can have roles assigned at tenant and team levels.
 *
 * @see <a href="../../../../../../docs/lld/02-database-erd.md">Database ERD - Tenant Database</a>
 */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String name;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "mfa_secret")
  private String mfaSecret;

  @Type(UserStatusType.class)
  @Column(nullable = false, columnDefinition = "user_status")
  private Status status;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private Long version;

  protected User() {
    // JPA
  }

  private User(Builder builder) {
    this.id = builder.id != null ? builder.id : UUID.randomUUID();
    this.tenantId = Objects.requireNonNull(builder.tenantId, "Tenant ID is required");
    this.email = Objects.requireNonNull(builder.email, "Email is required");
    this.name = Objects.requireNonNull(builder.name, "Name is required");
    this.passwordHash = builder.passwordHash;
    this.status = builder.status != null ? builder.status : Status.PENDING;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static Builder builder() {
    return new Builder();
  }

  public UUID getId() {
    return id;
  }

  public UserId getUserId() {
    return UserId.of(id);
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getMfaSecret() {
    return mfaSecret;
  }

  public boolean isMfaEnabled() {
    return mfaSecret != null && !mfaSecret.isBlank();
  }

  public void enableMfa(String secret) {
    this.mfaSecret = Objects.requireNonNull(secret, "MFA secret cannot be null");
    this.updatedAt = Instant.now();
  }

  public void disableMfa() {
    this.mfaSecret = null;
    this.updatedAt = Instant.now();
  }

  public Status getStatus() {
    return status;
  }

  public int getFailedLoginAttempts() {
    return failedLoginAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }

  public boolean isAccountLocked() {
    return lockedUntil != null && Instant.now().isBefore(lockedUntil);
  }

  public void activate() {
    if (this.status == Status.LOCKED) {
      throw new IllegalStateException("Cannot activate a locked user");
    }
    this.status = Status.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public void deactivate() {
    this.status = Status.INACTIVE;
    this.updatedAt = Instant.now();
  }

  public void lock() {
    this.status = Status.LOCKED;
    this.updatedAt = Instant.now();
  }

  public void updateName(String newName) {
    this.name = Objects.requireNonNull(newName, "Name cannot be null");
    this.updatedAt = Instant.now();
  }

  public void updateEmail(String newEmail) {
    this.email = Objects.requireNonNull(newEmail, "Email cannot be null");
    this.updatedAt = Instant.now();
  }

  public void updatePasswordHash(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.updatedAt = Instant.now();
  }

  public void recordLogin() {
    this.lastLoginAt = Instant.now();
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.updatedAt = Instant.now();
  }

  public void recordFailedLogin(int maxAttempts, java.time.Duration lockDuration) {
    this.failedLoginAttempts++;
    if (this.failedLoginAttempts >= maxAttempts) {
      this.lockedUntil = Instant.now().plus(lockDuration);
      this.status = Status.LOCKED;
    }
    this.updatedAt = Instant.now();
  }

  public void resetFailedLoginAttempts() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.updatedAt = Instant.now();
  }

  public void unlockAccount() {
    this.lockedUntil = null;
    this.failedLoginAttempts = 0;
    if (this.status == Status.LOCKED) {
      this.status = Status.ACTIVE;
    }
    this.updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User user)) return false;
    return Objects.equals(id, user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "User{id=%s, email='%s', name='%s', status=%s}".formatted(id, email, name, status);
  }

  /** User account status. */
  public enum Status {
    /** User has been invited but hasn't completed registration */
    PENDING,
    /** User is active and can log in */
    ACTIVE,
    /** User has been deactivated (soft delete) */
    INACTIVE,
    /** User has been locked due to security concerns */
    LOCKED
  }

  public static class Builder {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String name;
    private String passwordHash;
    private Status status;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder email(String email) {
      this.email = email;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder passwordHash(String passwordHash) {
      this.passwordHash = passwordHash;
      return this;
    }

    public Builder status(Status status) {
      this.status = status;
      return this;
    }

    public User build() {
      return new User(this);
    }
  }
}
