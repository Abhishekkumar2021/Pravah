package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for User entities. RLS policies filter by tenant_id automatically. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

  Optional<User> findByEmail(String email);

  List<User> findByTenantId(UUID tenantId);

  boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
