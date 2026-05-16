package io.pravah.tenant.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.pravah.common.exception.AccessDeniedException;
import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.ConcurrencyException;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.common.exception.PravahException;
import io.pravah.common.exception.QuotaExceededException;
import io.pravah.common.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  void handleNotFound_returns404() {
    ProblemDetail pd = handler.handleNotFound(new EntityNotFoundException("User", UUID.randomUUID()));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(pd.getTitle()).isEqualTo("Resource Not Found");
    assertThat(pd.getProperties()).containsKey("errorCode");
  }

  @Test
  void handleValidation_returns400WithFieldErrors() {
    ProblemDetail pd =
        handler.handleValidation(ValidationException.of("email", "Email already exists"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(pd.getProperties()).containsKey("fieldErrors");
  }

  @Test
  void handleAuthentication_returns401() {
    ProblemDetail pd =
        handler.handleAuthentication(new AuthenticationException("Invalid credentials"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void handleAccessDenied_returns403() {
    ProblemDetail pd = handler.handleAccessDenied(new AccessDeniedException("users", "write"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void handleConcurrency_returns409() {
    UUID id = UUID.randomUUID();
    ProblemDetail pd = handler.handleConcurrency(new ConcurrencyException("User", id.toString()));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void handleInvalidStateTransition_returns409() {
    ProblemDetail pd =
        handler.handleInvalidStateTransition(
            new InvalidStateTransitionException(
                "Execution", UUID.randomUUID().toString(), "pending", "run"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void handleQuotaExceeded_returns429() {
    ProblemDetail pd = handler.handleQuotaExceeded(new QuotaExceededException("runs", 11, 10));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void handleBeanValidation_mapsBindingErrors() throws Exception {
    var target = new Object();
    var bindingResult = new BeanPropertyBindingResult(target, "target");
    bindingResult.addError(new FieldError("target", "email", "must not be blank"));
    var ex =
        new MethodArgumentNotValidException(null, bindingResult);

    ProblemDetail pd = handler.handleBeanValidation(ex);

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(pd.getProperties()).containsKey("fieldErrors");
  }

  @Test
  void handlePravahException_usesSuggestedStatus() {
    ProblemDetail pd =
        handler.handlePravahException(
            new PravahException("CUSTOM", "Something failed") {
              @Override
              public int suggestedHttpStatus() {
                return 418;
              }
            });

    assertThat(pd.getStatus()).isEqualTo(418);
  }

  @Test
  void handleUnexpected_returns500() {
    ProblemDetail pd = handler.handleUnexpected(new RuntimeException("boom"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(pd.getDetail()).doesNotContain("boom");
  }
}
