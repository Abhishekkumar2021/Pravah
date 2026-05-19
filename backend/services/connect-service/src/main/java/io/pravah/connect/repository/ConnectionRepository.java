package io.pravah.connect.repository;

import io.pravah.connect.domain.Connection;
import io.pravah.connect.domain.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Connection entities.
 */
@Repository
public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    List<Connection> findByTenantId(UUID tenantId);

    List<Connection> findByTenantIdAndConnectorId(UUID tenantId, String connectorId);

    List<Connection> findByTenantIdAndStatus(UUID tenantId, ConnectionStatus status);

    Optional<Connection> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Connection> findByTenantIdAndName(UUID tenantId, String name);

    @Query("SELECT c FROM Connection c WHERE c.tenantId = :tenantId AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Connection> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("search") String search);

    boolean existsByTenantIdAndName(UUID tenantId, String name);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, ConnectionStatus status);
}
