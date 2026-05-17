package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  Optional<EmailVerificationToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

  @Modifying
  @Query("DELETE FROM EmailVerificationToken t WHERE t.userId = :userId AND t.usedAt IS NULL")
  void deleteUnusedByUserId(@Param("userId") UUID userId);
}
