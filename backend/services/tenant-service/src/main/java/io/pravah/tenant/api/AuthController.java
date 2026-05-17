package io.pravah.tenant.api;

import io.pravah.tenant.application.dto.AuthTokenResponse;
import io.pravah.tenant.application.dto.ConfirmPasswordResetRequest;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.PasswordResetRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.application.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (US-10.01).
 *
 * <p>Rate limiting is handled at the API Gateway level per ADR-012 (Redis token bucket). These
 * endpoints contain no rate limiting logic.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/password-reset/request")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
    authService.requestPasswordReset(request.email());
  }

  @PostMapping("/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(@Valid @RequestBody ConfirmPasswordResetRequest request) {
    authService.confirmPasswordReset(request);
  }
}
