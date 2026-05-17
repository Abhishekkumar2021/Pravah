package io.pravah.pipeline.infrastructure.persistence.repository;

import io.pravah.pipeline.infrastructure.persistence.entity.TenantSecretEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSecretRepository extends JpaRepository<TenantSecretEntity, UUID> {

  List<TenantSecretEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<TenantSecretEntity> findByTenantIdAndName(UUID tenantId, String name);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
