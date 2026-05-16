package io.pravah.tenant.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.application.service.RoleService;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.infrastructure.security.JwtTokenIssuer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DevAuthControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  /** Non-production test value only; not a real credential. */
  private static final String TEST_DEV_GUARD = "unit-test-dev-guard-value";

  private JwtTokenIssuer jwtTokenIssuer;
  private RoleService roleService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    jwtTokenIssuer = mock(JwtTokenIssuer.class);
    roleService = mock(RoleService.class);
    when(roleService.resolveAuthorization(TENANT_ID, USER_ID))
        .thenReturn(
            new RoleService.UserAuthorization(
                new UserRoleSummary(Role.OWNER_ROLE_ID, "owner"), List.of("*")));
    DevAuthController controller =
        new DevAuthController(jwtTokenIssuer, roleService, TENANT_ID, USER_ID, "");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void mintDevToken_usesDefaultsWhenBodyOmitted() throws Exception {
    when(jwtTokenIssuer.generateAccessToken(
            eq(USER_ID), eq(TENANT_ID), eq(List.of("owner")), eq(List.of("*"))))
        .thenReturn("test.jwt.token");

    mockMvc
        .perform(post("/api/v1/auth/dev-token").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("test.jwt.token"))
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()));
  }

  @Test
  void mintDevToken_acceptsExplicitIds() throws Exception {
    UUID otherUser = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    UUID otherTenant = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    when(roleService.resolveAuthorization(otherTenant, otherUser))
        .thenReturn(
            new RoleService.UserAuthorization(
                new UserRoleSummary(Role.VIEWER_ROLE_ID, "viewer"),
                List.of("pipelines:read", "executions:read")));
    when(jwtTokenIssuer.generateAccessToken(
            eq(otherUser),
            eq(otherTenant),
            eq(List.of("viewer")),
            eq(List.of("pipelines:read", "executions:read"))))
        .thenReturn("other.jwt");

    mockMvc
        .perform(
            post("/api/v1/auth/dev-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"userId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","tenantId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("other.jwt"));
  }

  @Test
  void mintDevToken_whenSecretConfigured_rejectsMissingHeader() throws Exception {
    DevAuthController secured =
        new DevAuthController(jwtTokenIssuer, roleService, TENANT_ID, USER_ID, TEST_DEV_GUARD);
    MockMvc securedMvc = MockMvcBuilders.standaloneSetup(secured).build();

    securedMvc
        .perform(post("/api/v1/auth/dev-token").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void mintDevToken_whenSecretConfigured_acceptsMatchingHeader() throws Exception {
    when(jwtTokenIssuer.generateAccessToken(
            eq(USER_ID), eq(TENANT_ID), eq(List.of("owner")), eq(List.of("*"))))
        .thenReturn("test.jwt.token");
    DevAuthController secured =
        new DevAuthController(jwtTokenIssuer, roleService, TENANT_ID, USER_ID, TEST_DEV_GUARD);
    MockMvc securedMvc = MockMvcBuilders.standaloneSetup(secured).build();

    securedMvc
        .perform(
            post("/api/v1/auth/dev-token")
                .header("X-Pravah-Dev-Secret", TEST_DEV_GUARD)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("test.jwt.token"));
  }
}
