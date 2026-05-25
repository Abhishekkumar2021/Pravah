package io.pravah.tenant.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.tenant.application.dto.TenantResponse;
import io.pravah.tenant.application.service.TenantService;
import io.pravah.tenant.domain.model.Tenant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID ROLE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Mock private TenantService tenantService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new TenantController(tenantService)).build();
  }

  @Test
  void createTenant_returnsCreated() throws Exception {
    when(tenantService.createTenant(any())).thenReturn(sampleResponse());

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Acme",
                      "slug": "acme",
                      "ownerEmail": "o@acme.com",
                      "ownerName": "Owner",
                      "ownerPassword": "Password1!"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("dev-local"));
  }

  @Test
  void getTenant_returnsOk() throws Exception {
    when(tenantService.getTenant(TENANT_ID)).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/v1/tenants/{tenantId}", TENANT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()));
  }

  @Test
  void getTenantBySlug_returnsOk() throws Exception {
    when(tenantService.getTenantBySlug("dev-local")).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/v1/tenants/by-slug/{slug}", "dev-local"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("dev-local"));
  }

  @Test
  void updateName_returnsOk() throws Exception {
    when(tenantService.updateTenantName(eq(TENANT_ID), eq("New Name")))
        .thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/v1/tenants/{tenantId}/name", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void updateTier_returnsOk() throws Exception {
    when(tenantService.updateTenantTier(eq(TENANT_ID), eq(Tenant.Tier.ENTERPRISE)))
        .thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/v1/tenants/{tenantId}/tier", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tier\": \"ENTERPRISE\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void addMember_returnsCreated() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tenants/{tenantId}/members", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"%s\", \"roleId\": \"%s\"}".formatted(USER_ID, ROLE_ID)))
        .andExpect(status().isCreated());

    verify(tenantService).addMember(TENANT_ID, USER_ID, ROLE_ID);
  }

  @Test
  void removeMember_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/tenants/{tenantId}/members/{userId}", TENANT_ID, USER_ID))
        .andExpect(status().isNoContent());

    verify(tenantService).removeMember(TENANT_ID, USER_ID);
  }

  @Test
  void changeMemberRole_returnsOk() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/tenants/{tenantId}/members/{userId}/role", TENANT_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\": \"%s\"}".formatted(ROLE_ID)))
        .andExpect(status().isOk());

    verify(tenantService).changeMemberRole(TENANT_ID, USER_ID, ROLE_ID);
  }

  private static TenantResponse sampleResponse() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new TenantResponse(TENANT_ID, "Dev", "dev-local", Tenant.Tier.TEAM, now, now);
  }
}
