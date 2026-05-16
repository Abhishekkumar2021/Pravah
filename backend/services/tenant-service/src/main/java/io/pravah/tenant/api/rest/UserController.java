package io.pravah.tenant.api.rest;

import io.pravah.tenant.application.dto.CreateUserRequest;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.application.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST API for user management within the current organization context. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    UserResponse response = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public ResponseEntity<List<UserResponse>> listUsers() {
    List<UserResponse> users = userService.listUsers();
    return ResponseEntity.ok(users);
  }

  @GetMapping("/{userId}")
  public ResponseEntity<UserResponse> getUser(@PathVariable UUID userId) {
    UserResponse response = userService.getUser(userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/by-email")
  public ResponseEntity<UserResponse> getUserByEmail(@RequestParam String email) {
    UserResponse response = userService.getUserByEmail(email);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/name")
  public ResponseEntity<UserResponse> updateName(
      @PathVariable UUID userId, @Valid @RequestBody UpdateNameRequest request) {
    UserResponse response = userService.updateUserName(userId, request.name());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{userId}/activate")
  public ResponseEntity<UserResponse> activateUser(@PathVariable UUID userId) {
    UserResponse response = userService.activateUser(userId);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{userId}/deactivate")
  public ResponseEntity<UserResponse> deactivateUser(@PathVariable UUID userId) {
    UserResponse response = userService.deactivateUser(userId);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{userId}/lock")
  public ResponseEntity<UserResponse> lockUser(@PathVariable UUID userId) {
    UserResponse response = userService.lockUser(userId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{userId}/password")
  public ResponseEntity<Void> updatePassword(
      @PathVariable UUID userId, @Valid @RequestBody UpdatePasswordRequest request) {
    userService.updatePassword(userId, request.currentPassword(), request.newPassword());
    return ResponseEntity.ok().build();
  }

  record UpdateNameRequest(@jakarta.validation.constraints.NotBlank String name) {}

  record UpdatePasswordRequest(
      @jakarta.validation.constraints.NotBlank String currentPassword,
      @jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Size(
              min = 8,
              message = "Password must be at least 8 characters")
          String newPassword) {}
}
