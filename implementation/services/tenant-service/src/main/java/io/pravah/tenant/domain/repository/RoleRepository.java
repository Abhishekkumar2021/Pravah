package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Role entities. */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

  /** Find all system roles (shared across all tenants). */
  @Query("SELECT r FROM Role r WHERE r.isSystem = true")
  List<Role> findSystemRoles();

  /** Find custom roles for a specific tenant. */
  List<Role> findByTenantId(UUID tenantId);

  /** Find all roles available to a tenant (system + custom). */
  @Query("SELECT r FROM Role r WHERE r.isSystem = true OR r.tenantId = :tenantId")
  List<Role> findAvailableRoles(UUID tenantId);
}
