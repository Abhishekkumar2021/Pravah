package io.pravah.tenant.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Single-use email verification token (US-10.01). Stores SHA-256 hash only. */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EmailVerificationToken() {}

  private EmailVerificationToken(UUID userId, String tokenHash, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    this.createdAt = Instant.now();
  }

  public static EmailVerificationToken create(UUID userId, String tokenHash, Instant expiresAt) {
    return new EmailVerificationToken(userId, tokenHash, expiresAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public void markUsed(Instant now) {
    if (usedAt != null) {
      throw new IllegalStateException("Token already used");
    }
    this.usedAt = now;
  }
}
