package io.pravah.tenant.api.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.application.dto.UserRoleSummary;
import io.pravah.tenant.application.service.UserService;
import io.pravah.tenant.domain.model.User;
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
class UserControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID ROLE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  @Mock private UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
  }

  @Test
  void createUser_returnsCreated() throws Exception {
    when(userService.createUser(any())).thenReturn(sampleResponse());

    mockMvc
        .perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "dev@localhost.pravah",
                      "name": "Dev",
                      "password": "Password1!"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("dev@localhost.pravah"));
  }

  @Test
  void listUsers_returnsOk() throws Exception {
    when(userService.listUsers()).thenReturn(List.of(sampleResponse()));

    mockMvc
        .perform(get("/api/v1/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(USER_ID.toString()));
  }

  @Test
  void getUser_returnsOk() throws Exception {
    when(userService.getUser(USER_ID)).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/v1/users/{userId}", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Dev User"));
  }

  @Test
  void getUserByEmail_returnsOk() throws Exception {
    when(userService.getUserByEmail("dev@localhost.pravah")).thenReturn(sampleResponse());

    mockMvc
        .perform(get("/api/v1/users/by-email").param("email", "dev@localhost.pravah"))
        .andExpect(status().isOk());
  }

  @Test
  void updateName_returnsOk() throws Exception {
    when(userService.updateUserName(eq(USER_ID), eq("New Name"))).thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/v1/users/{userId}/name", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void assignRole_returnsOk() throws Exception {
    when(userService.assignRole(USER_ID, ROLE_ID)).thenReturn(sampleResponse());

    mockMvc
        .perform(
            patch("/api/v1/users/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\": \"%s\"}".formatted(ROLE_ID)))
        .andExpect(status().isOk());
  }

  @Test
  void activateUser_returnsOk() throws Exception {
    when(userService.activateUser(USER_ID)).thenReturn(sampleResponse());

    mockMvc.perform(post("/api/v1/users/{userId}/activate", USER_ID)).andExpect(status().isOk());
  }

  @Test
  void deactivateUser_returnsOk() throws Exception {
    when(userService.deactivateUser(USER_ID)).thenReturn(sampleResponse());

    mockMvc.perform(post("/api/v1/users/{userId}/deactivate", USER_ID)).andExpect(status().isOk());
  }

  @Test
  void lockUser_returnsOk() throws Exception {
    when(userService.lockUser(USER_ID)).thenReturn(sampleResponse());

    mockMvc.perform(post("/api/v1/users/{userId}/lock", USER_ID)).andExpect(status().isOk());
  }

  @Test
  void updatePassword_returnsOk() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/{userId}/password", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "OldPassword1!",
                      "newPassword": "NewPassword1!"
                    }
                    """))
        .andExpect(status().isOk());

    verify(userService).updatePassword(USER_ID, "OldPassword1!", "NewPassword1!");
  }

  private static UserResponse sampleResponse() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new UserResponse(
        USER_ID,
        TENANT_ID,
        "dev@localhost.pravah",
        "Dev User",
        User.Status.ACTIVE,
        new UserRoleSummary(ROLE_ID, "Editor"),
        null,
        now);
  }
}
