package io.pravah.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.ValidationException;
import io.pravah.tenant.application.dto.CreateTenantRequest;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.Tenant;
import io.pravah.tenant.domain.model.TenantMember;
import io.pravah.tenant.domain.model.User;
import io.pravah.tenant.domain.repository.TenantMemberRepository;
import io.pravah.tenant.domain.repository.TenantRepository;
import io.pravah.tenant.domain.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private TenantRepository tenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private TenantMemberRepository memberRepository;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
  private TenantService tenantService;

  @BeforeEach
  void setUp() {
    tenantService =
        new TenantService(tenantRepository, userRepository, memberRepository, passwordEncoder);
  }

  @Test
  void createTenant_createsOwnerAndMembership() {
    when(tenantRepository.existsBySlug("acme")).thenReturn(false);
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    var response =
        tenantService.createTenant(
            new CreateTenantRequest(
                "Acme Corp", "acme", null, "owner@acme.com", "Owner", "Password1!"));

    assertThat(response.slug()).isEqualTo("acme");
    verify(memberRepository).save(any(TenantMember.class));
  }

  @Test
  void createTenant_rejectsDuplicateSlug() {
    when(tenantRepository.existsBySlug("acme")).thenReturn(true);

    assertThatThrownBy(
            () ->
                tenantService.createTenant(
                    new CreateTenantRequest(
                        "Acme", "acme", Tenant.Tier.FREE, "o@acme.com", "O", "Password1!")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void getTenant_returnsTenant() {
    Tenant tenant = sampleTenant();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

    assertThat(tenantService.getTenant(TENANT_ID).id()).isEqualTo(TENANT_ID);
  }

  @Test
  void getTenantBySlug_returnsTenant() {
    Tenant tenant = sampleTenant();
    when(tenantRepository.findBySlug("dev-local")).thenReturn(Optional.of(tenant));

    assertThat(tenantService.getTenantBySlug("dev-local").slug()).isEqualTo("dev-local");
  }

  @Test
  void updateTenantName_persistsChange() {
    Tenant tenant = sampleTenant();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(tenant)).thenReturn(tenant);

    assertThat(tenantService.updateTenantName(TENANT_ID, "Renamed").name()).isEqualTo("Renamed");
  }

  @Test
  void removeMember_succeedsWhenNotLastOwner() {
    TenantMember owner = TenantMember.createOwner(TENANT_ID, USER_ID);
    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(owner));
    when(memberRepository.countOwners(TENANT_ID, Role.OWNER_ROLE_ID)).thenReturn(2L);

    tenantService.removeMember(TENANT_ID, USER_ID);

    verify(memberRepository).delete(owner);
  }

  @Test
  void updateTenantTier_persistsChange() {
    Tenant tenant = sampleTenant();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(tenant)).thenReturn(tenant);

    assertThat(tenantService.updateTenantTier(TENANT_ID, Tenant.Tier.ENTERPRISE).tier())
        .isEqualTo(Tenant.Tier.ENTERPRISE);
  }

  @Test
  void removeMember_rejectsLastOwner() {
    TenantMember owner = TenantMember.createOwner(TENANT_ID, USER_ID);
    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(owner));
    when(memberRepository.countOwners(TENANT_ID, Role.OWNER_ROLE_ID)).thenReturn(1L);

    assertThatThrownBy(() -> tenantService.removeMember(TENANT_ID, USER_ID))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void changeMemberRole_rejectsDemotingLastOwner() {
    TenantMember owner = TenantMember.createOwner(TENANT_ID, USER_ID);
    when(memberRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
        .thenReturn(Optional.of(owner));
    when(memberRepository.countOwners(TENANT_ID, Role.OWNER_ROLE_ID)).thenReturn(1L);

    assertThatThrownBy(
            () -> tenantService.changeMemberRole(TENANT_ID, USER_ID, Role.VIEWER_ROLE_ID))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void addMember_validatesTenantAndUserExist() {
    when(tenantRepository.existsById(TENANT_ID)).thenReturn(false);

    assertThatThrownBy(
            () -> tenantService.addMember(TENANT_ID, USER_ID, Role.VIEWER_ROLE_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void addMember_persistsMembership() {
    when(tenantRepository.existsById(TENANT_ID)).thenReturn(true);
    when(userRepository.existsById(USER_ID)).thenReturn(true);

    tenantService.addMember(TENANT_ID, USER_ID, Role.EDITOR_ROLE_ID);

    ArgumentCaptor<TenantMember> captor = ArgumentCaptor.forClass(TenantMember.class);
    verify(memberRepository).save(captor.capture());
    assertThat(captor.getValue().getRoleId()).isEqualTo(Role.EDITOR_ROLE_ID);
  }

  private static Tenant sampleTenant() {
    return Tenant.builder().id(TENANT_ID).name("Dev").slug("dev-local").tier(Tenant.Tier.TEAM).build();
  }
}
