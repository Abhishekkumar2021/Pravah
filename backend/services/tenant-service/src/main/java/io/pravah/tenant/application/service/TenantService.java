package io.pravah.tenant.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.tenant.application.dto.CreateTenantRequest;
import io.pravah.tenant.application.dto.TenantResponse;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.Tenant;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.TenantRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import io.pravah.tenant.infrastructure.cache.TenantConfigCache;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for tenant management.
 *
 * <p>Handles tenant lifecycle: creation, updates, and member management. Uses Redis caching for
 * tenant configuration per ADR-012.
 */
@Service
@Transactional
public class TenantService {

  private static final Logger log = LoggerFactory.getLogger(TenantService.class);

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final TenantMemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final Optional<TenantConfigCache> tenantConfigCache;

  public TenantService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      TenantMemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      Optional<TenantConfigCache> tenantConfigCache) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantConfigCache = tenantConfigCache;
  }

  /**
   * Creates a new tenant with an owner user.
   *
   * @param request the creation request
   * @return the created tenant
   * @throws ValidationException if slug already exists
   */
  public TenantResponse createTenant(CreateTenantRequest request) {
    log.info("Creating tenant", kv("slug", request.slug()));

    if (tenantRepository.existsBySlug(request.slug())) {
      throw ValidationException.of("slug", "Tenant slug already exists");
    }

    Tenant tenant =
        Tenant.builder()
            .name(request.name())
            .slug(request.slug())
            .tier(request.tier() != null ? request.tier() : Tenant.Tier.FREE)
            .build();

    tenant = tenantRepository.save(tenant);
    log.debug("Created tenant", kv("tenant_id", tenant.getId()));

    User owner =
        User.builder()
            .tenantId(tenant.getId())
            .email(request.ownerEmail())
            .name(request.ownerName())
            .passwordHash(
                request.ownerPassword() != null
                    ? passwordEncoder.encode(request.ownerPassword())
                    : null)
            .status(User.Status.ACTIVE)
            .build();

    owner = userRepository.save(owner);
    log.debug("Created owner user", kv("user_id", owner.getId()), kv("email", owner.getEmail()));

    TenantMember membership = TenantMember.createOwner(tenant.getId(), owner.getId());
    memberRepository.save(membership);
    log.debug("Assigned owner role", kv("user_id", owner.getId()));

    log.info(
        "Tenant created successfully",
        kv("tenant_id", tenant.getId()),
        kv("slug", tenant.getSlug()),
        kv("owner_id", owner.getId()));
    return TenantResponse.from(tenant);
  }

  /**
   * Gets a tenant by ID.
   *
   * <p>Uses cache-aside pattern: check cache first, then database on miss.
   *
   * @param tenantId the tenant ID
   * @return the tenant
   * @throws EntityNotFoundException if not found
   */
  @Transactional(readOnly = true)
  public TenantResponse getTenant(UUID tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant", tenantId));

    tenantConfigCache.ifPresent(cache -> cache.put(tenant));

    return TenantResponse.from(tenant);
  }

  /**
   * Gets a tenant by slug.
   *
   * @param slug the tenant slug
   * @return the tenant
   * @throws EntityNotFoundException if not found
   */
  @Transactional(readOnly = true)
  public TenantResponse getTenantBySlug(String slug) {
    Tenant tenant =
        tenantRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Tenant with slug: " + slug));
    return TenantResponse.from(tenant);
  }

  /**
   * Updates a tenant's name.
   *
   * <p>Invalidates cache after update to ensure consistency.
   *
   * @param tenantId the tenant ID
   * @param newName the new name
   * @return the updated tenant
   */
  public TenantResponse updateTenantName(UUID tenantId, String newName) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant", tenantId));

    tenant.updateName(newName);
    tenant = tenantRepository.save(tenant);

    tenantConfigCache.ifPresent(cache -> cache.invalidate(tenantId));

    log.info("Updated tenant name", kv("tenant_id", tenantId), kv("new_name", newName));
    return TenantResponse.from(tenant);
  }

  /**
   * Updates a tenant's tier.
   *
   * <p>Invalidates cache after update to ensure rate limits are immediately effective.
   *
   * @param tenantId the tenant ID
   * @param newTier the new tier
   * @return the updated tenant
   */
  public TenantResponse updateTenantTier(UUID tenantId, Tenant.Tier newTier) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant", tenantId));

    Tenant.Tier oldTier = tenant.getTier();
    tenant.updateTier(newTier);
    tenant = tenantRepository.save(tenant);

    tenantConfigCache.ifPresent(cache -> cache.invalidate(tenantId));

    log.info(
        "Updated tenant tier",
        kv("tenant_id", tenantId),
        kv("old_tier", oldTier),
        kv("new_tier", newTier));
    return TenantResponse.from(tenant);
  }

  /**
   * Adds a member to a tenant.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @param roleId the role ID
   */
  public void addMember(UUID tenantId, UUID userId, UUID roleId) {
    if (!tenantRepository.existsById(tenantId)) {
      throw new EntityNotFoundException("Tenant", tenantId);
    }
    if (!userRepository.existsById(userId)) {
      throw new EntityNotFoundException("User", userId);
    }

    TenantMember member = new TenantMember(tenantId, userId, roleId);
    memberRepository.save(member);

    log.info(
        "Added member to tenant",
        kv("tenant_id", tenantId),
        kv("user_id", userId),
        kv("role_id", roleId));
  }

  /**
   * Removes a member from a tenant.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @throws ValidationException if trying to remove the last owner
   */
  public void removeMember(UUID tenantId, UUID userId) {
    TenantMember member =
        memberRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant member"));

    if (member.isOwner() && memberRepository.countOwners(tenantId, Role.OWNER_ROLE_ID) <= 1) {
      throw ValidationException.of("userId", "Cannot remove the last owner of a tenant");
    }

    memberRepository.delete(member);
    log.info("Removed member from tenant", kv("tenant_id", tenantId), kv("user_id", userId));
  }

  /**
   * Changes a member's role.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @param newRoleId the new role ID
   * @throws ValidationException if demoting the last owner
   */
  public void changeMemberRole(UUID tenantId, UUID userId, UUID newRoleId) {
    TenantMember member =
        memberRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Tenant member"));

    if (member.isOwner()
        && !Role.OWNER_ROLE_ID.equals(newRoleId)
        && memberRepository.countOwners(tenantId, Role.OWNER_ROLE_ID) <= 1) {
      throw ValidationException.of("roleId", "Cannot demote the last owner of a tenant");
    }

    UUID oldRoleId = member.getRoleId();
    member.changeRole(newRoleId);
    memberRepository.save(member);

    log.info(
        "Changed member role",
        kv("tenant_id", tenantId),
        kv("user_id", userId),
        kv("old_role_id", oldRoleId),
        kv("new_role_id", newRoleId));
  }
}
