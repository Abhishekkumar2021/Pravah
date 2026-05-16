package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.ApiToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for API tokens. RLS filters by tenant_id when context is set. */
@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {

  List<ApiToken> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ApiToken> findByTokenHash(String tokenHash);
}
