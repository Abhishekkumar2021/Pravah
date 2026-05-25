package io.pravah.tenant.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.tenant.application.dto.ApiTokenResponse;
import io.pravah.tenant.application.dto.CreateApiTokenResponse;
import io.pravah.tenant.application.service.ApiTokenService;
import java.time.Instant;
import java.util.List;
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
class ApiTokenControllerTest {

  private static final UUID TOKEN_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Mock private ApiTokenService apiTokenService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ApiTokenController(apiTokenService)).build();
  }

  @Test
  void createToken_returnsCreatedWithSecret() throws Exception {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    var token =
        new ApiTokenResponse(
            TOKEN_ID,
            USER_ID,
            "CI",
            List.of("pipelines:read"),
            expiresAt,
            null,
            Instant.now(),
            null);
    when(apiTokenService.createToken(any()))
        .thenReturn(new CreateApiTokenResponse(token, "prv_1_secret"));

    mockMvc
        .perform(
            post("/api/v1/api-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CI",
                      "permissions": ["pipelines:read"],
                      "expiresAt": "%s"
                    }
                    """
                        .formatted(expiresAt)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.secret").value("prv_1_secret"))
        .andExpect(jsonPath("$.token.name").value("CI"));
  }

  @Test
  void listTokens_returnsOk() throws Exception {
    when(apiTokenService.listTokens()).thenReturn(List.of());

    mockMvc.perform(get("/api/v1/api-tokens")).andExpect(status().isOk());
  }

  @Test
  void revokeToken_returnsOk() throws Exception {
    var token =
        new ApiTokenResponse(
            TOKEN_ID,
            USER_ID,
            "CI",
            List.of("pipelines:read"),
            Instant.now().plusSeconds(3600),
            null,
            Instant.now(),
            Instant.now());
    when(apiTokenService.revokeToken(TOKEN_ID)).thenReturn(token);

    mockMvc
        .perform(delete("/api/v1/api-tokens/{tokenId}", TOKEN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revokedAt").exists());
  }

  @Test
  void createToken_blankName_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/api-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "permissions": ["pipelines:read"]
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createToken_emptyPermissions_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/api-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CI Token",
                      "permissions": []
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
