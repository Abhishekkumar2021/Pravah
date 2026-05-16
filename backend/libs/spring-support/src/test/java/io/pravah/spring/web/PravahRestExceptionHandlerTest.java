package io.pravah.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pravah.common.exception.AuthenticationException;
import io.pravah.common.exception.EntityNotFoundException;
import io.pravah.common.exception.InvalidStateTransitionException;
import io.pravah.common.exception.QuotaExceededException;
import io.pravah.common.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class PravahRestExceptionHandlerTest {

  private final PravahRestExceptionHandler handler = new PravahRestExceptionHandler();

  @Test
  void handleAuthentication_returnsUnauthorized() {
    var pd = handler.handleAuthentication(new AuthenticationException("Invalid credentials"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(pd.getProperties()).containsKey("errorCode");
  }

  @Test
  void handleNotFound_returnsProblemDetail() {
    var pd = handler.handleNotFound(new EntityNotFoundException("Schedule", UUID.randomUUID()));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(pd.getProperties()).containsKey("entityType");
  }

  @Test
  void handleInvalidTransition_includesStates() {
    var pd =
        handler.handleInvalidTransition(
            new InvalidStateTransitionException("Run", "id", "pending", "running"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(pd.getProperties()).containsEntry("currentState", "pending");
  }

  @Test
  void handleValidation_includesFieldErrors() {
    var pd = handler.handleValidation(ValidationException.of("cron", "invalid"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(pd.getProperties()).containsKey("fieldErrors");
  }

  @Test
  void handlePravahException_returnsConfiguredStatus() {
    var pd = handler.handlePravahException(new QuotaExceededException("runs", 10, 5));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(pd.getProperties()).containsKey("errorCode");
  }

  @Test
  void handleOptimisticLock_returnsConflict() {
    var pd = handler.handleOptimisticLock(new ObjectOptimisticLockingFailureException("x", null));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(pd.getProperties()).containsEntry("errorCode", "OPTIMISTIC_LOCK_CONFLICT");
  }

  @Test
  void handleMethodArgumentNotValid_returnsBadRequest() throws Exception {
    var bindingResult = mock(BindingResult.class);
    when(bindingResult.getFieldErrors())
        .thenReturn(List.of(new FieldError("req", "name", "must not be blank")));
    var ex = new MethodArgumentNotValidException(null, bindingResult);

    var pd = handler.handleMethodArgumentNotValid(ex);

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(pd.getDetail()).contains("name");
  }

  @Test
  void handleBadRequest_returns400() {
    var pd = handler.handleBadRequest(new IllegalArgumentException("bad input"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void handleIllegalState_returns500() {
    var pd = handler.handleInternalError(new IllegalStateException("no tenant"));

    assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
  }
}
