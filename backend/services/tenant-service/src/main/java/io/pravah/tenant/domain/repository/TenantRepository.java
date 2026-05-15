package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for Tenant entities. */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  Optional<Tenant> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
