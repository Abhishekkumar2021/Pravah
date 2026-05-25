package io.pravah.pipeline.infrastructure.persistence.repository;

import io.pravah.pipeline.infrastructure.persistence.entity.ConnectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionRepository extends JpaRepository<ConnectionEntity, UUID> {

  List<ConnectionEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<ConnectionEntity> findByTenantIdAndName(UUID tenantId, String name);

  boolean existsByTenantIdAndName(UUID tenantId, String name);
}
