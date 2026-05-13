package io.pravah.tenant.api.rest;

import io.pravah.tenant.application.dto.CreateTenantRequest;
import io.pravah.tenant.application.dto.TenantResponse;
import io.pravah.tenant.application.service.TenantService;
import io.pravah.tenant.domain.model.Tenant;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST API for tenant management. */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

  private final TenantService tenantService;

  public TenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<TenantResponse> createTenant(
      @Valid @RequestBody CreateTenantRequest request) {
    TenantResponse response = tenantService.createTenant(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{tenantId}")
  public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID tenantId) {
    TenantResponse response = tenantService.getTenant(tenantId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/by-slug/{slug}")
  public ResponseEntity<TenantResponse> getTenantBySlug(@PathVariable String slug) {
    TenantResponse response = tenantService.getTenantBySlug(slug);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{tenantId}/name")
  public ResponseEntity<TenantResponse> updateName(
      @PathVariable UUID tenantId, @Valid @RequestBody UpdateNameRequest request) {
    TenantResponse response = tenantService.updateTenantName(tenantId, request.name);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{tenantId}/tier")
  public ResponseEntity<TenantResponse> updateTier(
      @PathVariable UUID tenantId, @Valid @RequestBody UpdateTierRequest request) {
    TenantResponse response = tenantService.updateTenantTier(tenantId, request.tier);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{tenantId}/members")
  public ResponseEntity<Void> addMember(
      @PathVariable UUID tenantId, @Valid @RequestBody AddMemberRequest request) {
    tenantService.addMember(tenantId, request.userId, request.roleId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping("/{tenantId}/members/{userId}")
  public ResponseEntity<Void> removeMember(@PathVariable UUID tenantId, @PathVariable UUID userId) {
    tenantService.removeMember(tenantId, userId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{tenantId}/members/{userId}/role")
  public ResponseEntity<Void> changeMemberRole(
      @PathVariable UUID tenantId,
      @PathVariable UUID userId,
      @Valid @RequestBody ChangeRoleRequest request) {
    tenantService.changeMemberRole(tenantId, userId, request.roleId);
    return ResponseEntity.ok().build();
  }

  record UpdateNameRequest(@jakarta.validation.constraints.NotBlank String name) {}

  record UpdateTierRequest(@jakarta.validation.constraints.NotNull Tenant.Tier tier) {}

  record AddMemberRequest(
      @jakarta.validation.constraints.NotNull UUID userId,
      @jakarta.validation.constraints.NotNull UUID roleId) {}

  record ChangeRoleRequest(@jakarta.validation.constraints.NotNull UUID roleId) {}
}
