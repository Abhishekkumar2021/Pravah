package io.pravah.tenant.api;

import io.pravah.tenant.application.dto.AuthTokenResponse;
import io.pravah.tenant.application.dto.ConfirmPasswordResetRequest;
import io.pravah.tenant.application.dto.LoginRequest;
import io.pravah.tenant.application.dto.LoginResult;
import io.pravah.tenant.application.dto.PasswordResetRequest;
import io.pravah.tenant.application.dto.RegisterRequest;
import io.pravah.tenant.application.dto.RegisterResponse;
import io.pravah.tenant.application.dto.ResendVerificationRequest;
import io.pravah.tenant.application.dto.UserResponse;
import io.pravah.tenant.application.dto.VerifyEmailRequest;
import io.pravah.tenant.application.service.AuthService;
import io.pravah.tenant.infrastructure.security.RefreshTokenCookieSupport;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints (US-10.01).
 *
 * <p>Rate limiting is handled at the API Gateway level per ADR-012 (Redis token bucket). Refresh
 * tokens are issued as HttpOnly cookies; access tokens are returned in JSON for in-memory storage.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenCookieSupport refreshTokenCookieSupport;

  public AuthController(
      AuthService authService, RefreshTokenCookieSupport refreshTokenCookieSupport) {
    this.authService = authService;
    this.refreshTokenCookieSupport = refreshTokenCookieSupport;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.loginWithRefresh(request);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookieSupport.create(result.refreshToken()).toString())
        .body(result.access());
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthTokenResponse> refresh(
      @CookieValue(name = RefreshTokenCookieSupport.COOKIE_NAME, required = false)
          String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    LoginResult result = authService.refreshSession(refreshToken);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshTokenCookieSupport.create(result.refreshToken()).toString())
        .body(result.access());
  }

  @GetMapping("/me")
  public UserResponse me() {
    return authService.currentUser();
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization != null && authorization.startsWith("Bearer ")) {
      authService.logout(authorization.substring(7));
    }
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookieSupport.clear().toString())
        .build();
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request.token());
  }

  @PostMapping("/verify-email/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
    authService.resendVerificationEmail(request.email());
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
