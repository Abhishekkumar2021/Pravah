package io.pravah.tenant.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.tenant.application.dto.AuthTokenResponse;
import io.pravah.tenant.application.dto.RegisterResponse;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.application.service.AuthService;
import io.pravah.tenant.domain.model.Role;
import io.pravah.tenant.domain.model.User;
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
class AuthControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
  }

  @Test
  void login_returnsToken() throws Exception {
    when(authService.login(any()))
        .thenReturn(
            new AuthTokenResponse(
                "jwt-token",
                USER_ID,
                TENANT_ID,
                Instant.parse("2026-05-16T12:00:00Z"),
                new UserResponse(
                    USER_ID,
                    TENANT_ID,
                    "dev@localhost.pravah",
                    "Dev",
                    User.Status.ACTIVE,
                    new UserRoleSummary(Role.OWNER_ROLE_ID, "owner"),
                    null,
                    Instant.parse("2026-01-01T00:00:00Z"))));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"dev@localhost.pravah","password":"PravahDev1!"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("jwt-token"));

    verify(authService).login(any());
  }

  @Test
  void register_returnsCreatedWithoutToken() throws Exception {
    when(authService.register(any()))
        .thenReturn(
            new RegisterResponse(
                "new@localhost.pravah", "Check your email for a verification link."));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"new@localhost.pravah","password":"PravahDev1!","name":"New User"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@localhost.pravah"))
        .andExpect(jsonPath("$.message").exists());

    verify(authService).register(any());
  }

  @Test
  void passwordResetRequest_returnsAccepted() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@localhost.pravah\"}"))
        .andExpect(status().isAccepted());

    verify(authService).requestPasswordReset("dev@localhost.pravah");
  }
}
