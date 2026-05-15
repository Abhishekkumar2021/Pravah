package io.pravah.tenant.domain.repository;

import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.TenantMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for TenantMember entities. */
@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, TenantMemberId> {

  List<TenantMember> findByTenantId(UUID tenantId);

  List<TenantMember> findByUserId(UUID userId);

  Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

  /** Count owners in a tenant (should always be at least 1). */
  @Query(
      "SELECT COUNT(m) FROM TenantMember m WHERE m.tenantId = :tenantId AND m.roleId = :ownerRoleId")
  long countOwners(UUID tenantId, UUID ownerRoleId);
}
