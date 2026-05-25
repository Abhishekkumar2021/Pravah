package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.PasswordResetToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);
}
